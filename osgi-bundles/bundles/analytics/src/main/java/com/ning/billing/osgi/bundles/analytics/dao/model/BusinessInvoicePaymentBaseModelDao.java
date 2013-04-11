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

package com.ning.billing.osgi.bundles.analytics.dao.model;

import java.math.BigDecimal;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;

import com.ning.billing.account.api.Account;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.invoice.api.InvoicePayment.InvoicePaymentType;
import com.ning.billing.payment.api.Payment;
import com.ning.billing.payment.api.PaymentMethod;
import com.ning.billing.payment.api.Refund;
import com.ning.billing.util.audit.AuditLog;

public abstract class BusinessInvoicePaymentBaseModelDao extends BusinessModelDaoBase {

    protected static final String INVOICE_PAYMENTS_TABLE_NAME = "bip";
    protected static final String INVOICE_PAYMENT_REFUNDS_TABLE_NAME = "bipr";
    protected static final String INVOICE_PAYMENT_CHARGEBACKS_TABLE_NAME = "bipc";

    public static final String[] ALL_INVOICE_PAYMENTS_TABLE_NAMES = new String[]{INVOICE_PAYMENTS_TABLE_NAME, INVOICE_PAYMENT_REFUNDS_TABLE_NAME, INVOICE_PAYMENT_CHARGEBACKS_TABLE_NAME};

    private Long invoicePaymentRecordId;
    private UUID invoicePaymentId;
    private UUID invoiceId;
    private Integer invoiceNumber;
    private DateTime invoiceCreatedDate;
    private LocalDate invoiceDate;
    private LocalDate invoiceTargetDate;
    private String invoiceCurrency;
    private BigDecimal invoiceBalance;
    private BigDecimal invoiceAmountPaid;
    private BigDecimal invoiceAmountCharged;
    private BigDecimal invoiceOriginalAmountCharged;
    private BigDecimal invoiceAmountCredited;
    private String invoicePaymentType;
    private Long paymentNumber;
    private UUID linkedInvoicePaymentId;
    private BigDecimal amount;
    private String currency;
    private DateTime pluginCreatedDate;
    private DateTime pluginEffectiveDate;
    private String pluginStatus;
    private String pluginGatewayError;
    private String pluginGatewayErrorCode;
    private String pluginFirstReferenceId;
    private String pluginSecondReferenceId;
    private String pluginPmId;
    private Boolean pluginPmIsDefault;
    private String pluginPmType;
    private String pluginPmCcName;
    private String pluginPmCcType;
    private String pluginPmCcExpirationMonth;
    private String pluginPmCcExpirationYear;
    private String pluginPmCcLast4;
    private String pluginPmAddress1;
    private String pluginPmAddress2;
    private String pluginPmCity;
    private String pluginPmState;
    private String pluginPmZip;
    private String pluginPmCountry;

    public static BusinessInvoicePaymentBaseModelDao create(final Account account,
                                                            final Long accountRecordId,
                                                            final Invoice invoice,
                                                            final InvoicePayment invoicePayment,
                                                            final Long invoicePaymentRecordId,
                                                            final Payment payment,
                                                            final Refund refund,
                                                            final PaymentMethod paymentMethod,
                                                            final AuditLog creationAuditLog,
                                                            final Long tenantRecordId) {
        if (invoicePayment.getType().equals(InvoicePaymentType.REFUND)) {
            return new BusinessInvoicePaymentRefundModelDao(account,
                                                            accountRecordId,
                                                            invoice,
                                                            invoicePayment,
                                                            invoicePaymentRecordId,
                                                            payment,
                                                            refund,
                                                            paymentMethod,
                                                            creationAuditLog,
                                                            tenantRecordId);
        } else if (invoicePayment.getType().equals(InvoicePaymentType.CHARGED_BACK)) {
            return new BusinessInvoicePaymentChargebackModelDao(account,
                                                                accountRecordId,
                                                                invoice,
                                                                invoicePayment,
                                                                invoicePaymentRecordId,
                                                                payment,
                                                                refund,
                                                                paymentMethod,
                                                                creationAuditLog,
                                                                tenantRecordId);
        } else {
            return new BusinessInvoicePaymentModelDao(account,
                                                      accountRecordId,
                                                      invoice,
                                                      invoicePayment,
                                                      invoicePaymentRecordId,
                                                      payment,
                                                      refund,
                                                      paymentMethod,
                                                      creationAuditLog,
                                                      tenantRecordId);
        }
    }

