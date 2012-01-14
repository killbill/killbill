/*
 * Copyright 2010-2011 Ning, Inc.
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

package com.ning.billing.payment.api;

import java.math.BigDecimal;

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;
import org.joda.time.DateTime;

import com.google.common.base.Objects;
import com.ning.billing.util.eventbus.EventBusNotification;

public class PaymentInfo implements EventBusNotification {
    private final String paymentId;
    private final BigDecimal amount;
    private final BigDecimal refundAmount;
    private final String paymentNumber;
    private final String bankIdentificationNumber;
    private final String status;
    private final String type;
    private final String referenceId;
    private final DateTime effectiveDate;
    private final DateTime createdDate;
    private final DateTime updatedDate;

    public PaymentInfo(PaymentInfo src) {
        this.paymentId = src.paymentId;
        this.amount = src.amount;
        this.refundAmount = src.refundAmount;
        this.paymentNumber = src.paymentNumber;
        this.bankIdentificationNumber = src.bankIdentificationNumber;
        this.status = src.status;
        this.type = src.type;
        this.referenceId = src.referenceId;
        this.effectiveDate = src.effectiveDate;
        this.createdDate = src.createdDate;
        this.updatedDate = src.updatedDate;
    }

    @JsonCreator
    public PaymentInfo(@JsonProperty("paymentId") String paymentId,
                       @JsonProperty("amount") BigDecimal amount,
                       @JsonProperty("refundAmount") BigDecimal refundAmount,
                       @JsonProperty("bankIdentificationNumber") String bankIdentificationNumber,
                       @JsonProperty("paymentNumber") String paymentNumber,
                       @JsonProperty("status") String status,
                       @JsonProperty("type") String type,
                       @JsonProperty("referenceId") String referenceId,
                       @JsonProperty("effectiveDate") DateTime effectiveDate,
                       @JsonProperty("createdDate") DateTime createdDate,
                       @JsonProperty("updatedDate") DateTime updatedDate) {
        this.paymentId = paymentId;
        this.amount = amount;
        this.refundAmount = refundAmount;
        this.bankIdentificationNumber = bankIdentificationNumber;
        this.effectiveDate = effectiveDate;
        this.paymentNumber = paymentNumber;
        this.referenceId = referenceId;
        this.status = status;
        this.type = type;
        this.createdDate = createdDate;
        this.updatedDate = updatedDate;
    }

    public Builder cloner() {
        return new Builder(this);
    }

    public String getPaymentId() {
        return paymentId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getBankIdentificationNumber() {
        return bankIdentificationNumber;
    }

    public DateTime getCreatedDate() {
        return createdDate;
    }

    public DateTime getEffectiveDate() {
        return effectiveDate;
    }

    public String getPaymentNumber() {
        return paymentNumber;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public BigDecimal getRefundAmount() {
        return refundAmount;
    }

    public String getStatus() {
        return status;
    }

    public String getType() {
        return type;
    }

    public DateTime getUpdatedDate() {
        return updatedDate;
    }

    public static class Builder {
        private String paymentId;
        private BigDecimal amount;
        private BigDecimal refundAmount;
        private String paymentNumber;
        private String bankIdentificationNumber;
        private String type;
        private String status;
        private String referenceId;
        private DateTime effectiveDate;
        private DateTime createdDate;
        private DateTime updatedDate;

        public Builder() {
        }

        public Builder(PaymentInfo src) {
            this.paymentId = src.paymentId;
            this.amount = src.amount;
            this.refundAmount = src.refundAmount;
            this.paymentNumber = src.paymentNumber;
            this.bankIdentificationNumber = src.bankIdentificationNumber;
            this.type = src.type;
            this.status = src.status;
            this.effectiveDate = src.effectiveDate;
            this.referenceId = src.referenceId;
            this.createdDate = src.createdDate;
            this.updatedDate = src.updatedDate;
        }

        public Builder setPaymentId(String paymentId) {
            this.paymentId = paymentId;
            return this;
        }

        public Builder setAmount(BigDecimal amount) {
            this.amount = amount;
            return this;
        }

        public Builder setBankIdentificationNumber(String bankIdentificationNumber) {
            this.bankIdentificationNumber = bankIdentificationNumber;
            return this;
        }

        public Builder setCreatedDate(DateTime createdDate) {
            this.createdDate = createdDate;
            return this;
        }

        public Builder setEffectiveDate(DateTime effectiveDate) {
            this.effectiveDate = effectiveDate;
            return this;
        }

        public Builder setPaymentNumber(String paymentNumber) {
            this.paymentNumber = paymentNumber;
            return this;
        }

        public Builder setReferenceId(String referenceId) {
            this.referenceId = referenceId;
            return this;
        }

        public Builder setRefundAmount(BigDecimal refundAmount) {
            this.refundAmount = refundAmount;
            return this;
        }

        public Builder setStatus(String status) {
            this.status = status;
            return this;
        }

        public Builder setType(String type) {
            this.type = type;
            return this;
        }

        public Builder setUpdatedDate(DateTime updatedDate) {
            this.updatedDate = updatedDate;
            return this;
        }

        public PaymentInfo build() {
            return new PaymentInfo(paymentId,
                                   amount,
                                   refundAmount,
                                   bankIdentificationNumber,
                                   paymentNumber,
                                   type,
                                   status,
                                   referenceId,
                                   effectiveDate,
                                   createdDate,
                                   updatedDate);
        }
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(amount,
                                bankIdentificationNumber,
                                createdDate,
                                effectiveDate,
                                paymentId,
                                paymentNumber,
                                referenceId,
                                refundAmount,
                                status,
                                type,
                                updatedDate);
    }

    @Override
    public boolean equals(Object obj) {
        if (getClass() == obj.getClass()) {
            PaymentInfo other = (PaymentInfo)obj;
            if (obj == other) {
                return true;
            }
            else {
                return Objects.equal(amount, other.amount) &&
                       Objects.equal(bankIdentificationNumber, other.bankIdentificationNumber) &&
                       Objects.equal(createdDate, other.createdDate) &&
                       Objects.equal(effectiveDate, other.effectiveDate) &&
                       Objects.equal(paymentId, other.paymentId) &&
                       Objects.equal(paymentNumber, other.paymentNumber) &&
                       Objects.equal(referenceId, other.referenceId) &&
                       Objects.equal(refundAmount, other.refundAmount) &&
                       Objects.equal(status, other.status) &&
                       Objects.equal(type, other.type) &&
                       Objects.equal(updatedDate, other.updatedDate);
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "PaymentInfo [paymentId=" + paymentId + ", amount=" + amount + ", refundAmount=" + refundAmount + ", paymentNumber=" + paymentNumber + ", bankIdentificationNumber=" + bankIdentificationNumber + ", status=" + status + ", type=" + type + ", referenceId=" + referenceId + ", effectiveDate=" + effectiveDate + ", createdDate=" + createdDate + ", updatedDate=" + updatedDate + "]";
    }
}
