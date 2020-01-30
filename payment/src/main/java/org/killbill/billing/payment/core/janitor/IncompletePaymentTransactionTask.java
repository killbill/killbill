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

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.account.api.ImmutableAccountData;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.core.PaymentPluginServiceRegistration;
import org.killbill.billing.payment.core.PaymentTransactionInfoPluginConverter;
import org.killbill.billing.payment.core.sm.PaymentAutomatonDAOHelper;
import org.killbill.billing.payment.core.sm.PaymentStateMachineHelper;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.dao.PaymentModelDao;
import org.killbill.billing.payment.dao.PaymentTransactionModelDao;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.payment.plugin.api.PaymentPluginStatus;
import org.killbill.billing.payment.plugin.api.PaymentTransactionInfoPlugin;
import org.killbill.billing.payment.provider.DefaultNoOpPaymentInfoPlugin;
import org.killbill.billing.util.UUIDs;
import org.killbill.billing.util.callcontext.CallOrigin;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.callcontext.UserType;
import org.killbill.billing.util.config.definition.PaymentConfig;
import org.killbill.billing.util.globallocker.LockerType;
import org.killbill.commons.locker.GlobalLock;
import org.killbill.commons.locker.GlobalLocker;
import org.killbill.commons.locker.LockFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

// Janitor implementation for payment transactions only (see IncompletePaymentAttemptTask for payments going through the control APIs).
// Invoked on-the-fly or as part of the Janitor notification queue
public class IncompletePaymentTransactionTask {

    private static final Logger log = LoggerFactory.getLogger(IncompletePaymentTransactionTask.class);

    static final ImmutableList<TransactionStatus> TRANSACTION_STATUSES_TO_CONSIDER = ImmutableList.<TransactionStatus>builder().add(TransactionStatus.PENDING)
                                                                                                                               .add(TransactionStatus.UNKNOWN)
                                                                                                                               .build();

    private final PaymentConfig paymentConfig;
    private final PaymentDao paymentDao;
    private final InternalCallContextFactory internalCallContextFactory;
    private final PaymentStateMachineHelper paymentStateMachineHelper;
    private final AccountInternalApi accountInternalApi;
    private final GlobalLocker locker;
    private final PaymentPluginServiceRegistration paymentPluginServiceRegistration;

    @Inject
    public IncompletePaymentTransactionTask(final InternalCallContextFactory internalCallContextFactory,
                                            final PaymentConfig paymentConfig,
                                            final PaymentDao paymentDao,
                                            final PaymentStateMachineHelper paymentStateMachineHelper,
                                            final AccountInternalApi accountInternalApi,
                                            final PaymentPluginServiceRegistration paymentPluginServiceRegistration,
                                            final GlobalLocker locker) {
        this.internalCallContextFactory = internalCallContextFactory;
        this.paymentConfig = paymentConfig;
        this.paymentDao = paymentDao;
        this.paymentStateMachineHelper = paymentStateMachineHelper;
        this.accountInternalApi = accountInternalApi;
        this.locker = locker;
        this.paymentPluginServiceRegistration = paymentPluginServiceRegistration;
    }

    // On-the-fly Janitor: we already have the latest plugin information, we just update the payment & transaction states if needed
    public boolean updatePaymentAndTransactionIfNeeded(final UUID accountId,
                                                       final UUID paymentTransactionId,
                                                       final TransactionStatus currentTransactionStatus,
                                                       final PaymentTransactionInfoPlugin paymentTransactionInfoPlugin,
                                                       final boolean isApiPayment,
                                                       final InternalTenantContext internalTenantContext) {
        try {
            final TransactionStatus latestTransactionStatus = updatePaymentAndTransactionIfNeeded2(accountId,
                                                                                                  paymentTransactionId,
                                                                                                  currentTransactionStatus,
                                                                                                  paymentTransactionInfoPlugin,
                                                                                                  isApiPayment,
                                                                                                  internalTenantContext);
            return latestTransactionStatus == null;
        } catch (final LockFailedException e) {
            log.warn("Error locking accountRecordId='{}'", internalTenantContext.getAccountRecordId(), e);
            return false;
        }
    }

