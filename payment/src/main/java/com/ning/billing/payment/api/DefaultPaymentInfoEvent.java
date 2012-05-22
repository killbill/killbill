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
import java.util.UUID;

import com.ning.billing.util.entity.EntityBase;
import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonProperty;
import org.joda.time.DateTime;

import com.google.common.base.Objects;

import com.ning.billing.payment.plugin.api.PaymentInfoPlugin;
import com.ning.billing.util.bus.BusEvent;
import com.ning.billing.util.bus.BusEvent.BusEventType;

public class DefaultPaymentInfoEvent extends EntityBase implements PaymentInfoEvent {

    private final UUID accountId;
    private final UUID invoiceId;    
    private final BigDecimal amount;
    private final BigDecimal refundAmount;
    private final String paymentNumber;
    private final String bankIdentificationNumber;
    private final String status;
    private final String type;
    private final String referenceId;
    private final String paymentMethodId;
    private final String paymentMethod;
    private final String cardType;
    private final String cardCountry;
    private final UUID userToken;
    private final DateTime effectiveDate;
    private final DateTime createdDate;    
    private final DateTime updatedDate;        

    @JsonCreator
    public DefaultPaymentInfoEvent(@JsonProperty("id") UUID id,
            @JsonProperty("accountId") UUID accountId,
            @JsonProperty("invoiceId") UUID invoiceId,            
            @JsonProperty("amount") BigDecimal amount,
            @JsonProperty("refundAmount") BigDecimal refundAmount,
            @JsonProperty("bankIdentificationNumber") String bankIdentificationNumber,
            @JsonProperty("paymentNumber") String paymentNumber,
            @JsonProperty("status") String status,
            @JsonProperty("type") String type,
            @JsonProperty("referenceId") String referenceId,
            @JsonProperty("paymentMethodId") String paymentMethodId,
            @JsonProperty("paymentMethod") String paymentMethod,
            @JsonProperty("cardType") String cardType,
            @JsonProperty("cardCountry") String cardCountry,
            @JsonProperty("userToken") UUID userToken,
            @JsonProperty("effectiveDate") DateTime effectiveDate,
            @JsonProperty("createdDate") DateTime createdDate,
            @JsonProperty("updatedDate") DateTime updatedDate) {
        super(id);
        this.accountId = accountId;
        this.invoiceId = invoiceId;
        this.amount = amount;
        this.refundAmount = refundAmount;
        this.bankIdentificationNumber = bankIdentificationNumber;
        this.paymentNumber = paymentNumber;
        this.status = status;
        this.type = type;
        this.referenceId = referenceId;
        this.paymentMethodId = paymentMethodId;
        this.paymentMethod = paymentMethod;
        this.cardType = cardType;
        this.cardCountry = cardCountry;
        this.userToken = userToken;
        this.effectiveDate = effectiveDate;
        this.createdDate = createdDate;
        this.updatedDate = updatedDate;
    }

    public DefaultPaymentInfoEvent(DefaultPaymentInfoEvent src) {
        this(src.id,
                src.accountId,
                src.invoiceId,
                src.amount,
                src.refundAmount,
                src.bankIdentificationNumber,
                src.paymentNumber,
                src.status,
                src.type,
                src.referenceId,
                src.paymentMethodId,
                src.paymentMethod,
                src.cardType,
                src.cardCountry,
                src.userToken,
                src.effectiveDate,
                src.createdDate,
                src.updatedDate);
    }
    

    public DefaultPaymentInfoEvent(PaymentInfoPlugin info, UUID accountId, UUID invoiceId) {
        this(UUID.randomUUID(), accountId,  invoiceId, info.getAmount(), info.getRefundAmount(), info.getBankIdentificationNumber(), info.getPaymentNumber(),
                info.getStatus(), info.getCardType(), info.getReferenceId(), info.getPaymentMethodId(), info.getPaymentMethod(), info.getCardType(), info.getCardCountry(),
                null, info.getEffectiveDate(), info.getCreatedDate(), info.getUpdatedDate());
    }

    

    @JsonIgnore
    @Override
    public BusEventType getBusEventType() {
        return BusEventType.PAYMENT_INFO;
    }

    @Override
    public UUID getUserToken() {
        return userToken;
    }

    public Builder cloner() {
        return new Builder(this);
    }

