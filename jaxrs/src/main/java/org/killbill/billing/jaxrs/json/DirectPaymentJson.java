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

package org.killbill.billing.jaxrs.json;

import java.math.BigDecimal;
import java.util.List;

import javax.annotation.Nullable;

import org.killbill.billing.payment.api.DirectPayment;
import org.killbill.billing.payment.api.DirectPaymentTransaction;
import org.killbill.billing.util.audit.AccountAuditLogs;
import org.killbill.billing.util.audit.AuditLog;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class DirectPaymentJson extends JsonBase {

    private final String accountId;
    private final String paymentId;
    private final String paymentNumber;
    private final String paymentExternalKey;
    private final BigDecimal authAmount;
    private final BigDecimal capturedAmount;
    private final BigDecimal purchasedAmount;
    private final BigDecimal refundedAmount;
    private final BigDecimal creditedAmount;
    private final String currency;
    private final String paymentMethodId;
    private final List<? extends DirectTransactionJson> transactions;

    @JsonCreator
    public DirectPaymentJson(@JsonProperty("accountId") final String accountId,
                             @JsonProperty("paymentId") final String paymentId,
                             @JsonProperty("paymentNumber") final String paymentNumber,
                             @JsonProperty("paymentExternalKey") final String paymentExternalKey,
                             @JsonProperty("authAmount") final BigDecimal authAmount,
                             @JsonProperty("capturedAmount") final BigDecimal capturedAmount,
                             @JsonProperty("purchasedAmount") final BigDecimal purchasedAmount,
                             @JsonProperty("refundedAmount") final BigDecimal refundedAmount,
                             @JsonProperty("creditedAmount") final BigDecimal creditedAmount,
                             @JsonProperty("currency") final String currency,
                             @JsonProperty("paymentMethodId") final String paymentMethodId,
                             @JsonProperty("transactions") final List<? extends DirectTransactionJson> transactions,
                             @JsonProperty("auditLogs") @Nullable final List<AuditLogJson> auditLogs) {
        super(auditLogs);
        this.accountId = accountId;
        this.paymentId = paymentId;
        this.paymentNumber = paymentNumber;
        this.paymentExternalKey = paymentExternalKey;
        this.authAmount = authAmount;
        this.capturedAmount = capturedAmount;
        this.purchasedAmount = purchasedAmount;
        this.refundedAmount = refundedAmount;
        this.creditedAmount = creditedAmount;
        this.currency = currency;
        this.paymentMethodId = paymentMethodId;
        this.transactions = transactions;
    }

    public DirectPaymentJson(final DirectPayment dp, @Nullable final AccountAuditLogs accountAuditLogs) {
        this(dp.getAccountId().toString(),
             dp.getId().toString(),
             dp.getPaymentNumber().toString(),
             dp.getExternalKey(),
             dp.getAuthAmount(),
             dp.getCapturedAmount(),
             dp.getPurchasedAmount(),
             dp.getRefundedAmount(),
             dp.getCreditedAmount(),
             dp.getCurrency() != null ? dp.getCurrency().toString() : null,
             dp.getPaymentMethodId() != null ? dp.getPaymentMethodId().toString() : null,
             getTransactions(dp.getTransactions(), dp.getExternalKey(), accountAuditLogs),
             toAuditLogJson(accountAuditLogs == null ? null : accountAuditLogs.getAuditLogsForPayment(dp.getId())));
    }

    private static List<DirectTransactionJson> getTransactions(final Iterable<DirectPaymentTransaction> transactions, final String directPaymentExternalKey, @Nullable final AccountAuditLogs accountAuditLogs) {
        return ImmutableList.copyOf(Iterables.transform(transactions,
                                                        new Function<DirectPaymentTransaction, DirectTransactionJson>() {
                                                            @Override
                                                            public DirectTransactionJson apply(final DirectPaymentTransaction directPaymentTransaction) {
                                                                final List<AuditLog> auditLogsForDirectPaymentTransaction = accountAuditLogs == null ? null : accountAuditLogs.getAuditLogsForPaymentTransaction(directPaymentTransaction.getId());
                                                                return new DirectTransactionJson(directPaymentTransaction, directPaymentExternalKey, auditLogsForDirectPaymentTransaction);
                                                            }
                                                        }
                                                       ));
    }

    public String getAccountId() {
        return accountId;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public String getPaymentNumber() {
        return paymentNumber;
    }

    public String getPaymentExternalKey() {
        return paymentExternalKey;
    }

    public BigDecimal getAuthAmount() {
        return authAmount;
    }

    public BigDecimal getCapturedAmount() {
        return capturedAmount;
    }

    public BigDecimal getRefundedAmount() {
        return refundedAmount;
    }

    public BigDecimal getPurchasedAmount() {
        return purchasedAmount;
    }

    public BigDecimal getCreditedAmount() {
        return creditedAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getPaymentMethodId() {
        return paymentMethodId;
    }

    public List<? extends DirectTransactionJson> getTransactions() {
        return transactions;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("DirectPaymentJson{");
        sb.append("accountId='").append(accountId).append('\'');
        sb.append(", paymentId='").append(paymentId).append('\'');
        sb.append(", paymentNumber='").append(paymentNumber).append('\'');
        sb.append(", paymentExternalKey='").append(paymentExternalKey).append('\'');
        sb.append(", authAmount=").append(authAmount);
        sb.append(", capturedAmount=").append(capturedAmount);
        sb.append(", purchasedAmount=").append(purchasedAmount);
        sb.append(", refundedAmount=").append(refundedAmount);
        sb.append(", creditedAmount=").append(creditedAmount);
        sb.append(", currency='").append(currency).append('\'');
        sb.append(", paymentMethodId='").append(paymentMethodId).append('\'');
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

        final DirectPaymentJson that = (DirectPaymentJson) o;

        if (accountId != null ? !accountId.equals(that.accountId) : that.accountId != null) {
            return false;
        }
        if (authAmount != null ? authAmount.compareTo(that.authAmount) != 0 : that.authAmount != null) {
            return false;
        }
        if (capturedAmount != null ? capturedAmount.compareTo(that.capturedAmount) != 0 : that.capturedAmount != null) {
            return false;
        }
        if (creditedAmount != null ? creditedAmount.compareTo(that.creditedAmount) != 0 : that.creditedAmount != null) {
            return false;
        }
        if (purchasedAmount != null ? purchasedAmount.compareTo(that.purchasedAmount) != 0 : that.purchasedAmount != null) {
            return false;
        }
        if (currency != null ? !currency.equals(that.currency) : that.currency != null) {
            return false;
        }
        if (paymentExternalKey != null ? !paymentExternalKey.equals(that.paymentExternalKey) : that.paymentExternalKey != null) {
            return false;
        }
        if (paymentId != null ? !paymentId.equals(that.paymentId) : that.paymentId != null) {
            return false;
        }
        if (paymentMethodId != null ? !paymentMethodId.equals(that.paymentMethodId) : that.paymentMethodId != null) {
            return false;
        }
        if (paymentNumber != null ? !paymentNumber.equals(that.paymentNumber) : that.paymentNumber != null) {
            return false;
        }
        if (refundedAmount != null ? refundedAmount.compareTo(that.refundedAmount) != 0 : that.refundedAmount != null) {
            return false;
        }
        if (transactions != null ? !transactions.equals(that.transactions) : that.transactions != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = accountId != null ? accountId.hashCode() : 0;
        result = 31 * result + (paymentId != null ? paymentId.hashCode() : 0);
        result = 31 * result + (paymentNumber != null ? paymentNumber.hashCode() : 0);
        result = 31 * result + (paymentExternalKey != null ? paymentExternalKey.hashCode() : 0);
        result = 31 * result + (authAmount != null ? authAmount.hashCode() : 0);
        result = 31 * result + (capturedAmount != null ? capturedAmount.hashCode() : 0);
        result = 31 * result + (creditedAmount != null ? creditedAmount.hashCode() : 0);
        result = 31 * result + (purchasedAmount != null ? purchasedAmount.hashCode() : 0);
        result = 31 * result + (refundedAmount != null ? refundedAmount.hashCode() : 0);
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        result = 31 * result + (paymentMethodId != null ? paymentMethodId.hashCode() : 0);
        result = 31 * result + (transactions != null ? transactions.hashCode() : 0);
        return result;
    }
}
