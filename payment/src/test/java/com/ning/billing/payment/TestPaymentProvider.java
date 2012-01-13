/*
 * Copyright 2010-2011 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.ning.billing.payment;

import static com.jayway.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.apache.commons.lang.RandomStringUtils;
import org.joda.time.DateTime;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import com.google.inject.Inject;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.MockAccountUserApi;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceCreationNotification;
import com.ning.billing.invoice.api.MockInvoicePaymentApi;
import com.ning.billing.invoice.model.DefaultInvoice;
import com.ning.billing.invoice.model.DefaultInvoiceItem;
import com.ning.billing.payment.api.PaymentError;
import com.ning.billing.payment.api.PaymentInfo;
import com.ning.billing.payment.setup.PaymentTestModule;
import com.ning.billing.util.eventbus.EventBus;
import com.ning.billing.util.eventbus.EventBus.EventBusException;

@Guice(modules = PaymentTestModule.class)
public class TestPaymentProvider {
    @Inject
    private EventBus eventBus;
    @Inject
    private RequestProcessor invoiceProcessor;
    @Inject
    protected MockAccountUserApi accountUserApi;
    @Inject
    protected MockInvoicePaymentApi invoicePaymentApi;

    private MockPaymentInfoReceiver paymentInfoReceiver;

    @BeforeMethod(alwaysRun = true)
    public void setUp() throws EventBusException {
        paymentInfoReceiver = new MockPaymentInfoReceiver();

        eventBus.start();
        eventBus.register(invoiceProcessor);
        eventBus.register(paymentInfoReceiver);
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        eventBus.stop();
    }

    protected Account createTestAccount() {
        String name = "First" + RandomStringUtils.random(5) + " " + "Last" + RandomStringUtils.random(5);
        String externalKey = "12345";
        return accountUserApi.createAccount(UUID.randomUUID(), externalKey, "user@example.com", name, name.length(), "123-456-7890", Currency.USD, 1, null, BigDecimal.ZERO);
    }

    @Test
    public void testSimpleInvoice() throws Exception {
        final Account account = createTestAccount();
        final Invoice invoice = createTestInvoice(account);

        eventBus.post(createNotificationFor(invoice));
        await().atMost(1, MINUTES).until(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                List<PaymentInfo> processedPayments = paymentInfoReceiver.getProcessedPayments();
                List<PaymentError> errors = paymentInfoReceiver.getErrors();

                return processedPayments.size() == 1 || errors.size() == 1;
            }
        });

        assertFalse(paymentInfoReceiver.getProcessedPayments().isEmpty());
        assertTrue(paymentInfoReceiver.getErrors().isEmpty());

        final PaymentInfo paymentInfo = paymentInfoReceiver.getProcessedPayments().get(0);
        final PaymentInfoRequest paymentInfoRequest = new PaymentInfoRequest(account.getId(), paymentInfo.getPaymentId());

        paymentInfoReceiver.clear();
        eventBus.post(paymentInfoRequest);
        await().atMost(5, MINUTES).until(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                List<PaymentInfo> processedPayments = paymentInfoReceiver.getProcessedPayments();
                List<PaymentError> errors = paymentInfoReceiver.getErrors();

                return processedPayments.size() == 1 || errors.size() == 1;
            }
        });

        assertFalse(paymentInfoReceiver.getProcessedPayments().isEmpty());
        assertTrue(paymentInfoReceiver.getErrors().isEmpty());
        assertEquals(paymentInfoReceiver.getProcessedPayments().get(0), paymentInfo);
    }

    protected Invoice createTestInvoice(final Account account) {
        final DateTime now = new DateTime();
        final UUID subscriptionId = UUID.randomUUID();
        final BigDecimal amount = new BigDecimal("10.00");
        final Invoice invoice = createInvoice(account, now, Currency.USD);

        invoice.add(new DefaultInvoiceItem(invoice.getId(), subscriptionId, now, now.plusMonths(1), "Test", amount, new BigDecimal("1.0"), Currency.USD));
        return invoice;
    }

    protected Invoice createInvoice(Account account,
                                    DateTime targetDate,
                                    Currency currency) {
        Invoice invoice = new DefaultInvoice(account.getId(), targetDate, currency);

        invoicePaymentApi.add(invoice);
        return invoice;
    }

    protected InvoiceCreationNotification createNotificationFor(final Invoice invoice) {
        return new InvoiceCreationNotification() {
            @Override
            public UUID getInvoiceId() {
                return invoice.getId();
            }

            @Override
            public DateTime getInvoiceCreationDate() {
                return invoice.getInvoiceDate();
            }

            @Override
            public Currency getCurrency() {
                return invoice.getCurrency();
            }

            @Override
            public BigDecimal getAmountOwed() {
                return invoice.getAmountOutstanding();
            }

            @Override
            public UUID getAccountId() {
                return invoice.getAccountId();
            }
        };
    }

}