    public BusinessInvoicePaymentBaseModelDao() { /* When reading from the database */ }

    public BusinessInvoicePaymentBaseModelDao(final Long invoicePaymentRecordId,
                                              final UUID invoicePaymentId,
                                              final UUID invoiceId,
                                              final Integer invoiceNumber,
                                              final DateTime invoiceCreatedDate,
                                              final LocalDate invoiceDate,
                                              final LocalDate invoiceTargetDate,
                                              final String invoiceCurrency,
                                              final BigDecimal invoiceBalance,
                                              final BigDecimal invoiceAmountPaid,
                                              final BigDecimal invoiceAmountCharged,
                                              final BigDecimal invoiceOriginalAmountCharged,
                                              final BigDecimal invoiceAmountCredited,
                                              final String invoicePaymentType,
                                              final Long paymentNumber,
                                              final UUID linkedInvoicePaymentId,
                                              final BigDecimal amount,
                                              final String currency,
                                              final DateTime pluginCreatedDate,
                                              final DateTime pluginEffectiveDate,
                                              final String pluginStatus,
                                              final String pluginGatewayError,
                                              final String pluginGatewayErrorCode,
                                              final String pluginFirstReferenceId,
                                              final String pluginSecondReferenceId,
                                              final String pluginPmId,
                                              final Boolean pluginPmIsDefault,
                                              final String pluginPmType,
                                              final String pluginPmCcName,
                                              final String pluginPmCcType,
                                              final String pluginPmCcExpirationMonth,
                                              final String pluginPmCcExpirationYear,
                                              final String pluginPmCcLast4,
                                              final String pluginPmAddress1,
                                              final String pluginPmAddress2,
                                              final String pluginPmCity,
                                              final String pluginPmState,
                                              final String pluginPmZip,
                                              final String pluginPmCountry,
                                              final DateTime createdDate,
                                              final String createdBy,
                                              final String createdReasonCode,
                                              final String createdComments,
                                              final UUID accountId,
                                              final String accountName,
                                              final String accountExternalKey,
                                              final Long accountRecordId,
                                              final Long tenantRecordId) {
        super(createdDate,
              createdBy,
              createdReasonCode,
              createdComments,
              accountId,
              accountName,
              accountExternalKey,
              accountRecordId,
              tenantRecordId);
        this.invoicePaymentRecordId = invoicePaymentRecordId;
        this.invoicePaymentId = invoicePaymentId;
        this.invoiceId = invoiceId;
        this.invoiceNumber = invoiceNumber;
        this.invoiceCreatedDate = invoiceCreatedDate;
        this.invoiceDate = invoiceDate;
        this.invoiceTargetDate = invoiceTargetDate;
        this.invoiceCurrency = invoiceCurrency;
        this.invoiceBalance = invoiceBalance;
        this.invoiceAmountPaid = invoiceAmountPaid;
        this.invoiceAmountCharged = invoiceAmountCharged;
        this.invoiceOriginalAmountCharged = invoiceOriginalAmountCharged;
        this.invoiceAmountCredited = invoiceAmountCredited;
        this.invoicePaymentType = invoicePaymentType;
        this.paymentNumber = paymentNumber;
        this.linkedInvoicePaymentId = linkedInvoicePaymentId;
        this.amount = amount;
        this.currency = currency;
        this.pluginCreatedDate = pluginCreatedDate;
        this.pluginEffectiveDate = pluginEffectiveDate;
        this.pluginStatus = pluginStatus;
        this.pluginGatewayError = pluginGatewayError;
        this.pluginGatewayErrorCode = pluginGatewayErrorCode;
        this.pluginFirstReferenceId = pluginFirstReferenceId;
        this.pluginSecondReferenceId = pluginSecondReferenceId;
        this.pluginPmId = pluginPmId;
        this.pluginPmIsDefault = pluginPmIsDefault;
        this.pluginPmType = pluginPmType;
        this.pluginPmCcName = pluginPmCcName;
        this.pluginPmCcType = pluginPmCcType;
        this.pluginPmCcExpirationMonth = pluginPmCcExpirationMonth;
        this.pluginPmCcExpirationYear = pluginPmCcExpirationYear;
        this.pluginPmCcLast4 = pluginPmCcLast4;
        this.pluginPmAddress1 = pluginPmAddress1;
        this.pluginPmAddress2 = pluginPmAddress2;
        this.pluginPmCity = pluginPmCity;
        this.pluginPmState = pluginPmState;
        this.pluginPmZip = pluginPmZip;
        this.pluginPmCountry = pluginPmCountry;
    }

