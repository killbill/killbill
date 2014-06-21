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

package org.killbill.billing.payment.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.entity.EntityBase;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

public class DefaultDirectPayment extends EntityBase implements DirectPayment {

    private final UUID accountId;
    private final UUID paymentMethodId;
    private final Integer paymentNumber;
    private final String externalKey;
    private final BigDecimal authAmount;
    private final BigDecimal captureAmount;
    private final BigDecimal purchasedAmount;
    private final BigDecimal creditAmount;
    private final BigDecimal refundAmount;
    private final boolean isVoided;

    private final Currency currency;
    private final List<DirectPaymentTransaction> transactions;

    public DefaultDirectPayment(final UUID id, @Nullable final DateTime createdDate, @Nullable final DateTime updatedDate, final UUID accountId,
                                final UUID paymentMethodId,
                                final Integer paymentNumber,
                                final String externalKey,
                                final List<DirectPaymentTransaction> transactions) {
        super(id, createdDate, updatedDate);
        this.accountId = accountId;
        this.paymentMethodId = paymentMethodId;
        this.paymentNumber = paymentNumber;
        this.externalKey = externalKey;
        this.transactions = transactions;
        this.authAmount = getAmountForType(transactions, TransactionType.AUTHORIZE);
        this.captureAmount = getAmountForType(transactions, TransactionType.CAPTURE);
        this.purchasedAmount = getAmountForType(transactions, TransactionType.PURCHASE);
        this.creditAmount = getAmountForType(transactions, TransactionType.CREDIT);
        this.refundAmount = getAmountForType(transactions, TransactionType.REFUND);
        this.isVoided = Iterables.filter(transactions, new Predicate<DirectPaymentTransaction>() {
            @Override
            public boolean apply(final DirectPaymentTransaction input) {
                return input.getTransactionType() == TransactionType.VOID && TransactionStatus.SUCCESS.equals(input.getTransactionStatus());
            }
        }).iterator().hasNext();
        this.currency = (transactions != null && !transactions.isEmpty()) ? transactions.get(0).getCurrency() : null;
    }

    private static BigDecimal getAmountForType(final Iterable<DirectPaymentTransaction> transactions, final TransactionType transactiontype) {
        BigDecimal result = BigDecimal.ZERO;
        final Iterable<DirectPaymentTransaction> filtered = Iterables.filter(transactions, new Predicate<DirectPaymentTransaction>() {
            @Override
            public boolean apply(final DirectPaymentTransaction input) {
                return input.getTransactionType() == transactiontype && TransactionStatus.SUCCESS.equals(input.getTransactionStatus());
            }
        });
        if (TransactionType.AUTHORIZE.equals(transactiontype) && filtered.iterator().hasNext()) {
            // HACK - For multi-step AUTH, don't sum the individual transactions
            result = filtered.iterator().next().getAmount();
        } else {
            for (final DirectPaymentTransaction dpt : filtered) {
                result = result.add(dpt.getAmount());
            }
        }
        return result;
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
    public boolean isAuthVoided() {
        return false;
    }

    @Override
    public Currency getCurrency() {
        return currency;
    }

    @Override
    public List<DirectPaymentTransaction> getTransactions() {
        return transactions;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("DefaultDirectPayment{");
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

        final DefaultDirectPayment that = (DefaultDirectPayment) o;

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
