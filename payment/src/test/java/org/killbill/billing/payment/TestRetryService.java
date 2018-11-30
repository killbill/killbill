/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

import org.awaitility.Duration;
import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.dao.PaymentAttemptModelDao;
import org.killbill.billing.payment.dao.PaymentTransactionModelDao;
import org.killbill.billing.payment.provider.MockPaymentProviderPlugin;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Awaitility.setDefaultPollInterval;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class TestRetryService extends PaymentTestSuiteNoDB {

    private static final int TIMEOUT = 10;
    private static final ImmutableList<PluginProperty> NO_PROPERTIES = ImmutableList.<PluginProperty>of();

    private MockPaymentProviderPlugin mockPaymentProviderPlugin;

    @Override
    @BeforeMethod(groups = "fast")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeMethod();

        setDefaultPollInterval(Duration.ONE_HUNDRED_MILLISECONDS);

        mockPaymentProviderPlugin = (MockPaymentProviderPlugin) registry.getServiceForName(MockPaymentProviderPlugin.PLUGIN_NAME);
        mockPaymentProviderPlugin.clear();
        retryService.initialize();
        retryService.start();
    }

    @Override
    @AfterMethod(groups = "fast")
    public void afterMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.afterMethod();
        retryService.stop();
    }

    private Payment getPaymentForExternalKey(final String externalKey) throws PaymentApiException {
        final Payment payment = paymentRefresher.getPaymentByExternalKey(externalKey, false, false, ImmutableList.<PluginProperty>of(), callContext, internalCallContext);
        return payment;
    }

    // PLUGIN_EXCEPTION will lead to UNKNOWN row that will not be retried by the plugin
    @Test(groups = "fast")
    public void testAbortedPlugin() throws Exception {

        final Account account = testHelper.createTestAccount("yiyi.gmail.com", true);
        final Invoice invoice = testHelper.createTestInvoice(account, clock.getUTCToday(), Currency.USD);
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
        setPaymentFailure(FailureType.PLUGIN_EXCEPTION);

        boolean failed = false;
        final String paymentExternalKey = UUID.randomUUID().toString();
        final String transactionExternalKey = UUID.randomUUID().toString();
        try {
            invoicePaymentInternalApi.createPurchaseForInvoicePayment(false,
                                                                      account,
                                                                      invoice.getId(),
                                                                      account.getPaymentMethodId(),
                                                                      null,
                                                                      amount,
                                                                      Currency.USD,
                                                                      null,
                                                                      paymentExternalKey,
                                                                      transactionExternalKey,
                                                                      NO_PROPERTIES,
                                                                      PAYMENT_OPTIONS,
                                                                      internalCallContext);
        } catch (final PaymentApiException e) {
            failed = true;
        }
        assertTrue(failed);

        Payment payment = getPaymentForExternalKey(paymentExternalKey);
        List<PaymentAttemptModelDao> attempts = paymentDao.getPaymentAttempts(paymentExternalKey, internalCallContext);
        assertEquals(attempts.size(), 1);

        final List<PaymentTransactionModelDao> transactions = paymentDao.getTransactionsForPayment(payment.getId(), internalCallContext);
        assertEquals(transactions.size(), 1);

        attempts = paymentDao.getPaymentAttempts(payment.getExternalKey(), internalCallContext);
        final int expectedAttempts = 1;
        assertEquals(attempts.size(), expectedAttempts);
        assertEquals(attempts.get(0).getStateName(), "ABORTED");
    }

    @Test(groups = "fast")
    public void testFailedPaymentWithOneSuccessfulRetry() throws Exception {

        final Account account = testHelper.createTestAccount("yiyi.gmail.com", true);
        final Invoice invoice = testHelper.createTestInvoice(account, clock.getUTCToday(), Currency.USD);
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
        setPaymentFailure(FailureType.PAYMENT_FAILURE);

        final String paymentExternalKey = UUID.randomUUID().toString();
        final String transactionExternalKey = UUID.randomUUID().toString();
        invoicePaymentInternalApi.createPurchaseForInvoicePayment(false,
                                                                  account,
                                                                  invoice.getId(),
                                                                  account.getPaymentMethodId(),
                                                                  null,
                                                                  amount,
                                                                  Currency.USD,
                                                                  null,
                                                                  paymentExternalKey,
                                                                  transactionExternalKey,
                                                                  NO_PROPERTIES,
                                                                  PAYMENT_OPTIONS,
                                                                  internalCallContext);

        Payment payment = getPaymentForExternalKey(paymentExternalKey);
        List<PaymentAttemptModelDao> attempts = paymentDao.getPaymentAttempts(paymentExternalKey, internalCallContext);
        assertEquals(attempts.size(), 1);

        final List<PaymentTransactionModelDao> transactions = paymentDao.getTransactionsForPayment(payment.getId(), internalCallContext);
        assertEquals(transactions.size(), 1);

        moveClockForFailureType(FailureType.PAYMENT_FAILURE, 0);

        await().atMost(TIMEOUT, SECONDS).until(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                final List<PaymentAttemptModelDao> attempts = paymentDao.getPaymentAttempts(paymentExternalKey, internalCallContext);
                final List<PaymentAttemptModelDao> filteredAttempts = ImmutableList.copyOf(Iterables.filter(attempts, new Predicate<PaymentAttemptModelDao>() {
                    @Override
                    public boolean apply(final PaymentAttemptModelDao input) {
                        return input.getStateName().equals("SUCCESS") ||
                               input.getStateName().equals("RETRIED") ||
                               input.getStateName().equals("ABORTED");
                    }
                }));
                return filteredAttempts.size() == 2;
            }
        });

        attempts = paymentDao.getPaymentAttempts(payment.getExternalKey(), internalCallContext);
        final int expectedAttempts = 2;
        assertEquals(attempts.size(), expectedAttempts);
        Collections.sort(attempts, new Comparator<PaymentAttemptModelDao>() {
            @Override
            public int compare(final PaymentAttemptModelDao o1, final PaymentAttemptModelDao o2) {
                return o1.getCreatedDate().compareTo(o2.getCreatedDate());
            }
        });

        for (int i = 0; i < attempts.size(); i++) {
            final PaymentAttemptModelDao cur = attempts.get(i);
            if (i < attempts.size() - 1) {
                assertEquals(cur.getStateName(), "RETRIED");
            } else {
                assertEquals(cur.getStateName(), "SUCCESS");
            }
        }
    }

    @Test(groups = "fast")
    public void testFailedPaymentWithLastRetrySuccess() throws Exception {

        final Account account = testHelper.createTestAccount("yiyi.gmail.com", true);
        final Invoice invoice = testHelper.createTestInvoice(account, clock.getUTCToday(), Currency.USD);
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
        setPaymentFailure(FailureType.PAYMENT_FAILURE);

        final String paymentExternalKey = UUID.randomUUID().toString();
        final String transactionExternalKey = UUID.randomUUID().toString();
        invoicePaymentInternalApi.createPurchaseForInvoicePayment(false,
                                                                  account,
                                                                  invoice.getId(),
                                                                  account.getPaymentMethodId(),
                                                                  null,
                                                                  amount,
                                                                  Currency.USD,
                                                                  null,
                                                                  paymentExternalKey,
                                                                  transactionExternalKey,
                                                                  NO_PROPERTIES,
                                                                  PAYMENT_OPTIONS,
                                                                  internalCallContext);

        Payment payment = getPaymentForExternalKey(paymentExternalKey);
        List<PaymentAttemptModelDao> attempts = paymentDao.getPaymentAttempts(paymentExternalKey, internalCallContext);
        assertEquals(attempts.size(), 1);

        final List<PaymentTransactionModelDao> transactions = paymentDao.getTransactionsForPayment(payment.getId(), internalCallContext);
        assertEquals(transactions.size(), 1);

        int maxTries = paymentConfig.getPaymentFailureRetryDays(internalCallContext).size();
        for (int curFailure = 0; curFailure < maxTries; curFailure++) {

            // Set plugin to fail with specific type unless this is the last attempt and we want a success
            if (curFailure < (maxTries - 1)) {
                setPaymentFailure(FailureType.PAYMENT_FAILURE);
            }

            moveClockForFailureType(FailureType.PAYMENT_FAILURE, curFailure);
            final int curFailureCondition = curFailure;

            await().atMost(TIMEOUT, SECONDS).until(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    final List<PaymentAttemptModelDao> attempts = paymentDao.getPaymentAttempts(paymentExternalKey, internalCallContext);
                    final List<PaymentAttemptModelDao> filteredAttempts = ImmutableList.copyOf(Iterables.filter(attempts, new Predicate<PaymentAttemptModelDao>() {
                        @Override
                        public boolean apply(final PaymentAttemptModelDao input) {
                            return input.getStateName().equals("SUCCESS") ||
                                   input.getStateName().equals("RETRIED") ||
                                   input.getStateName().equals("ABORTED");
                        }
                    }));
                    return filteredAttempts.size() == curFailureCondition + 2;
                }
            });
        }
        attempts = paymentDao.getPaymentAttempts(payment.getExternalKey(), internalCallContext);
        final int expectedAttempts = maxTries + 1;
        assertEquals(attempts.size(), expectedAttempts);
        Collections.sort(attempts, new Comparator<PaymentAttemptModelDao>() {
            @Override
            public int compare(final PaymentAttemptModelDao o1, final PaymentAttemptModelDao o2) {
                return o1.getCreatedDate().compareTo(o2.getCreatedDate());
            }
        });

        for (int i = 0; i < attempts.size(); i++) {
            final PaymentAttemptModelDao cur = attempts.get(i);
            if (i < attempts.size() - 1) {
                assertEquals(cur.getStateName(), "RETRIED");
            } else {
                assertEquals(cur.getStateName(), "SUCCESS");
            }
        }

    }

    @Test(groups = "fast")
    public void testAbortedPayment() throws Exception {

        final Account account = testHelper.createTestAccount("yiyi.gmail.com", true);
        final Invoice invoice = testHelper.createTestInvoice(account, clock.getUTCToday(), Currency.USD);
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
        setPaymentFailure(FailureType.PAYMENT_FAILURE);

        final String paymentExternalKey = UUID.randomUUID().toString();
        final String transactionExternalKey = UUID.randomUUID().toString();
        invoicePaymentInternalApi.createPurchaseForInvoicePayment(false,
                                                                  account,
                                                                  invoice.getId(),
                                                                  account.getPaymentMethodId(),
                                                                  null,
                                                                  amount,
                                                                  Currency.USD,
                                                                  null,
                                                                  paymentExternalKey,
                                                                  transactionExternalKey,
                                                                  NO_PROPERTIES,
                                                                  PAYMENT_OPTIONS,
                                                                  internalCallContext);

        Payment payment = getPaymentForExternalKey(paymentExternalKey);
        List<PaymentAttemptModelDao> attempts = paymentDao.getPaymentAttempts(paymentExternalKey, internalCallContext);
        assertEquals(attempts.size(), 1);

        final List<PaymentTransactionModelDao> transactions = paymentDao.getTransactionsForPayment(payment.getId(), internalCallContext);
        assertEquals(transactions.size(), 1);

        int maxTries = paymentConfig.getPaymentFailureRetryDays(internalCallContext).size();
        for (int curFailure = 0; curFailure < maxTries; curFailure++) {

            // Set plugin to fail with specific type unless this is the last attempt and we want a success
            setPaymentFailure(FailureType.PAYMENT_FAILURE);

            moveClockForFailureType(FailureType.PAYMENT_FAILURE, curFailure);
            final int curFailureCondition = curFailure;

            await().atMost(TIMEOUT, SECONDS).until(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    final List<PaymentAttemptModelDao> attempts = paymentDao.getPaymentAttempts(paymentExternalKey, internalCallContext);
                    final List<PaymentAttemptModelDao> filteredAttempts = ImmutableList.copyOf(Iterables.filter(attempts, new Predicate<PaymentAttemptModelDao>() {
                        @Override
                        public boolean apply(final PaymentAttemptModelDao input) {
                            return input.getStateName().equals("SUCCESS") ||
                                   input.getStateName().equals("RETRIED") ||
                                   input.getStateName().equals("ABORTED");
                        }
                    }));
                    return filteredAttempts.size() == curFailureCondition + 2;
                }
            });
        }
        attempts = paymentDao.getPaymentAttempts(payment.getExternalKey(), internalCallContext);
        final int expectedAttempts = maxTries + 1;
        assertEquals(attempts.size(), expectedAttempts);
        Collections.sort(attempts, new Comparator<PaymentAttemptModelDao>() {
            @Override
            public int compare(final PaymentAttemptModelDao o1, final PaymentAttemptModelDao o2) {
                return o1.getCreatedDate().compareTo(o2.getCreatedDate());
            }
        });

        for (int i = 0; i < attempts.size(); i++) {
            final PaymentAttemptModelDao cur = attempts.get(i);
            if (i < attempts.size() - 1) {
                assertEquals(cur.getStateName(), "RETRIED");
            } else {
                assertEquals(cur.getStateName(), "ABORTED");
            }
        }
    }

    private void setPaymentFailure(final FailureType failureType) {
        if (failureType == FailureType.PAYMENT_FAILURE) {
            mockPaymentProviderPlugin.makeNextPaymentFailWithError();
        } else if (failureType == FailureType.PLUGIN_EXCEPTION) {
            mockPaymentProviderPlugin.makeNextPaymentFailWithException();
        }
    }

    private void moveClockForFailureType(final FailureType failureType, final int curFailure) throws InterruptedException {
        final int nbDays;
        if (failureType == FailureType.PAYMENT_FAILURE) {
            nbDays = paymentConfig.getPaymentFailureRetryDays(internalCallContext).get(curFailure) + 1;
        } else {
            nbDays = 1;
        }
        clock.addDays(nbDays);
    }

    private enum FailureType {
        PLUGIN_EXCEPTION,
        PAYMENT_FAILURE
    }
}
