/*
 * Copyright 2014 Groupon, Inc
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

import org.joda.time.DateTime;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.payment.api.DefaultDirectPayment;
import org.killbill.billing.payment.api.DefaultDirectPaymentTransaction;
import org.killbill.billing.payment.api.DirectPayment;
import org.killbill.billing.payment.api.DirectPaymentTransaction;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.util.callcontext.CallContext;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

public class RetryableDirectPaymentStateContext extends DirectPaymentStateContext {

    private boolean isApiPayment;
    private DateTime retryDate;
    private String pluginName;
    private DirectPayment result;

    public RetryableDirectPaymentStateContext(@Nullable final String pluginName, final boolean isApiPayment, @Nullable final UUID directPaymentId, final String directPaymentExternalKey,
                                              @Nullable final String directPaymentTransactionExternalKey, final TransactionType transactionType,
                                              final Account account, @Nullable final UUID paymentMethodId, final BigDecimal amount, final Currency currency,
                                              final Iterable<PluginProperty> properties, final InternalCallContext internalCallContext, final CallContext callContext) {
        super(directPaymentId, null, directPaymentExternalKey, directPaymentTransactionExternalKey, transactionType, account, paymentMethodId, amount, currency, true, properties, internalCallContext, callContext);
        this.pluginName = pluginName;
        this.isApiPayment = isApiPayment;
    }

    public DateTime getRetryDate() {
        return retryDate;
    }

    public void setRetryDate(final DateTime retryDate) {
        this.retryDate = retryDate;
    }

    public String getPluginName() {
        return pluginName;
    }

    public void setPluginName(final String pluginName) {
        this.pluginName = pluginName;
    }

    public DirectPayment getResult() {
        return result;
    }

    public void setResult(final DirectPayment result) {
        this.result = result;
    }

    public boolean isApiPayment() {
        return isApiPayment;
    }

    public void setAmount(final BigDecimal adjustedAmount) {
        this.amount = adjustedAmount;
    }

    public DirectPaymentTransaction getCurrentTransaction() {
        if (result == null || result.getTransactions() == null) {
            return null;
        }
        return Iterables.tryFind(result.getTransactions(), new Predicate<DirectPaymentTransaction>() {
            @Override
            public boolean apply(final DirectPaymentTransaction input) {
                return ((DefaultDirectPaymentTransaction) input).getAttemptId().equals(attemptId);
            }
        }).orNull();
    }
}
