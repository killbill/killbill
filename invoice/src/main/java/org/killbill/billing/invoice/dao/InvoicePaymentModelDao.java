/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

package org.killbill.billing.invoice.dao;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTime;

import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.InvoicePayment;
import org.killbill.billing.invoice.api.InvoicePaymentType;
import org.killbill.billing.util.dao.TableName;
import org.killbill.billing.entity.EntityBase;
import org.killbill.billing.util.entity.dao.EntityModelDao;
import org.killbill.billing.util.entity.dao.EntityModelDaoBase;

public class InvoicePaymentModelDao extends EntityModelDaoBase implements EntityModelDao<InvoicePayment> {

    private InvoicePaymentType type;
    private UUID invoiceId;
    private UUID paymentId;
    private DateTime paymentDate;
    private BigDecimal amount;
    private Currency currency;
    private Currency processedCurrency;
    private String paymentCookieId;
    private UUID linkedInvoicePaymentId;
    private Boolean success;

    public InvoicePaymentModelDao() { /* For the DAO mapper */ }

    public InvoicePaymentModelDao(final UUID id, final DateTime createdDate, final InvoicePaymentType type, final UUID invoiceId,
                                  final UUID paymentId, final DateTime paymentDate, final BigDecimal amount, final Currency currency,
                                  final Currency processedCurrency, final String paymentCookieId, final UUID linkedInvoicePaymentId, final Boolean success) {
        super(id, createdDate, createdDate);
        this.type = type;
        this.invoiceId = invoiceId;
        this.paymentId = paymentId;
        this.paymentDate = paymentDate;
        this.amount = amount;
        this.currency = currency;
        this.processedCurrency = processedCurrency;
        this.paymentCookieId = paymentCookieId;
        this.linkedInvoicePaymentId = linkedInvoicePaymentId;
        this.success = success;
    }

    public InvoicePaymentModelDao(final InvoicePayment invoicePayment) {
        this(invoicePayment.getId(), invoicePayment.getCreatedDate(), invoicePayment.getType(), invoicePayment.getInvoiceId(), invoicePayment.getPaymentId(),
             invoicePayment.getPaymentDate(), invoicePayment.getAmount(), invoicePayment.getCurrency(), invoicePayment.getProcessedCurrency(), invoicePayment.getPaymentCookieId(),
             invoicePayment.getLinkedInvoicePaymentId(), invoicePayment.isSuccess());
    }

    public InvoicePaymentType getType() {
        return type;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public DateTime getPaymentDate() {
        return paymentDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Currency getCurrency() {
        return currency;
    }

    public Currency getProcessedCurrency() {
        return processedCurrency;
    }

    public String getPaymentCookieId() {
        return paymentCookieId;
    }

    public UUID getLinkedInvoicePaymentId() {
        return linkedInvoicePaymentId;
    }

    public void setType(final InvoicePaymentType type) {
        this.type = type;
    }

    public void setInvoiceId(final UUID invoiceId) {
        this.invoiceId = invoiceId;
    }

    public void setPaymentId(final UUID paymentId) {
        this.paymentId = paymentId;
    }

    public void setPaymentDate(final DateTime paymentDate) {
        this.paymentDate = paymentDate;
    }

    public void setAmount(final BigDecimal amount) {
        this.amount = amount;
    }

    public void setCurrency(final Currency currency) {
        this.currency = currency;
    }

    public void setProcessedCurrency(final Currency processedCurrency) {
        this.processedCurrency = processedCurrency;
    }

    public void setPaymentCookieId(final String paymentCookieId) {
        this.paymentCookieId = paymentCookieId;
    }

    public void setLinkedInvoicePaymentId(final UUID linkedInvoicePaymentId) {
        this.linkedInvoicePaymentId = linkedInvoicePaymentId;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(final Boolean success) {
        this.success = success;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("InvoicePaymentModelDao{");
        sb.append("type=").append(type);
        sb.append(", invoiceId=").append(invoiceId);
        sb.append(", paymentId=").append(paymentId);
        sb.append(", paymentDate=").append(paymentDate);
        sb.append(", amount=").append(amount);
        sb.append(", currency=").append(currency);
        sb.append(", processedCurrency=").append(processedCurrency);
        sb.append(", paymentCookieId='").append(paymentCookieId).append('\'');
        sb.append(", linkedInvoicePaymentId=").append(linkedInvoicePaymentId);
        sb.append(", success=").append(success);
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

        final InvoicePaymentModelDao that = (InvoicePaymentModelDao) o;

        if (amount != null ? !amount.equals(that.amount) : that.amount != null) {
            return false;
        }
        if (currency != that.currency) {
            return false;
        }
        if (invoiceId != null ? !invoiceId.equals(that.invoiceId) : that.invoiceId != null) {
            return false;
        }
        if (linkedInvoicePaymentId != null ? !linkedInvoicePaymentId.equals(that.linkedInvoicePaymentId) : that.linkedInvoicePaymentId != null) {
            return false;
        }
        if (paymentCookieId != null ? !paymentCookieId.equals(that.paymentCookieId) : that.paymentCookieId != null) {
            return false;
        }
        if (paymentDate != null ? !paymentDate.equals(that.paymentDate) : that.paymentDate != null) {
            return false;
        }
        if (paymentId != null ? !paymentId.equals(that.paymentId) : that.paymentId != null) {
            return false;
        }
        if (type != that.type) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (type != null ? type.hashCode() : 0);
        result = 31 * result + (invoiceId != null ? invoiceId.hashCode() : 0);
        result = 31 * result + (paymentId != null ? paymentId.hashCode() : 0);
        result = 31 * result + (paymentDate != null ? paymentDate.hashCode() : 0);
        result = 31 * result + (amount != null ? amount.hashCode() : 0);
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        result = 31 * result + (paymentCookieId != null ? paymentCookieId.hashCode() : 0);
        result = 31 * result + (linkedInvoicePaymentId != null ? linkedInvoicePaymentId.hashCode() : 0);
        return result;
    }

    @Override
    public TableName getTableName() {
        return TableName.INVOICE_PAYMENTS;
    }

    @Override
    public TableName getHistoryTableName() {
        return null;
    }
}
