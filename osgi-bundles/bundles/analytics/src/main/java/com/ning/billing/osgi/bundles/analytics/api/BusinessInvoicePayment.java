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

package com.ning.billing.osgi.bundles.analytics.api;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;

import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoicePaymentBaseModelDao;

public class BusinessInvoicePayment extends BusinessEntityBase {

    private final UUID invoicePaymentId;
    private final UUID invoiceId;
    private final Integer invoiceNumber;
    private final DateTime invoiceCreatedDate;
    private final LocalDate invoiceDate;
    private final LocalDate invoiceTargetDate;
    private final String invoiceCurrency;
    private final BigDecimal invoiceBalance;
    private final BigDecimal invoiceAmountPaid;
    private final BigDecimal invoiceAmountCharged;
    private final BigDecimal invoiceOriginalAmountCharged;
    private final BigDecimal invoiceAmountCredited;
    private final String invoicePaymentType;
    private final Long paymentNumber;
    private final UUID linkedInvoicePaymentId;
    private final BigDecimal amount;
    private final String currency;
    private final DateTime pluginCreatedDate;
    private final DateTime pluginEffectiveDate;
    private final String pluginStatus;
    private final String pluginGatewayError;
    private final String pluginGatewayErrorCode;
    private final String pluginFirstReferenceId;
    private final String pluginSecondReferenceId;
    private final String pluginPmId;
    private final Boolean pluginPmIsDefault;
    private final String pluginPmType;
    private final String pluginPmCcName;
    private final String pluginPmCcType;
    private final String pluginPmCcExpirationMonth;
    private final String pluginPmCcExpirationYear;
    private final String pluginPmCcLast4;
    private final String pluginPmAddress1;
    private final String pluginPmAddress2;
    private final String pluginPmCity;
    private final String pluginPmState;
    private final String pluginPmZip;
    private final String pluginPmCountry;

    public BusinessInvoicePayment(final BusinessInvoicePaymentBaseModelDao businessInvoicePaymentBaseModelDao) {
        super(businessInvoicePaymentBaseModelDao.getCreatedDate(),
              businessInvoicePaymentBaseModelDao.getCreatedBy(),
              businessInvoicePaymentBaseModelDao.getCreatedReasonCode(),
              businessInvoicePaymentBaseModelDao.getCreatedComments(),
              businessInvoicePaymentBaseModelDao.getAccountId(),
              businessInvoicePaymentBaseModelDao.getAccountName(),
              businessInvoicePaymentBaseModelDao.getAccountExternalKey(),
              businessInvoicePaymentBaseModelDao.getReportGroup());
        this.invoicePaymentId = businessInvoicePaymentBaseModelDao.getInvoicePaymentId();
        this.invoiceId = businessInvoicePaymentBaseModelDao.getInvoiceId();
        this.invoiceNumber = businessInvoicePaymentBaseModelDao.getInvoiceNumber();
        this.invoiceCreatedDate = businessInvoicePaymentBaseModelDao.getInvoiceCreatedDate();
        this.invoiceDate = businessInvoicePaymentBaseModelDao.getInvoiceDate();
        this.invoiceTargetDate = businessInvoicePaymentBaseModelDao.getInvoiceTargetDate();
        this.invoiceCurrency = businessInvoicePaymentBaseModelDao.getInvoiceCurrency();
        this.invoiceBalance = businessInvoicePaymentBaseModelDao.getInvoiceBalance();
        this.invoiceAmountPaid = businessInvoicePaymentBaseModelDao.getInvoiceAmountPaid();
        this.invoiceAmountCharged = businessInvoicePaymentBaseModelDao.getInvoiceAmountCharged();
        this.invoiceOriginalAmountCharged = businessInvoicePaymentBaseModelDao.getInvoiceOriginalAmountCharged();
        this.invoiceAmountCredited = businessInvoicePaymentBaseModelDao.getInvoiceAmountCredited();
        this.invoicePaymentType = businessInvoicePaymentBaseModelDao.getInvoicePaymentType();
        this.paymentNumber = businessInvoicePaymentBaseModelDao.getPaymentNumber();
        this.linkedInvoicePaymentId = businessInvoicePaymentBaseModelDao.getLinkedInvoicePaymentId();
        this.amount = businessInvoicePaymentBaseModelDao.getAmount();
        this.currency = businessInvoicePaymentBaseModelDao.getCurrency();
        this.pluginCreatedDate = businessInvoicePaymentBaseModelDao.getPluginCreatedDate();
        this.pluginEffectiveDate = businessInvoicePaymentBaseModelDao.getPluginEffectiveDate();
        this.pluginStatus = businessInvoicePaymentBaseModelDao.getPluginStatus();
        this.pluginGatewayError = businessInvoicePaymentBaseModelDao.getPluginGatewayError();
        this.pluginGatewayErrorCode = businessInvoicePaymentBaseModelDao.getPluginGatewayErrorCode();
        this.pluginFirstReferenceId = businessInvoicePaymentBaseModelDao.getPluginFirstReferenceId();
        this.pluginSecondReferenceId = businessInvoicePaymentBaseModelDao.getPluginSecondReferenceId();
        this.pluginPmId = businessInvoicePaymentBaseModelDao.getPluginPmId();
        this.pluginPmIsDefault = businessInvoicePaymentBaseModelDao.getPluginPmIsDefault();
        this.pluginPmType = businessInvoicePaymentBaseModelDao.getPluginPmType();
        this.pluginPmCcName = businessInvoicePaymentBaseModelDao.getPluginPmCcName();
        this.pluginPmCcType = businessInvoicePaymentBaseModelDao.getPluginPmCcType();
        this.pluginPmCcExpirationMonth = businessInvoicePaymentBaseModelDao.getPluginPmCcExpirationMonth();
        this.pluginPmCcExpirationYear = businessInvoicePaymentBaseModelDao.getPluginPmCcExpirationYear();
        this.pluginPmCcLast4 = businessInvoicePaymentBaseModelDao.getPluginPmCcLast4();
        this.pluginPmAddress1 = businessInvoicePaymentBaseModelDao.getPluginPmAddress1();
        this.pluginPmAddress2 = businessInvoicePaymentBaseModelDao.getPluginPmAddress2();
        this.pluginPmCity = businessInvoicePaymentBaseModelDao.getPluginPmCity();
        this.pluginPmState = businessInvoicePaymentBaseModelDao.getPluginPmState();
        this.pluginPmZip = businessInvoicePaymentBaseModelDao.getPluginPmZip();
        this.pluginPmCountry = businessInvoicePaymentBaseModelDao.getPluginPmCountry();
    }

