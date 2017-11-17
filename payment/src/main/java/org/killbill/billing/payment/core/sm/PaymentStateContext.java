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

package org.killbill.billing.payment.core.sm;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.killbill.automaton.OperationResult;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.dao.PaymentModelDao;
import org.killbill.billing.payment.dao.PaymentTransactionModelDao;
import org.killbill.billing.payment.plugin.api.PaymentTransactionInfoPlugin;
import org.killbill.billing.util.callcontext.CallContext;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

public class PaymentStateContext {

    private PaymentModelDao paymentModelDao;
    // The following fields (paymentId, transactionId, amount, currency) may take their value from the paymentTransactionModelDao *when they are not already set*
    private PaymentTransactionModelDao paymentTransactionModelDao;
    // Initialized in CTOR or only set through paymentTransactionModelDao
    private UUID paymentId;
    private UUID transactionId;

    // Can be overriden by control plugin
    private BigDecimal amount;
    private Currency currency;
    private UUID paymentMethodId;
    private Iterable<PluginProperty> properties;

    // Set in the doOperationCallback when coming back from payment plugin
    private PaymentTransactionInfoPlugin paymentTransactionInfoPlugin;

    // Set in the control layer in the leavingState callback
    private String paymentExternalKey;
    private String paymentTransactionExternalKey;
    protected UUID paymentIdForNewPayment;
    protected UUID paymentTransactionIdForNewPaymentTransaction;

    // Set in the control layer after creating the attempt in the enteringState callback
    private UUID attemptId;

    // This is purely a performance improvement to avoid fetching the existing transactions for that payment throughout the state machine
    private List<PaymentTransactionModelDao> onLeavingStateExistingTransactions;

    // Immutable
    private final Account account;
    private final TransactionType transactionType;
    private final boolean shouldLockAccountAndDispatch;
    private final OperationResult overridePluginOperationResult;
    private final InternalCallContext internalCallContext;
    private final CallContext callContext;
    private final boolean isApiPayment;
    private final DateTime effectiveDate;

    @VisibleForTesting
    public PaymentStateContext(final boolean isApiPayment, @Nullable final UUID paymentId, @Nullable final String paymentTransactionExternalKey, final TransactionType transactionType,
                               final Account account, @Nullable final UUID paymentMethodId, final BigDecimal amount, final Currency currency, final DateTime effectiveDate,
                               final boolean shouldLockAccountAndDispatch, final Iterable<PluginProperty> properties,
                               final InternalCallContext internalCallContext, final CallContext callContext) {
        this(isApiPayment, paymentId, null, null, null, paymentTransactionExternalKey, transactionType, account, paymentMethodId,
             amount, currency, effectiveDate, null, null, shouldLockAccountAndDispatch, null, properties, internalCallContext, callContext);
    }

    // Used to create new payment and transactions
    public PaymentStateContext(final boolean isApiPayment, @Nullable final UUID paymentId, final UUID transactionId, @Nullable final UUID attemptId, @Nullable final String paymentExternalKey,
                               @Nullable final String paymentTransactionExternalKey, final TransactionType transactionType,
                               final Account account, @Nullable final UUID paymentMethodId, final BigDecimal amount, final Currency currency, final DateTime effectiveDate,
                               @Nullable final UUID paymentIdForNewPayment, @Nullable final UUID paymentTransactionIdForNewPaymentTransaction, final boolean shouldLockAccountAndDispatch,
                               final OperationResult overridePluginOperationResult, final Iterable<PluginProperty> properties,
                               final InternalCallContext internalCallContext, final CallContext callContext) {
        this.isApiPayment = isApiPayment;
        this.paymentId = paymentId;
        this.transactionId = transactionId;
        this.attemptId = attemptId;
        this.paymentExternalKey = paymentExternalKey;
        this.paymentTransactionExternalKey = paymentTransactionExternalKey;
        this.transactionType = transactionType;
        this.account = account;
        this.paymentMethodId = paymentMethodId;
        this.amount = amount;
        this.currency = currency;
        this.effectiveDate = effectiveDate;
        this.paymentIdForNewPayment = paymentIdForNewPayment;
        this.paymentTransactionIdForNewPaymentTransaction = paymentTransactionIdForNewPaymentTransaction;
        this.shouldLockAccountAndDispatch = shouldLockAccountAndDispatch;
        this.overridePluginOperationResult = overridePluginOperationResult;
        this.properties = properties;
        this.internalCallContext = internalCallContext;
        this.callContext = callContext;
        this.onLeavingStateExistingTransactions = ImmutableList.of();
    }

