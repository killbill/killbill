/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.events.BusEventBase;
import org.killbill.billing.events.PaymentInternalEvent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public abstract class DefaultPaymentInternalEvent extends BusEventBase implements PaymentInternalEvent {

    private final UUID accountId;
    private final UUID paymentId;
    private final UUID paymentTransactionId;
    private final BigDecimal amount;
    private final Currency currency;
    private final TransactionStatus status;
    private final TransactionType transactionType;
    private final DateTime effectiveDate;

    @JsonCreator
    public DefaultPaymentInternalEvent(@JsonProperty("accountId") final UUID accountId,
                                       @JsonProperty("paymentId") final UUID paymentId,
                                       @JsonProperty("paymentTransactionId") final UUID paymentTransactionId,
                                       @JsonProperty("amount") final BigDecimal amount,
                                       @JsonProperty("currency") final Currency currency,
                                       @JsonProperty("status") final TransactionStatus status,
                                       @JsonProperty("transactionType") final TransactionType transactionType,
                                       @JsonProperty("effectiveDate") final DateTime effectiveDate,
                                       @JsonProperty("searchKey1") final Long searchKey1,
                                       @JsonProperty("searchKey2") final Long searchKey2,
                                       @JsonProperty("userToken") final UUID userToken) {
        super(searchKey1, searchKey2, userToken);
        this.accountId = accountId;
        this.paymentId = paymentId;
        this.paymentTransactionId = paymentTransactionId;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.transactionType = transactionType;
        this.effectiveDate = effectiveDate;
    }

    @Override
    public UUID getAccountId() {
        return accountId;
    }

    @Override
    public BigDecimal getAmount() {
        return amount;
    }

    @Override
    public Currency getCurrency() {
        return currency;
    }

    @Override
    public DateTime getEffectiveDate() {
        return effectiveDate;
    }

    @Override
    public UUID getPaymentId() {
        return paymentId;
    }

    @Override
    public UUID getPaymentTransactionId() {
        return paymentTransactionId;
    }

    @Override
    public TransactionType getTransactionType() {
        return transactionType;
    }

    @Override
    public TransactionStatus getStatus() {
        return status;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(getPaymentInternalEventClass().toString());
        sb.append(" {accountId=").append(accountId);
        sb.append(", paymentId=").append(paymentId);
        sb.append(", paymentTransactionId=").append(paymentTransactionId);
        sb.append(", amount=").append(amount);
        sb.append(", currency=").append(currency);
        sb.append(", status=").append(status);
        sb.append(", transactionType=").append(transactionType);
        sb.append(", effectiveDate=").append(effectiveDate);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                 + ((accountId == null) ? 0 : accountId.hashCode());
        result = prime * result + ((amount == null) ? 0 : amount.hashCode());
        result = prime * result
                 + ((effectiveDate == null) ? 0 : effectiveDate.hashCode());
        result = prime * result
                 + ((paymentId == null) ? 0 : paymentId.hashCode());
        result = prime * result
                 + ((paymentTransactionId == null) ? 0 : paymentTransactionId.hashCode());
        result = prime * result
                 + ((currency == null) ? 0 : currency.hashCode());
        result = prime * result + ((status == null) ? 0 : status.hashCode());
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final DefaultPaymentInternalEvent other = (DefaultPaymentInternalEvent) obj;
        if (accountId == null) {
            if (other.accountId != null) {
                return false;
            }
        } else if (!accountId.equals(other.accountId)) {
            return false;
        }
        if (transactionType == null) {
            if (other.transactionType != null) {
                return false;
            }
        } else if (!transactionType.equals(other.transactionType)) {
            return false;
        }
        if (amount == null) {
            if (other.amount != null) {
                return false;
            }
        } else if (amount.compareTo(other.amount) != 0) {
            return false;
        }
        if (effectiveDate == null) {
            if (other.effectiveDate != null) {
                return false;
            }
        } else if (effectiveDate.compareTo(other.effectiveDate) != 0) {
            return false;
        }
        if (paymentId == null) {
            if (other.paymentId != null) {
                return false;
            }
        } else if (!paymentId.equals(other.paymentId)) {
            return false;
        }
        if (paymentTransactionId == null) {
            if (other.paymentTransactionId != null) {
                return false;
            }
        } else if (!paymentTransactionId.equals(other.paymentTransactionId)) {
            return false;
        }
        if (currency == null) {
            if (other.currency != null) {
                return false;
            }
        } else if (!currency.equals(other.currency)) {
            return false;
        }
        if (status != other.status) {
            return false;
        }
        return true;
    }

    protected abstract Class getPaymentInternalEventClass();
}
