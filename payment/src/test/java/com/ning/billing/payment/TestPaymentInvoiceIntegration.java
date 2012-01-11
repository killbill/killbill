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
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.RandomStringUtils;
import org.joda.time.DateTime;
import org.skife.jdbi.v2.IDBI;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.user.AccountBuilder;
import com.ning.billing.account.dao.AccountDao;
import com.ning.billing.account.glue.AccountModule;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.dbi.MysqlTestingHelper;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoicePaymentApi;
import com.ning.billing.invoice.dao.InvoiceDao;
import com.ning.billing.invoice.glue.InvoiceModule;
import com.ning.billing.invoice.model.DefaultInvoice;
import com.ning.billing.invoice.model.DefaultInvoiceItem;
import com.ning.billing.payment.api.PaymentApi;
import com.ning.billing.payment.api.PaymentAttempt;
import com.ning.billing.payment.api.PaymentError;
import com.ning.billing.payment.api.PaymentInfo;
import com.ning.billing.payment.setup.PaymentTestModuleWithEmbeddedDb;
import com.ning.billing.util.eventbus.EventBus;
import com.ning.billing.util.eventbus.EventBus.EventBusException;

public class TestPaymentInvoiceIntegration {
    // create payment for received invoice and save it -- positive and negative
    // check that notification for payment attempt is created
    // check that invoice-payment is saved
    @Inject
    private EventBus eventBus;
    @Inject
    private RequestProcessor invoiceProcessor;
    @Inject
    protected AccountDao accountDao;
    @Inject
    protected InvoiceDao invoiceDao;
    @Inject
    protected InvoicePaymentApi invoicePaymentApi;
    @Inject
    protected PaymentApi paymentApi;

    private MockPaymentInfoReceiver paymentInfoReceiver;

    private IDBI dbi;
    private MysqlTestingHelper helper;

    @BeforeClass(alwaysRun = true)
    public void startMysql() throws IOException {
        final String accountddl = IOUtils.toString(MysqlTestingHelper.class.getResourceAsStream("/com/ning/billing/account/ddl.sql"));
        final String utilddl = IOUtils.toString(MysqlTestingHelper.class.getResourceAsStream("/com/ning/billing/util/ddl.sql"));
        final String invoiceddl = IOUtils.toString(MysqlTestingHelper.class.getResourceAsStream("/com/ning/billing/invoice/ddl.sql"));

        helper = new MysqlTestingHelper();
        helper.startMysql();
        helper.initDb(accountddl + "\n" + invoiceddl + "\n" + utilddl);
        dbi = helper.getDBI();
    }

    @AfterClass(alwaysRun = true)
    public void stopMysql() {
        helper.stopMysql();
    }

    @BeforeMethod(alwaysRun = true)
    public void setUp() throws EventBusException {
        Injector injector = Guice.createInjector(new PaymentTestModuleWithEmbeddedDb(),
                                                 new AccountModule(),
                                                 new InvoiceModule(),
                                                 new AbstractModule() {
                                                    @Override
                                                    protected void configure() {
                                                        bind(IDBI.class).toInstance(dbi);
                                                    }
                                                });
        injector.injectMembers(this);

        paymentInfoReceiver = new MockPaymentInfoReceiver();

        eventBus.start();
        eventBus.register(invoiceProcessor);
        eventBus.register(paymentInfoReceiver);

    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        eventBus.stop();
    }

    @Test(enabled = false)
    protected Account createTestAccount() {
        final String name = "First" + RandomStringUtils.random(5) + " " + "Last" + RandomStringUtils.random(5);
        final Account account = new AccountBuilder(UUID.randomUUID()).name(name)
                                                                     .firstNameLength(name.length())
                                                                     .externalKey("12345")
                                                                     .phone("123-456-7890")
                                                                     .email("user@example.com")
                                                                     .currency(Currency.USD)
                                                                     .billingCycleDay(1)
                                                                     .build();
        accountDao.create(account);
        return account;
    }

    @Test(enabled = false)
    protected Invoice createTestInvoice(Account account,
                                        DateTime targetDate,
                                        Currency currency,
                                        InvoiceItem... items) {
        Invoice invoice = new DefaultInvoice(UUID.randomUUID(), account.getId(), new DateTime(), targetDate, currency, null, new BigDecimal("0"));

        for (InvoiceItem item : items) {
            invoice.add(new DefaultInvoiceItem(invoice.getId(),
                                               item.getSubscriptionId(),
                                               item.getStartDate(),
                                               item.getEndDate(),
                                               item.getDescription(),
                                               item.getAmount(),
                                               item.getRate(),
                                               item.getCurrency()));
        }
        invoiceDao.create(invoice);
        return invoice;
    }

    @Test
    public void testInvoiceIntegration() throws Exception {
        final DateTime now = new DateTime();
        final Account account = createTestAccount();
        final UUID subscriptionId = UUID.randomUUID();
        final BigDecimal amount = new BigDecimal("10.00");
        final InvoiceItem item = new DefaultInvoiceItem(null, subscriptionId, now, now.plusMonths(1), "Test", amount, new BigDecimal("1.0"), Currency.USD);
        final Invoice invoice = createTestInvoice(account, now, Currency.USD, item);

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

        List<PaymentInfo> payments = paymentInfoReceiver.getProcessedPayments();
        PaymentAttempt paymentAttempt = paymentApi.getPaymentAttemptForPaymentId(payments.get(0).getId());
        Assert.assertNotNull(paymentAttempt);

        Invoice invoiceForPayment = invoicePaymentApi.getInvoiceForPaymentAttemptId(paymentAttempt.getPaymentAttemptId());
    }
}
