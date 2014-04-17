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
    private final BigDecimal refundAmount;
    private final Currency currency;
    private final PaymentStatus paymentStatus;
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
        this.refundAmount = getAmountForType(transactions, TransactionType.CREDIT);
        this.currency = (transactions != null && transactions.size() > 0) ? transactions.get(0).getCurrency() : null;
        this.paymentStatus = (transactions != null && transactions.size() > 0) ? transactions.get(transactions.size() - 1).getPaymentStatus() : null;
    }

    private static BigDecimal getAmountForType(final List<DirectPaymentTransaction> transactions, final TransactionType transactiontype) {
        BigDecimal result = BigDecimal.ZERO;
        final Iterable<DirectPaymentTransaction> filtered = Iterables.filter(transactions, new Predicate<DirectPaymentTransaction>() {
            @Override
            public boolean apply(final DirectPaymentTransaction input) {
                return input.getTransactionType() == transactiontype;
            }
        });
        for (DirectPaymentTransaction dpt : filtered) {
            result = result.add(dpt.getAmount());
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
    public BigDecimal getRefundedAmount() {
        return refundAmount;
    }

    @Override
    public Currency getCurrency() {
        return currency;
    }

    @Override
    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    @Override
    public List<DirectPaymentTransaction> getTransactions() {
        return transactions;
    }

}
