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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.core.PaymentPluginServiceRegistration;
import org.killbill.billing.payment.core.PaymentTransactionInfoPluginConverter;
import org.killbill.billing.payment.core.sm.PaymentControlStateMachineHelper;
import org.killbill.billing.payment.core.sm.PaymentStateMachineHelper;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.dao.PaymentModelDao;
import org.killbill.billing.payment.dao.PaymentTransactionModelDao;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.payment.plugin.api.PaymentPluginStatus;
import org.killbill.billing.payment.plugin.api.PaymentTransactionInfoPlugin;
import org.killbill.billing.payment.provider.DefaultNoOpPaymentInfoPlugin;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.config.definition.PaymentConfig;
import org.killbill.clock.Clock;
import org.killbill.commons.locker.GlobalLocker;
import org.killbill.commons.locker.LockFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class IncompletePaymentTransactionTask extends CompletionTaskBase<PaymentTransactionModelDao> {

    private static final Logger log = LoggerFactory.getLogger(IncompletePaymentTransactionTask.class);

    private final PaymentPluginServiceRegistration paymentPluginServiceRegistration;

    @Inject
    public IncompletePaymentTransactionTask(final InternalCallContextFactory internalCallContextFactory,
                                            final PaymentConfig paymentConfig,
                                            final PaymentDao paymentDao,
                                            final Clock clock,
                                            final PaymentStateMachineHelper paymentStateMachineHelper,
                                            final PaymentControlStateMachineHelper retrySMHelper,
                                            final AccountInternalApi accountInternalApi,
                                            final PaymentPluginServiceRegistration paymentPluginServiceRegistration,
                                            final GlobalLocker locker) {
        super(internalCallContextFactory, paymentConfig, paymentDao, clock, paymentStateMachineHelper, retrySMHelper, accountInternalApi, locker);
        this.paymentPluginServiceRegistration = paymentPluginServiceRegistration;
    }

    @Override
    public Iterable<PaymentTransactionModelDao> getItemsForIteration() {
        // This is not triggered by Janitor proper but instead relies on bus event + notificationQ
        return ImmutableList.of();
    }

    @Override
    public void doIteration(final PaymentTransactionModelDao paymentTransaction) {
        // Nothing
    }

    protected void tryToProcessNotification(final JanitorNotificationKey notificationKey, final UUID userToken, final Long accountRecordId, final long tenantRecordId) throws LockFailedException {
        final InternalTenantContext internalTenantContext = internalCallContextFactory.createInternalTenantContext(tenantRecordId, accountRecordId);
        tryToDoJanitorOperationWithAccountLock(new JanitorIterationCallback() {
            @Override
            public Void doIteration() {
                // State may have changed since we originally retrieved with no lock
                final PaymentTransactionModelDao rehydratedPaymentTransaction = paymentDao.getPaymentTransaction(notificationKey.getUuidKey(), internalTenantContext);

                final TenantContext tenantContext = internalCallContextFactory.createTenantContext(internalTenantContext);
                final PaymentModelDao payment = paymentDao.getPayment(rehydratedPaymentTransaction.getPaymentId(), internalTenantContext);

                final PaymentTransactionInfoPlugin undefinedPaymentTransaction = new DefaultNoOpPaymentInfoPlugin(payment.getId(),
                                                                                                                  rehydratedPaymentTransaction.getId(),
                                                                                                                  rehydratedPaymentTransaction.getTransactionType(),
                                                                                                                  rehydratedPaymentTransaction.getAmount(),
                                                                                                                  rehydratedPaymentTransaction.getCurrency(),
                                                                                                                  rehydratedPaymentTransaction.getCreatedDate(),
                                                                                                                  rehydratedPaymentTransaction.getCreatedDate(),
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
                            return input.getKbTransactionPaymentId().equals(rehydratedPaymentTransaction.getId());
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
                updatePaymentAndTransactionIfNeeded(payment, notificationKey.getAttemptNumber(), userToken, rehydratedPaymentTransaction, paymentTransactionInfoPlugin, internalTenantContext);
                return null;
            }
        }, internalTenantContext);
    }

    public boolean updatePaymentAndTransactionIfNeededWithAccountLock(final PaymentModelDao payment, final PaymentTransactionModelDao paymentTransaction, final PaymentTransactionInfoPlugin paymentTransactionInfoPlugin, final InternalTenantContext internalTenantContext) {
        // Can happen in the GET case, see PaymentProcessor#toPayment
        if (!TRANSACTION_STATUSES_TO_CONSIDER.contains(paymentTransaction.getTransactionStatus())) {
            // Nothing to do
            return false;
        }
        final Boolean result = doJanitorOperationWithAccountLock(new JanitorIterationCallback() {
            @Override
            public Boolean doIteration() {
                final PaymentTransactionModelDao refreshedPaymentTransaction = paymentDao.getPaymentTransaction(paymentTransaction.getId(), internalTenantContext);
                return updatePaymentAndTransactionInternal(payment, null, null, refreshedPaymentTransaction, paymentTransactionInfoPlugin, internalTenantContext);
            }
        }, internalTenantContext);
        return result != null && result;
    }

    private boolean updatePaymentAndTransactionIfNeeded(final PaymentModelDao payment, final int attemptNumber, final UUID userToken, final PaymentTransactionModelDao paymentTransaction, final PaymentTransactionInfoPlugin paymentTransactionInfoPlugin, final InternalTenantContext internalTenantContext) {
        if (!TRANSACTION_STATUSES_TO_CONSIDER.contains(paymentTransaction.getTransactionStatus())) {
            // Nothing to do
            return false;
        }
        return updatePaymentAndTransactionInternal(payment, attemptNumber, userToken, paymentTransaction, paymentTransactionInfoPlugin, internalTenantContext);
    }

    private boolean updatePaymentAndTransactionInternal(final PaymentModelDao payment, @Nullable final Integer attemptNumber, @Nullable final UUID userToken, final PaymentTransactionModelDao paymentTransaction, final PaymentTransactionInfoPlugin paymentTransactionInfoPlugin, final InternalTenantContext internalTenantContext) {
        final CallContext callContext = createCallContext("IncompletePaymentTransactionTask", internalTenantContext);

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
                         payment.getId(), paymentTransaction.getId(), paymentTransaction.getTransactionStatus(), transactionStatus);
                insertNewNotificationForUnresolvedTransactionIfNeeded(paymentTransaction.getId(), transactionStatus, attemptNumber, userToken, internalTenantContext.getAccountRecordId(), internalTenantContext.getTenantRecordId());
                return false;
        }

        // Our status did not change, so we just insert a new notification (attemptNumber will be incremented)
        if (transactionStatus == paymentTransaction.getTransactionStatus()) {
            log.info("Unable to repair paymentId='{}', paymentTransactionId='{}', currentTransactionStatus='{}', newTransactionStatus='{}'",
                     payment.getId(), paymentTransaction.getId(), paymentTransaction.getTransactionStatus(), transactionStatus);
            insertNewNotificationForUnresolvedTransactionIfNeeded(paymentTransaction.getId(), transactionStatus, attemptNumber, userToken, internalTenantContext.getAccountRecordId(), internalTenantContext.getTenantRecordId());
            return false;
        }

        // Update processedAmount and processedCurrency
        final BigDecimal processedAmount;
        if (TransactionStatus.SUCCESS.equals(transactionStatus) || TransactionStatus.PENDING.equals(transactionStatus)) {
            if (paymentTransactionInfoPlugin == null || paymentTransactionInfoPlugin.getAmount() == null) {
                processedAmount = paymentTransaction.getProcessedAmount();
            } else {
                processedAmount = paymentTransactionInfoPlugin.getAmount();
            }
        } else {
            processedAmount = BigDecimal.ZERO;
        }
        final Currency processedCurrency;
        if (paymentTransactionInfoPlugin == null || paymentTransactionInfoPlugin.getCurrency() == null) {
            processedCurrency = paymentTransaction.getProcessedCurrency();
        } else {
            processedCurrency = paymentTransactionInfoPlugin.getCurrency();
        }

        // Update the gatewayErrorCode, gatewayError if we got a paymentTransactionInfoPlugin
        final String gatewayErrorCode = paymentTransactionInfoPlugin != null ? paymentTransactionInfoPlugin.getGatewayErrorCode() : paymentTransaction.getGatewayErrorCode();
        final String gatewayError = paymentTransactionInfoPlugin != null ? paymentTransactionInfoPlugin.getGatewayError() : paymentTransaction.getGatewayErrorMsg();

        log.info("Repairing paymentId='{}', paymentTransactionId='{}', currentTransactionStatus='{}', newTransactionStatus='{}'",
                 payment.getId(), paymentTransaction.getId(), paymentTransaction.getTransactionStatus(), transactionStatus);

        final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(payment.getAccountId(), callContext);

        // Recompute new lastSuccessPaymentState. This is important to be able to allow new operations on the state machine (for e.g an AUTH_SUCCESS would now allow a CAPTURE operation)
        if (paymentStateMachineHelper.isSuccessState(newPaymentState)) {
            final String lastSuccessPaymentState = newPaymentState;
            paymentDao.updatePaymentAndTransactionOnCompletion(payment.getAccountId(), paymentTransaction.getAttemptId(), payment.getId(), paymentTransaction.getTransactionType(), newPaymentState, lastSuccessPaymentState,
                                                               paymentTransaction.getId(), transactionStatus, processedAmount, processedCurrency, gatewayErrorCode, gatewayError, internalCallContext);
        } else {
            paymentDao.updatePaymentAndTransactionOnCompletion(payment.getAccountId(), paymentTransaction.getAttemptId(), payment.getId(), paymentTransaction.getTransactionType(), newPaymentState,
                                                               paymentTransaction.getId(), transactionStatus, processedAmount, processedCurrency, gatewayErrorCode, gatewayError, internalCallContext);
        }

        return true;
    }

    // Keep the existing currentTransactionStatus if we can't obtain a better answer from the plugin; if not, return the newTransactionStatus
    private TransactionStatus computeNewTransactionStatusFromPaymentTransactionInfoPlugin(final PaymentTransactionInfoPlugin input, final TransactionStatus currentTransactionStatus) {
        final TransactionStatus newTransactionStatus = PaymentTransactionInfoPluginConverter.toTransactionStatus(input);
        return (newTransactionStatus != TransactionStatus.UNKNOWN) ? newTransactionStatus : currentTransactionStatus;
    }
}
