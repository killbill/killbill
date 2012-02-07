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

import static org.testng.Assert.assertNotNull;

import java.util.UUID;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import com.google.inject.Inject;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.glue.AccountModuleWithMocks;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoicePaymentApi;
import com.ning.billing.invoice.glue.InvoiceModuleWithMocks;
import com.ning.billing.payment.api.InvoicePayment;
import com.ning.billing.payment.setup.PaymentTestModuleWithMocks;
import com.ning.billing.util.eventbus.EventBus;
import com.ning.billing.util.eventbus.EventBus.EventBusException;

@Test
@Guice(modules = { PaymentTestModuleWithMocks.class, AccountModuleWithMocks.class, InvoiceModuleWithMocks.class })
public class TestNotifyInvoicePaymentApi {
    @Inject
    private EventBus eventBus;
    @Inject
    private RequestProcessor invoiceProcessor;
    @Inject
    private InvoicePaymentApi invoicePaymentApi;
    @Inject
    private TestHelper testHelper;

    @BeforeMethod(alwaysRun = true)
    public void setUp() throws EventBusException {
        eventBus.start();
        eventBus.register(invoiceProcessor);
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() throws EventBusException {
        eventBus.unregister(invoiceProcessor);
        eventBus.stop();
    }

    @Test
    public void testNotifyPaymentSuccess() throws AccountApiException {
        final Account account = testHelper.createTestCreditCardAccount();
        final Invoice invoice = testHelper.createTestInvoice(account);

        PaymentAttempt paymentAttempt = new PaymentAttempt(UUID.randomUUID(), invoice);

        invoicePaymentApi.notifyOfPaymentAttempt(invoice.getId(),
                                     invoice.getAmountOutstanding(),
                                     invoice.getCurrency(),
                                     paymentAttempt.getPaymentAttemptId(),
                                     paymentAttempt.getPaymentAttemptDate());

        InvoicePayment invoicePayment = invoicePaymentApi.getInvoicePayment(paymentAttempt.getPaymentAttemptId());

        assertNotNull(invoicePayment);
    }

    @Test
    public void testNotifyPaymentFailure() throws AccountApiException {
        final Account account = testHelper.createTestCreditCardAccount();
        final Invoice invoice = testHelper.createTestInvoice(account);

        PaymentAttempt paymentAttempt = new PaymentAttempt(UUID.randomUUID(), invoice);
        invoicePaymentApi.notifyOfPaymentAttempt(invoice.getId(),
                                                 paymentAttempt.getPaymentAttemptId(),
                                                 paymentAttempt.getPaymentAttemptDate());

        InvoicePayment invoicePayment = invoicePaymentApi.getInvoicePayment(paymentAttempt.getPaymentAttemptId());

        assertNotNull(invoicePayment);
    }

}