    protected BusinessInvoicePaymentBaseModelDao(final Account account,
                                                 final Long accountRecordId,
                                                 final Invoice invoice,
                                                 final InvoicePayment invoicePayment,
                                                 final Long invoicePaymentRecordId,
                                                 final Payment payment,
                                                 @Nullable final Refund refund,
                                                 final PaymentMethod paymentMethod,
                                                 final AuditLog creationAuditLog,
                                                 final Long tenantRecordId) {
        this(invoicePaymentRecordId,
             invoicePayment.getId(),
             invoice.getId(),
             invoice.getInvoiceNumber(),
             invoice.getCreatedDate(),
             invoice.getInvoiceDate(),
             invoice.getTargetDate(),
             invoice.getCurrency() == null ? null : invoice.getCurrency().toString(),
             invoice.getBalance(),
             invoice.getPaidAmount(),
             invoice.getChargedAmount(),
             invoice.getOriginalChargedAmount(),
             invoice.getCreditAdjAmount(),
             invoicePayment.getType().toString(),
             payment.getPaymentNumber() == null ? null : payment.getPaymentNumber().longValue(),
             invoicePayment.getLinkedInvoicePaymentId(),
             invoicePayment.getAmount(),
             invoicePayment.getCurrency() == null ? null : invoicePayment.getCurrency().toString(),
             refund != null ? (refund.getPluginDetail() != null ? refund.getPluginDetail().getCreatedDate() : null) : (payment.getPaymentInfoPlugin() != null ? payment.getPaymentInfoPlugin().getCreatedDate() : null),
             refund != null ? (refund.getPluginDetail() != null ? refund.getPluginDetail().getEffectiveDate() : null) : (payment.getPaymentInfoPlugin() != null ? payment.getPaymentInfoPlugin().getEffectiveDate() : null),
             refund != null ? (refund.getPluginDetail() != null ? refund.getPluginDetail().getStatus().toString() : null) : (payment.getPaymentInfoPlugin() != null ? payment.getPaymentInfoPlugin().getStatus().toString() : null),
             refund != null ? (refund.getPluginDetail() != null ? refund.getPluginDetail().getGatewayError() : null) : (payment.getPaymentInfoPlugin() != null ? payment.getPaymentInfoPlugin().getGatewayError() : null),
             refund != null ? (refund.getPluginDetail() != null ? refund.getPluginDetail().getGatewayErrorCode() : null) : (payment.getPaymentInfoPlugin() != null ? payment.getPaymentInfoPlugin().getGatewayErrorCode() : null),
             refund != null ? (refund.getPluginDetail() != null ? refund.getPluginDetail().getReferenceId() : null) : (payment.getPaymentInfoPlugin() != null ? payment.getPaymentInfoPlugin().getFirstPaymentReferenceId() : null),
             refund != null ? null : (payment.getPaymentInfoPlugin() != null ? payment.getPaymentInfoPlugin().getSecondPaymentReferenceId() : null),
             paymentMethod.getPluginDetail() != null ? paymentMethod.getPluginDetail().getExternalPaymentMethodId() : null,
             paymentMethod.getPluginDetail() != null ? paymentMethod.getPluginDetail().isDefaultPaymentMethod() : null,
             paymentMethod.getPluginDetail() != null ? paymentMethod.getPluginDetail().getType() : null,
             paymentMethod.getPluginDetail() != null ? paymentMethod.getPluginDetail().getCCName() : null,
             paymentMethod.getPluginDetail() != null ? paymentMethod.getPluginDetail().getCCType() : null,
             paymentMethod.getPluginDetail() != null ? paymentMethod.getPluginDetail().getCCExprirationMonth() : null,
             paymentMethod.getPluginDetail() != null ? paymentMethod.getPluginDetail().getCCExprirationYear() : null,
             paymentMethod.getPluginDetail() != null ? paymentMethod.getPluginDetail().getCCLast4() : null,
             paymentMethod.getPluginDetail() != null ? paymentMethod.getPluginDetail().getAddress1() : null,
             paymentMethod.getPluginDetail() != null ? paymentMethod.getPluginDetail().getAddress2() : null,
             paymentMethod.getPluginDetail() != null ? paymentMethod.getPluginDetail().getCity() : null,
             paymentMethod.getPluginDetail() != null ? paymentMethod.getPluginDetail().getState() : null,
             paymentMethod.getPluginDetail() != null ? paymentMethod.getPluginDetail().getZip() : null,
             paymentMethod.getPluginDetail() != null ? paymentMethod.getPluginDetail().getCountry() : null,
             invoicePayment.getCreatedDate(),
             creationAuditLog.getUserName(),
             creationAuditLog.getReasonCode(),
             creationAuditLog.getComment(),
             account.getId(),
             account.getName(),
             account.getExternalKey(),
             accountRecordId,
             tenantRecordId);
    }