    public UUID getInvoicePaymentId() {
        return invoicePaymentId;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public Integer getInvoiceNumber() {
        return invoiceNumber;
    }

    public DateTime getInvoiceCreatedDate() {
        return invoiceCreatedDate;
    }

    public LocalDate getInvoiceDate() {
        return invoiceDate;
    }

    public LocalDate getInvoiceTargetDate() {
        return invoiceTargetDate;
    }

    public String getInvoiceCurrency() {
        return invoiceCurrency;
    }

    public BigDecimal getInvoiceBalance() {
        return invoiceBalance;
    }

    public BigDecimal getInvoiceAmountPaid() {
        return invoiceAmountPaid;
    }

    public BigDecimal getInvoiceAmountCharged() {
        return invoiceAmountCharged;
    }

    public BigDecimal getInvoiceOriginalAmountCharged() {
        return invoiceOriginalAmountCharged;
    }

    public BigDecimal getInvoiceAmountCredited() {
        return invoiceAmountCredited;
    }

    public String getInvoicePaymentType() {
        return invoicePaymentType;
    }

    public Long getPaymentNumber() {
        return paymentNumber;
    }

    public UUID getLinkedInvoicePaymentId() {
        return linkedInvoicePaymentId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public DateTime getPluginCreatedDate() {
        return pluginCreatedDate;
    }

    public DateTime getPluginEffectiveDate() {
        return pluginEffectiveDate;
    }

    public String getPluginStatus() {
        return pluginStatus;
    }

    public String getPluginGatewayError() {
        return pluginGatewayError;
    }

    public String getPluginGatewayErrorCode() {
        return pluginGatewayErrorCode;
    }

    public String getPluginFirstReferenceId() {
        return pluginFirstReferenceId;
    }

    public String getPluginSecondReferenceId() {
        return pluginSecondReferenceId;
    }

    public String getPluginPmId() {
        return pluginPmId;
    }

    public Boolean getPluginPmIsDefault() {
        return pluginPmIsDefault;
    }

    public String getPluginPmType() {
        return pluginPmType;
    }

    public String getPluginPmCcName() {
        return pluginPmCcName;
    }

    public String getPluginPmCcType() {
        return pluginPmCcType;
    }

    public String getPluginPmCcExpirationMonth() {
        return pluginPmCcExpirationMonth;
    }

    public String getPluginPmCcExpirationYear() {
        return pluginPmCcExpirationYear;
    }

    public String getPluginPmCcLast4() {
        return pluginPmCcLast4;
    }

    public String getPluginPmAddress1() {
        return pluginPmAddress1;
    }

    public String getPluginPmAddress2() {
        return pluginPmAddress2;
    }

    public String getPluginPmCity() {
        return pluginPmCity;
    }

    public String getPluginPmState() {
        return pluginPmState;
    }

    public String getPluginPmZip() {
        return pluginPmZip;
    }

    public String getPluginPmCountry() {
        return pluginPmCountry;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("BusinessInvoicePayment{");
        sb.append("invoicePaymentId=").append(invoicePaymentId);
        sb.append(", invoiceId=").append(invoiceId);
        sb.append(", invoiceNumber=").append(invoiceNumber);
        sb.append(", invoiceCreatedDate=").append(invoiceCreatedDate);
        sb.append(", invoiceDate=").append(invoiceDate);
        sb.append(", invoiceTargetDate=").append(invoiceTargetDate);
        sb.append(", invoiceCurrency='").append(invoiceCurrency).append('\'');
        sb.append(", invoiceBalance=").append(invoiceBalance);
        sb.append(", invoiceAmountPaid=").append(invoiceAmountPaid);
        sb.append(", invoiceAmountCharged=").append(invoiceAmountCharged);
        sb.append(", invoiceOriginalAmountCharged=").append(invoiceOriginalAmountCharged);
        sb.append(", invoiceAmountCredited=").append(invoiceAmountCredited);
        sb.append(", invoicePaymentType='").append(invoicePaymentType).append('\'');
        sb.append(", paymentNumber=").append(paymentNumber);
        sb.append(", linkedInvoicePaymentId=").append(linkedInvoicePaymentId);
        sb.append(", amount=").append(amount);
        sb.append(", currency='").append(currency).append('\'');
        sb.append(", pluginCreatedDate=").append(pluginCreatedDate);
        sb.append(", pluginEffectiveDate=").append(pluginEffectiveDate);
        sb.append(", pluginStatus='").append(pluginStatus).append('\'');
        sb.append(", pluginGatewayError='").append(pluginGatewayError).append('\'');
        sb.append(", pluginGatewayErrorCode='").append(pluginGatewayErrorCode).append('\'');
        sb.append(", pluginFirstReferenceId='").append(pluginFirstReferenceId).append('\'');
        sb.append(", pluginSecondReferenceId='").append(pluginSecondReferenceId).append('\'');
        sb.append(", pluginPmId='").append(pluginPmId).append('\'');
        sb.append(", pluginPmIsDefault=").append(pluginPmIsDefault);
        sb.append(", pluginPmType='").append(pluginPmType).append('\'');
        sb.append(", pluginPmCcName='").append(pluginPmCcName).append('\'');
        sb.append(", pluginPmCcType='").append(pluginPmCcType).append('\'');
        sb.append(", pluginPmCcExpirationMonth='").append(pluginPmCcExpirationMonth).append('\'');
        sb.append(", pluginPmCcExpirationYear='").append(pluginPmCcExpirationYear).append('\'');
        sb.append(", pluginPmCcLast4='").append(pluginPmCcLast4).append('\'');
        sb.append(", pluginPmAddress1='").append(pluginPmAddress1).append('\'');
        sb.append(", pluginPmAddress2='").append(pluginPmAddress2).append('\'');
        sb.append(", pluginPmCity='").append(pluginPmCity).append('\'');
        sb.append(", pluginPmState='").append(pluginPmState).append('\'');
        sb.append(", pluginPmZip='").append(pluginPmZip).append('\'');
        sb.append(", pluginPmCountry='").append(pluginPmCountry).append('\'');
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

        final BusinessInvoicePayment that = (BusinessInvoicePayment) o;

        if (amount != null ? !(amount.compareTo(that.amount) == 0) : that.amount != null) {
            return false;
        }
        if (currency != null ? !currency.equals(that.currency) : that.currency != null) {
            return false;
        }
        if (invoiceAmountCharged != null ? !(invoiceAmountCharged.compareTo(that.invoiceAmountCharged) == 0) : that.invoiceAmountCharged != null) {
            return false;
        }
        if (invoiceAmountCredited != null ? !(invoiceAmountCredited.compareTo(that.invoiceAmountCredited) == 0) : that.invoiceAmountCredited != null) {
            return false;
        }
        if (invoiceAmountPaid != null ? !(invoiceAmountPaid.compareTo(that.invoiceAmountPaid) == 0) : that.invoiceAmountPaid != null) {
            return false;
        }
        if (invoiceBalance != null ? !(invoiceBalance.compareTo(that.invoiceBalance) == 0) : that.invoiceBalance != null) {
            return false;
        }
        if (invoiceCreatedDate != null ? !invoiceCreatedDate.equals(that.invoiceCreatedDate) : that.invoiceCreatedDate != null) {
            return false;
        }
        if (invoiceCurrency != null ? !invoiceCurrency.equals(that.invoiceCurrency) : that.invoiceCurrency != null) {
            return false;
        }
        if (invoiceDate != null ? !invoiceDate.equals(that.invoiceDate) : that.invoiceDate != null) {
            return false;
        }
        if (invoiceId != null ? !invoiceId.equals(that.invoiceId) : that.invoiceId != null) {
            return false;
        }
        if (invoiceNumber != null ? !invoiceNumber.equals(that.invoiceNumber) : that.invoiceNumber != null) {
            return false;
        }
        if (invoiceOriginalAmountCharged != null ? !(invoiceOriginalAmountCharged.compareTo(that.invoiceOriginalAmountCharged) == 0) : that.invoiceOriginalAmountCharged != null) {
            return false;
        }
        if (invoicePaymentId != null ? !invoicePaymentId.equals(that.invoicePaymentId) : that.invoicePaymentId != null) {
            return false;
        }
        if (invoicePaymentType != null ? !invoicePaymentType.equals(that.invoicePaymentType) : that.invoicePaymentType != null) {
            return false;
        }
        if (invoiceTargetDate != null ? !invoiceTargetDate.equals(that.invoiceTargetDate) : that.invoiceTargetDate != null) {
            return false;
        }
        if (linkedInvoicePaymentId != null ? !linkedInvoicePaymentId.equals(that.linkedInvoicePaymentId) : that.linkedInvoicePaymentId != null) {
            return false;
        }
        if (paymentNumber != null ? !paymentNumber.equals(that.paymentNumber) : that.paymentNumber != null) {
            return false;
        }
        if (pluginCreatedDate != null ? !pluginCreatedDate.equals(that.pluginCreatedDate) : that.pluginCreatedDate != null) {
            return false;
        }
        if (pluginEffectiveDate != null ? !pluginEffectiveDate.equals(that.pluginEffectiveDate) : that.pluginEffectiveDate != null) {
            return false;
        }
        if (pluginFirstReferenceId != null ? !pluginFirstReferenceId.equals(that.pluginFirstReferenceId) : that.pluginFirstReferenceId != null) {
            return false;
        }
        if (pluginGatewayError != null ? !pluginGatewayError.equals(that.pluginGatewayError) : that.pluginGatewayError != null) {
            return false;
        }
        if (pluginGatewayErrorCode != null ? !pluginGatewayErrorCode.equals(that.pluginGatewayErrorCode) : that.pluginGatewayErrorCode != null) {
            return false;
        }
        if (pluginPmAddress1 != null ? !pluginPmAddress1.equals(that.pluginPmAddress1) : that.pluginPmAddress1 != null) {
            return false;
        }
        if (pluginPmAddress2 != null ? !pluginPmAddress2.equals(that.pluginPmAddress2) : that.pluginPmAddress2 != null) {
            return false;
        }
        if (pluginPmCcExpirationMonth != null ? !pluginPmCcExpirationMonth.equals(that.pluginPmCcExpirationMonth) : that.pluginPmCcExpirationMonth != null) {
            return false;
        }
        if (pluginPmCcExpirationYear != null ? !pluginPmCcExpirationYear.equals(that.pluginPmCcExpirationYear) : that.pluginPmCcExpirationYear != null) {
            return false;
        }
        if (pluginPmCcLast4 != null ? !pluginPmCcLast4.equals(that.pluginPmCcLast4) : that.pluginPmCcLast4 != null) {
            return false;
        }
        if (pluginPmCcName != null ? !pluginPmCcName.equals(that.pluginPmCcName) : that.pluginPmCcName != null) {
            return false;
        }
        if (pluginPmCcType != null ? !pluginPmCcType.equals(that.pluginPmCcType) : that.pluginPmCcType != null) {
            return false;
        }
        if (pluginPmCity != null ? !pluginPmCity.equals(that.pluginPmCity) : that.pluginPmCity != null) {
            return false;
        }
        if (pluginPmCountry != null ? !pluginPmCountry.equals(that.pluginPmCountry) : that.pluginPmCountry != null) {
            return false;
        }
        if (pluginPmId != null ? !pluginPmId.equals(that.pluginPmId) : that.pluginPmId != null) {
            return false;
        }
        if (pluginPmIsDefault != null ? !pluginPmIsDefault.equals(that.pluginPmIsDefault) : that.pluginPmIsDefault != null) {
            return false;
        }
        if (pluginPmState != null ? !pluginPmState.equals(that.pluginPmState) : that.pluginPmState != null) {
            return false;
        }
        if (pluginPmType != null ? !pluginPmType.equals(that.pluginPmType) : that.pluginPmType != null) {
            return false;
        }
        if (pluginPmZip != null ? !pluginPmZip.equals(that.pluginPmZip) : that.pluginPmZip != null) {
            return false;
        }
        if (pluginSecondReferenceId != null ? !pluginSecondReferenceId.equals(that.pluginSecondReferenceId) : that.pluginSecondReferenceId != null) {
            return false;
        }
        if (pluginStatus != null ? !pluginStatus.equals(that.pluginStatus) : that.pluginStatus != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (invoicePaymentId != null ? invoicePaymentId.hashCode() : 0);
        result = 31 * result + (invoiceId != null ? invoiceId.hashCode() : 0);
        result = 31 * result + (invoiceNumber != null ? invoiceNumber.hashCode() : 0);
        result = 31 * result + (invoiceCreatedDate != null ? invoiceCreatedDate.hashCode() : 0);
        result = 31 * result + (invoiceDate != null ? invoiceDate.hashCode() : 0);
        result = 31 * result + (invoiceTargetDate != null ? invoiceTargetDate.hashCode() : 0);
        result = 31 * result + (invoiceCurrency != null ? invoiceCurrency.hashCode() : 0);
        result = 31 * result + (invoiceBalance != null ? invoiceBalance.hashCode() : 0);
        result = 31 * result + (invoiceAmountPaid != null ? invoiceAmountPaid.hashCode() : 0);
        result = 31 * result + (invoiceAmountCharged != null ? invoiceAmountCharged.hashCode() : 0);
        result = 31 * result + (invoiceOriginalAmountCharged != null ? invoiceOriginalAmountCharged.hashCode() : 0);
        result = 31 * result + (invoiceAmountCredited != null ? invoiceAmountCredited.hashCode() : 0);
        result = 31 * result + (invoicePaymentType != null ? invoicePaymentType.hashCode() : 0);
        result = 31 * result + (paymentNumber != null ? paymentNumber.hashCode() : 0);
        result = 31 * result + (linkedInvoicePaymentId != null ? linkedInvoicePaymentId.hashCode() : 0);
        result = 31 * result + (amount != null ? amount.hashCode() : 0);
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        result = 31 * result + (pluginCreatedDate != null ? pluginCreatedDate.hashCode() : 0);
        result = 31 * result + (pluginEffectiveDate != null ? pluginEffectiveDate.hashCode() : 0);
        result = 31 * result + (pluginStatus != null ? pluginStatus.hashCode() : 0);
        result = 31 * result + (pluginGatewayError != null ? pluginGatewayError.hashCode() : 0);
        result = 31 * result + (pluginGatewayErrorCode != null ? pluginGatewayErrorCode.hashCode() : 0);
        result = 31 * result + (pluginFirstReferenceId != null ? pluginFirstReferenceId.hashCode() : 0);
        result = 31 * result + (pluginSecondReferenceId != null ? pluginSecondReferenceId.hashCode() : 0);
        result = 31 * result + (pluginPmId != null ? pluginPmId.hashCode() : 0);
        result = 31 * result + (pluginPmIsDefault != null ? pluginPmIsDefault.hashCode() : 0);
        result = 31 * result + (pluginPmType != null ? pluginPmType.hashCode() : 0);
        result = 31 * result + (pluginPmCcName != null ? pluginPmCcName.hashCode() : 0);
        result = 31 * result + (pluginPmCcType != null ? pluginPmCcType.hashCode() : 0);
        result = 31 * result + (pluginPmCcExpirationMonth != null ? pluginPmCcExpirationMonth.hashCode() : 0);
        result = 31 * result + (pluginPmCcExpirationYear != null ? pluginPmCcExpirationYear.hashCode() : 0);
        result = 31 * result + (pluginPmCcLast4 != null ? pluginPmCcLast4.hashCode() : 0);
        result = 31 * result + (pluginPmAddress1 != null ? pluginPmAddress1.hashCode() : 0);
        result = 31 * result + (pluginPmAddress2 != null ? pluginPmAddress2.hashCode() : 0);
        result = 31 * result + (pluginPmCity != null ? pluginPmCity.hashCode() : 0);
        result = 31 * result + (pluginPmState != null ? pluginPmState.hashCode() : 0);
        result = 31 * result + (pluginPmZip != null ? pluginPmZip.hashCode() : 0);
        result = 31 * result + (pluginPmCountry != null ? pluginPmCountry.hashCode() : 0);
        return result;
    }
}
