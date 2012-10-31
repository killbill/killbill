/*
 * Copyright 2010-2012 Ning, Inc.
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

package com.ning.billing.jaxrs.json;

import java.math.BigDecimal;

import org.joda.time.DateTime;

import com.ning.billing.analytics.api.BusinessInvoicePayment;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class BusinessInvoicePaymentJson extends JsonBase {

    private final String paymentId;
    private final String extFirstPaymentRefId;
    private final String extSecondPaymentRefId;
    private final String accountKey;
    private final String invoiceId;
    private final DateTime effectiveDate;
    private final BigDecimal amount;
    private final String currency;
    private final String paymentError;
    private final String processingStatus;
    private final BigDecimal requestedAmount;
    private final String pluginName;
    private final String paymentType;
    private final String paymentMethod;
    private final String cardType;
    private final String cardCountry;
    private final String invoicePaymentType;
    private final String linkedInvoicePaymentId;

    @JsonCreator
    public BusinessInvoicePaymentJson(@JsonProperty("paymentId") final String paymentId,
                                      @JsonProperty("extFirstPaymentRefId") final String extFirstPaymentRefId,
                                      @JsonProperty("extSecondPaymentRefId") final String extSecondPaymentRefId,
                                      @JsonProperty("accountKey") final String accountKey,
                                      @JsonProperty("invoiceId") final String invoiceId,
                                      @JsonProperty("effectiveDate") final DateTime effectiveDate,
                                      @JsonProperty("amount") final BigDecimal amount,
                                      @JsonProperty("currency") final String currency,
                                      @JsonProperty("paymentError") final String paymentError,
                                      @JsonProperty("processingStatus") final String processingStatus,
                                      @JsonProperty("requestedAmount") final BigDecimal requestedAmount,
                                      @JsonProperty("pluginName") final String pluginName,
                                      @JsonProperty("paymentType") final String paymentType,
                                      @JsonProperty("paymentMethod") final String paymentMethod,
                                      @JsonProperty("cardType") final String cardType,
                                      @JsonProperty("cardCountry") final String cardCountry,
                                      @JsonProperty("invoicePaymentType") final String invoicePaymentType,
                                      @JsonProperty("linkedInvoicePaymentId") final String linkedInvoicePaymentId) {
        this.paymentId = paymentId;
        this.extFirstPaymentRefId = extFirstPaymentRefId;
        this.extSecondPaymentRefId = extSecondPaymentRefId;
        this.accountKey = accountKey;
        this.invoiceId = invoiceId;
        this.effectiveDate = effectiveDate;
        this.amount = amount;
        this.currency = currency;
        this.paymentError = paymentError;
        this.processingStatus = processingStatus;
        this.requestedAmount = requestedAmount;
        this.pluginName = pluginName;
        this.paymentType = paymentType;
        this.paymentMethod = paymentMethod;
        this.cardType = cardType;
        this.cardCountry = cardCountry;
        this.invoicePaymentType = invoicePaymentType;
        this.linkedInvoicePaymentId = linkedInvoicePaymentId;
    }

    public BusinessInvoicePaymentJson(final BusinessInvoicePayment businessInvoicePayment) {
        this(businessInvoicePayment.getPaymentId().toString(),
             businessInvoicePayment.getExtFirstPaymentRefId(),
             businessInvoicePayment.getExtSecondPaymentRefId(),
             businessInvoicePayment.getAccountKey(),
             businessInvoicePayment.getInvoiceId().toString(),
             businessInvoicePayment.getEffectiveDate(),
             businessInvoicePayment.getAmount(),
             businessInvoicePayment.getCurrency().toString(),
             businessInvoicePayment.getPaymentError(),
             businessInvoicePayment.getProcessingStatus(),
             businessInvoicePayment.getRequestedAmount(),
             businessInvoicePayment.getPluginName(),
             businessInvoicePayment.getPaymentType(),
             businessInvoicePayment.getPaymentMethod(),
             businessInvoicePayment.getCardType(),
             businessInvoicePayment.getInvoicePaymentType(),
             businessInvoicePayment.getInvoicePaymentType(),
             businessInvoicePayment.getLinkedInvoicePaymentId().toString());
    }

    public String getPaymentId() {
        return paymentId;
    }

    public String getExtFirstPaymentRefId() {
        return extFirstPaymentRefId;
    }

    public String getExtSecondPaymentRefId() {
        return extSecondPaymentRefId;
    }

    public String getAccountKey() {
        return accountKey;
    }

    public String getInvoiceId() {
        return invoiceId;
    }

    public DateTime getEffectiveDate() {
        return effectiveDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getPaymentError() {
        return paymentError;
    }

    public String getProcessingStatus() {
        return processingStatus;
    }

    public BigDecimal getRequestedAmount() {
        return requestedAmount;
    }

    public String getPluginName() {
        return pluginName;
    }

    public String getPaymentType() {
        return paymentType;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public String getCardType() {
        return cardType;
    }

    public String getCardCountry() {
        return cardCountry;
    }

    public String getInvoicePaymentType() {
        return invoicePaymentType;
    }

    public String getLinkedInvoicePaymentId() {
        return linkedInvoicePaymentId;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("BusinessInvoicePaymentJson");
        sb.append("{paymentId='").append(paymentId).append('\'');
        sb.append(", extFirstPaymentRefId='").append(extFirstPaymentRefId).append('\'');
        sb.append(", extSecondPaymentRefId='").append(extSecondPaymentRefId).append('\'');
        sb.append(", accountKey='").append(accountKey).append('\'');
        sb.append(", invoiceId='").append(invoiceId).append('\'');
        sb.append(", effectiveDate=").append(effectiveDate);
        sb.append(", amount=").append(amount);
        sb.append(", currency='").append(currency).append('\'');
        sb.append(", paymentError='").append(paymentError).append('\'');
        sb.append(", processingStatus='").append(processingStatus).append('\'');
        sb.append(", requestedAmount=").append(requestedAmount);
        sb.append(", pluginName='").append(pluginName).append('\'');
        sb.append(", paymentType='").append(paymentType).append('\'');
        sb.append(", paymentMethod='").append(paymentMethod).append('\'');
        sb.append(", cardType='").append(cardType).append('\'');
        sb.append(", cardCountry='").append(cardCountry).append('\'');
        sb.append(", invoicePaymentType='").append(invoicePaymentType).append('\'');
        sb.append(", linkedInvoicePaymentId='").append(linkedInvoicePaymentId).append('\'');
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

        final BusinessInvoicePaymentJson that = (BusinessInvoicePaymentJson) o;

        if (accountKey != null ? !accountKey.equals(that.accountKey) : that.accountKey != null) {
            return false;
        }
        if (amount != null ? !amount.equals(that.amount) : that.amount != null) {
            return false;
        }
        if (cardCountry != null ? !cardCountry.equals(that.cardCountry) : that.cardCountry != null) {
            return false;
        }
        if (cardType != null ? !cardType.equals(that.cardType) : that.cardType != null) {
            return false;
        }
        if (currency != null ? !currency.equals(that.currency) : that.currency != null) {
            return false;
        }
        if (effectiveDate != null ? !effectiveDate.equals(that.effectiveDate) : that.effectiveDate != null) {
            return false;
        }
        if (extFirstPaymentRefId != null ? !extFirstPaymentRefId.equals(that.extFirstPaymentRefId) : that.extFirstPaymentRefId != null) {
            return false;
        }
        if (extSecondPaymentRefId != null ? !extSecondPaymentRefId.equals(that.extSecondPaymentRefId) : that.extSecondPaymentRefId != null) {
            return false;
        }
        if (invoiceId != null ? !invoiceId.equals(that.invoiceId) : that.invoiceId != null) {
            return false;
        }
        if (invoicePaymentType != null ? !invoicePaymentType.equals(that.invoicePaymentType) : that.invoicePaymentType != null) {
            return false;
        }
        if (linkedInvoicePaymentId != null ? !linkedInvoicePaymentId.equals(that.linkedInvoicePaymentId) : that.linkedInvoicePaymentId != null) {
            return false;
        }
        if (paymentError != null ? !paymentError.equals(that.paymentError) : that.paymentError != null) {
            return false;
        }
        if (paymentId != null ? !paymentId.equals(that.paymentId) : that.paymentId != null) {
            return false;
        }
        if (paymentMethod != null ? !paymentMethod.equals(that.paymentMethod) : that.paymentMethod != null) {
            return false;
        }
        if (paymentType != null ? !paymentType.equals(that.paymentType) : that.paymentType != null) {
            return false;
        }
        if (pluginName != null ? !pluginName.equals(that.pluginName) : that.pluginName != null) {
            return false;
        }
        if (processingStatus != null ? !processingStatus.equals(that.processingStatus) : that.processingStatus != null) {
            return false;
        }
        if (requestedAmount != null ? !requestedAmount.equals(that.requestedAmount) : that.requestedAmount != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = paymentId != null ? paymentId.hashCode() : 0;
        result = 31 * result + (extFirstPaymentRefId != null ? extFirstPaymentRefId.hashCode() : 0);
        result = 31 * result + (extSecondPaymentRefId != null ? extSecondPaymentRefId.hashCode() : 0);
        result = 31 * result + (accountKey != null ? accountKey.hashCode() : 0);
        result = 31 * result + (invoiceId != null ? invoiceId.hashCode() : 0);
        result = 31 * result + (effectiveDate != null ? effectiveDate.hashCode() : 0);
        result = 31 * result + (amount != null ? amount.hashCode() : 0);
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        result = 31 * result + (paymentError != null ? paymentError.hashCode() : 0);
        result = 31 * result + (processingStatus != null ? processingStatus.hashCode() : 0);
        result = 31 * result + (requestedAmount != null ? requestedAmount.hashCode() : 0);
        result = 31 * result + (pluginName != null ? pluginName.hashCode() : 0);
        result = 31 * result + (paymentType != null ? paymentType.hashCode() : 0);
        result = 31 * result + (paymentMethod != null ? paymentMethod.hashCode() : 0);
        result = 31 * result + (cardType != null ? cardType.hashCode() : 0);
        result = 31 * result + (cardCountry != null ? cardCountry.hashCode() : 0);
        result = 31 * result + (invoicePaymentType != null ? invoicePaymentType.hashCode() : 0);
        result = 31 * result + (linkedInvoicePaymentId != null ? linkedInvoicePaymentId.hashCode() : 0);
        return result;
    }
}
