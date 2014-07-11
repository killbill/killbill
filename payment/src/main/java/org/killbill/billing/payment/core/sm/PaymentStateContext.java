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

package org.killbill.billing.payment.core.sm;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.killbill.billing.account.api.Account;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.dao.PaymentTransactionModelDao;
import org.killbill.billing.payment.plugin.api.PaymentTransactionInfoPlugin;
import org.killbill.billing.util.callcontext.CallContext;

import com.google.common.collect.ImmutableList;

public class PaymentStateContext {

    // HACK
    protected UUID paymentMethodId;
    protected UUID attemptId;

    // Stateful objects created by the callbacks and passed to the other following callbacks in the automaton
    protected List<PaymentTransactionModelDao> onLeavingStateExistingTransactions;
    protected PaymentTransactionModelDao paymentTransactionModelDao;
    protected PaymentTransactionInfoPlugin paymentInfoPlugin;
    protected BigDecimal amount;
    protected UUID transactionPaymentId;
    protected String paymentExternalKey;

    // Can be updated later via paymentTransactionModelDao (e.g. for auth or purchase)
    protected final UUID paymentId;
    protected final String paymentTransactionExternalKey;
    protected final Account account;
    protected final Currency currency;
    protected final TransactionType transactionType;
    protected final boolean shouldLockAccountAndDispatch;
    protected final Iterable<PluginProperty> properties;
    protected final InternalCallContext internalCallContext;
    protected final CallContext callContext;
    protected final boolean isApiPayment;

    // Use to create new transactions only
    public PaymentStateContext(final boolean isApiPayment, @Nullable final UUID paymentId, @Nullable final String paymentTransactionExternalKey, final TransactionType transactionType,
                               final Account account, @Nullable final UUID paymentMethodId, final BigDecimal amount, final Currency currency,
                               final boolean shouldLockAccountAndDispatch, final Iterable<PluginProperty> properties,
                               final InternalCallContext internalCallContext, final CallContext callContext) {
        this(isApiPayment, paymentId, null, null, paymentTransactionExternalKey, transactionType, account, paymentMethodId,
             amount, currency, shouldLockAccountAndDispatch, properties, internalCallContext, callContext);
    }

    // Used to create new payment and transactions
    public PaymentStateContext(final boolean isApiPayment, @Nullable final UUID paymentId, @Nullable final UUID attemptId, @Nullable final String paymentExternalKey,
                               @Nullable final String paymentTransactionExternalKey, final TransactionType transactionType,
                               final Account account, @Nullable final UUID paymentMethodId, final BigDecimal amount, final Currency currency,
                               final boolean shouldLockAccountAndDispatch, final Iterable<PluginProperty> properties,
                               final InternalCallContext internalCallContext, final CallContext callContext) {
        this.isApiPayment = isApiPayment;
        this.paymentId = paymentId;
        this.attemptId= attemptId;
        this.paymentExternalKey = paymentExternalKey;
        this.paymentTransactionExternalKey = paymentTransactionExternalKey;
        this.transactionType = transactionType;
        this.account = account;
        this.paymentMethodId = paymentMethodId;
        this.amount = amount;
        this.currency = currency;
        this.shouldLockAccountAndDispatch = shouldLockAccountAndDispatch;
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

    public PaymentTransactionModelDao getPaymentTransactionModelDao() {
        return paymentTransactionModelDao;
    }

    public void setPaymentTransactionModelDao(final PaymentTransactionModelDao paymentTransactionModelDao) {
        this.paymentTransactionModelDao = paymentTransactionModelDao;
    }

    public List<PaymentTransactionModelDao> getOnLeavingStateExistingTransactions() {
        return onLeavingStateExistingTransactions;
    }

    public void setOnLeavingStateExistingTransactions(final List<PaymentTransactionModelDao> onLeavingStateExistingTransactions) {
        this.onLeavingStateExistingTransactions = onLeavingStateExistingTransactions;
    }

    public PaymentTransactionInfoPlugin getPaymentInfoPlugin() {
        return paymentInfoPlugin;
    }

    public void setPaymentInfoPlugin(final PaymentTransactionInfoPlugin paymentInfoPlugin) {
        this.paymentInfoPlugin = paymentInfoPlugin;
    }

    public UUID getPaymentId() {
        return paymentId != null ? paymentId : (paymentTransactionModelDao != null ? paymentTransactionModelDao.getPaymentId() : null);
    }

    public UUID getTransactionPaymentId() {
        return transactionPaymentId != null ? transactionPaymentId : (paymentTransactionModelDao != null ? paymentTransactionModelDao.getId() : null);
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

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public boolean shouldLockAccountAndDispatch() {
        return shouldLockAccountAndDispatch;
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
}
