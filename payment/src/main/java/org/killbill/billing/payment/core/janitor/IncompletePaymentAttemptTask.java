/*
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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

package org.killbill.billing.payment.core.janitor;

import java.util.List;

import javax.inject.Inject;

import org.joda.time.DateTime;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.events.PaymentInternalEvent;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.core.sm.PaymentControlStateMachineHelper;
import org.killbill.billing.payment.core.sm.PaymentStateMachineHelper;
import org.killbill.billing.payment.core.sm.PluginControlPaymentAutomatonRunner;
import org.killbill.billing.payment.core.sm.control.PaymentStateControlContext;
import org.killbill.billing.payment.dao.PaymentAttemptModelDao;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.dao.PaymentTransactionModelDao;
import org.killbill.billing.payment.dao.PluginPropertySerializer;
import org.killbill.billing.payment.dao.PluginPropertySerializer.PluginPropertySerializerException;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.config.definition.PaymentConfig;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.clock.Clock;
import org.killbill.commons.locker.GlobalLocker;
import org.killbill.notificationq.api.NotificationQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

/**
 * Task to complete 'partially' incomplete payment attempts. Tis only matters for calls that went through PaymentControl apis.
 * <p/>
 * If the state of the transaction associated with the attempt completed, but the attempt state machine did not,
 * we rerun the retry state machine to complete the call and transition the attempt into a terminal state.
 */
public class IncompletePaymentAttemptTask extends CompletionTaskBase<PaymentAttemptModelDao> {

    private static final Logger log = LoggerFactory.getLogger(IncompletePaymentAttemptTask.class);

    //
    // Each paymentAttempt *should* transition to a new state, so fetching a limited size will still allow us to progress (as opposed to fetching the same entries over and over)
    // We also don't expect to see too many entries in the INIT state.
    //
    private static final long MAX_ATTEMPTS_PER_ITERATIONS = 1000L;

    private final PluginControlPaymentAutomatonRunner pluginControlledPaymentAutomatonRunner;

    @Inject
    public IncompletePaymentAttemptTask(final InternalCallContextFactory internalCallContextFactory,
                                        final PaymentConfig paymentConfig,
                                        final PaymentDao paymentDao,
                                        final Clock clock,
                                        final PaymentStateMachineHelper paymentStateMachineHelper,
                                        final PaymentControlStateMachineHelper retrySMHelper,
                                        final AccountInternalApi accountInternalApi,
                                        final PluginControlPaymentAutomatonRunner pluginControlledPaymentAutomatonRunner,
                                        final GlobalLocker locker) {
        super(internalCallContextFactory, paymentConfig, paymentDao, clock, paymentStateMachineHelper, retrySMHelper, accountInternalApi, locker);
        this.pluginControlledPaymentAutomatonRunner = pluginControlledPaymentAutomatonRunner;
    }

    @Override
    public Iterable<PaymentAttemptModelDao> getItemsForIteration() {
        final Pagination<PaymentAttemptModelDao> incompleteAttempts = paymentDao.getPaymentAttemptsByStateAcrossTenants(retrySMHelper.getInitialState().getName(), getCreatedDateBefore(), 0L, MAX_ATTEMPTS_PER_ITERATIONS);
        if (incompleteAttempts.getTotalNbRecords() > 0) {
            log.info("Janitor AttemptCompletionTask start run: found {} incomplete attempts", incompleteAttempts.getTotalNbRecords());
        }
        return incompleteAttempts;
    }

    @Override
    public void doIteration(final PaymentAttemptModelDao attempt) {
        // We don't grab account lock here as the lock will be taken when calling the completeRun API.
        final InternalTenantContext tenantContext = internalCallContextFactory.createInternalTenantContext(attempt.getTenantRecordId(), attempt.getAccountRecordId());
        final CallContext callContext = createCallContext("AttemptCompletionJanitorTask", tenantContext);
        final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(attempt.getAccountId(), callContext);

        final List<PaymentTransactionModelDao> transactions = paymentDao.getPaymentTransactionsByExternalKey(attempt.getTransactionExternalKey(), tenantContext);
        final List<PaymentTransactionModelDao> filteredTransactions = ImmutableList.copyOf(Iterables.filter(transactions, new Predicate<PaymentTransactionModelDao>() {
            @Override
            public boolean apply(final PaymentTransactionModelDao input) {
                return input.getAttemptId().equals(attempt.getId());
            }
        }));

        // We only expect at most one transaction for a given attempt, but as a precaution we check for more; if this is the case we log a warn and continue processing the first one.
        if (filteredTransactions.size() > 1) {
            log.warn("Found {} transactions for paymentAttempt {}", filteredTransactions.size(), attempt.getId());
        }
        final PaymentTransactionModelDao transaction = filteredTransactions.isEmpty() ? null : filteredTransactions.get(0);
        if (transaction == null) {
            log.info("Moving attemptId='{}' to ABORTED", attempt.getId());
            paymentDao.updatePaymentAttempt(attempt.getId(), attempt.getTransactionId(), "ABORTED", internalCallContext);
            return;
        }

        // UNKNOWN transaction are handled by the Janitor IncompletePaymentTransactionTask  and should eventually transition to something else,
        // at which point the attempt can also be transition to a different state.
        if (transaction.getTransactionStatus() == TransactionStatus.UNKNOWN) {
            return;
        }

        try {
            log.info("Completing attemptId='{}'", attempt.getId());

            final Account account = accountInternalApi.getAccountById(attempt.getAccountId(), tenantContext);
            final boolean isApiPayment = true; // unclear
            final PaymentStateControlContext paymentStateContext = new PaymentStateControlContext(attempt.toPaymentControlPluginNames(),
                                                                                                  isApiPayment,
                                                                                                  null,
                                                                                                  transaction.getPaymentId(),
                                                                                                  attempt.getPaymentExternalKey(),
                                                                                                  transaction.getId(),
                                                                                                  transaction.getTransactionExternalKey(),
                                                                                                  transaction.getTransactionType(),
                                                                                                  account,
                                                                                                  attempt.getPaymentMethodId(),
                                                                                                  transaction.getAmount(),
                                                                                                  transaction.getCurrency(),
                                                                                                  null,
                                                                                                  PluginPropertySerializer.deserialize(attempt.getPluginProperties()),
                                                                                                  internalCallContext,
                                                                                                  callContext);

            paymentStateContext.setAttemptId(attempt.getId()); // Normally set by leavingState Callback
            paymentStateContext.setPaymentTransactionModelDao(transaction); // Normally set by raw state machine
            //
            // Will rerun the state machine with special callbacks to only make the executePluginOnSuccessCalls / executePluginOnFailureCalls calls
            // to the PaymentControlPluginApi plugin and transition the state.
            //
            pluginControlledPaymentAutomatonRunner.completeRun(paymentStateContext);
        } catch (final AccountApiException e) {
            log.warn("Error completing paymentAttemptId='{}'", attempt.getId(), e);
        } catch (final PluginPropertySerializerException e) {
            log.warn("Error completing paymentAttemptId='{}'", attempt.getId(), e);
        } catch (final PaymentApiException e) {
            log.warn("Error completing paymentAttemptId='{}'", attempt.getId(), e);
        }
    }

    @Override
    public void processPaymentEvent(final PaymentInternalEvent event, final NotificationQueue janitorQueue) {
        // Nothing
    }

    private DateTime getCreatedDateBefore() {
        final long delayBeforeNowMs = paymentConfig.getIncompleteAttemptsTimeSpanDelay().getMillis();
        return clock.getUTCNow().minusMillis((int) delayBeforeNowMs);
    }
}