    public Long getInvoicePaymentRecordId() {
        return invoicePaymentRecordId;
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
        final StringBuilder sb = new StringBuilder();
        sb.append("BusinessInvoicePaymentBaseModelDao");
        sb.append("{invoicePaymentRecordId=").append(invoicePaymentRecordId);
        sb.append(", invoicePaymentId=").append(invoicePaymentId);
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

        final BusinessInvoicePaymentBaseModelDao that = (BusinessInvoicePaymentBaseModelDao) o;

        if (amount != null ? !amount.equals(that.amount) : that.amount != null) {
            return false;
        }
        if (currency != null ? !currency.equals(that.currency) : that.currency != null) {
            return false;
        }
        if (invoiceAmountCharged != null ? !invoiceAmountCharged.equals(that.invoiceAmountCharged) : that.invoiceAmountCharged != null) {
            return false;
        }
        if (invoiceAmountCredited != null ? !invoiceAmountCredited.equals(that.invoiceAmountCredited) : that.invoiceAmountCredited != null) {
            return false;
        }
        if (invoiceAmountPaid != null ? !invoiceAmountPaid.equals(that.invoiceAmountPaid) : that.invoiceAmountPaid != null) {
            return false;
        }
        if (invoiceBalance != null ? !invoiceBalance.equals(that.invoiceBalance) : that.invoiceBalance != null) {
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
        if (invoiceOriginalAmountCharged != null ? !invoiceOriginalAmountCharged.equals(that.invoiceOriginalAmountCharged) : that.invoiceOriginalAmountCharged != null) {
            return false;
        }
        if (invoicePaymentId != null ? !invoicePaymentId.equals(that.invoicePaymentId) : that.invoicePaymentId != null) {
            return false;
        }
        if (invoicePaymentRecordId != null ? !invoicePaymentRecordId.equals(that.invoicePaymentRecordId) : that.invoicePaymentRecordId != null) {
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
        result = 31 * result + (invoicePaymentRecordId != null ? invoicePaymentRecordId.hashCode() : 0);
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
