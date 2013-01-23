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

package com.ning.billing.invoice.dao;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.invoice.api.InvoicePayment.InvoicePaymentType;
import com.ning.billing.util.dao.TableName;
import com.ning.billing.util.entity.EntityBase;
import com.ning.billing.util.entity.dao.EntityModelDao;

public class InvoicePaymentModelDao extends EntityBase implements EntityModelDao<InvoicePayment> {

    private InvoicePaymentType type;
    private UUID invoiceId;
    private UUID paymentId;
    private DateTime paymentDate;
    private BigDecimal amount;
    private Currency currency;
    private UUID paymentCookieId;
    private UUID linkedInvoicePaymentId;

    public InvoicePaymentModelDao() { /* For the DAO mapper */ }

    public InvoicePaymentModelDao(final UUID id, final DateTime createdDate, final InvoicePaymentType type, final UUID invoiceId,
                                  final UUID paymentId, final DateTime paymentDate, final BigDecimal amount, final Currency currency,
                                  final UUID paymentCookieId, final UUID linkedInvoicePaymentId) {
        super(id, createdDate, createdDate);
        this.type = type;
        this.invoiceId = invoiceId;
        this.paymentId = paymentId;
        this.paymentDate = paymentDate;
        this.amount = amount;
        this.currency = currency;
        this.paymentCookieId = paymentCookieId;
        this.linkedInvoicePaymentId = linkedInvoicePaymentId;
    }

    public InvoicePaymentModelDao(final InvoicePayment invoicePayment) {
        this(invoicePayment.getId(), invoicePayment.getCreatedDate(), invoicePayment.getType(), invoicePayment.getInvoiceId(), invoicePayment.getPaymentId(),
             invoicePayment.getPaymentDate(), invoicePayment.getAmount(), invoicePayment.getCurrency(), invoicePayment.getPaymentCookieId(),
             invoicePayment.getLinkedInvoicePaymentId());
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

    public UUID getPaymentCookieId() {
        return paymentCookieId;
    }

    public UUID getLinkedInvoicePaymentId() {
        return linkedInvoicePaymentId;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("InvoicePaymentModelDao");
        sb.append("{type=").append(type);
        sb.append(", invoiceId=").append(invoiceId);
        sb.append(", paymentId=").append(paymentId);
        sb.append(", paymentDate=").append(paymentDate);
        sb.append(", amount=").append(amount);
        sb.append(", currency=").append(currency);
        sb.append(", paymentCookieId=").append(paymentCookieId);
        sb.append(", linkedInvoicePaymentId=").append(linkedInvoicePaymentId);
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
