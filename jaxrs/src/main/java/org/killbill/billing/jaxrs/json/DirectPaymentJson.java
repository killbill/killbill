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
    private final String directPaymentId;
    private final String paymentNumber;
    private final String directPaymentExternalKey;
    private final BigDecimal authAmount;
    private final BigDecimal capturedAmount;
    private final BigDecimal refundedAmount;
    private final String currency;
    private final String paymentMethodId;
    private final List<DirectTransactionJson> transactions;

    @JsonCreator
    public DirectPaymentJson(@JsonProperty("accountId") final String accountId,
                             @JsonProperty("directPaymentId") final String directPaymentId,
                             @JsonProperty("paymentNumber") final String paymentNumber,
                             @JsonProperty("directPaymentExternalKey") final String directPaymentExternalKey,
                             @JsonProperty("authAmount") final BigDecimal authAmount,
                             @JsonProperty("capturedAmount") final BigDecimal capturedAmount,
                             @JsonProperty("refundedAmount") final BigDecimal refundedAmount,
                             @JsonProperty("currency") final String currency,
                             @JsonProperty("paymentMethodId") final String paymentMethodId,
                             @JsonProperty("transactions") final List<DirectTransactionJson> transactions,
                             @JsonProperty("auditLogs") @Nullable final List<AuditLogJson> auditLogs) {
        super(auditLogs);
        this.accountId = accountId;
        this.directPaymentId = directPaymentId;
        this.paymentNumber = paymentNumber;
        this.directPaymentExternalKey = directPaymentExternalKey;
        this.authAmount = authAmount;
        this.capturedAmount = capturedAmount;
        this.refundedAmount = refundedAmount;
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
             dp.getRefundedAmount(),
             dp.getCurrency() != null ? dp.getCurrency().toString() : null,
             dp.getPaymentMethodId() != null ? dp.getPaymentMethodId().toString() : null,
             getTransactions(dp.getTransactions(), dp.getExternalKey(), accountAuditLogs),
             toAuditLogJson(accountAuditLogs == null ? null : accountAuditLogs.getAuditLogsForDirectPayment(dp.getId())));
    }

    private static List<DirectTransactionJson> getTransactions(final Iterable<DirectPaymentTransaction> transactions, final String directPaymentExternalKey, @Nullable final AccountAuditLogs accountAuditLogs) {
        return ImmutableList.copyOf(Iterables.transform(transactions,
                                                        new Function<DirectPaymentTransaction, DirectTransactionJson>() {
                                                            @Override
                                                            public DirectTransactionJson apply(final DirectPaymentTransaction directPaymentTransaction) {
                                                                final List<AuditLog> auditLogsForDirectPaymentTransaction = accountAuditLogs == null ? null : accountAuditLogs.getAuditLogsForDirectPaymentTransaction(directPaymentTransaction.getId());
                                                                return new DirectTransactionJson(directPaymentTransaction, directPaymentExternalKey, auditLogsForDirectPaymentTransaction);
                                                            }
                                                        }
                                                       ));
    }

    public String getAccountId() {
        return accountId;
    }

    public String getDirectPaymentId() {
        return directPaymentId;
    }

    public String getPaymentNumber() {
        return paymentNumber;
    }

    public String getDirectPaymentExternalKey() {
        return directPaymentExternalKey;
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

    public String getCurrency() {
        return currency;
    }

    public String getPaymentMethodId() {
        return paymentMethodId;
    }

    public List<DirectTransactionJson> getTransactions() {
        return transactions;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("DirectPaymentJson{");
        sb.append("accountId='").append(accountId).append('\'');
        sb.append(", directPaymentId='").append(directPaymentId).append('\'');
        sb.append(", paymentNumber='").append(paymentNumber).append('\'');
        sb.append(", directPaymentExternalKey='").append(directPaymentExternalKey).append('\'');
        sb.append(", authAmount=").append(authAmount);
        sb.append(", capturedAmount=").append(capturedAmount);
        sb.append(", refundedAmount=").append(refundedAmount);
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
        if (currency != null ? !currency.equals(that.currency) : that.currency != null) {
            return false;
        }
        if (directPaymentExternalKey != null ? !directPaymentExternalKey.equals(that.directPaymentExternalKey) : that.directPaymentExternalKey != null) {
            return false;
        }
        if (directPaymentId != null ? !directPaymentId.equals(that.directPaymentId) : that.directPaymentId != null) {
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
        result = 31 * result + (directPaymentId != null ? directPaymentId.hashCode() : 0);
        result = 31 * result + (paymentNumber != null ? paymentNumber.hashCode() : 0);
        result = 31 * result + (directPaymentExternalKey != null ? directPaymentExternalKey.hashCode() : 0);
        result = 31 * result + (authAmount != null ? authAmount.hashCode() : 0);
        result = 31 * result + (capturedAmount != null ? capturedAmount.hashCode() : 0);
        result = 31 * result + (refundedAmount != null ? refundedAmount.hashCode() : 0);
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        result = 31 * result + (paymentMethodId != null ? paymentMethodId.hashCode() : 0);
        result = 31 * result + (transactions != null ? transactions.hashCode() : 0);
        return result;
    }
}
