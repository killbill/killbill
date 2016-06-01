/*
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
 *
 * Groupon licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.payment.core.sm.payments;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.killbill.automaton.OperationException;
import org.killbill.automaton.State;
import org.killbill.automaton.State.LeavingStateCallback;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.core.sm.PaymentAutomatonDAOHelper;
import org.killbill.billing.payment.core.sm.PaymentStateContext;
import org.killbill.billing.payment.dao.PaymentTransactionModelDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public abstract class PaymentLeavingStateCallback implements LeavingStateCallback {

    private final Logger logger = LoggerFactory.getLogger(PaymentLeavingStateCallback.class);

    protected final PaymentAutomatonDAOHelper daoHelper;
    protected final PaymentStateContext paymentStateContext;

    protected PaymentLeavingStateCallback(final PaymentAutomatonDAOHelper daoHelper, final PaymentStateContext paymentStateContext) throws PaymentApiException {
        this.daoHelper = daoHelper;
        this.paymentStateContext = paymentStateContext;
    }

    @Override
    public void leavingState(final State oldState) throws OperationException {
        logger.debug("Leaving state {}", oldState.getName());

        // Create or update the payment and transaction
        try {
            // No paymentMethodId was passed through API and account does not have a default paymentMethodId
            if (paymentStateContext.getPaymentMethodId() == null) {
                throw new PaymentApiException(ErrorCode.PAYMENT_NO_DEFAULT_PAYMENT_METHOD, paymentStateContext.getAccount().getId());
            }

            // If we were given a paymentId (or existing paymentExternalId -> effectivePaymentId) we first fetch existing transactions (required for sanity and handling PENDING transactions)
            final List<PaymentTransactionModelDao> paymentTransactionsForCurrentPayment = paymentStateContext.getPaymentId() != null ?
                                                                                          daoHelper.getPaymentDao().getTransactionsForPayment(paymentStateContext.getPaymentId(), paymentStateContext.getInternalCallContext()) :
                                                                                          ImmutableList.<PaymentTransactionModelDao>of();

            //
            // Extract existing transaction matching the transactionId if specified (for e.g notifyPendingTransactionOfStateChanged), or based on transactionExternalKey
            //
            final Iterable<PaymentTransactionModelDao> existingPaymentTransactionsForTransactionIdOrKey = filterExistingPaymentTransactionsForTransactionIdOrKey(paymentTransactionsForCurrentPayment, paymentStateContext.getTransactionId(), paymentStateContext.getPaymentTransactionExternalKey());

            // Validate the payment transactions belong to the right payment
            validatePaymentIdAndTransactionType(existingPaymentTransactionsForTransactionIdOrKey);

            // Validate some constraints on the unicity of that paymentTransactionExternalKey
            validateUniqueTransactionExternalKey(existingPaymentTransactionsForTransactionIdOrKey);

            //
            // Handle PENDING case:
            // a) If we have a PENDING transaction for the same (payment transaction) key, this is a completion and we want to re-use the same transaction
            // b) If we have a PENDING transaction for a different (payment transaction) key, and for an initial request (AUTH, PURCHASE, CREDIT), we FAIL the request
            //   (unfortunately this cannot be caught by the state machine because the transition XXX_PENDING -> _SUCCESS needs to be allowed and this is irrespective of the keys)
            // c) If we have a PENDING transaction for a different (payment transaction) key, and for other follow-up request  (CAPTURE, REFUND, ..), we ignore it and create a new transaction
            //
            final Iterable<PaymentTransactionModelDao> pendingTransactionsForPaymentAndTransactionType = filterPendingTransactionsForPaymentAndTransactionType(paymentTransactionsForCurrentPayment, paymentStateContext.getTransactionType());

            // Case b)
            validateUniqueInitialPendingTransaction(pendingTransactionsForPaymentAndTransactionType, paymentStateContext.getTransactionType(), paymentStateContext.getPaymentTransactionExternalKey());


            final PaymentTransactionModelDao pendingPaymentTransaction = filterPendingTransactionsForTransactionKey(pendingTransactionsForPaymentAndTransactionType, paymentStateContext.getPaymentTransactionExternalKey());
            if (pendingPaymentTransaction != null) {
                // Case a) Set the current paymentTransaction in the context (needed for the state machine logic)
                paymentStateContext.setPaymentTransactionModelDao(pendingPaymentTransaction);
                return;
            }

            // At this point we are left with PAYMENT_FAILURE, PLUGIN_FAILURE or nothing, and we validated the uniqueness of the paymentTransactionExternalKey so we will create a new row
            daoHelper.createNewPaymentTransaction();

        } catch (PaymentApiException e) {
            throw new OperationException(e);
        }
    }

    private void validateUniqueInitialPendingTransaction(final Iterable<PaymentTransactionModelDao> pendingTransactionsForPaymentAndTransactionType, final TransactionType transactionType, final String paymentTransactionExternalKey) throws PaymentApiException {
        if (transactionType != TransactionType.AUTHORIZE &&
            transactionType != TransactionType.PURCHASE &&
            transactionType != TransactionType.CREDIT) {
            return;
        }

        final PaymentTransactionModelDao existingPendingTransactionForDifferentKey = Iterables.tryFind(pendingTransactionsForPaymentAndTransactionType, new Predicate<PaymentTransactionModelDao>() {
            @Override
            public boolean apply(final PaymentTransactionModelDao input) {
                return !input.getTransactionExternalKey().equals(paymentTransactionExternalKey);
            }
        }).orNull();
        if (existingPendingTransactionForDifferentKey !=  null) {
            throw new PaymentApiException(ErrorCode.PAYMENT_ACTIVE_TRANSACTION_KEY_EXISTS, paymentStateContext.getPaymentTransactionExternalKey());
        }
    }

    protected Iterable<PaymentTransactionModelDao> filterExistingPaymentTransactionsForTransactionIdOrKey(final List<PaymentTransactionModelDao> paymentTransactionsForCurrentPayment, @Nullable final UUID paymentTransactionId, @Nullable final String paymentTransactionExternalKey) throws PaymentApiException {
        return Iterables.filter(paymentTransactionsForCurrentPayment, new Predicate<PaymentTransactionModelDao>() {
            @Override
            public boolean apply(final PaymentTransactionModelDao input) {
                if (paymentTransactionId != null && input.getId().equals(paymentTransactionId)) {
                    return true;
                }
                if (paymentTransactionExternalKey != null && input.getTransactionExternalKey().equals(paymentTransactionExternalKey)) {
                    return true;
                }
                return false;
            }
        });
    }

    protected Iterable<PaymentTransactionModelDao> filterPendingTransactionsForPaymentAndTransactionType(final Iterable<PaymentTransactionModelDao> paymentTransactionsForCurrentPayment, final TransactionType transactionType) throws PaymentApiException {
        return Iterables.filter(paymentTransactionsForCurrentPayment, new Predicate<PaymentTransactionModelDao>() {
            @Override
            public boolean apply(final PaymentTransactionModelDao input) {
                return input.getTransactionStatus() == TransactionStatus.PENDING &&
                       input.getTransactionType() == transactionType;
            }
        });
    }

    protected PaymentTransactionModelDao filterPendingTransactionsForTransactionKey(final Iterable<PaymentTransactionModelDao> existingPendingPaymentTransactions, final String paymentTransactionExternalKey) throws PaymentApiException {
        return Iterables.tryFind(existingPendingPaymentTransactions, new Predicate<PaymentTransactionModelDao>() {
            @Override
            public boolean apply(final PaymentTransactionModelDao input) {
                return input.getTransactionExternalKey().equals(paymentTransactionExternalKey);
            }
        }).orNull();
    }

    protected void validateUniqueTransactionExternalKey(final Iterable<PaymentTransactionModelDao> existingPaymentTransactions) throws PaymentApiException {
        // If no key specified, system will allocate a unique one later, there is nothing to check
        if (paymentStateContext.getPaymentTransactionExternalKey() == null) {
            return;
        }

        if (Iterables.any(existingPaymentTransactions, new Predicate<PaymentTransactionModelDao>() {
            @Override
            public boolean apply(final PaymentTransactionModelDao input) {
                // An existing transaction in a SUCCESS state
                return input.getTransactionStatus() == TransactionStatus.SUCCESS ||
                       // Or, an existing transaction for a different payment (to do really well, we should also check on paymentExternalKey which is not available here)
                       (paymentStateContext.getPaymentId() != null && input.getPaymentId().compareTo(paymentStateContext.getPaymentId()) != 0) ||
                       // Or, an existing transaction for a different account.
                       (!input.getAccountRecordId().equals(paymentStateContext.getInternalCallContext().getAccountRecordId()));

            }
        })) {
            throw new PaymentApiException(ErrorCode.PAYMENT_ACTIVE_TRANSACTION_KEY_EXISTS, paymentStateContext.getPaymentTransactionExternalKey());
        }
    }

    // At this point, the payment id should have been populated for follow-up transactions (see PaymentAutomationRunner#run)
    protected void validatePaymentIdAndTransactionType(final Iterable<PaymentTransactionModelDao> existingPaymentTransactions) throws PaymentApiException {
        for (final PaymentTransactionModelDao paymentTransactionModelDao : existingPaymentTransactions) {
            if (!paymentTransactionModelDao.getPaymentId().equals(paymentStateContext.getPaymentId())) {
                throw new PaymentApiException(ErrorCode.PAYMENT_INVALID_PARAMETER, paymentTransactionModelDao.getId(), "does not belong to payment " + paymentStateContext.getPaymentId());
            }
            if (paymentStateContext.getTransactionType() != null && paymentTransactionModelDao.getTransactionType() != paymentStateContext.getTransactionType()) {
                throw new PaymentApiException(ErrorCode.PAYMENT_INVALID_PARAMETER, paymentTransactionModelDao.getId(), "has a transaction type of " + paymentTransactionModelDao.getTransactionType() +
                                                                                                                       " instead of requested " + paymentStateContext.getTransactionType());
            }
        }
    }
}
