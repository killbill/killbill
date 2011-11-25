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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.joda.time.DateTime;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.model.Invoice;
import com.ning.billing.invoice.model.InvoiceItem;
import com.ning.billing.payment.setup.PaymentTestModule;
import com.ning.billing.util.eventbus.IEventBus;
import com.ning.billing.util.eventbus.IEventBus.EventBusException;

@Guice(modules = PaymentTestModule.class)
public class TestPaymentProvider {
    private static class MockPaymentProcessor {
        private final List<PaymentInfo> processedPayments = Collections.synchronizedList(new ArrayList<PaymentInfo>());
        private final List<PaymentError> errors = Collections.synchronizedList(new ArrayList<PaymentError>());

        @Subscribe
        public void processedPayment(PaymentInfo paymentInfo) {
            processedPayments.add(paymentInfo);
        }

        @Subscribe
        public void processedPaymentError(PaymentError paymentError) {
            errors.add(paymentError);
        }

        public List<PaymentInfo> getProcessedPayments() {
            return new ArrayList<PaymentInfo>(processedPayments);
        }

        public List<PaymentError> getErrors() {
            return new ArrayList<PaymentError>(errors);
        }
    }

    @Inject
    private IEventBus eventBus;
    private MockPaymentProcessor mockPaymentProcessor;
    @Inject
    private InvoiceProcessor invoiceProcessor;

    @BeforeMethod(alwaysRun = true)
    public void setUp() throws EventBusException {
        mockPaymentProcessor = new MockPaymentProcessor();

        eventBus.start();
        eventBus.register(invoiceProcessor);
        eventBus.register(mockPaymentProcessor);
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        eventBus.stop();
    }

    @Test
    public void testSimpleInvoice() throws Exception {
        final UUID subscriptionUuid = UUID.randomUUID();
        final UUID invoiceUuid = UUID.randomUUID();
        final DateTime now = new DateTime();
        final InvoiceItem lineItem = new InvoiceItem(invoiceUuid,
                                                     subscriptionUuid,
                                                     now,
                                                     now.plusMonths(1),
                                                     "Test invoice",
                                                     new BigDecimal("10"),
                                                     new BigDecimal("1"),
                                                     Currency.USD);
        final List<InvoiceItem> lineItems = Arrays.asList(lineItem);
        final Invoice invoice = new Invoice(invoiceUuid, lineItems, Currency.USD);

        eventBus.post(invoice);
        await().atMost(1, MINUTES).until(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                List<PaymentInfo> processedPayments = mockPaymentProcessor.getProcessedPayments();
                List<PaymentError> errors = mockPaymentProcessor.getErrors();

                return processedPayments.size() == 1 || errors.size() == 1;
            }
        });

        assertFalse(mockPaymentProcessor.getProcessedPayments().isEmpty());
        assertTrue(mockPaymentProcessor.getErrors().isEmpty());
    }
}
