/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.payment;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;

import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PaymentAttempt;
import org.killbill.billing.payment.api.PaymentStatus;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.glue.DefaultPaymentService;
import org.killbill.billing.payment.provider.MockPaymentProviderPlugin;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

import static com.jayway.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class TestRetryService extends PaymentTestSuiteNoDB {

    private MockPaymentProviderPlugin mockPaymentProviderPlugin;

    @Override
    @BeforeMethod(groups = "fast")
    public void beforeMethod() throws Exception {
        super.beforeMethod();
        pluginRetryService.initialize(DefaultPaymentService.SERVICE_NAME);
        pluginRetryService.start();

        retryService.initialize(DefaultPaymentService.SERVICE_NAME);
        retryService.start();

        mockPaymentProviderPlugin = (MockPaymentProviderPlugin) registry.getServiceForName(MockPaymentProviderPlugin.PLUGIN_NAME);
        mockPaymentProviderPlugin.clear();
    }

    @Override
    @AfterMethod(groups = "fast")
    public void afterMethod() throws Exception {
        super.afterMethod();
        retryService.stop();
        pluginRetryService.stop();
    }

    private Payment getPaymentForInvoice(final UUID invoiceId) throws PaymentApiException {
        final List<Payment> payments = paymentProcessor.getInvoicePayments(invoiceId, internalCallContext);
        assertEquals(payments.size(), 1);
        final Payment payment = payments.get(0);
        assertEquals(payment.getInvoiceId(), invoiceId);
        return payment;
    }

    @Test(groups = "fast")
    public void testFailedPluginWithOneSuccessfulRetry() throws Exception {
        testSchedulesRetryInternal(1, FailureType.PLUGIN_EXCEPTION);
    }

    @Test(groups = "fast")
    public void testFailedPpluginWithLastRetrySuccess() throws Exception {
        testSchedulesRetryInternal(paymentConfig.getPluginFailureRetryMaxAttempts(), FailureType.PLUGIN_EXCEPTION);
    }

    @Test(groups = "fast")
    public void testAbortedPlugin() throws Exception {
        testSchedulesRetryInternal(paymentConfig.getPluginFailureRetryMaxAttempts() + 1, FailureType.PLUGIN_EXCEPTION);
    }

    @Test(groups = "fast")
    public void testFailedPaymentWithOneSuccessfulRetry() throws Exception {
        testSchedulesRetryInternal(1, FailureType.PAYMENT_FAILURE);
    }

    @Test(groups = "fast")
    public void testFailedPaymentWithLastRetrySuccess() throws Exception {
        testSchedulesRetryInternal(paymentConfig.getPaymentRetryDays().size(), FailureType.PAYMENT_FAILURE);
    }

    @Test(groups = "fast")
    public void testAbortedPayment() throws Exception {
        testSchedulesRetryInternal(paymentConfig.getPaymentRetryDays().size() + 1, FailureType.PAYMENT_FAILURE);
    }

    private void testSchedulesRetryInternal(final int maxTries, final FailureType failureType) throws Exception {

        final Account account = testHelper.createTestAccount("yiyi.gmail.com", true);
        final Invoice invoice = testHelper.createTestInvoice(account, clock.getUTCToday(), Currency.USD, callContext);
        final BigDecimal amount = new BigDecimal("10.00");
        final UUID subscriptionId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();

        final LocalDate startDate = clock.getUTCToday();
        final LocalDate endDate = startDate.plusMonths(1);
        invoice.addInvoiceItem(new MockRecurringInvoiceItem(invoice.getId(),
                                                            account.getId(),
                                                            subscriptionId,
                                                            bundleId,
                                                            "test plan", "test phase", null,
                                                            startDate,
                                                            endDate,
                                                            amount,
                                                            new BigDecimal("1.0"),
                                                            Currency.USD));
        setPaymentFailure(failureType);
        boolean failed = false;
        try {
            paymentProcessor.createPayment(account, invoice.getId(), amount, false, false, ImmutableList.<PluginProperty>of(), internalCallContext);
        } catch (final PaymentApiException e) {
            failed = true;
        }
        assertTrue(failed);

        for (int curFailure = 0; curFailure < maxTries; curFailure++) {

            if (curFailure < maxTries - 1) {
                setPaymentFailure(failureType);
            }

            if (curFailure < getMaxRetrySizeForFailureType(failureType)) {

                moveClockForFailureType(failureType, curFailure);
                try {
                    await().atMost(3, SECONDS).until(new Callable<Boolean>() {
                        @Override
                        public Boolean call() throws Exception {
                            final Payment payment = getPaymentForInvoice(invoice.getId());
                            return payment.getPaymentStatus() == PaymentStatus.SUCCESS;
                        }
                    });
                } catch (final TimeoutException e) {
                    if (curFailure == maxTries - 1) {
                        fail("Failed to find successful payment for attempt " + (curFailure + 1) + "/" + maxTries);
                    }
                }
            }
        }
        final Payment payment = getPaymentForInvoice(invoice.getId());
        final List<PaymentAttempt> attempts = payment.getAttempts();

        final int expectedAttempts = maxTries < getMaxRetrySizeForFailureType(failureType) ?
                                     maxTries + 1 : getMaxRetrySizeForFailureType(failureType) + 1;
        assertEquals(attempts.size(), expectedAttempts);
        Collections.sort(attempts, new Comparator<PaymentAttempt>() {
            @Override
            public int compare(final PaymentAttempt o1, final PaymentAttempt o2) {
                return o1.getEffectiveDate().compareTo(o2.getEffectiveDate());
            }
        });

        for (int i = 0; i < attempts.size(); i++) {
            final PaymentAttempt cur = attempts.get(i);
            if (i < attempts.size() - 1) {
                if (failureType == FailureType.PAYMENT_FAILURE) {
                    assertEquals(cur.getPaymentStatus(), PaymentStatus.PAYMENT_FAILURE);
                } else {
                    assertEquals(cur.getPaymentStatus(), PaymentStatus.PLUGIN_FAILURE);
                }
            } else if (maxTries <= getMaxRetrySizeForFailureType(failureType)) {
                assertEquals(cur.getPaymentStatus(), PaymentStatus.SUCCESS);
                assertEquals(payment.getPaymentStatus(), PaymentStatus.SUCCESS);
            } else {
                if (failureType == FailureType.PAYMENT_FAILURE) {
                    assertEquals(cur.getPaymentStatus(), PaymentStatus.PAYMENT_FAILURE_ABORTED);
                    assertEquals(payment.getPaymentStatus(), PaymentStatus.PAYMENT_FAILURE_ABORTED);
                } else {
                    assertEquals(cur.getPaymentStatus(), PaymentStatus.PLUGIN_FAILURE_ABORTED);
                    assertEquals(payment.getPaymentStatus(), PaymentStatus.PLUGIN_FAILURE_ABORTED);
                }
            }
        }
    }

    private enum FailureType {
        PLUGIN_EXCEPTION,
        PAYMENT_FAILURE
    }

    private void setPaymentFailure(final FailureType failureType) {
        if (failureType == FailureType.PAYMENT_FAILURE) {
            mockPaymentProviderPlugin.makeNextPaymentFailWithError();
        } else if (failureType == FailureType.PLUGIN_EXCEPTION) {
            mockPaymentProviderPlugin.makeNextPaymentFailWithException();
        }
    }

    private void moveClockForFailureType(final FailureType failureType, final int curFailure) {
        if (failureType == FailureType.PAYMENT_FAILURE) {
            final int nbDays = paymentConfig.getPaymentRetryDays().get(curFailure);
            clock.addDays(nbDays + 1);
        } else {
            clock.addDays(1);
        }
    }

    private int getMaxRetrySizeForFailureType(final FailureType failureType) {
        if (failureType == FailureType.PAYMENT_FAILURE) {
            return paymentConfig.getPaymentRetryDays().size();
        } else {
            return paymentConfig.getPluginFailureRetryMaxAttempts();
        }
    }
}
