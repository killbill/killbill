/*
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

package org.killbill.billing.payment.core.sm.control;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.payment.api.DefaultPaymentTransaction;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PaymentTransaction;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.core.sm.PaymentStateContext;
import org.killbill.billing.util.callcontext.CallContext;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

public class PaymentStateControlContext extends PaymentStateContext {

    private final Boolean isSuccess;

    private DateTime retryDate;
    private List<String> paymentControlPluginNames;
    private Payment result;
    private BigDecimal processedAmount;
    private Currency processedCurrency;

    public PaymentStateControlContext(@Nullable final List<String> paymentControlPluginNames,
                                      final boolean isApiPayment,
                                      final Boolean isSuccess,
                                      @Nullable final UUID paymentId,
                                      final String paymentExternalKey,
                                      @Nullable final UUID transactionId,
                                      @Nullable final String paymentTransactionExternalKey,
                                      final TransactionType transactionType,
                                      final Account account,
                                      @Nullable final UUID paymentMethodId,
                                      final BigDecimal amount,
                                      final Currency currency,
                                      final DateTime effectiveDate,
                                      final Iterable<PluginProperty> properties, final InternalCallContext internalCallContext, final CallContext callContext) {
        super(isApiPayment, paymentId, transactionId, null, paymentExternalKey, paymentTransactionExternalKey, transactionType, account, paymentMethodId, amount, currency, effectiveDate, null, null, true, null, properties, internalCallContext, callContext);
        this.paymentControlPluginNames = paymentControlPluginNames;
        this.isSuccess = isSuccess;
    }

    public void setPaymentIdForNewPayment(final UUID paymentIdForNewPayment) {
        this.paymentIdForNewPayment = paymentIdForNewPayment;
    }

    public void setPaymentTransactionIdForNewPaymentTransaction(final UUID paymentTransactionIdForNewPaymentTransaction) {
        this.paymentTransactionIdForNewPaymentTransaction = paymentTransactionIdForNewPaymentTransaction;
    }

    public DateTime getRetryDate() {
        return retryDate;
    }

    public void setRetryDate(final DateTime retryDate) {
        this.retryDate = retryDate;
    }

    public List<String> getPaymentControlPluginNames() {
        return paymentControlPluginNames;
    }

    public Payment getResult() {
        return result;
    }

    public void setResult(final Payment result) {
        this.result = result;
    }

    public Boolean isSuccess() {
        return isSuccess;
    }

    public BigDecimal getProcessedAmount() {
        return processedAmount;
    }

    public void setProcessedAmount(final BigDecimal processedAmount) {
        this.processedAmount = processedAmount;
    }

    public Currency getProcessedCurrency() {
        return processedCurrency;
    }

    public void setProcessedCurrency(final Currency processedCurrency) {
        this.processedCurrency = processedCurrency;
    }

    public PaymentTransaction getCurrentTransaction() {
        if (result == null || result.getTransactions() == null) {
            return null;
        }
        return Iterables.tryFind(result.getTransactions(), new Predicate<PaymentTransaction>() {
            @Override
            public boolean apply(final PaymentTransaction input) {
                final DefaultPaymentTransaction defaultPaymentTransaction = (DefaultPaymentTransaction) input;
                return defaultPaymentTransaction.getAttemptId() == null ? getAttemptId() == null : defaultPaymentTransaction.getAttemptId().equals(getAttemptId());
            }
        }).orNull();
    }
}
