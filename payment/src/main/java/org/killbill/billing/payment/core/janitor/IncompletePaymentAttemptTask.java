/*
 * Copyright 2014-2019 Groupon, Inc
 * Copyright 2014-2019 The Billing Project, LLC
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

import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;
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
import org.killbill.billing.payment.plugin.api.PaymentTransactionInfoPlugin;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.config.definition.PaymentConfig;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.clock.Clock;
import org.killbill.commons.locker.GlobalLocker;
import org.killbill.commons.locker.LockFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

// Janitor implementation for payments going through the control APIs.
// Invoked on-the-fly or as part of the Janitor notification queue
// Also invoked by a scheduled executor service to complete 'partially' incomplete payment attempts: if the state of
// the transaction associated with the attempt completed, but the attempt state machine did not, we rerun
// the retry state machine to complete the call and transition the attempt into a terminal state.
public class IncompletePaymentAttemptTask extends CompletionTaskBase<PaymentAttemptModelDao> implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(IncompletePaymentAttemptTask.class);

    //
    // Each paymentAttempt *should* transition to a new state, so fetching a limited size will still allow us to progress (as opposed to fetching the same entries over and over)
    // We also don't expect to see too many entries in the INIT state.
    //
    private static final long MAX_ATTEMPTS_PER_ITERATIONS = 1000L;

    private final PluginControlPaymentAutomatonRunner pluginControlledPaymentAutomatonRunner;
    private final IncompletePaymentTransactionTask incompletePaymentTransactionTask;

    private volatile boolean isStopped;

    @Inject
    public IncompletePaymentAttemptTask(final InternalCallContextFactory internalCallContextFactory,
                                        final PaymentConfig paymentConfig,
                                        final PaymentDao paymentDao,
                                        final Clock clock,
                                        final PaymentStateMachineHelper paymentStateMachineHelper,
                                        final PaymentControlStateMachineHelper retrySMHelper,
                                        final AccountInternalApi accountInternalApi,
                                        final PluginControlPaymentAutomatonRunner pluginControlledPaymentAutomatonRunner,
                                        final GlobalLocker locker,
                                        final IncompletePaymentTransactionTask incompletePaymentTransactionTask) {
        super(internalCallContextFactory, paymentConfig, paymentDao, clock, paymentStateMachineHelper, retrySMHelper, accountInternalApi, locker);
        this.pluginControlledPaymentAutomatonRunner = pluginControlledPaymentAutomatonRunner;
        this.incompletePaymentTransactionTask = incompletePaymentTransactionTask;
        this.isStopped = false;
    }

    public synchronized void start() {
        this.isStopped = false;
    }

    public synchronized void stop() {
        this.isStopped = true;
    }

    @Override
    public void run() {
        if (isStopped) {
            log.info("Janitor was requested to stop");
            return;
        }

        final Iterator<PaymentAttemptModelDao> iterator = getItemsForIteration().iterator();
        try {
            while (iterator.hasNext()) {
                final PaymentAttemptModelDao item = iterator.next();
                if (isStopped) {
                    log.info("Janitor was requested to stop");
                    return;
                }
                try {
                    doIteration(item);
                } catch (final Exception e) {
                    log.warn(e.getMessage());
                }
            }
        } finally {
            // In case the loop stops early, make sure to close the underlying DB connection
            while (iterator.hasNext()) {
                iterator.next();
            }
        }
    }

    @VisibleForTesting
    Iterable<PaymentAttemptModelDao> getItemsForIteration() {
        final Pagination<PaymentAttemptModelDao> incompleteAttempts = paymentDao.getPaymentAttemptsByStateAcrossTenants(retrySMHelper.getInitialState().getName(), getCreatedDateBefore(), 0L, MAX_ATTEMPTS_PER_ITERATIONS);
        if (incompleteAttempts.getTotalNbRecords() > 0) {
            log.info("Janitor AttemptCompletionTask start run: found {} incomplete attempts", incompleteAttempts.getTotalNbRecords());
        }
        return incompleteAttempts;
    }

    @VisibleForTesting
    public boolean doIteration(final PaymentAttemptModelDao attempt) {
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
            return true;
        }

        // UNKNOWN transaction are handled by the Janitor IncompletePaymentTransactionTask and should eventually transition to something else,
        // at which point the attempt can also be transition to a different state.
        if (transaction.getTransactionStatus() == TransactionStatus.UNKNOWN) {
            return false;
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
            return true;
        } catch (final AccountApiException e) {
            log.warn("Error completing paymentAttemptId='{}'", attempt.getId(), e);
        } catch (final PluginPropertySerializerException e) {
            log.warn("Error completing paymentAttemptId='{}'", attempt.getId(), e);
        } catch (final PaymentApiException e) {
            log.warn("Error completing paymentAttemptId='{}'", attempt.getId(), e);
        }

        return false;
    }

    protected void processNotification(final JanitorNotificationKey notificationKey, final UUID userToken, final Long accountRecordId, final long tenantRecordId) {
        final InternalTenantContext internalTenantContext = internalCallContextFactory.createInternalTenantContext(tenantRecordId, accountRecordId);

        try {
            updatePaymentAndTransactionIfNeeded(notificationKey, userToken, internalTenantContext);
        } catch (final LockFailedException e) {
            log.warn("Error locking accountRecordId='{}', will attempt to retry later", accountRecordId, e);

            final PaymentTransactionModelDao paymentTransaction = paymentDao.getPaymentTransaction(notificationKey.getUuidKey(), internalTenantContext);
            if (TRANSACTION_STATUSES_TO_CONSIDER.contains(paymentTransaction.getTransactionStatus())) {
                insertNewNotificationForUnresolvedTransactionIfNeeded(notificationKey.getUuidKey(), paymentTransaction.getTransactionStatus(), notificationKey.getAttemptNumber(), userToken, accountRecordId, tenantRecordId);
            }
        }
    }

    // We're processing a Janitor notification: we'll go back to the plugin, find the matching plugin transaction (using the kbTransactionId) and update the payment, transaction and attempt states if needed
    boolean updatePaymentAndTransactionIfNeeded(final JanitorNotificationKey notificationKey,
                                                final UUID userToken,
                                                final InternalTenantContext internalTenantContext) throws LockFailedException {
        final UUID accountId = internalCallContextFactory.createTenantContext(internalTenantContext).getAccountId();
        final TransactionStatus latestTransactionStatus = incompletePaymentTransactionTask.updatePaymentAndTransactionIfNeeded(accountId,
                                                                                                                               notificationKey.getUuidKey(),
                                                                                                                               null,
                                                                                                                               null,
                                                                                                                               notificationKey.getAttemptNumber(),
                                                                                                                               userToken,
                                                                                                                               internalTenantContext);
        return latestTransactionStatus == null;

        /* TODO https://github.com/killbill/killbill/issues/1061.
        return updatePaymentAndTransactionIfNeeded(accountId,
                                                   notificationKey.getUuidKey(),
                                                   null,
                                                   null,
                                                   notificationKey.getAttemptNumber(),
                                                   userToken,
                                                   internalTenantContext);
         */
    }

    // On-the-fly Janitor: we already have the latest plugin information, we just update the payment, transaction and attempt states if needed
    public boolean updatePaymentAndTransactionIfNeeded(final UUID accountId,
                                                       final UUID paymentTransactionId,
                                                       final TransactionStatus currentTransactionStatus,
                                                       final PaymentTransactionInfoPlugin paymentTransactionInfoPlugin,
                                                       final InternalTenantContext internalTenantContext) {
        try {
            return updatePaymentAndTransactionIfNeeded(accountId,
                                                       paymentTransactionId,
                                                       currentTransactionStatus,
                                                       paymentTransactionInfoPlugin,
                                                       null,
                                                       null,
                                                       internalTenantContext);
        } catch (final LockFailedException e) {
            log.warn("Error locking accountRecordId='{}'", internalTenantContext.getAccountRecordId(), e);
            return false;
        }
    }

    private boolean updatePaymentAndTransactionIfNeeded(final UUID accountId,
                                                        final UUID paymentTransactionId,
                                                        @Nullable final TransactionStatus currentTransactionStatus,
                                                        @Nullable final PaymentTransactionInfoPlugin paymentTransactionInfoPlugin,
                                                        @Nullable final Integer attemptNumber,
                                                        @Nullable final UUID userToken,
                                                        final InternalTenantContext internalTenantContext) throws LockFailedException {
        // First, fix the transaction itself
        final TransactionStatus latestTransactionStatus = incompletePaymentTransactionTask.updatePaymentAndTransactionIfNeeded(accountId,
                                                                                                                               paymentTransactionId,
                                                                                                                               currentTransactionStatus,
                                                                                                                               paymentTransactionInfoPlugin,
                                                                                                                               attemptNumber,
                                                                                                                               userToken,
                                                                                                                               internalTenantContext);
        final boolean hasTransactionChanged = latestTransactionStatus == null;

        // Don't insert a notification for the on-the-fly Janitor
        final boolean shouldInsertNotification = attemptNumber != null;
        if (!hasTransactionChanged && shouldInsertNotification) {
            insertNewNotificationForUnresolvedTransactionIfNeeded(paymentTransactionId,
                                                                  latestTransactionStatus,
                                                                  attemptNumber,
                                                                  userToken,
                                                                  internalTenantContext.getAccountRecordId(),
                                                                  internalTenantContext.getTenantRecordId());
        }

        // If there is a payment attempt associated with that transaction, we need to update it as well
        boolean hasAttemptChanged = false;
        final PaymentTransactionModelDao paymentTransactionModelDao = paymentDao.getPaymentTransaction(paymentTransactionId, internalTenantContext);
        if (paymentTransactionModelDao.getAttemptId() != null) {
            final PaymentAttemptModelDao paymentAttemptModelDao = paymentDao.getPaymentAttempt(paymentTransactionModelDao.getAttemptId(), internalTenantContext);
            if (paymentAttemptModelDao != null) {
                // Run the completion part of the state machine to call the plugins and update the attempt in the right terminal state)
                hasAttemptChanged = doIteration(paymentAttemptModelDao);
            }
        }

        return hasTransactionChanged || hasAttemptChanged;
    }

    public void processPaymentEvent(final PaymentInternalEvent event) {
        if (!TRANSACTION_STATUSES_TO_CONSIDER.contains(event.getStatus())) {
            return;
        }
        insertNewNotificationForUnresolvedTransactionIfNeeded(event.getPaymentTransactionId(),
                                                              event.getStatus(),
                                                              0,
                                                              event.getUserToken(),
                                                              event.getSearchKey1(),
                                                              event.getSearchKey2());
    }

    private DateTime getCreatedDateBefore() {
        final long delayBeforeNowMs = paymentConfig.getIncompleteAttemptsTimeSpanDelay().getMillis();
        return clock.getUTCNow().minusMillis((int) delayBeforeNowMs);
    }
}
