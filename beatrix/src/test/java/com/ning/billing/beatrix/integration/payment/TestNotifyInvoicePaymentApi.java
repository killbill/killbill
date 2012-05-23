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

package com.ning.billing.beatrix.integration.payment;

import static org.testng.Assert.assertNotNull;

import java.util.UUID;

import com.ning.billing.payment.api.DefaultPaymentAttempt;
import com.ning.billing.payment.api.PaymentAttempt.PaymentAttemptStatus;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import com.google.inject.Inject;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.account.glue.AccountModuleWithMocks;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.invoice.api.InvoicePaymentApi;
import com.ning.billing.invoice.glue.InvoiceModuleWithMocks;
import com.ning.billing.mock.BrainDeadProxyFactory;
import com.ning.billing.mock.BrainDeadProxyFactory.ZombieControl;
import com.ning.billing.mock.glue.MockJunctionModule;
import com.ning.billing.payment.RequestProcessor;
import com.ning.billing.payment.api.PaymentAttempt;
import com.ning.billing.util.bus.Bus;
import com.ning.billing.util.bus.Bus.EventBusException;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.TestCallContext;
import com.ning.billing.util.customfield.MockCustomFieldModuleMemory;
import com.ning.billing.util.entity.EntityPersistenceException;
import com.ning.billing.util.tag.MockTagStoreModuleMemory;

@Test
@Guice(modules = {MockCustomFieldModuleMemory.class, MockTagStoreModuleMemory.class, PaymentTestModule.class,
        AccountModuleWithMocks.class, InvoiceModuleWithMocks.class, MockJunctionModule.class})
public class TestNotifyInvoicePaymentApi {
    @Inject
    private Bus eventBus;
    @Inject
    private RequestProcessor invoiceProcessor;
    @Inject
    private InvoicePaymentApi invoicePaymentApi;
    @Inject
    private TestHelper testHelper;
    @Inject
    private AccountUserApi accountApi;

    private CallContext context = new TestCallContext("Payment Api Tests");

    @BeforeClass(alwaysRun = true)
    public void setUpClass() {
        ((ZombieControl)accountApi).addResult("getAccountById", BrainDeadProxyFactory.ZOMBIE_VOID);
    }
    
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
    public void testNotifyPaymentSuccess() throws AccountApiException, EntityPersistenceException {
        final Account account = testHelper.createTestCreditCardAccount();
        final Invoice invoice = testHelper.createTestInvoice(account);

        PaymentAttempt paymentAttempt = new DefaultPaymentAttempt(UUID.randomUUID(), invoice, PaymentAttemptStatus.COMPLETED_SUCCESS);

        invoicePaymentApi.notifyOfPaymentAttempt(invoice.getId(),
                                     invoice.getBalance(),
                                     invoice.getCurrency(),
                                     paymentAttempt.getId(),
                                     paymentAttempt.getPaymentAttemptDate(),
                                     context);

        InvoicePayment invoicePayment = invoicePaymentApi.getInvoicePayment(paymentAttempt.getId());

        assertNotNull(invoicePayment);
    }

    @Test
    public void testNotifyPaymentFailure() throws AccountApiException, EntityPersistenceException {
        final Account account = testHelper.createTestCreditCardAccount();
        final Invoice invoice = testHelper.createTestInvoice(account);


        PaymentAttempt paymentAttempt = new DefaultPaymentAttempt(UUID.randomUUID(), invoice, PaymentAttemptStatus.COMPLETED_SUCCESS);
        invoicePaymentApi.notifyOfPaymentAttempt(invoice.getId(),
                                                 paymentAttempt.getId(),
                                                 paymentAttempt.getPaymentAttemptDate(),
                                                 context);

        InvoicePayment invoicePayment = invoicePaymentApi.getInvoicePayment(paymentAttempt.getId());

        assertNotNull(invoicePayment);
    }

}