    public boolean isApiPayment() {
        return isApiPayment;
    }

    public void setPaymentMethodId(final UUID paymentMethodId) {
        this.paymentMethodId = paymentMethodId;
    }

    public PaymentModelDao getPaymentModelDao() {
        return paymentModelDao;
    }

    public void setPaymentModelDao(final PaymentModelDao paymentModelDao) {
        this.paymentModelDao = paymentModelDao;
    }

    public PaymentTransactionModelDao getPaymentTransactionModelDao() {
        return paymentTransactionModelDao;
    }

    public void setPaymentTransactionModelDao(final PaymentTransactionModelDao paymentTransactionModelDao) {
        this.paymentTransactionModelDao = paymentTransactionModelDao;
        if (paymentId == null) {
            this.paymentId = paymentTransactionModelDao.getPaymentId();
        }
        if (transactionId == null) {
            this.transactionId = paymentTransactionModelDao.getId();
        }
        if (amount == null) {
            this.amount = paymentTransactionModelDao.getAmount();
        }
        if (currency == null) {
            this.currency = paymentTransactionModelDao.getCurrency();
        }
    }

    public List<PaymentTransactionModelDao> getOnLeavingStateExistingTransactions() {
        return onLeavingStateExistingTransactions;
    }

    public void setOnLeavingStateExistingTransactions(final List<PaymentTransactionModelDao> onLeavingStateExistingTransactions) {
        this.onLeavingStateExistingTransactions = onLeavingStateExistingTransactions;
    }

    public PaymentTransactionInfoPlugin getPaymentTransactionInfoPlugin() {
        return paymentTransactionInfoPlugin;
    }

    public void setPaymentTransactionInfoPlugin(final PaymentTransactionInfoPlugin paymentTransactionInfoPlugin) {
        this.paymentTransactionInfoPlugin = paymentTransactionInfoPlugin;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public String getPaymentExternalKey() {
        return paymentExternalKey;
    }

    public void setPaymentExternalKey(final String paymentExternalKey) {
        this.paymentExternalKey = paymentExternalKey;
    }

    public String getPaymentTransactionExternalKey() {
        return paymentTransactionExternalKey;
    }

    public void setPaymentTransactionExternalKey(final String paymentTransactionExternalKey) {
        this.paymentTransactionExternalKey = paymentTransactionExternalKey;
    }

    public Account getAccount() {
        return account;
    }

    public UUID getPaymentMethodId() {
        return paymentMethodId;
    }

    public UUID getAttemptId() {
        return attemptId;
    }

    public void setAttemptId(final UUID attemptId) {
        this.attemptId = attemptId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Currency getCurrency() {
        return currency;
    }

    public DateTime getEffectiveDate() {
        return effectiveDate;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public boolean shouldLockAccountAndDispatch() {
        return shouldLockAccountAndDispatch;
    }

    public OperationResult getOverridePluginOperationResult() {
        return overridePluginOperationResult;
    }

    public Iterable<PluginProperty> getProperties() {
        return properties;
    }

    public InternalCallContext getInternalCallContext() {
        return internalCallContext;
    }

    public CallContext getCallContext() {
        return callContext;
    }

    public void setAmount(final BigDecimal adjustedAmount) {
        this.amount = adjustedAmount;
    }

    public void setCurrency(final Currency currency) {
        this.currency = currency;
    }

    public void setProperties(final Iterable<PluginProperty> properties) {
        this.properties = properties;
    }

    public UUID getPaymentIdForNewPayment() {
        return paymentIdForNewPayment;
    }

    public UUID getPaymentTransactionIdForNewPaymentTransaction() {
        return paymentTransactionIdForNewPaymentTransaction;
    }
}
