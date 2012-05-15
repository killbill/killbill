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

import static com.jayway.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;

import org.apache.commons.io.IOUtils;
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
import com.ning.billing.dbi.MysqlTestingHelper;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoicePaymentApi;
import com.ning.billing.invoice.glue.InvoiceModuleWithMocks;
import com.ning.billing.mock.glue.MockJunctionModule;
import com.ning.billing.payment.RequestProcessor;
import com.ning.billing.payment.api.PaymentApi;
import com.ning.billing.payment.api.PaymentAttempt;
import com.ning.billing.payment.api.PaymentErrorEvent;
import com.ning.billing.payment.api.PaymentInfoEvent;
import com.ning.billing.payment.setup.PaymentTestModuleWithEmbeddedDb;
import com.ning.billing.util.bus.Bus;
import com.ning.billing.util.bus.Bus.EventBusException;
import com.ning.billing.util.clock.MockClockModule;
import com.ning.billing.util.glue.CallContextModule;

public class TestPaymentInvoiceIntegration {
    // create payment for received invoice and save it -- positive and negative
    // check that notification for payment attempt is created
    // check that invoice-payment is saved
    @Inject
    private Bus eventBus;
    @Inject
    private RequestProcessor invoiceProcessor;
    @Inject
    private InvoicePaymentApi invoicePaymentApi;
    @Inject
    private PaymentApi paymentApi;
    @Inject
    private TestHelper testHelper;

    private MockPaymentInfoReceiver paymentInfoReceiver;

    private IDBI dbi;
    private MysqlTestingHelper helper;
    
    @BeforeClass(alwaysRun = true)
    public void startMysql() throws IOException {
        final String accountddl = IOUtils.toString(MysqlTestingHelper.class.getResourceAsStream("/com/ning/billing/account/ddl.sql"));
        final String utilddl = IOUtils.toString(MysqlTestingHelper.class.getResourceAsStream("/com/ning/billing/util/ddl.sql"));
        final String invoiceddl = IOUtils.toString(MysqlTestingHelper.class.getResourceAsStream("/com/ning/billing/invoice/ddl.sql"));
        final String paymentddl = IOUtils.toString(MysqlTestingHelper.class.getResourceAsStream("/com/ning/billing/payment/ddl.sql"));

        helper = new MysqlTestingHelper();
        helper.startMysql();
        helper.initDb(accountddl + "\n" + invoiceddl + "\n" + utilddl + "\n" + paymentddl);
        dbi = helper.getDBI();
    }

    @AfterClass(alwaysRun = true)
    public void stopMysql() {
        if (helper != null) helper.stopMysql();
    }

    @BeforeMethod(alwaysRun = true)
    public void setUp() throws EventBusException {
        Injector injector = Guice.createInjector(new PaymentTestModuleWithEmbeddedDb(),
                                                 new InvoiceModuleWithMocks(),
                                                 new CallContextModule(),
                                                 new MockClockModule(),
                                                 new MockJunctionModule(),
                                                 new AbstractModule()
                                            
                                                 {
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
    public void tearDown() throws EventBusException {
        if (eventBus != null) {
            eventBus.unregister(invoiceProcessor);
            eventBus.unregister(paymentInfoReceiver);
            eventBus.stop();
        }
    }

    @Test
    public void testInvoiceIntegration() throws Exception {
        final Account account = testHelper.createTestCreditCardAccount();
        final Invoice invoice = testHelper.createTestInvoice(account);

        await().atMost(1, MINUTES).until(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                List<PaymentInfoEvent> processedPayments = paymentInfoReceiver.getProcessedPayments();
                List<PaymentErrorEvent> errors = paymentInfoReceiver.getErrors();

                return processedPayments.size() == 1 || errors.size() == 1;
            }
        });

        assertFalse(paymentInfoReceiver.getProcessedPayments().isEmpty());
        assertTrue(paymentInfoReceiver.getErrors().isEmpty());

        List<PaymentInfoEvent> payments = paymentInfoReceiver.getProcessedPayments();
        PaymentAttempt paymentAttempt = paymentApi.getPaymentAttemptForPaymentId(payments.get(0).getId());
        Assert.assertNotNull(paymentAttempt);

        Invoice invoiceForPayment = invoicePaymentApi.getInvoiceForPaymentAttemptId(paymentAttempt.getId());

        Assert.assertNotNull(invoiceForPayment);
        Assert.assertEquals(invoiceForPayment.getId(), invoice.getId());
        Assert.assertEquals(invoiceForPayment.getAccountId(), account.getId());

        DateTime invoicePaymentAttempt = invoiceForPayment.getLastPaymentAttempt();
        DateTime correctedDate = invoicePaymentAttempt.minus(invoicePaymentAttempt.millisOfSecond().get());
        Assert.assertTrue(correctedDate.isEqual(paymentAttempt.getPaymentAttemptDate()));

        Assert.assertEquals(invoiceForPayment.getBalance().floatValue(), new BigDecimal("0").floatValue());
        Assert.assertEquals(invoiceForPayment.getAmountPaid().floatValue(), invoice.getAmountPaid().floatValue());
    }
}
