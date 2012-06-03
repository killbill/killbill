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
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;

import com.ning.billing.payment.api.Payment;
import com.ning.billing.payment.api.Payment.PaymentAttempt;
import com.ning.billing.payment.api.PaymentApi;
import com.ning.billing.payment.api.PaymentApiException;
import com.ning.billing.payment.api.PaymentStatus;
import com.ning.billing.payment.core.PaymentProcessor;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.DefaultCallContext;
import com.ning.billing.util.callcontext.UserType;
import org.joda.time.DateTime;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import com.google.inject.Inject;
import com.ning.billing.account.api.Account;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.config.PaymentConfig;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoicePaymentApi;
import com.ning.billing.mock.BrainDeadProxyFactory;
import com.ning.billing.mock.BrainDeadProxyFactory.ZombieControl;
import com.ning.billing.mock.glue.MockClockModule;
import com.ning.billing.mock.glue.MockJunctionModule;
import com.ning.billing.payment.glue.DefaultPaymentService;
import com.ning.billing.payment.glue.PaymentTestModuleWithMocks;
import com.ning.billing.payment.provider.MockPaymentProviderPlugin;
import com.ning.billing.payment.provider.PaymentProviderPluginRegistry;
import com.ning.billing.payment.retry.FailedPaymentRetryService;
import com.ning.billing.util.bus.Bus;
import com.ning.billing.util.clock.ClockMock;
import com.ning.billing.util.glue.CallContextModule;

@Guice(modules = { PaymentTestModuleWithMocks.class, MockClockModule.class, MockJunctionModule.class, CallContextModule.class })
public class TestRetryService {
    @Inject
    private PaymentConfig paymentConfig;
    @Inject
    private Bus eventBus;
    @Inject
    private PaymentProcessor paymentProcessor;
    @Inject
    private InvoicePaymentApi invoicePaymentApi;
    @Inject
    private TestHelper testHelper;
    @Inject
    private PaymentProviderPluginRegistry registry;
    @Inject
    private FailedPaymentRetryService retryService;

    @Inject
    private ClockMock clock;

    private MockPaymentProviderPlugin mockPaymentProviderPlugin;
    private CallContext context;

    @BeforeClass(groups = "fast")
    public void initialize() throws Exception {
        
    }

    @BeforeMethod(groups = "fast")
    public void setUp() throws Exception {

        retryService.initialize(DefaultPaymentService.SERVICE_NAME);
        retryService.start();
        eventBus.start();
        
        mockPaymentProviderPlugin = (MockPaymentProviderPlugin) registry.getPlugin(null);
        mockPaymentProviderPlugin.clear();
        
        context = new DefaultCallContext("RetryServiceTests", CallOrigin.INTERNAL, UserType.TEST, clock);
        ((ZombieControl)invoicePaymentApi).addResult("notifyOfPaymentAttempt", BrainDeadProxyFactory.ZOMBIE_VOID);

    }

    @AfterMethod(groups = "fast")
    public void tearDown() throws Exception {
        retryService.stop();
        eventBus.stop();
    }

    
    private Payment getPaymentForInvoice(final UUID invoiceId) throws PaymentApiException {
        List<Payment> payments = paymentProcessor.getInvoicePayments(invoiceId);
        assertEquals(payments.size(), 1);
        Payment payment = payments.get(0);
        assertEquals(payment.getInvoiceId(), invoiceId);
        return payment;
    }


    @Test(groups = "fast")
    public void testFailedPaymentWithOneSuccessfulRetry() throws Exception {
        testSchedulesRetryInternal(1);
    }

    @Test(groups = "fast")
    public void testFailedPaymentWithLastRetrySuccess() throws Exception {
        testSchedulesRetryInternal(paymentConfig.getPaymentRetryDays().size());
    }

    @Test(groups = "fast")
    public void testAbortedPayment() throws Exception {
        testSchedulesRetryInternal(paymentConfig.getPaymentRetryDays().size() + 1);
    }


    private void testSchedulesRetryInternal(int maxTries) throws Exception {
        
        final Account account = testHelper.createTestCreditCardAccount();
        final Invoice invoice = testHelper.createTestInvoice(account, clock.getUTCNow(), Currency.USD);
        final BigDecimal amount = new BigDecimal("10.00");
        final UUID subscriptionId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();

        final DateTime startDate = clock.getUTCNow();
        final DateTime endDate = startDate.plusMonths(1);
        invoice.addInvoiceItem(new MockRecurringInvoiceItem(invoice.getId(),
                                                       account.getId(),
                                                       subscriptionId,
                                                       bundleId,
                                                       "test plan", "test phase",
                                                       startDate,
                                                       endDate,
                                                       amount,
                                                       new BigDecimal("1.0"),
                                                       Currency.USD));

        mockPaymentProviderPlugin.makeNextPaymentFailWithError();
        boolean failed = false;
        try {
            paymentProcessor.createPayment(account.getExternalKey(), invoice.getId(), amount, context, false);
        } catch (PaymentApiException e) {
            failed = true;
        }
        assertTrue(failed);


        //int maxTries = paymentAborted ? paymentConfig.getPaymentRetryDays().size() + 1 : 1;
        
        for (int curFailure = 0; curFailure < maxTries; curFailure++) {

            if (curFailure < maxTries - 1) {
                mockPaymentProviderPlugin.makeNextPaymentFailWithError();
            }

            if (curFailure < paymentConfig.getPaymentRetryDays().size()) {
                
                int nbDays = paymentConfig.getPaymentRetryDays().get(curFailure);            
                clock.addDays(nbDays + 1);

                try {
                    await().atMost(3, SECONDS).until(new Callable<Boolean>() {
                        @Override
                        public Boolean call() throws Exception {
                            Payment payment = getPaymentForInvoice(invoice.getId());
                            return payment.getPaymentStatus() == PaymentStatus.SUCCESS;
                        }
                    });
                } catch (TimeoutException e) {
                    if (curFailure == maxTries - 1) {
                        fail("Failed to find succesful payment for attempt " + (curFailure + 1) + "/" + maxTries);
                    }
                }
            }
        }


        Payment payment = getPaymentForInvoice(invoice.getId());
        List<PaymentAttempt> attempts = payment.getAttempts();
        
        int expectedAttempts = maxTries < paymentConfig.getPaymentRetryDays().size() ? maxTries + 1 : paymentConfig.getPaymentRetryDays().size() + 1;
        assertEquals(attempts.size(), expectedAttempts);
        Collections.sort(attempts, new Comparator<PaymentAttempt>() {
            @Override
            public int compare(PaymentAttempt o1, PaymentAttempt o2) {
                return o1.getEffectiveDate().compareTo(o2.getEffectiveDate());
            }
        });
 
        for (int i = 0; i < attempts.size(); i++) {
            PaymentAttempt cur = attempts.get(i);
            if (i < attempts.size() - 1) {
                assertEquals(cur.getPaymentStatus(), PaymentStatus.PAYMENT_FAILURE);
            } else if (maxTries <= paymentConfig.getPaymentRetryDays().size()) {
                assertEquals(cur.getPaymentStatus(), PaymentStatus.SUCCESS);
                assertEquals(payment.getPaymentStatus(), PaymentStatus.SUCCESS);
            } else {
                assertEquals(cur.getPaymentStatus(), PaymentStatus.PAYMENT_FAILURE_ABORTED);      
                assertEquals(payment.getPaymentStatus(), PaymentStatus.PAYMENT_FAILURE_ABORTED);                
            }
        }
    }
}
