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
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.joda.time.LocalDate;

import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoiceItemBaseModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoiceModelDao;

public class BusinessInvoice extends BusinessEntityBase {

    private final UUID invoiceId;
    private final Integer invoiceNumber;
    private final LocalDate invoiceDate;
    private final LocalDate targetDate;
    private final String currency;
    private final BigDecimal balance;
    private final BigDecimal amountPaid;
    private final BigDecimal amountCharged;
    private final BigDecimal originalAmountCharged;
    private final BigDecimal amountCredited;
    private final List<BusinessInvoiceItem> invoiceItems = new LinkedList<BusinessInvoiceItem>();

    public BusinessInvoice(final BusinessInvoiceModelDao businessInvoiceModelDao,
                           final Collection<BusinessInvoiceItemBaseModelDao> businessInvoiceItemModelDaos) {
        super(businessInvoiceModelDao.getCreatedDate(),
              businessInvoiceModelDao.getCreatedBy(),
              businessInvoiceModelDao.getCreatedReasonCode(),
              businessInvoiceModelDao.getCreatedComments(),
              businessInvoiceModelDao.getAccountId(),
              businessInvoiceModelDao.getAccountName(),
              businessInvoiceModelDao.getAccountExternalKey(),
              businessInvoiceModelDao.getReportGroup());
        this.invoiceId = businessInvoiceModelDao.getInvoiceId();
        this.invoiceNumber = businessInvoiceModelDao.getInvoiceNumber();
        this.invoiceDate = businessInvoiceModelDao.getInvoiceDate();
        this.targetDate = businessInvoiceModelDao.getTargetDate();
        this.currency = businessInvoiceModelDao.getCurrency();
        this.balance = businessInvoiceModelDao.getBalance();
        this.amountPaid = businessInvoiceModelDao.getAmountPaid();
        this.amountCharged = businessInvoiceModelDao.getAmountCharged();
        this.originalAmountCharged = businessInvoiceModelDao.getOriginalAmountCharged();
        this.amountCredited = businessInvoiceModelDao.getAmountCredited();
        for (final BusinessInvoiceItemBaseModelDao businessInvoiceItemModelDao : businessInvoiceItemModelDaos) {
            invoiceItems.add(new BusinessInvoiceItem(businessInvoiceItemModelDao));
        }
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public Integer getInvoiceNumber() {
        return invoiceNumber;
    }

    public LocalDate getInvoiceDate() {
        return invoiceDate;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public BigDecimal getAmountPaid() {
        return amountPaid;
    }

    public BigDecimal getAmountCharged() {
        return amountCharged;
    }

    public BigDecimal getOriginalAmountCharged() {
        return originalAmountCharged;
    }

    public BigDecimal getAmountCredited() {
        return amountCredited;
    }

    public List<BusinessInvoiceItem> getInvoiceItems() {
        return invoiceItems;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("BusinessInvoice");
        sb.append("{invoiceId=").append(invoiceId);
        sb.append(", invoiceNumber=").append(invoiceNumber);
        sb.append(", invoiceDate=").append(invoiceDate);
        sb.append(", targetDate=").append(targetDate);
        sb.append(", currency='").append(currency).append('\'');
        sb.append(", balance=").append(balance);
        sb.append(", amountPaid=").append(amountPaid);
        sb.append(", amountCharged=").append(amountCharged);
        sb.append(", originalAmountCharged=").append(originalAmountCharged);
        sb.append(", amountCredited=").append(amountCredited);
        sb.append(", invoiceItems=").append(invoiceItems);
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

        final BusinessInvoice that = (BusinessInvoice) o;

        if (amountCharged != null ? !(amountCharged.compareTo(that.amountCharged) == 0) : that.amountCharged != null) {
            return false;
        }
        if (amountCredited != null ? !(amountCredited.compareTo(that.amountCredited) == 0) : that.amountCredited != null) {
            return false;
        }
        if (amountPaid != null ? !(amountPaid.compareTo(that.amountPaid) == 0) : that.amountPaid != null) {
            return false;
        }
        if (balance != null ? !(balance.compareTo(that.balance) == 0) : that.balance != null) {
            return false;
        }
        if (currency != null ? !currency.equals(that.currency) : that.currency != null) {
            return false;
        }
        if (invoiceDate != null ? !invoiceDate.equals(that.invoiceDate) : that.invoiceDate != null) {
            return false;
        }
        if (invoiceId != null ? !invoiceId.equals(that.invoiceId) : that.invoiceId != null) {
            return false;
        }
        if (invoiceItems != null ? !invoiceItems.equals(that.invoiceItems) : that.invoiceItems != null) {
            return false;
        }
        if (invoiceNumber != null ? !invoiceNumber.equals(that.invoiceNumber) : that.invoiceNumber != null) {
            return false;
        }
        if (originalAmountCharged != null ? !(originalAmountCharged.compareTo(that.originalAmountCharged) == 0) : that.originalAmountCharged != null) {
            return false;
        }
        if (targetDate != null ? !targetDate.equals(that.targetDate) : that.targetDate != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (invoiceId != null ? invoiceId.hashCode() : 0);
        result = 31 * result + (invoiceNumber != null ? invoiceNumber.hashCode() : 0);
        result = 31 * result + (invoiceDate != null ? invoiceDate.hashCode() : 0);
        result = 31 * result + (targetDate != null ? targetDate.hashCode() : 0);
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        result = 31 * result + (balance != null ? balance.hashCode() : 0);
        result = 31 * result + (amountPaid != null ? amountPaid.hashCode() : 0);
        result = 31 * result + (amountCharged != null ? amountCharged.hashCode() : 0);
        result = 31 * result + (originalAmountCharged != null ? originalAmountCharged.hashCode() : 0);
        result = 31 * result + (amountCredited != null ? amountCredited.hashCode() : 0);
        result = 31 * result + (invoiceItems != null ? invoiceItems.hashCode() : 0);
        return result;
    }
}
