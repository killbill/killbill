/*
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

package org.killbill.billing.payment.core;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Named;

import org.joda.time.DateTime;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.callcontext.DefaultCallContext;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.core.sm.PluginControlledPaymentAutomatonRunner;
import org.killbill.billing.payment.core.sm.RetryStateMachineHelper;
import org.killbill.billing.payment.core.sm.RetryablePaymentStateContext;
import org.killbill.billing.payment.dao.PaymentAttemptModelDao;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.dao.PaymentTransactionModelDao;
import org.killbill.billing.payment.dao.PluginPropertySerializer;
import org.killbill.billing.payment.dao.PluginPropertySerializer.PluginPropertySerializerException;
import org.killbill.billing.payment.glue.PaymentModule;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.CallOrigin;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.UserType;
import org.killbill.billing.util.config.PaymentConfig;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.clock.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

/**
 * Takes care of incomplete payment/transactions.
 */
public class Janitor {

    private final static Logger log = LoggerFactory.getLogger(Janitor.class);

    private final ScheduledExecutorService janitorExecutor;
    private final AccountInternalApi accountInternalApi;
    private final PaymentDao paymentDao;
    private final Clock clock;
    private final PaymentConfig paymentConfig;
    private final InternalCallContextFactory internalCallContextFactory;
    private final NonEntityDao nonEntityDao;
    private final PluginControlledPaymentAutomatonRunner pluginControlledPaymentAutomatonRunner;
    private final RetryStateMachineHelper retrySMHelper;

    private volatile boolean isStopped;
    private CountDownLatch shutdownLatch;

    @Inject
    public Janitor(final AccountInternalApi accountInternalApi,
                   final PaymentDao paymentDao,
                   final PaymentConfig paymentConfig,
                   final Clock clock,
                   final NonEntityDao nonEntityDao,
                   final InternalCallContextFactory internalCallContextFactory,
                   final PluginControlledPaymentAutomatonRunner pluginControlledPaymentAutomatonRunner,
                   @Named(PaymentModule.JANITOR_EXECUTOR_NAMED) final ScheduledExecutorService janitorExecutor,
                   final RetryStateMachineHelper retrySMHelper) {
        this.accountInternalApi = accountInternalApi;
        this.paymentDao = paymentDao;
        this.clock = clock;
        this.paymentConfig = paymentConfig;
        this.janitorExecutor = janitorExecutor;
        this.nonEntityDao = nonEntityDao;
        this.internalCallContextFactory = internalCallContextFactory;
        this.pluginControlledPaymentAutomatonRunner = pluginControlledPaymentAutomatonRunner;
        this.retrySMHelper = retrySMHelper;
    }

    public void start() {
        isStopped = false;
        // Start task for removing old pending payments.
        final TimeUnit pendingRateUnit = paymentConfig.getJanitorRunningRate().getUnit();
        final long pendingPeriod = paymentConfig.getJanitorRunningRate().getPeriod();
        janitorExecutor.scheduleAtFixedRate(new PendingTransactionTask(), pendingPeriod, pendingPeriod, pendingRateUnit);

        // Start task for completing incomplete payment attempts
        final TimeUnit attemptCompletionRateUnit = paymentConfig.getJanitorRunningRate().getUnit();
        final long attemptCompletionPeriod = paymentConfig.getJanitorRunningRate().getPeriod();
        janitorExecutor.scheduleAtFixedRate(new AttemptCompletionTask(), attemptCompletionPeriod, attemptCompletionPeriod, attemptCompletionRateUnit);
        this.shutdownLatch = new CountDownLatch(2);
    }

