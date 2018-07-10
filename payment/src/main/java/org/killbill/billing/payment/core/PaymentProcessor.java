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

package org.killbill.billing.payment.core;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.joda.time.DateTime;
import org.killbill.automaton.OperationResult;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.InvoiceInternalApi;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.core.sm.PaymentAutomatonDAOHelper;
import org.killbill.billing.payment.core.sm.PaymentAutomatonRunner;
import org.killbill.billing.payment.core.sm.PaymentStateContext;
import org.killbill.billing.payment.dao.PaymentAttemptModelDao;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.dao.PaymentModelDao;
import org.killbill.billing.payment.dao.PaymentTransactionModelDao;
import org.killbill.billing.payment.glue.DefaultPaymentService;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.payment.plugin.api.PaymentTransactionInfoPlugin;
import org.killbill.billing.payment.retry.DefaultRetryService;
import org.killbill.billing.payment.retry.PaymentRetryNotificationKey;
import org.killbill.billing.tag.TagInternalApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.clock.Clock;
import org.killbill.commons.locker.GlobalLocker;
import org.killbill.notificationq.api.NotificationEvent;
import org.killbill.notificationq.api.NotificationEventWithMetadata;
import org.killbill.notificationq.api.NotificationQueue;
import org.killbill.notificationq.api.NotificationQueueService;
import org.killbill.notificationq.api.NotificationQueueService.NoSuchNotificationQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class PaymentProcessor extends ProcessorBase {

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessor.class);

    private static final ImmutableList<PluginProperty> PLUGIN_PROPERTIES = ImmutableList.<PluginProperty>of();

    private final PaymentAutomatonRunner paymentAutomatonRunner;
    private final NotificationQueueService notificationQueueService;
    private final PaymentRefresher paymentRefresher;

    @Inject
    public PaymentProcessor(final PaymentPluginServiceRegistration paymentPluginServiceRegistration,
                            final AccountInternalApi accountUserApi,
                            final InvoiceInternalApi invoiceApi,
                            final TagInternalApi tagUserApi,
                            final PaymentDao paymentDao,
                            final InternalCallContextFactory internalCallContextFactory,
                            final GlobalLocker locker,
                            final PaymentAutomatonRunner paymentAutomatonRunner,
                            final NotificationQueueService notificationQueueService,
                            final Clock clock, final PaymentRefresher paymentRefresher) {
        super(paymentPluginServiceRegistration, accountUserApi, paymentDao, tagUserApi, locker, internalCallContextFactory, invoiceApi, clock);
        this.paymentAutomatonRunner = paymentAutomatonRunner;
        this.notificationQueueService = notificationQueueService;
        this.paymentRefresher = paymentRefresher;
    }

    public Payment createAuthorization(final boolean isApiPayment, @Nullable final UUID attemptId, final Account account, @Nullable final UUID paymentMethodId, @Nullable final UUID paymentId, final BigDecimal amount, final Currency currency, @Nullable final DateTime effectiveDate,
                                       @Nullable final String paymentExternalKey, @Nullable final String paymentTransactionExternalKey, @Nullable final UUID paymentIdForNewPayment, @Nullable final UUID paymentTransactionIdForNewPaymentTransaction, final boolean shouldLockAccountAndDispatch,
                                       final Iterable<PluginProperty> properties, final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
        return performOperation(isApiPayment, attemptId, TransactionType.AUTHORIZE, account, paymentMethodId, paymentId, null, amount, currency, effectiveDate, paymentExternalKey, paymentTransactionExternalKey, paymentIdForNewPayment, paymentTransactionIdForNewPaymentTransaction, shouldLockAccountAndDispatch, null, properties, callContext, internalCallContext);
    }

    public Payment createCapture(final boolean isApiPayment, @Nullable final UUID attemptId, final Account account, final UUID paymentId, final BigDecimal amount, final Currency currency, @Nullable final DateTime effectiveDate,
                                 @Nullable final String paymentTransactionExternalKey, @Nullable final UUID paymentTransactionIdForNewPaymentTransaction, final boolean shouldLockAccountAndDispatch,
                                 final Iterable<PluginProperty> properties, final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
        return performOperation(isApiPayment, attemptId, TransactionType.CAPTURE, account, null, paymentId, null, amount, currency, effectiveDate, null, paymentTransactionExternalKey, null, paymentTransactionIdForNewPaymentTransaction, shouldLockAccountAndDispatch, null, properties, callContext, internalCallContext);
    }

    public Payment createPurchase(final boolean isApiPayment, @Nullable final UUID attemptId, final Account account, @Nullable final UUID paymentMethodId, @Nullable final UUID paymentId, final BigDecimal amount, final Currency currency, @Nullable final DateTime effectiveDate,
                                  @Nullable final String paymentExternalKey, @Nullable final String paymentTransactionExternalKey, @Nullable final UUID paymentIdForNewPayment, @Nullable final UUID paymentTransactionIdForNewPaymentTransaction, final boolean shouldLockAccountAndDispatch,
                                  final Iterable<PluginProperty> properties, final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
        return performOperation(isApiPayment, attemptId, TransactionType.PURCHASE, account, paymentMethodId, paymentId, null, amount, currency, effectiveDate, paymentExternalKey, paymentTransactionExternalKey, paymentIdForNewPayment, paymentTransactionIdForNewPaymentTransaction, shouldLockAccountAndDispatch, null, properties, callContext, internalCallContext);
    }

    public Payment createVoid(final boolean isApiPayment, @Nullable final UUID attemptId, final Account account, final UUID paymentId, @Nullable final DateTime effectiveDate, @Nullable final String paymentTransactionExternalKey, @Nullable final UUID paymentTransactionIdForNewPaymentTransaction, final boolean shouldLockAccountAndDispatch,
                              final Iterable<PluginProperty> properties, final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
        return performOperation(isApiPayment, attemptId, TransactionType.VOID, account, null, paymentId, null, null, null, effectiveDate, null, paymentTransactionExternalKey, null, paymentTransactionIdForNewPaymentTransaction, shouldLockAccountAndDispatch, null, properties, callContext, internalCallContext);
    }

    public Payment createRefund(final boolean isApiPayment, @Nullable final UUID attemptId, final Account account, final UUID paymentId, final BigDecimal amount, final Currency currency, @Nullable final DateTime effectiveDate,
                                final String paymentTransactionExternalKey, @Nullable final UUID paymentTransactionIdForNewPaymentTransaction, final boolean shouldLockAccountAndDispatch,
                                final Iterable<PluginProperty> properties, final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
        return performOperation(isApiPayment, attemptId, TransactionType.REFUND, account, null, paymentId, null, amount, currency, effectiveDate, null, paymentTransactionExternalKey, null, paymentTransactionIdForNewPaymentTransaction, shouldLockAccountAndDispatch, null, properties, callContext, internalCallContext);
    }

    public Payment createCredit(final boolean isApiPayment, @Nullable final UUID attemptId, final Account account, @Nullable final UUID paymentMethodId, @Nullable final UUID paymentId, final BigDecimal amount, final Currency currency, @Nullable final DateTime effectiveDate,
                                @Nullable final String paymentExternalKey, @Nullable final String paymentTransactionExternalKey, @Nullable final UUID paymentIdForNewPayment, @Nullable final UUID paymentTransactionIdForNewPaymentTransaction, final boolean shouldLockAccountAndDispatch,
                                final Iterable<PluginProperty> properties, final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
        return performOperation(isApiPayment, attemptId, TransactionType.CREDIT, account, paymentMethodId, paymentId, null, amount, currency, effectiveDate, paymentExternalKey, paymentTransactionExternalKey, paymentIdForNewPayment, paymentTransactionIdForNewPaymentTransaction, shouldLockAccountAndDispatch, null, properties, callContext, internalCallContext);
    }

    public Payment createChargeback(final boolean isApiPayment, @Nullable final UUID attemptId, final Account account, final UUID paymentId, @Nullable final String paymentTransactionExternalKey, final BigDecimal amount, final Currency currency, @Nullable final DateTime effectiveDate, @Nullable final UUID paymentTransactionIdForNewPaymentTransaction, final boolean shouldLockAccountAndDispatch,
                                    final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
        return performOperation(isApiPayment, attemptId, TransactionType.CHARGEBACK, account, null, paymentId, null, amount, currency, effectiveDate, null, paymentTransactionExternalKey, null, paymentTransactionIdForNewPaymentTransaction, shouldLockAccountAndDispatch, null, PLUGIN_PROPERTIES, callContext, internalCallContext);
    }

    public Payment createChargebackReversal(final boolean isApiPayment, @Nullable final UUID attemptId, final Account account, final UUID paymentId, @Nullable final String paymentTransactionExternalKey, final BigDecimal amount, final Currency currency, @Nullable final DateTime effectiveDate, @Nullable final UUID paymentTransactionIdForNewPaymentTransaction, final boolean shouldLockAccountAndDispatch,
                                            final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
        return performOperation(isApiPayment, attemptId, TransactionType.CHARGEBACK, account, null, paymentId, null, amount, currency, effectiveDate, null, paymentTransactionExternalKey, null, paymentTransactionIdForNewPaymentTransaction, shouldLockAccountAndDispatch, OperationResult.FAILURE, PLUGIN_PROPERTIES, callContext, internalCallContext);
    }

    public Payment notifyPendingPaymentOfStateChanged(final Account account, final UUID transactionId, final boolean isSuccess, final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
        final PaymentTransactionModelDao transactionModelDao = paymentDao.getPaymentTransaction(transactionId, internalCallContext);
        if (transactionModelDao.getTransactionStatus() != TransactionStatus.PENDING) {
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_SUCCESS_PAYMENT, transactionModelDao.getPaymentId());
        }

        final OperationResult overridePluginResult = isSuccess ? OperationResult.SUCCESS : OperationResult.FAILURE;

        final boolean runJanitor = false;
        return performOperation(true, runJanitor, null, transactionModelDao.getTransactionType(), account, null, transactionModelDao.getPaymentId(),
                                transactionModelDao.getId(), transactionModelDao.getAmount(), transactionModelDao.getCurrency(), null, null, transactionModelDao.getTransactionExternalKey(), null, null, true,
                                overridePluginResult, PLUGIN_PROPERTIES, callContext, internalCallContext);
    }

    public void cancelScheduledPaymentTransaction(@Nullable final UUID paymentTransactionId, @Nullable final String paymentTransactionExternalKey, final CallContext callContext) throws PaymentApiException {

        final InternalCallContext internalCallContextWithoutAccountRecordId = internalCallContextFactory.createInternalCallContextWithoutAccountRecordId(callContext);
        final String effectivePaymentTransactionExternalKey;
        if (paymentTransactionExternalKey == null) {
            final PaymentTransactionModelDao transaction = paymentDao.getPaymentTransaction(paymentTransactionId, internalCallContextWithoutAccountRecordId);
            effectivePaymentTransactionExternalKey = transaction.getTransactionExternalKey();
        } else {
            effectivePaymentTransactionExternalKey = paymentTransactionExternalKey;
        }

        final List<PaymentAttemptModelDao> attempts = paymentDao.getPaymentAttemptByTransactionExternalKey(effectivePaymentTransactionExternalKey, internalCallContextWithoutAccountRecordId);
        if (attempts.isEmpty()) {
            return;
        }

        final PaymentAttemptModelDao lastPaymentAttempt = attempts.get(attempts.size() - 1);
        final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(lastPaymentAttempt.getAccountId(), callContext);

        cancelScheduledPaymentTransaction(lastPaymentAttempt.getId(), internalCallContext);
    }

    public void cancelScheduledPaymentTransaction(final UUID lastPaymentAttemptId, final InternalCallContext internalCallContext) throws PaymentApiException {
        try {
            final NotificationQueue retryQueue = notificationQueueService.getNotificationQueue(DefaultPaymentService.SERVICE_NAME, DefaultRetryService.QUEUE_NAME);
            final Iterable<NotificationEventWithMetadata<NotificationEvent>> notificationEventWithMetadatas =
                    retryQueue.getFutureNotificationForSearchKeys(internalCallContext.getAccountRecordId(), internalCallContext.getTenantRecordId());

            final Iterator<NotificationEventWithMetadata<NotificationEvent>> iterator = notificationEventWithMetadatas.iterator();
            try {
                while (iterator.hasNext()) {
                    final NotificationEventWithMetadata<NotificationEvent> notificationEvent = iterator.next();
                    if (((PaymentRetryNotificationKey) notificationEvent.getEvent()).getAttemptId().equals(lastPaymentAttemptId)) {
                        retryQueue.removeNotification(notificationEvent.getRecordId());
                    }
                }
            } finally {
                // Go through all results to close the connection
                while (iterator.hasNext()) {
                    iterator.next();
                }
            }
        } catch (final NoSuchNotificationQueue noSuchNotificationQueue) {
            log.error("ERROR Loading Notification Queue - " + noSuchNotificationQueue.getMessage());
            throw new IllegalStateException(noSuchNotificationQueue);
        }
    }

    private Payment performOperation(final boolean isApiPayment,
                                     @Nullable final UUID attemptId,
                                     final TransactionType transactionType,
                                     final Account account,
                                     @Nullable final UUID paymentMethodId,
                                     @Nullable final UUID paymentId,
                                     @Nullable final UUID transactionId,
                                     @Nullable final BigDecimal amount,
                                     @Nullable final Currency currency,
                                     @Nullable final DateTime effectiveDate,
                                     @Nullable final String paymentExternalKey,
                                     @Nullable final String paymentTransactionExternalKey,
                                     @Nullable final UUID paymentIdForNewPayment,
                                     @Nullable final UUID paymentTransactionIdForNewPaymentTransaction,
                                     final boolean shouldLockAccountAndDispatch,
                                     @Nullable final OperationResult overridePluginOperationResult,
                                     final Iterable<PluginProperty> properties,
                                     final CallContext callContext,
                                     final InternalCallContext internalCallContext) throws PaymentApiException {
        boolean runJanitor = true;
        return performOperation(isApiPayment, runJanitor, attemptId, transactionType, account, paymentMethodId, paymentId,
                                transactionId, amount, currency, effectiveDate, paymentExternalKey, paymentTransactionExternalKey,
                                paymentIdForNewPayment, paymentTransactionIdForNewPaymentTransaction, shouldLockAccountAndDispatch, overridePluginOperationResult, properties, callContext, internalCallContext);
    }

    private Payment performOperation(final boolean isApiPayment,
                                     final boolean runJanitor,
                                     @Nullable final UUID attemptId,
                                     final TransactionType transactionType,
                                     final Account account,
                                     @Nullable final UUID paymentMethodId,
                                     @Nullable final UUID paymentId,
                                     @Nullable final UUID transactionId,
                                     @Nullable final BigDecimal amount,
                                     @Nullable final Currency currency,
                                     @Nullable final DateTime effectiveDate,
                                     @Nullable final String paymentExternalKey,
                                     @Nullable final String paymentTransactionExternalKey,
                                     @Nullable final UUID paymentIdForNewPayment,
                                     @Nullable final UUID paymentTransactionIdForNewPaymentTransaction,
                                     final boolean shouldLockAccountAndDispatch,
                                     @Nullable final OperationResult overridePluginOperationResult,
                                     final Iterable<PluginProperty> properties,
                                     final CallContext callContext,
                                     final InternalCallContext internalCallContext) throws PaymentApiException {
        final PaymentStateContext paymentStateContext = paymentAutomatonRunner.buildPaymentStateContext(isApiPayment,
                                                                                                        transactionType,
                                                                                                        account,
                                                                                                        attemptId,
                                                                                                        paymentMethodId != null ? paymentMethodId : account.getPaymentMethodId(),
                                                                                                        paymentId,
                                                                                                        transactionId,
                                                                                                        paymentExternalKey,
                                                                                                        paymentTransactionExternalKey,
                                                                                                        amount,
                                                                                                        currency,
                                                                                                        effectiveDate,
                                                                                                        paymentIdForNewPayment,
                                                                                                        paymentTransactionIdForNewPaymentTransaction,
                                                                                                        shouldLockAccountAndDispatch,
                                                                                                        overridePluginOperationResult,
                                                                                                        properties,
                                                                                                        callContext,
                                                                                                        internalCallContext);
        final PaymentAutomatonDAOHelper daoHelper = paymentAutomatonRunner.buildDaoHelper(paymentStateContext, internalCallContext);

        String currentStateName = null;
        if (paymentStateContext.getPaymentId() != null) {
            PaymentModelDao paymentModelDao = daoHelper.getPayment();

            // Sanity: verify the payment belongs to the right account (in case it was looked-up by payment or transaction external key)
            if (!paymentModelDao.getAccountRecordId().equals(internalCallContext.getAccountRecordId())) {
                throw new PaymentApiException(ErrorCode.PAYMENT_DIFFERENT_ACCOUNT_ID, paymentStateContext.getPaymentId());
            }

            // Note: the list needs to be modifiable for invokeJanitor
            final Collection<PaymentTransactionModelDao> paymentTransactionsForCurrentPayment = new LinkedList<PaymentTransactionModelDao>(daoHelper.getPaymentDao().getTransactionsForPayment(paymentStateContext.getPaymentId(), paymentStateContext.getInternalCallContext()));
            // Always invoke the Janitor first to get the latest state. The state machine will then
            // prevent disallowed transitions in case the state couldn't be fixed (or if it's already in a final state).
            if (runJanitor) {
                final PaymentPluginApi plugin = getPaymentProviderPlugin(paymentModelDao.getPaymentMethodId(), true, internalCallContext);
                final List<PaymentTransactionInfoPlugin> pluginTransactions = paymentRefresher.getPaymentTransactionInfoPlugins(plugin, paymentModelDao, properties, callContext);
                paymentModelDao = paymentRefresher.invokeJanitor(paymentModelDao, paymentTransactionsForCurrentPayment, pluginTransactions, internalCallContext);
            }

            if (paymentStateContext.getPaymentTransactionExternalKey() != null) {
                final List<PaymentTransactionModelDao> allPaymentTransactionsForKey = daoHelper.getPaymentDao().getPaymentTransactionsByExternalKey(paymentStateContext.getPaymentTransactionExternalKey(), internalCallContext);
                runSanityOnTransactionExternalKey(allPaymentTransactionsForKey, paymentStateContext, internalCallContext);
            }

            if (paymentStateContext.getTransactionId() != null || paymentStateContext.getPaymentTransactionExternalKey() != null) {
                // If a transaction id or key is passed, we are maybe completing an existing transaction (unless a new key was provided)
                PaymentTransactionModelDao transactionToComplete = findTransactionToCompleteAndRunSanityChecks(paymentModelDao, paymentTransactionsForCurrentPayment, paymentStateContext, internalCallContext);

                if (transactionToComplete != null) {
                    final UUID transactionToCompleteId = transactionToComplete.getId();
                    transactionToComplete = Iterables.<PaymentTransactionModelDao>find(paymentTransactionsForCurrentPayment,
                                                                                       new Predicate<PaymentTransactionModelDao>() {
                                                                                           @Override
                                                                                           public boolean apply(final PaymentTransactionModelDao input) {
                                                                                               return transactionToCompleteId.equals(input.getId());
                                                                                           }
                                                                                       });

                    // We can't tell where we should be in the state machine - bail (cannot be enforced by the state machine unfortunately because UNKNOWN and PLUGIN_FAILURE are both treated as EXCEPTION)
                    if (transactionToComplete.getTransactionStatus() == TransactionStatus.UNKNOWN) {
                        throw new PaymentApiException(ErrorCode.PAYMENT_INVALID_OPERATION, paymentStateContext.getTransactionType(), transactionToComplete.getTransactionStatus());
                    }

                    paymentStateContext.setPaymentTransactionModelDao(transactionToComplete);
                }
            }

            // Use the original payment method id of the payment being completed
            paymentStateContext.setPaymentMethodId(paymentModelDao.getPaymentMethodId());
            // We always take the last successful state name to permit retries on failures
            currentStateName = paymentModelDao.getLastSuccessStateName();
        }

        paymentAutomatonRunner.run(paymentStateContext, daoHelper, currentStateName, transactionType);

        return paymentRefresher.getPayment(paymentStateContext.getPaymentModelDao(), true, false, properties, callContext, internalCallContext);
    }

    private void runSanityOnTransactionExternalKey(final Iterable<PaymentTransactionModelDao> allPaymentTransactionsForKey,
                                                   final PaymentStateContext paymentStateContext,
                                                   final InternalCallContext internalCallContext) throws PaymentApiException {
        for (final PaymentTransactionModelDao paymentTransactionModelDao : allPaymentTransactionsForKey) {
            // Sanity: verify we don't already have a successful transaction for that key (chargeback reversals are a bit special, it's the only transaction type we can revert)
            if (paymentTransactionModelDao.getTransactionExternalKey().equals(paymentStateContext.getPaymentTransactionExternalKey()) &&
                paymentTransactionModelDao.getTransactionStatus() == TransactionStatus.SUCCESS &&
                paymentTransactionModelDao.getTransactionType() != TransactionType.CHARGEBACK) {
                throw new PaymentApiException(ErrorCode.PAYMENT_ACTIVE_TRANSACTION_KEY_EXISTS, paymentStateContext.getPaymentTransactionExternalKey());
            }

            // Sanity: don't share keys across accounts
            if (!paymentTransactionModelDao.getAccountRecordId().equals(internalCallContext.getAccountRecordId())) {
                UUID accountId;
                try {
                    accountId = accountInternalApi.getAccountByRecordId(paymentTransactionModelDao.getAccountRecordId(), internalCallContext).getId();
                } catch (final AccountApiException e) {
                    log.warn("Unable to retrieve account", e);
                    accountId = null;
                }
                throw new PaymentApiException(ErrorCode.PAYMENT_TRANSACTION_DIFFERENT_ACCOUNT_ID, accountId);
            }
        }
    }

    private PaymentTransactionModelDao findTransactionToCompleteAndRunSanityChecks(final PaymentModelDao paymentModelDao,
                                                                                   final Iterable<PaymentTransactionModelDao> paymentTransactionsForCurrentPayment,
                                                                                   final PaymentStateContext paymentStateContext,
                                                                                   final InternalCallContext internalCallContext) throws PaymentApiException {
        final Collection<PaymentTransactionModelDao> completionCandidates = new LinkedList<PaymentTransactionModelDao>();
        for (final PaymentTransactionModelDao paymentTransactionModelDao : paymentTransactionsForCurrentPayment) {
            // Check if we already have a transaction for that id or key
            if (!(paymentStateContext.getTransactionId() != null && paymentTransactionModelDao.getId().equals(paymentStateContext.getTransactionId())) &&
                !(paymentStateContext.getPaymentTransactionExternalKey() != null && paymentTransactionModelDao.getTransactionExternalKey().equals(paymentStateContext.getPaymentTransactionExternalKey()))) {
                // Sanity: if not, prevent multiple PENDING transactions for initial calls (cannot be enforced by the state machine unfortunately)
                if ((paymentTransactionModelDao.getTransactionType() == TransactionType.AUTHORIZE ||
                     paymentTransactionModelDao.getTransactionType() == TransactionType.PURCHASE ||
                     paymentTransactionModelDao.getTransactionType() == TransactionType.CREDIT) &&
                    paymentTransactionModelDao.getTransactionStatus() == TransactionStatus.PENDING) {
                    throw new PaymentApiException(ErrorCode.PAYMENT_INVALID_OPERATION, paymentTransactionModelDao.getTransactionType(), paymentModelDao.getStateName());
                } else {
                    continue;
                }
            }

            // Sanity: if we already have a transaction for that id or key, the transaction type must match
            if (paymentTransactionModelDao.getTransactionType() != paymentStateContext.getTransactionType()) {
                throw new PaymentApiException(ErrorCode.PAYMENT_INVALID_PARAMETER, "transactionType", String.format("%s doesn't match existing transaction type %s", paymentStateContext.getTransactionType(), paymentTransactionModelDao.getTransactionType()));
            }

            // UNKNOWN transactions are potential candidates, we'll invoke the Janitor first though
            if (paymentTransactionModelDao.getTransactionStatus() == TransactionStatus.PENDING || paymentTransactionModelDao.getTransactionStatus() == TransactionStatus.UNKNOWN) {
                completionCandidates.add(paymentTransactionModelDao);
            }
        }

        Preconditions.checkState(Iterables.size(completionCandidates) <= 1, "There should be at most one completion candidate");
        return Iterables.<PaymentTransactionModelDao>getLast(completionCandidates, null);
    }
}
