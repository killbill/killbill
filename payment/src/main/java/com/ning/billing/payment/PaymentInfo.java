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

package com.ning.billing.payment;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.util.eventbus.IEventBusType;

public class PaymentInfo implements IEventBusType {
    public static class Builder {
        private UUID id;
        private BigDecimal amount;
        private BigDecimal appliedCreditBalanceAmount;
        private String bankIdentificationNumber;
        private DateTime createdDate;
        private DateTime effectiveDate;
        private String paymentNumber;
        private String referenceId;
        private BigDecimal refundAmount;
        private String secondPaymentReferenceId;
        private String status;
        private String type;
        private DateTime updatedDate;

        public Builder() {
        }

        public Builder(PaymentInfo src) {
            this.id = src.id;
            this.amount = src.amount;
            this.appliedCreditBalanceAmount = src.appliedCreditBalanceAmount;
            this.bankIdentificationNumber = src.bankIdentificationNumber;
            this.createdDate = src.createdDate;
            this.effectiveDate = src.effectiveDate;
            this.paymentNumber = src.paymentNumber;
            this.referenceId = src.referenceId;
            this.refundAmount = src.refundAmount;
            this.secondPaymentReferenceId = src.secondPaymentReferenceId;
            this.status = src.status;
            this.type = src.type;
            this.updatedDate = src.updatedDate;
        }

        public Builder setId(UUID id) {
            this.id = id;
            return this;
        }

        public Builder setAmount(BigDecimal amount) {
            this.amount = amount;
            return this;
        }

        public Builder setAppliedCreditBalanceAmount(BigDecimal appliedCreditBalanceAmount) {
            this.appliedCreditBalanceAmount = appliedCreditBalanceAmount;
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

        public Builder setSecondPaymentReferenceId(String secondPaymentReferenceId) {
            this.secondPaymentReferenceId = secondPaymentReferenceId;
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
            return new PaymentInfo(id,
                                   amount,
                                   appliedCreditBalanceAmount,
                                   bankIdentificationNumber,
                                   createdDate,
                                   effectiveDate,
                                   paymentNumber,
                                   referenceId,
                                   refundAmount,
                                   secondPaymentReferenceId,
                                   status,
                                   type,
                                   updatedDate);
        }
    }

    private final UUID id;
    private final BigDecimal amount;
    private final BigDecimal appliedCreditBalanceAmount;
    private final String bankIdentificationNumber;
    private final DateTime createdDate;
    private final DateTime effectiveDate;
    private final String paymentNumber;
    private final String referenceId;
    private final BigDecimal refundAmount;
    private final String secondPaymentReferenceId;
    private final String status;
    private final String type;
    private final DateTime updatedDate;

    public PaymentInfo(PaymentInfo src) {
        this.id = src.id;
        this.amount = src.amount;
        this.appliedCreditBalanceAmount = src.appliedCreditBalanceAmount;
        this.bankIdentificationNumber = src.bankIdentificationNumber;
        this.createdDate = src.createdDate;
        this.effectiveDate = src.effectiveDate;
        this.paymentNumber = src.paymentNumber;
        this.referenceId = src.referenceId;
        this.refundAmount = src.refundAmount;
        this.secondPaymentReferenceId = src.secondPaymentReferenceId;
        this.status = src.status;
        this.type = src.type;
        this.updatedDate = src.updatedDate;
    }

    public PaymentInfo(UUID id,
                       BigDecimal amount,
                       BigDecimal appliedCreditBalanceAmount,
                       String bankIdentificationNumber,
                       DateTime createdDate,
                       DateTime effectiveDate,
                       String paymentNumber,
                       String referenceId,
                       BigDecimal refundAmount,
                       String secondPaymentReferenceId,
                       String status,
                       String type,
                       DateTime updatedDate) {
        this.id = id;
        this.amount = amount;
        this.appliedCreditBalanceAmount = appliedCreditBalanceAmount;
        this.bankIdentificationNumber = bankIdentificationNumber;
        this.createdDate = createdDate;
        this.effectiveDate = effectiveDate;
        this.paymentNumber = paymentNumber;
        this.referenceId = referenceId;
        this.refundAmount = refundAmount;
        this.secondPaymentReferenceId = secondPaymentReferenceId;
        this.status = status;
        this.type = type;
        this.updatedDate = updatedDate;
    }

