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

import java.util.List;
import java.util.concurrent.Callable;

import com.ning.billing.invoice.api.Invoice;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import com.google.inject.Inject;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.glue.AccountModuleWithMocks;
import com.ning.billing.invoice.glue.InvoiceModuleWithMocks;
import com.ning.billing.payment.api.PaymentError;
import com.ning.billing.payment.api.PaymentInfo;
import com.ning.billing.payment.setup.PaymentTestModuleWithMocks;
import com.ning.billing.util.bus.Bus;
import com.ning.billing.util.bus.Bus.EventBusException;

@Guice(modules = { PaymentTestModuleWithMocks.class, AccountModuleWithMocks.class, InvoiceModuleWithMocks.class })
public class TestPaymentProvider {
    @Inject
    private Bus eventBus;
    @Inject
    private RequestProcessor invoiceProcessor;
    @Inject
    private TestHelper testHelper;

    private MockPaymentInfoReceiver paymentInfoReceiver;

    @BeforeMethod(alwaysRun = true)
    public void setUp() throws EventBusException {
        paymentInfoReceiver = new MockPaymentInfoReceiver();

        eventBus.start();
        eventBus.register(invoiceProcessor);
        eventBus.register(paymentInfoReceiver);

        assertTrue(true);
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() throws EventBusException {
        eventBus.unregister(invoiceProcessor);
        eventBus.unregister(paymentInfoReceiver);
        eventBus.stop();

        assertTrue(true);
    }

    @Test
    public void testSimpleInvoice() throws Exception {
        final Account account = testHelper.createTestCreditCardAccount();

        Invoice invoice = testHelper.createTestInvoice(account);

        await().atMost(1, MINUTES).until(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                List<PaymentInfo> processedPayments = paymentInfoReceiver.getProcessedPayments();
                List<PaymentError> errors = paymentInfoReceiver.getErrors();

                return processedPayments.size() == 1 || errors.size() == 1;
            }
        });

        assertFalse(paymentInfoReceiver.getProcessedPayments().isEmpty());
        // can't check errors; the mock is flaky and results in $0 payment attempt
        assertTrue(invoice.getPayments().size() > 0);
    }
}
