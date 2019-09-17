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

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.account.api.ImmutableAccountData;
import org.killbill.billing.callcontext.DefaultCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.core.sm.PaymentControlStateMachineHelper;
import org.killbill.billing.payment.core.sm.PaymentStateMachineHelper;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.util.UUIDs;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.CallOrigin;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.callcontext.UserType;
import org.killbill.billing.util.config.definition.PaymentConfig;
import org.killbill.billing.util.globallocker.LockerType;
import org.killbill.clock.Clock;
import org.killbill.commons.locker.GlobalLock;
import org.killbill.commons.locker.GlobalLocker;
import org.killbill.commons.locker.LockFailedException;
import org.killbill.notificationq.api.NotificationEvent;
import org.killbill.notificationq.api.NotificationQueue;
import org.skife.config.TimeSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

abstract class CompletionTaskBase<T> {

    private static final Logger log = LoggerFactory.getLogger(CompletionTaskBase.class);

    protected static final ImmutableList<TransactionStatus> TRANSACTION_STATUSES_TO_CONSIDER = ImmutableList.<TransactionStatus>builder().add(TransactionStatus.PENDING)
                                                                                                                                         .add(TransactionStatus.UNKNOWN)
                                                                                                                                         .build();

    protected final PaymentConfig paymentConfig;
    protected final Clock clock;
    protected final PaymentDao paymentDao;
    protected final InternalCallContextFactory internalCallContextFactory;
    protected final PaymentStateMachineHelper paymentStateMachineHelper;
    protected final PaymentControlStateMachineHelper retrySMHelper;
    protected final AccountInternalApi accountInternalApi;
    protected final GlobalLocker locker;

    protected NotificationQueue janitorQueue;

    public CompletionTaskBase(final InternalCallContextFactory internalCallContextFactory,
                              final PaymentConfig paymentConfig,
                              final PaymentDao paymentDao,
                              final Clock clock,
                              final PaymentStateMachineHelper paymentStateMachineHelper,
                              final PaymentControlStateMachineHelper retrySMHelper,
                              final AccountInternalApi accountInternalApi,
                              final GlobalLocker locker) {
        this.internalCallContextFactory = internalCallContextFactory;
        this.paymentConfig = paymentConfig;
        this.paymentDao = paymentDao;
        this.clock = clock;
        this.paymentStateMachineHelper = paymentStateMachineHelper;
        this.retrySMHelper = retrySMHelper;
        this.accountInternalApi = accountInternalApi;
        this.locker = locker;
    }

    public void attachJanitorQueue(final NotificationQueue janitorQueue) {
        this.janitorQueue = janitorQueue;
    }

    public interface JanitorIterationCallback {

        public Boolean doIteration();
    }

    protected Boolean doJanitorOperationWithAccountLock(final JanitorIterationCallback callback, final InternalTenantContext internalTenantContext) {
        try {
            return tryToDoJanitorOperationWithAccountLock(callback, internalTenantContext);
        } catch (final LockFailedException e) {
            log.warn("Error locking accountRecordId='{}'", internalTenantContext.getAccountRecordId(), e);
        }
        return null;
    }

    protected Boolean tryToDoJanitorOperationWithAccountLock(final JanitorIterationCallback callback, final InternalTenantContext internalTenantContext) throws LockFailedException {
        GlobalLock lock = null;
        try {
            final ImmutableAccountData account = accountInternalApi.getImmutableAccountDataByRecordId(internalTenantContext.getAccountRecordId(), internalTenantContext);
            lock = locker.lockWithNumberOfTries(LockerType.ACCNT_INV_PAY.toString(), account.getId().toString(), paymentConfig.getMaxGlobalLockRetries());
            return callback.doIteration();
        } catch (final AccountApiException e) {
            log.warn("Error retrieving accountRecordId='{}'", internalTenantContext.getAccountRecordId(), e);
        } finally {
            if (lock != null) {
                lock.release();
            }
        }
        return null;
    }

    protected CallContext createCallContext(final String taskName, final InternalTenantContext internalTenantContext) {
        final TenantContext tenantContext = internalCallContextFactory.createTenantContext(internalTenantContext);
        return new DefaultCallContext(null, tenantContext.getTenantId(), taskName, CallOrigin.INTERNAL, UserType.SYSTEM, UUIDs.randomUUID(), clock);
    }

    protected void insertNewNotificationForUnresolvedTransactionIfNeeded(final UUID paymentTransactionId,
                                                                         final TransactionStatus transactionStatus,
                                                                         final Integer attemptNumber,
                                                                         final UUID userToken,
                                                                         final Long accountRecordId,
                                                                         final Long tenantRecordId) {
        final InternalTenantContext tenantContext = internalCallContextFactory.createInternalTenantContext(tenantRecordId, accountRecordId);

        // Increment value before we insert
        final Integer newAttemptNumber = attemptNumber + 1;
        final NotificationEvent key = new JanitorNotificationKey(paymentTransactionId, IncompletePaymentTransactionTask.class.toString(), newAttemptNumber);
        final DateTime notificationTime = getNextNotificationTime(transactionStatus, newAttemptNumber, tenantContext);
        // Will be null in the GET path or when we run out opf attempts..
        if (notificationTime != null) {
            try {
                janitorQueue.recordFutureNotification(notificationTime, key, userToken, accountRecordId, tenantRecordId);
            } catch (final IOException e) {
                log.warn("Failed to insert future notification for paymentTransactionId = {}: {}", paymentTransactionId, e.getMessage());
            }
        }
    }

    @VisibleForTesting
    DateTime getNextNotificationTime(final TransactionStatus transactionStatus, final Integer attemptNumber, final InternalTenantContext internalTenantContext) {
        final List<TimeSpan> retries;
        if (TransactionStatus.UNKNOWN.equals(transactionStatus)) {
            retries = paymentConfig.getUnknownTransactionsRetries(internalTenantContext);
        } else if (TransactionStatus.PENDING.equals(transactionStatus)) {
            retries = paymentConfig.getPendingTransactionsRetries(internalTenantContext);
        } else {
            retries = ImmutableList.of();
            log.warn("Unexpected transactionStatus='{}' from janitor, ignore...", transactionStatus);
        }

        if (attemptNumber > retries.size()) {
            return null;
        }
        final TimeSpan nextDelay = retries.get(attemptNumber - 1);
        return clock.getUTCNow().plusMillis((int) nextDelay.getMillis());
    }
}
