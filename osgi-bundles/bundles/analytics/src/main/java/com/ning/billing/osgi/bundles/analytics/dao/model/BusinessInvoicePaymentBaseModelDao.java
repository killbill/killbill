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

import org.joda.time.DateTime;
import org.joda.time.LocalDate;

import com.ning.billing.account.api.Account;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.invoice.api.InvoicePayment.InvoicePaymentType;
import com.ning.billing.payment.api.Payment;
import com.ning.billing.payment.api.PaymentMethod;
import com.ning.billing.util.audit.AuditLog;

public abstract class BusinessInvoicePaymentBaseModelDao extends BusinessModelDaoBase {

    protected static final String INVOICE_PAYMENTS_TABLE_NAME = "bip";
    protected static final String INVOICE_PAYMENT_REFUNDS_TABLE_NAME = "bipr";
    protected static final String INVOICE_PAYMENT_CHARGEBACKS_TABLE_NAME = "bipc";

    public static final String[] ALL_INVOICE_PAYMENTS_TABLE_NAMES = new String[]{INVOICE_PAYMENTS_TABLE_NAME, INVOICE_PAYMENT_REFUNDS_TABLE_NAME, INVOICE_PAYMENT_CHARGEBACKS_TABLE_NAME};

    private final Long invoicePaymentRecordId;
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

    public static BusinessInvoicePaymentBaseModelDao create(final Account account,
                                                            final Long accountRecordId,
                                                            final Invoice invoice,
                                                            final InvoicePayment invoicePayment,
                                                            final Long invoicePaymentRecordId,
                                                            final Payment payment,
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
                                                      paymentMethod,
                                                      creationAuditLog,
                                                      tenantRecordId);
        }
    }

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
    }

    protected BusinessInvoicePaymentBaseModelDao(final Account account,
                                                 final Long accountRecordId,
                                                 final Invoice invoice,
                                                 final InvoicePayment invoicePayment,
                                                 final Long invoicePaymentRecordId,
                                                 final Payment payment,
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
             null /* TODO */,
             invoicePayment.getLinkedInvoicePaymentId(),
             invoicePayment.getAmount(),
             invoicePayment.getCurrency() == null ? null : invoicePayment.getCurrency().toString(),
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
        sb.append(", currency=").append(currency);
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
        if (currency != that.currency) {
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
        return result;
    }
}