    public void stop() {
        isStopped = true;
        try {
            boolean res = shutdownLatch.await(5, TimeUnit.SECONDS);
            if (!res) {
                log.warn("Janitor stop sequence timed out : did not complete in 5 sec");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Janitor stop sequence got interrupted");
        }
    }

    /**
     * Task to find old PENDING transactions and move them into
     */
    private final class PendingTransactionTask implements Runnable {

        private final InternalCallContext fakeCallContext;

        private PendingTransactionTask() {
            this.fakeCallContext = internalCallContextFactory.createInternalCallContext((Long) null, (Long) null, "PendingJanitorTask", CallOrigin.INTERNAL, UserType.SYSTEM, UUID.randomUUID());
        }

        @Override
        public void run() {

            if (isStopped) {
                shutdownLatch.countDown();
                return;
            }
            log.info("PendingTransactionTask start run ");

            int result = paymentDao.failOldPendingTransactions(TransactionStatus.PLUGIN_FAILURE, getCreatedDateBefore(), fakeCallContext);
            if (result > 0) {
                log.info("PendingTransactionTask moved " + result + " PENDING payments ->  PLUGIN_FAILURE");
            }
        }

        private DateTime getCreatedDateBefore() {
            final long delayBeforeNowMs = paymentConfig.getJanitorPendingCleanupTime().getMillis();
            return clock.getUTCNow().minusMillis((int) delayBeforeNowMs);
        }
    }

    /**
     * Task to complete 'partially' incomplete attempts
     * <p/>
     * If the state of the transaction associated with the attempt completed, but the attempt state machine did not,
     * we rerun the retry state machine to complete the call and transition the attempt into a terminal state.
     */
    private final class AttemptCompletionTask implements Runnable {

        private final InternalCallContext fakeCallContext;

        private AttemptCompletionTask() {
            this.fakeCallContext = internalCallContextFactory.createInternalCallContext((Long) null, (Long) null, "PendingJanitorTask", CallOrigin.INTERNAL, UserType.SYSTEM, UUID.randomUUID());
        }

        @Override
        public void run() {

            if (isStopped) {
                shutdownLatch.countDown();
                return;
            }

            final List<PaymentAttemptModelDao> incompleteAttempts = paymentDao.getPaymentAttemptsByState(retrySMHelper.getInitialState().getName(), getCreatedDateBefore(), fakeCallContext);
            log.info("AttemptCompletionTask start run : found " + incompleteAttempts.size() + " incomplete attempts");

            for (PaymentAttemptModelDao cur : incompleteAttempts) {
                if (isStopped) {
                    shutdownLatch.countDown();
                    return;
                }
                complete(cur);
            }
        }

        private DateTime getCreatedDateBefore() {
            final long delayBeforeNowMs = paymentConfig.getJanitorAttemptCompletionTime().getMillis();
            return clock.getUTCNow().minusMillis((int) delayBeforeNowMs);
        }

        private void complete(final PaymentAttemptModelDao attempt) {

            // STEPH seems a bit insane??
            final InternalTenantContext tenantContext = internalCallContextFactory.createInternalTenantContext(attempt.getAccountId(), attempt.getId(), ObjectType.PAYMENT_ATTEMPT);
            final UUID tenantId = nonEntityDao.retrieveIdFromObject(tenantContext.getTenantRecordId(), ObjectType.TENANT);
            final CallContext callContext = new DefaultCallContext(tenantId, "AttemptCompletionJanitorTask", CallOrigin.INTERNAL, UserType.SYSTEM, UUID.randomUUID(), clock);
            final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(attempt.getAccountId(), callContext);

            final List<PaymentTransactionModelDao> transactions = paymentDao.getPaymentTransactionsByExternalKey(attempt.getTransactionExternalKey(), tenantContext);
            final PaymentTransactionModelDao transaction = Iterables.tryFind(transactions, new Predicate<PaymentTransactionModelDao>() {
                @Override
                public boolean apply(final PaymentTransactionModelDao input) {
                    return input.getAttemptId().equals(attempt.getId()) &&
                           input.getTransactionStatus() == TransactionStatus.SUCCESS;
                }
            }).orNull();

            if (transaction == null) {
                log.info("AttemptCompletionTask moving attempt " + attempt.getId() + " -> ABORTED");
                paymentDao.updatePaymentAttempt(attempt.getId(), attempt.getTransactionId(), "ABORTED", internalCallContext);
                return;
            }

            try {

                log.info("AttemptCompletionTask completing attempt " + attempt.getId() + " -> SUCCESS");

                final Account account = accountInternalApi.getAccountById(attempt.getAccountId(), tenantContext);
                final boolean isApiPayment = true; // unclear
                final RetryablePaymentStateContext paymentStateContext = new RetryablePaymentStateContext(attempt.getPluginName(),
                                                                                                                      isApiPayment,
                                                                                                                      transaction.getPaymentId(),
                                                                                                                      attempt.getPaymentExternalKey(),
                                                                                                                      transaction.getTransactionExternalKey(),
                                                                                                                      transaction.getTransactionType(),
                                                                                                                      account,
                                                                                                                      attempt.getPaymentMethodId(),
                                                                                                                      transaction.getAmount(),
                                                                                                                      transaction.getCurrency(),
                                                                                                                      PluginPropertySerializer.deserialize(attempt.getPluginProperties()),
                                                                                                                      internalCallContext,
                                                                                                                      callContext);

                paymentStateContext.setAttemptId(attempt.getId()); // Normally set by leavingState Callback
                paymentStateContext.setPaymentTransactionModelDao(transaction); // Normally set by raw state machine
                //
                // Will rerun the state machine with special callbacks to only make the onCompletion call
                // to the PaymentControlPluginApi plugin and transition the state.
                //
                pluginControlledPaymentAutomatonRunner.completeRun(paymentStateContext);

            } catch (AccountApiException e) {
                log.warn("Failed to complete payment attempt " + attempt.getId(), e);
            } catch (PluginPropertySerializerException e) {
                log.warn("Failed to complete payment attempt " + attempt.getId(), e);
            } catch (PaymentApiException e) {
                log.warn("Failed to complete payment attempt " + attempt.getId(), e);
            }
        }
    }
}
