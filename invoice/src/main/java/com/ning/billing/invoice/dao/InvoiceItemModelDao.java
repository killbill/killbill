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
import org.joda.time.LocalDate;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoiceItemType;
import com.ning.billing.util.dao.TableName;
import com.ning.billing.util.entity.EntityBase;
import com.ning.billing.util.entity.dao.EntityModelDao;

public class InvoiceItemModelDao extends EntityBase implements EntityModelDao<InvoiceItem> {

    private InvoiceItemType type;
    private UUID invoiceId;
    private UUID accountId;
    private UUID bundleId;
    private UUID subscriptionId;
    private String planName;
    private String phaseName;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal amount;
    private BigDecimal rate;
    private Currency currency;
    private UUID linkedItemId;

    public InvoiceItemModelDao() { /* For the DAO mapper */ }

    public InvoiceItemModelDao(final UUID id, final DateTime createdDate, final InvoiceItemType type, final UUID invoiceId,
                               final UUID accountId, final UUID bundleId, final UUID subscriptionId, final String planName,
                               final String phaseName, final LocalDate startDate, final LocalDate endDate, final BigDecimal amount,
                               final BigDecimal rate, final Currency currency, final UUID linkedItemId) {
        super(id, createdDate, createdDate);
        this.type = type;
        this.invoiceId = invoiceId;
        this.accountId = accountId;
        this.bundleId = bundleId;
        this.subscriptionId = subscriptionId;
        this.planName = planName;
        this.phaseName = phaseName;
        this.startDate = startDate;
        this.endDate = endDate;
        this.amount = amount;
        this.rate = rate;
        this.currency = currency;
        this.linkedItemId = linkedItemId;
    }

    public InvoiceItemModelDao(final DateTime createdDate, final InvoiceItemType type, final UUID invoiceId, final UUID accountId,
                               final UUID bundleId, final UUID subscriptionId, final String planName,
                               final String phaseName, final LocalDate startDate, final LocalDate endDate, final BigDecimal amount,
                               final BigDecimal rate, final Currency currency, final UUID linkedItemId) {
        this(UUID.randomUUID(), createdDate, type, invoiceId, accountId, bundleId, subscriptionId, planName, phaseName,
             startDate, endDate, amount, rate, currency, linkedItemId);
    }

    public InvoiceItemModelDao(final InvoiceItem invoiceItem) {
        this(invoiceItem.getId(), invoiceItem.getCreatedDate(), invoiceItem.getInvoiceItemType(), invoiceItem.getInvoiceId(), invoiceItem.getAccountId(), invoiceItem.getBundleId(),
             invoiceItem.getSubscriptionId(), invoiceItem.getPlanName(), invoiceItem.getPhaseName(), invoiceItem.getStartDate(), invoiceItem.getEndDate(),
             invoiceItem.getAmount(), invoiceItem.getRate(), invoiceItem.getCurrency(), invoiceItem.getLinkedItemId());
    }

    public InvoiceItemType getType() {
        return type;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public UUID getBundleId() {
        return bundleId;
    }

    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    public String getPlanName() {
        return planName;
    }

    public String getPhaseName() {
        return phaseName;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public Currency getCurrency() {
        return currency;
    }

    public UUID getLinkedItemId() {
        return linkedItemId;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("InvoiceItemModelDao");
        sb.append("{type=").append(type);
        sb.append(", invoiceId=").append(invoiceId);
        sb.append(", accountId=").append(accountId);
        sb.append(", bundleId=").append(bundleId);
        sb.append(", subscriptionId=").append(subscriptionId);
        sb.append(", planName='").append(planName).append('\'');
        sb.append(", phaseName='").append(phaseName).append('\'');
        sb.append(", startDate=").append(startDate);
        sb.append(", endDate=").append(endDate);
        sb.append(", amount=").append(amount);
        sb.append(", rate=").append(rate);
        sb.append(", currency=").append(currency);
        sb.append(", linkedItemId=").append(linkedItemId);
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

        final InvoiceItemModelDao that = (InvoiceItemModelDao) o;

        if (accountId != null ? !accountId.equals(that.accountId) : that.accountId != null) {
            return false;
        }
        if (amount != null ? amount.compareTo(that.amount) != 0 : that.amount != null) {
            return false;
        }
        if (bundleId != null ? !bundleId.equals(that.bundleId) : that.bundleId != null) {
            return false;
        }
        if (currency != that.currency) {
            return false;
        }
        if (endDate != null ? !endDate.equals(that.endDate) : that.endDate != null) {
            return false;
        }
        if (invoiceId != null ? !invoiceId.equals(that.invoiceId) : that.invoiceId != null) {
            return false;
        }
        if (linkedItemId != null ? !linkedItemId.equals(that.linkedItemId) : that.linkedItemId != null) {
            return false;
        }
        if (phaseName != null ? !phaseName.equals(that.phaseName) : that.phaseName != null) {
            return false;
        }
        if (planName != null ? !planName.equals(that.planName) : that.planName != null) {
            return false;
        }
        if (rate != null ? rate.compareTo(that.rate) != 0 : that.rate != null) {
            return false;
        }
        if (startDate != null ? !startDate.equals(that.startDate) : that.startDate != null) {
            return false;
        }
        if (subscriptionId != null ? !subscriptionId.equals(that.subscriptionId) : that.subscriptionId != null) {
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
        result = 31 * result + (accountId != null ? accountId.hashCode() : 0);
        result = 31 * result + (bundleId != null ? bundleId.hashCode() : 0);
        result = 31 * result + (subscriptionId != null ? subscriptionId.hashCode() : 0);
        result = 31 * result + (planName != null ? planName.hashCode() : 0);
        result = 31 * result + (phaseName != null ? phaseName.hashCode() : 0);
        result = 31 * result + (startDate != null ? startDate.hashCode() : 0);
        result = 31 * result + (endDate != null ? endDate.hashCode() : 0);
        result = 31 * result + (amount != null ? amount.hashCode() : 0);
        result = 31 * result + (rate != null ? rate.hashCode() : 0);
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        result = 31 * result + (linkedItemId != null ? linkedItemId.hashCode() : 0);
        return result;
    }

    @Override
    public TableName getTableName() {
        return TableName.INVOICE_ITEMS;
    }

    @Override
    public TableName getHistoryTableName() {
        return null;
    }
}