    @Override
    public UUID getId() {
        return id;
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
    public String getBankIdentificationNumber() {
        return bankIdentificationNumber;
    }

    @Override
    public DateTime getEffectiveDate() {
        return effectiveDate;
    }

    @Override
    public String getPaymentNumber() {
        return paymentNumber;
    }

    @Override
    public String getPaymentMethod() {
        return paymentMethod;
    }

    @Override
    public String getCardType() {
        return cardType;
    }

    @Override
    public String getCardCountry() {
        return cardCountry;
    }

    @Override
    public String getReferenceId() {
        return referenceId;
    }

    @Override
    public String getPaymentMethodId() {
        return paymentMethodId;
    }

    @Override
    public BigDecimal getRefundAmount() {
        return refundAmount;
    }

    @Override
    public String getStatus() {
        return status;
    }

    @Override
    public String getType() {
        return type;
    }
    
    @Override
    public DateTime getCreatedDate() {
        return createdDate;
    }

    @Override
    public DateTime getUpdatedDate() {
        return updatedDate;
    }


    public static class Builder {

        private UUID id;
        private UUID accountId;
        private UUID invoiceId;
        private BigDecimal amount;
        private BigDecimal refundAmount;
        private String paymentNumber;
        private String bankIdentificationNumber;
        private String type;
        private String status;
        private String referenceId;
        private String paymentMethodId;
        private String paymentMethod;
        private String cardType;
        private String cardCountry;
        private UUID userToken;
        private DateTime effectiveDate;
        private DateTime createdDate;
        private DateTime updatedDate;        

        public Builder() {
        }

        public Builder(DefaultPaymentInfoEvent src) {
            this.id = src.id;
            this.accountId = src.accountId;
            this.invoiceId = src.invoiceId;
            this.amount = src.amount;
            this.refundAmount = src.refundAmount;
            this.paymentNumber = src.paymentNumber;
            this.bankIdentificationNumber = src.bankIdentificationNumber;
            this.type = src.type;
            this.status = src.status;
            this.effectiveDate = src.effectiveDate;
            this.referenceId = src.referenceId;
            this.paymentMethodId = src.paymentMethodId;
            this.paymentMethod = src.paymentMethod;
            this.cardType = src.cardType;
            this.cardCountry = src.cardCountry;
            this.userToken = src.userToken;
            this.createdDate = src.createdDate;
            this.updatedDate = src.updatedDate;
        }


        public Builder setAccountId(UUID accountId) {
            this.accountId = accountId;
            return this;
        }


        public Builder setInvoiceId(UUID invoiceId) {
            this.invoiceId = invoiceId;
            return this;
        }

        public Builder setId(UUID id) {
            this.id = id;
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

        public Builder setUserToken(UUID userToken) {
            this.userToken = userToken;
            return this;
        }

        public Builder setEffectiveDate(DateTime effectiveDate) {
            this.effectiveDate = effectiveDate;
            return this;
        }

        public Builder setCreatedDate(DateTime createdDate) {
            this.createdDate = createdDate;
            return this;
        }

        public Builder setUpdatedDate(DateTime updatedDate) {
            this.updatedDate = updatedDate;
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

        public Builder setPaymentMethodId(String paymentMethodId) {
            this.paymentMethodId = paymentMethodId;
            return this;
        }

        public Builder setPaymentMethod(String paymentMethod) {
            this.paymentMethod = paymentMethod;
            return this;
        }

        public Builder setCardType(String cardType) {
            this.cardType = cardType;
            return this;
        }

        public Builder setCardCountry(String cardCountry) {
            this.cardCountry = cardCountry;
            return this;
        }

        public PaymentInfoEvent build() {
            return new DefaultPaymentInfoEvent(id,
                    accountId,
                    invoiceId,
                    amount,
                    refundAmount,
                    bankIdentificationNumber,
                    paymentNumber,
                    status,
                    type,
                    referenceId,
                    paymentMethodId,
                    paymentMethod,
                    cardType,
                    cardCountry,
                    userToken,
                    effectiveDate,
                    createdDate,
                    updatedDate);
        }

    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id,
                amount,
                refundAmount,
                bankIdentificationNumber,
                paymentNumber,
                status,
                type,
                referenceId,
                paymentMethodId,
                paymentMethod,
                cardType,
                cardCountry,
                effectiveDate);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final DefaultPaymentInfoEvent that = (DefaultPaymentInfoEvent) o;

        if (amount != null ? !(amount.compareTo(that.amount) == 0) : that.amount != null) return false;
        if (bankIdentificationNumber != null ? !bankIdentificationNumber.equals(that.bankIdentificationNumber) : that.bankIdentificationNumber != null)
            return false;
        if (cardCountry != null ? !cardCountry.equals(that.cardCountry) : that.cardCountry != null) return false;
        if (cardType != null ? !cardType.equals(that.cardType) : that.cardType != null) return false;
        if (effectiveDate == null ? that.effectiveDate != null : effectiveDate.compareTo(that.effectiveDate) != 0) return false;
        if (id != null ? !id.equals(that.id) : that.id != null) return false;
        if (paymentMethod != null ? !paymentMethod.equals(that.paymentMethod) : that.paymentMethod != null)
            return false;
        if (paymentMethodId != null ? !paymentMethodId.equals(that.paymentMethodId) : that.paymentMethodId != null)
            return false;
        if (paymentNumber != null ? !paymentNumber.equals(that.paymentNumber) : that.paymentNumber != null)
            return false;
        if (referenceId != null ? !referenceId.equals(that.referenceId) : that.referenceId != null) return false;
        if (refundAmount != null ? !refundAmount.equals(that.refundAmount) : that.refundAmount != null) return false;
        if (status != null ? !status.equals(that.status) : that.status != null) return false;
        if (type != null ? !type.equals(that.type) : that.type != null) return false;

        return true;
    }

    @Override
    public String toString() {
        return "PaymentInfo [id=" + id + ", amount=" + amount + ", refundAmount=" + refundAmount + ", paymentNumber=" + paymentNumber + ", bankIdentificationNumber=" + bankIdentificationNumber + ", status=" + status + ", type=" + type + ", referenceId=" + referenceId + ", paymentMethodId=" + paymentMethodId + ", paymentMethod=" + paymentMethod + ", cardType=" + cardType + ", cardCountry=" + cardCountry + ", effectiveDate=" + effectiveDate + "]";
    }
}
