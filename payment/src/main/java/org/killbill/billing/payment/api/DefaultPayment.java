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

package org.killbill.billing.payment.api;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.entity.EntityBase;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public class DefaultPayment extends EntityBase implements Payment {

    private final UUID accountId;
    private final UUID paymentMethodId;
    private final Integer paymentNumber;
    private final String externalKey;
    private final BigDecimal authAmount;
    private final BigDecimal captureAmount;
    private final BigDecimal purchasedAmount;
    private final BigDecimal creditAmount;
    private final BigDecimal refundAmount;
    private final Boolean isAuthVoided;

    private final Currency currency;
    private final List<PaymentTransaction> transactions;

    public DefaultPayment(final UUID id, @Nullable final DateTime createdDate, @Nullable final DateTime updatedDate, final UUID accountId,
                          final UUID paymentMethodId,
                          final Integer paymentNumber,
                          final String externalKey,
                          final List<PaymentTransaction> transactions) {
        super(id, createdDate, updatedDate);
        this.accountId = accountId;
        this.paymentMethodId = paymentMethodId;
        this.paymentNumber = paymentNumber;
        this.externalKey = externalKey;
        this.transactions = transactions;

        final Collection<PaymentTransaction> voidedTransactions = new LinkedList<PaymentTransaction>();
        final Collection<PaymentTransaction> nonVoidedTransactions = new LinkedList<PaymentTransaction>();
        int nvTxToVoid = 0;
        for (final PaymentTransaction paymentTransaction : Lists.<PaymentTransaction>reverse(transactions)) {
            if (TransactionStatus.SUCCESS.equals(paymentTransaction.getTransactionStatus())) {
                if (paymentTransaction.getTransactionType() == TransactionType.VOID) {
                    nvTxToVoid++;
                } else {
                    if (nvTxToVoid > 0) {
                        nvTxToVoid--;
                        voidedTransactions.add(paymentTransaction);
                    } else {
                        nonVoidedTransactions.add(paymentTransaction);
                    }
                }
            }
        }

        this.authAmount = getAmountForType(nonVoidedTransactions, TransactionType.AUTHORIZE);
        this.captureAmount = getAmountForType(nonVoidedTransactions, TransactionType.CAPTURE);
        this.purchasedAmount = getAmountForType(nonVoidedTransactions, TransactionType.PURCHASE);
        this.creditAmount = getAmountForType(nonVoidedTransactions, TransactionType.CREDIT);
        this.refundAmount = getAmountForType(nonVoidedTransactions, TransactionType.REFUND);

        this.isAuthVoided = Iterables.<PaymentTransaction>tryFind(voidedTransactions,
                                                                  new Predicate<PaymentTransaction>() {
                                                                      @Override
                                                                      public boolean apply(final PaymentTransaction input) {
                                                                          return input.getTransactionType() == TransactionType.AUTHORIZE && TransactionStatus.SUCCESS.equals(input.getTransactionStatus());
                                                                      }
                                                                  }).isPresent();

        this.currency = !transactions.isEmpty() ? transactions.get(0).getCurrency() : null;
    }

    private static BigDecimal getAmountForType(final Iterable<PaymentTransaction> transactions, final TransactionType transactiontype) {
        BigDecimal result = BigDecimal.ZERO;
        BigDecimal processedResult = BigDecimal.ZERO;
        boolean shouldUseProcessedAmount = true;

        for (final PaymentTransaction transaction : transactions) {
            if (transaction.getTransactionType() != transactiontype || !TransactionStatus.SUCCESS.equals(transaction.getTransactionStatus())) {
                continue;
            }

            result = result.add(transaction.getAmount());

            shouldUseProcessedAmount = shouldUseProcessedAmount && transaction.getCurrency().equals(transaction.getProcessedCurrency()) && transaction.getProcessedAmount() != null;
            processedResult = shouldUseProcessedAmount ? processedResult.add(transaction.getProcessedAmount()) : BigDecimal.ZERO;

            // For multi-step AUTH, don't sum the individual transactions
            if (TransactionType.AUTHORIZE.equals(transactiontype)) {
                break;
            }
        }

        return shouldUseProcessedAmount ? processedResult : result;
    }

    @Override
    public UUID getAccountId() {
        return accountId;
    }

    @Override
    public UUID getPaymentMethodId() {
        return paymentMethodId;
    }

    @Override
    public Integer getPaymentNumber() {
        return paymentNumber;
    }

    @Override
    public String getExternalKey() {
        return externalKey;
    }

    @Override
    public BigDecimal getAuthAmount() {
        return authAmount;
    }

    @Override
    public BigDecimal getCapturedAmount() {
        return captureAmount;
    }

    @Override
    public BigDecimal getPurchasedAmount() {
        return purchasedAmount;
    }

    @Override
    public BigDecimal getCreditedAmount() {
        return creditAmount;
    }

    @Override
    public BigDecimal getRefundedAmount() {
        return refundAmount;
    }

    @Override
    public Boolean isAuthVoided() {
        return isAuthVoided;
    }

    @Override
    public Currency getCurrency() {
        return currency;
    }

    @Override
    public List<PaymentTransaction> getTransactions() {
        return transactions;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("DefaultPayment{");
        sb.append("accountId=").append(accountId);
        sb.append(", paymentMethodId=").append(paymentMethodId);
        sb.append(", paymentNumber=").append(paymentNumber);
        sb.append(", externalKey='").append(externalKey).append('\'');
        sb.append(", authAmount=").append(authAmount);
        sb.append(", captureAmount=").append(captureAmount);
        sb.append(", purchasedAmount=").append(purchasedAmount);
        sb.append(", refundAmount=").append(refundAmount);
        sb.append(", currency=").append(currency);
        sb.append(", transactions=").append(transactions);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        final DefaultPayment that = (DefaultPayment) o;

        if (accountId != null ? !accountId.equals(that.accountId) : that.accountId != null) {
            return false;
        }
        if (authAmount != null ? authAmount.compareTo(that.authAmount) != 0 : that.authAmount != null) {
            return false;
        }
        if (captureAmount != null ? captureAmount.compareTo(that.captureAmount) != 0 : that.captureAmount != null) {
            return false;
        }
        if (purchasedAmount != null ? purchasedAmount.compareTo(that.purchasedAmount) != 0 : that.purchasedAmount != null) {
            return false;
        }
        if (currency != that.currency) {
            return false;
        }
        if (externalKey != null ? !externalKey.equals(that.externalKey) : that.externalKey != null) {
            return false;
        }
        if (paymentMethodId != null ? !paymentMethodId.equals(that.paymentMethodId) : that.paymentMethodId != null) {
            return false;
        }
        if (paymentNumber != null ? !paymentNumber.equals(that.paymentNumber) : that.paymentNumber != null) {
            return false;
        }
        if (refundAmount != null ? refundAmount.compareTo(that.refundAmount) != 0 : that.refundAmount != null) {
            return false;
        }
        if (transactions != null ? !transactions.equals(that.transactions) : that.transactions != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (accountId != null ? accountId.hashCode() : 0);
        result = 31 * result + (paymentMethodId != null ? paymentMethodId.hashCode() : 0);
        result = 31 * result + (paymentNumber != null ? paymentNumber.hashCode() : 0);
        result = 31 * result + (externalKey != null ? externalKey.hashCode() : 0);
        result = 31 * result + (authAmount != null ? authAmount.hashCode() : 0);
        result = 31 * result + (captureAmount != null ? captureAmount.hashCode() : 0);
        result = 31 * result + (purchasedAmount != null ? purchasedAmount.hashCode() : 0);
        result = 31 * result + (refundAmount != null ? refundAmount.hashCode() : 0);
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        result = 31 * result + (transactions != null ? transactions.hashCode() : 0);
        return result;
    }
}