    // By convention, we return null if the update was 'needed' and happened.
    TransactionStatus updatePaymentAndTransactionIfNeeded2(final UUID accountId,
                                                          final UUID paymentTransactionId,
                                                          @Nullable final TransactionStatus currentTransactionStatus,
                                                          @Nullable final PaymentTransactionInfoPlugin paymentTransactionInfoPlugin,
                                                          final boolean isApiPayment,
                                                          final InternalTenantContext internalTenantContext) throws LockFailedException {
        // In the GET case, make sure we bail as early as possible (see PaymentRefresher)
        if (currentTransactionStatus != null && !TRANSACTION_STATUSES_TO_CONSIDER.contains(currentTransactionStatus)) {
            // Nothing to do, so we return the currentTransactionStatus to indicate that nothing has changed
            return currentTransactionStatus;
        }

        return tryToDoJanitorOperationWithAccountLock(new JanitorIterationCallback() {
            @Override
            public TransactionStatus doIteration() {
                return updatePaymentAndTransactionIfNeeded(accountId,
                                                           paymentTransactionId,
                                                           paymentTransactionInfoPlugin,
                                                           isApiPayment,
                                                           internalTenantContext);
            }
        }, internalTenantContext);
    }

    private TransactionStatus updatePaymentAndTransactionIfNeeded(final UUID accountId,
                                                                  final UUID paymentTransactionId,
                                                                  @Nullable final PaymentTransactionInfoPlugin paymentTransactionInfoPlugin,
                                                                  final boolean isApiPayment,
                                                                  final InternalTenantContext internalTenantContext) {
        // State may have changed since we originally retrieved with no lock
        final PaymentTransactionModelDao paymentTransaction = paymentDao.getPaymentTransaction(paymentTransactionId, internalTenantContext);
        if (!TRANSACTION_STATUSES_TO_CONSIDER.contains(paymentTransaction.getTransactionStatus())) {
            // Nothing to do, so we return the currentTransactionStatus to indicate that nothing has changed
            return paymentTransaction.getTransactionStatus();
        }

        // On-the-fly Janitor already has the latest state, avoid a round-trip to the plugin
        final PaymentTransactionInfoPlugin latestPaymentTransactionInfoPlugin = paymentTransactionInfoPlugin != null ? paymentTransactionInfoPlugin : getLatestPaymentTransactionInfoPlugin(paymentTransaction, internalTenantContext);
        return updatePaymentAndTransactionInternal(accountId,
                                                   paymentTransaction,
                                                   latestPaymentTransactionInfoPlugin,
                                                   isApiPayment,
                                                   internalTenantContext);
    }

    // Return the latest transactionStatus in case the state wasn't updated, null otherwise
    private TransactionStatus updatePaymentAndTransactionInternal(final UUID accountId,
                                                                  final PaymentTransactionModelDao paymentTransaction,
                                                                  final PaymentTransactionInfoPlugin paymentTransactionInfoPlugin,
                                                                  final boolean isApiPayment,
                                                                  final InternalTenantContext internalTenantContext) {
        final UUID paymentId = paymentTransaction.getPaymentId();

        // First obtain the new transactionStatus,
        // Then compute the new paymentState; this one is mostly interesting in case of success (to compute the lastSuccessPaymentState below)
        final TransactionStatus transactionStatus = computeNewTransactionStatusFromPaymentTransactionInfoPlugin(paymentTransactionInfoPlugin, paymentTransaction.getTransactionStatus());
        final String newPaymentState;
        switch (transactionStatus) {
            case PENDING:
                newPaymentState = paymentStateMachineHelper.getPendingStateForTransaction(paymentTransaction.getTransactionType());
                break;
            case SUCCESS:
                newPaymentState = paymentStateMachineHelper.getSuccessfulStateForTransaction(paymentTransaction.getTransactionType());
                break;
            case PAYMENT_FAILURE:
                newPaymentState = paymentStateMachineHelper.getFailureStateForTransaction(paymentTransaction.getTransactionType());
                break;
            case PLUGIN_FAILURE:
                newPaymentState = paymentStateMachineHelper.getErroredStateForTransaction(paymentTransaction.getTransactionType());
                break;
            case UNKNOWN:
            default:
                // We can't get anything interesting from the plugin...
                log.info("Unable to repair paymentId='{}', paymentTransactionId='{}', currentTransactionStatus='{}', newTransactionStatus='{}'",
                         paymentId, paymentTransaction.getId(), paymentTransaction.getTransactionStatus(), transactionStatus);
                return transactionStatus;
        }

        // Our status did not change, so we just insert a new notification (attemptNumber will be incremented)
        if (transactionStatus == paymentTransaction.getTransactionStatus()) {
            log.info("Unable to repair paymentId='{}', paymentTransactionId='{}', currentTransactionStatus='{}', newTransactionStatus='{}'",
                     paymentId, paymentTransaction.getId(), paymentTransaction.getTransactionStatus(), transactionStatus);
            return transactionStatus;
        }

        final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(internalTenantContext.getTenantRecordId(),
                                                                                                             internalTenantContext.getAccountRecordId(),
                                                                                                             "IncompletePaymentTransactionTask",
                                                                                                             CallOrigin.INTERNAL,
                                                                                                             UserType.SYSTEM,
                                                                                                             UUIDs.randomUUID());
        final PaymentAutomatonDAOHelper paymentAutomatonDAOHelper = new PaymentAutomatonDAOHelper(paymentDao,
                                                                                                  internalCallContext,
                                                                                                  paymentStateMachineHelper);

        log.info("Repairing paymentId='{}', paymentTransactionId='{}', currentTransactionStatus='{}', newTransactionStatus='{}'",
                 paymentId, paymentTransaction.getId(), paymentTransaction.getTransactionStatus(), transactionStatus);
        paymentAutomatonDAOHelper.processPaymentInfoPlugin(transactionStatus,
                                                           paymentTransactionInfoPlugin,
                                                           newPaymentState,
                                                           paymentTransaction.getProcessedAmount(),
                                                           paymentTransaction.getProcessedCurrency(),
                                                           accountId,
                                                           paymentTransaction.getAttemptId(),
                                                           paymentId,
                                                           paymentTransaction.getId(),
                                                           paymentTransaction.getTransactionType(),
                                                           isApiPayment);

        return null;
    }