    public Builder cloner() {
        return new Builder(this);
    }

    public UUID getId() {
        return id;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getAppliedCreditBalanceAmount() {
        return appliedCreditBalanceAmount;
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

    public String getSecondPaymentReferenceId() {
        return secondPaymentReferenceId;
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

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((amount == null) ? 0 : amount.hashCode());
        result = prime * result + ((appliedCreditBalanceAmount == null) ? 0
                                                                       : appliedCreditBalanceAmount.hashCode());
        result = prime * result + ((bankIdentificationNumber == null) ? 0
                                                                     : bankIdentificationNumber.hashCode());
        result = prime * result + ((createdDate == null) ? 0
                                                        : createdDate.hashCode());
        result = prime * result + ((effectiveDate == null) ? 0
                                                          : effectiveDate.hashCode());
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        result = prime * result + ((paymentNumber == null) ? 0
                                                          : paymentNumber.hashCode());
        result = prime * result + ((referenceId == null) ? 0
                                                        : referenceId.hashCode());
        result = prime * result + ((refundAmount == null) ? 0
                                                         : refundAmount.hashCode());
        result = prime * result + ((secondPaymentReferenceId == null) ? 0
                                                                     : secondPaymentReferenceId.hashCode());
        result = prime * result + ((status == null) ? 0 : status.hashCode());
        result = prime * result + ((type == null) ? 0 : type.hashCode());
        result = prime * result + ((updatedDate == null) ? 0
                                                        : updatedDate.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        PaymentInfo other = (PaymentInfo) obj;
        if (amount == null) {
            if (other.amount != null)
                return false;
        }
        else if (!amount.equals(other.amount))
            return false;
        if (appliedCreditBalanceAmount == null) {
            if (other.appliedCreditBalanceAmount != null)
                return false;
        }
        else if (!appliedCreditBalanceAmount.equals(other.appliedCreditBalanceAmount))
            return false;
        if (bankIdentificationNumber == null) {
            if (other.bankIdentificationNumber != null)
                return false;
        }
        else if (!bankIdentificationNumber.equals(other.bankIdentificationNumber))
            return false;
        if (createdDate == null) {
            if (other.createdDate != null)
                return false;
        }
        else if (!createdDate.equals(other.createdDate))
            return false;
        if (effectiveDate == null) {
            if (other.effectiveDate != null)
                return false;
        }
        else if (!effectiveDate.equals(other.effectiveDate))
            return false;
        if (id == null) {
            if (other.id != null)
                return false;
        }
        else if (!id.equals(other.id))
            return false;
        if (paymentNumber == null) {
            if (other.paymentNumber != null)
                return false;
        }
        else if (!paymentNumber.equals(other.paymentNumber))
            return false;
        if (referenceId == null) {
            if (other.referenceId != null)
                return false;
        }
        else if (!referenceId.equals(other.referenceId))
            return false;
        if (refundAmount == null) {
            if (other.refundAmount != null)
                return false;
        }
        else if (!refundAmount.equals(other.refundAmount))
            return false;
        if (secondPaymentReferenceId == null) {
            if (other.secondPaymentReferenceId != null)
                return false;
        }
        else if (!secondPaymentReferenceId.equals(other.secondPaymentReferenceId))
            return false;
        if (status == null) {
            if (other.status != null)
                return false;
        }
        else if (!status.equals(other.status))
            return false;
        if (type == null) {
            if (other.type != null)
                return false;
        }
        else if (!type.equals(other.type))
            return false;
        if (updatedDate == null) {
            if (other.updatedDate != null)
                return false;
        }
        else if (!updatedDate.equals(other.updatedDate))
            return false;
        return true;
    }
}
