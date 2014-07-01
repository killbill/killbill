/*
 * Copyright 2010-2013 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
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
import org.killbill.billing.events.BusEventBase;
import org.killbill.billing.events.PaymentInfoInternalEvent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class DefaultPaymentInfoEvent extends BusEventBase implements PaymentInfoInternalEvent {

    private final UUID accountId;
    private final UUID invoiceId;
    private final UUID paymentId;
    private final BigDecimal amount;
    private final Integer paymentNumber;
    private final TransactionStatus status;
    private final TransactionType transactionType;
    private final DateTime effectiveDate;

    @JsonCreator
    public DefaultPaymentInfoEvent(@JsonProperty("accountId") final UUID accountId,
                                   @JsonProperty("invoiceId") final UUID invoiceId,
                                   @JsonProperty("paymentId") final UUID paymentId,
                                   @JsonProperty("amount") final BigDecimal amount,
                                   @JsonProperty("paymentNumber") final Integer paymentNumber,
                                   @JsonProperty("status") final TransactionStatus status,
                                   @JsonProperty("transactionType")  final TransactionType transactionType,
                                   @JsonProperty("extFirstPaymentRefId") final String extFirstPaymentRefId /* TODO for backward compatibility only */,
                                   @JsonProperty("extSecondPaymentRefId") final String extSecondPaymentRefId /* TODO for backward compatibility only */,
                                   @JsonProperty("effectiveDate") final DateTime effectiveDate,
                                   @JsonProperty("searchKey1") final Long searchKey1,
                                   @JsonProperty("searchKey2") final Long searchKey2,
                                   @JsonProperty("userToken") final UUID userToken) {
        super(searchKey1, searchKey2, userToken);
        this.accountId = accountId;
        this.invoiceId = invoiceId;
        this.paymentId = paymentId;
        this.amount = amount;
        this.paymentNumber = paymentNumber;
        this.status = status;
        this.transactionType = transactionType;
        this.effectiveDate = effectiveDate;
    }

    public DefaultPaymentInfoEvent(final UUID accountId, final UUID invoiceId,
                                   final UUID paymentId, final BigDecimal amount, final Integer paymentNumber,
                                   final TransactionStatus status,
                                   final TransactionType transactionType,
                                   final DateTime effectiveDate,
                                   final Long searchKey1,
                                   final Long searchKey2,
                                   final UUID userToken) {
        this(accountId, invoiceId, paymentId, amount, paymentNumber, status, transactionType, null, null,
             effectiveDate, searchKey1, searchKey2, userToken);
    }

    @JsonIgnore
    @Override
    public BusInternalEventType getBusEventType() {
        return BusInternalEventType.PAYMENT_INFO;
    }

    @Override
    public UUID getAccountId() {
        return accountId;
    }

    @Override
    public UUID getInvoiceId() {
        return invoiceId;
    }

    @Override
    public BigDecimal getAmount() {
        return amount;
    }

    @Override
    public DateTime getEffectiveDate() {
        return effectiveDate;
    }

    @Override
    public UUID getPaymentId() {
        return paymentId;
    }

    public Integer getPaymentNumber() {
        return paymentNumber;
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
        sb.append("DefaultPaymentInfoEvent");
        sb.append("{accountId=").append(accountId);
        sb.append(", invoiceId=").append(invoiceId);
        sb.append(", paymentId=").append(paymentId);
        sb.append(", amount=").append(amount);
        sb.append(", paymentNumber=").append(paymentNumber);
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
                 + ((invoiceId == null) ? 0 : invoiceId.hashCode());
        result = prime * result
                 + ((paymentId == null) ? 0 : paymentId.hashCode());
        result = prime * result
                 + ((paymentNumber == null) ? 0 : paymentNumber.hashCode());
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
        final DefaultPaymentInfoEvent other = (DefaultPaymentInfoEvent) obj;
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
        if (invoiceId == null) {
            if (other.invoiceId != null) {
                return false;
            }
        } else if (!invoiceId.equals(other.invoiceId)) {
            return false;
        }
        if (paymentId == null) {
            if (other.paymentId != null) {
                return false;
            }
        } else if (!paymentId.equals(other.paymentId)) {
            return false;
        }
        if (paymentNumber == null) {
            if (other.paymentNumber != null) {
                return false;
            }
        } else if (!paymentNumber.equals(other.paymentNumber)) {
            return false;
        }
        if (status != other.status) {
            return false;
        }
        return true;
    }
}
