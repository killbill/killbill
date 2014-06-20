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

public class DirectPaymentStateContext {

    // HACK
    protected UUID paymentMethodId;

    // Stateful objects created by the callbacks and passed to the other following callbacks in the automaton
    protected PaymentTransactionModelDao directPaymentTransactionModelDao;
    protected PaymentTransactionInfoPlugin paymentInfoPlugin;
    protected BigDecimal amount;
    protected UUID transactionPaymentId;
    protected String directPaymentExternalKey;

    // Can be updated later via directPaymentTransactionModelDao (e.g. for auth or purchase)
    protected final UUID directPaymentId;
    protected final String directPaymentTransactionExternalKey;
    protected final Account account;
    protected final Currency currency;
    protected final TransactionType transactionType;
    protected final boolean shouldLockAccountAndDispatch;
    protected final Iterable<PluginProperty> properties;
    protected final InternalCallContext internalCallContext;
    protected final CallContext callContext;

    // Use to create new transactions only
    public DirectPaymentStateContext(@Nullable final UUID directPaymentId, @Nullable final String directPaymentTransactionExternalKey, final TransactionType transactionType,
                                     final Account account, @Nullable final UUID paymentMethodId, final BigDecimal amount, final Currency currency,
                                     final boolean shouldLockAccountAndDispatch, final Iterable<PluginProperty> properties,
                                     final InternalCallContext internalCallContext, final CallContext callContext) {
        this(directPaymentId, null, directPaymentTransactionExternalKey, transactionType, account, paymentMethodId,
             amount, currency, shouldLockAccountAndDispatch, properties, internalCallContext, callContext);
    }

    // Used to create new payment and transactions
    public DirectPaymentStateContext(@Nullable final UUID directPaymentId, @Nullable final String directPaymentExternalKey,
                                     @Nullable final String directPaymentTransactionExternalKey, final TransactionType transactionType,
                                     final Account account, @Nullable final UUID paymentMethodId, final BigDecimal amount, final Currency currency,
                                     final boolean shouldLockAccountAndDispatch, final Iterable<PluginProperty> properties,
                                     final InternalCallContext internalCallContext, final CallContext callContext) {
        this.directPaymentId = directPaymentId;
        this.directPaymentExternalKey = directPaymentExternalKey;
        this.directPaymentTransactionExternalKey = directPaymentTransactionExternalKey;
        this.transactionType = transactionType;
        this.account = account;
        this.paymentMethodId = paymentMethodId;
        this.amount = amount;
        this.currency = currency;
        this.shouldLockAccountAndDispatch = shouldLockAccountAndDispatch;
        this.properties = properties;
        this.internalCallContext = internalCallContext;
        this.callContext = callContext;
    }

    public void setPaymentMethodId(final UUID paymentMethodId) {
        this.paymentMethodId = paymentMethodId;
    }

    public PaymentTransactionModelDao getDirectPaymentTransactionModelDao() {
        return directPaymentTransactionModelDao;
    }

    public void setDirectPaymentTransactionModelDao(final PaymentTransactionModelDao directPaymentTransactionModelDao) {
        this.directPaymentTransactionModelDao = directPaymentTransactionModelDao;
    }

    public PaymentTransactionInfoPlugin getPaymentInfoPlugin() {
        return paymentInfoPlugin;
    }

    public void setPaymentInfoPlugin(final PaymentTransactionInfoPlugin paymentInfoPlugin) {
        this.paymentInfoPlugin = paymentInfoPlugin;
    }

    public UUID getDirectPaymentId() {
        return directPaymentId != null ? directPaymentId : (directPaymentTransactionModelDao != null ? directPaymentTransactionModelDao.getPaymentId() : null);
    }

    public UUID getTransactionPaymentId() {
        return transactionPaymentId;
    }

    public void setTransactionPaymentId(final UUID transactionPaymentId) {
        this.transactionPaymentId = transactionPaymentId;
    }

    public String getDirectPaymentExternalKey() {
        return directPaymentExternalKey;
    }

    public void setDirectPaymentExternalKey(final String directPaymentExternalKey) {
        this.directPaymentExternalKey = directPaymentExternalKey;
    }

    public String getDirectPaymentTransactionExternalKey() {
        return directPaymentTransactionExternalKey;
    }

    public Account getAccount() {
        return account;
    }

    public UUID getPaymentMethodId() {
        return paymentMethodId;
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