    // Keep the existing currentTransactionStatus if we can't obtain a better answer from the plugin; if not, return the newTransactionStatus
    private TransactionStatus computeNewTransactionStatusFromPaymentTransactionInfoPlugin(final PaymentTransactionInfoPlugin input,
                                                                                          final TransactionStatus currentTransactionStatus) {
        final TransactionStatus newTransactionStatus = PaymentTransactionInfoPluginConverter.toTransactionStatus(input);
        return (newTransactionStatus != TransactionStatus.UNKNOWN) ? newTransactionStatus : currentTransactionStatus;
    }

    private PaymentTransactionInfoPlugin getLatestPaymentTransactionInfoPlugin(final PaymentTransactionModelDao paymentTransaction,
                                                                               final InternalTenantContext internalTenantContext) {
        final TenantContext tenantContext = internalCallContextFactory.createTenantContext(internalTenantContext);
        final PaymentModelDao payment = paymentDao.getPayment(paymentTransaction.getPaymentId(), internalTenantContext);

        final PaymentTransactionInfoPlugin undefinedPaymentTransaction = new DefaultNoOpPaymentInfoPlugin(payment.getId(),
                                                                                                          paymentTransaction.getId(),
                                                                                                          paymentTransaction.getTransactionType(),
                                                                                                          paymentTransaction.getAmount(),
                                                                                                          paymentTransaction.getCurrency(),
                                                                                                          paymentTransaction.getCreatedDate(),
                                                                                                          paymentTransaction.getCreatedDate(),
                                                                                                          PaymentPluginStatus.UNDEFINED,
                                                                                                          null,
                                                                                                          null);
        PaymentTransactionInfoPlugin paymentTransactionInfoPlugin;
        try {
            final PaymentPluginApi paymentPluginApi = paymentPluginServiceRegistration.getPaymentPluginApi(payment.getPaymentMethodId(), false, internalTenantContext);
            final List<PaymentTransactionInfoPlugin> result = paymentPluginApi.getPaymentInfo(payment.getAccountId(), payment.getId(), ImmutableList.<PluginProperty>of(), tenantContext);
            paymentTransactionInfoPlugin = Iterables.tryFind(result, new Predicate<PaymentTransactionInfoPlugin>() {
                @Override
                public boolean apply(final PaymentTransactionInfoPlugin input) {
                    return input.getKbTransactionPaymentId().equals(paymentTransaction.getId());
                }
            }).or(new Supplier<PaymentTransactionInfoPlugin>() {
                @Override
                public PaymentTransactionInfoPlugin get() {
                    return undefinedPaymentTransaction;
                }
            });
        } catch (final Exception e) {
            paymentTransactionInfoPlugin = undefinedPaymentTransaction;
        }
        return paymentTransactionInfoPlugin;
    }

    private interface JanitorIterationCallback {

        TransactionStatus doIteration();
    }

    private TransactionStatus tryToDoJanitorOperationWithAccountLock(final JanitorIterationCallback callback, final InternalTenantContext internalTenantContext) throws LockFailedException {
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
}
