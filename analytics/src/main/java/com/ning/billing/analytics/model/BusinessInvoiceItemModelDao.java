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

package com.ning.billing.analytics.model;

import java.math.BigDecimal;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;

import com.ning.billing.analytics.utils.Rounder;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.util.entity.EntityBase;

public class BusinessInvoiceItemModelDao extends EntityBase {

    private final UUID itemId;
    private final UUID invoiceId;
    private final String itemType;
    private final String externalKey;
    private final String productName;
    private final String productType;
    private final String productCategory;
    private final String slug;
    private final String phase;
    private final String billingPeriod;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final BigDecimal amount;
    private final Currency currency;
    private final UUID linkedItemId;

    public BusinessInvoiceItemModelDao(final BigDecimal amount, @Nullable final String billingPeriod, final DateTime createdDate,
                                       final Currency currency, final LocalDate endDate, final String externalKey,
                                       final UUID invoiceId, final UUID itemId, @Nullable final UUID linkedItemId, final String itemType,
                                       @Nullable final String phase, @Nullable final String productCategory, @Nullable final String productName,
                                       @Nullable final String productType, @Nullable final String slug, final LocalDate startDate, final DateTime updatedDate) {
        super(itemId, createdDate, updatedDate);
        this.amount = amount;
        this.billingPeriod = billingPeriod;
        this.currency = currency;
        this.endDate = endDate;
        this.externalKey = externalKey;
        this.invoiceId = invoiceId;
        this.itemId = itemId;
        this.linkedItemId = linkedItemId;
        this.itemType = itemType;
        this.phase = phase;
        this.productCategory = productCategory;
        this.productName = productName;
        this.productType = productType;
        this.slug = slug;
        this.startDate = startDate;
    }

    public BusinessInvoiceItemModelDao(@Nullable final String externalKey, final InvoiceItem invoiceItem, @Nullable final Plan plan, @Nullable final PlanPhase planPhase) {
        this(invoiceItem.getAmount(), planPhase != null ? planPhase.getBillingPeriod().toString() : null, invoiceItem.getCreatedDate(), invoiceItem.getCurrency(),
             /* Populate end date for fixed items for convenience (null in invoice_items table) */
             (invoiceItem.getEndDate() == null && planPhase != null) ? invoiceItem.getStartDate().plus(planPhase.getDuration().toJodaPeriod()) : invoiceItem.getEndDate(),
             externalKey, invoiceItem.getInvoiceId(), invoiceItem.getId(), invoiceItem.getLinkedItemId(), invoiceItem.getInvoiceItemType().toString(),
             planPhase != null ? planPhase.getPhaseType().toString() : null, plan != null ? plan.getProduct().getCategory().toString() : null,
             plan != null ? plan.getProduct().getName() : null, plan != null ? plan.getProduct().getCatalogName() : null,
             planPhase != null ? planPhase.getName() : null, invoiceItem.getStartDate(), invoiceItem.getUpdatedDate());
    }

    public UUID getItemId() {
        return itemId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getBillingPeriod() {
        return billingPeriod;
    }

    public Currency getCurrency() {
        return currency;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public String getExternalKey() {
        return externalKey;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public String getItemType() {
        return itemType;
    }

    public UUID getLinkedItemId() {
        return linkedItemId;
    }

    public String getPhase() {
        return phase;
    }

    public String getProductCategory() {
        return productCategory;
    }

    public String getProductName() {
        return productName;
    }

    public String getProductType() {
        return productType;
    }

    public String getSlug() {
        return slug;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("BusinessInvoiceItemModelDao");
        sb.append("{amount=").append(amount);
        sb.append(", itemId=").append(itemId);
        sb.append(", createdDate=").append(createdDate);
        sb.append(", updatedDate=").append(updatedDate);
        sb.append(", invoiceId=").append(invoiceId);
        sb.append(", itemType='").append(itemType).append('\'');
        sb.append(", externalKey='").append(externalKey).append('\'');
        sb.append(", productName='").append(productName).append('\'');
        sb.append(", productType='").append(productType).append('\'');
        sb.append(", productCategory='").append(productCategory).append('\'');
        sb.append(", slug='").append(slug).append('\'');
        sb.append(", phase='").append(phase).append('\'');
        sb.append(", billingPeriod='").append(billingPeriod).append('\'');
        sb.append(", startDate=").append(startDate);
        sb.append(", endDate=").append(endDate);
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

        final BusinessInvoiceItemModelDao that = (BusinessInvoiceItemModelDao) o;

        if (amount != null ? Rounder.round(amount) != (Rounder.round(that.amount)) : that.amount != null) {
            return false;
        }
        if (billingPeriod != null ? !billingPeriod.equals(that.billingPeriod) : that.billingPeriod != null) {
            return false;
        }
        if (createdDate != null ? !createdDate.equals(that.createdDate) : that.createdDate != null) {
            return false;
        }
        if (currency != that.currency) {
            return false;
        }
        if (endDate != null ? !endDate.equals(that.endDate) : that.endDate != null) {
            return false;
        }
        if (externalKey != null ? !externalKey.equals(that.externalKey) : that.externalKey != null) {
            return false;
        }
        if (invoiceId != null ? !invoiceId.equals(that.invoiceId) : that.invoiceId != null) {
            return false;
        }
        if (itemId != null ? !itemId.equals(that.itemId) : that.itemId != null) {
            return false;
        }
        if (itemType != null ? !itemType.equals(that.itemType) : that.itemType != null) {
            return false;
        }
        if (linkedItemId != null ? !linkedItemId.equals(that.linkedItemId) : that.linkedItemId != null) {
            return false;
        }
        if (phase != null ? !phase.equals(that.phase) : that.phase != null) {
            return false;
        }
        if (productCategory != null ? !productCategory.equals(that.productCategory) : that.productCategory != null) {
            return false;
        }
        if (productName != null ? !productName.equals(that.productName) : that.productName != null) {
            return false;
        }
        if (productType != null ? !productType.equals(that.productType) : that.productType != null) {
            return false;
        }
        if (slug != null ? !slug.equals(that.slug) : that.slug != null) {
            return false;
        }
        if (startDate != null ? !startDate.equals(that.startDate) : that.startDate != null) {
            return false;
        }
        if (updatedDate != null ? !updatedDate.equals(that.updatedDate) : that.updatedDate != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = itemId != null ? itemId.hashCode() : 0;
        result = 31 * result + (createdDate != null ? createdDate.hashCode() : 0);
        result = 31 * result + (updatedDate != null ? updatedDate.hashCode() : 0);
        result = 31 * result + (invoiceId != null ? invoiceId.hashCode() : 0);
        result = 31 * result + (itemType != null ? itemType.hashCode() : 0);
        result = 31 * result + (externalKey != null ? externalKey.hashCode() : 0);
        result = 31 * result + (productName != null ? productName.hashCode() : 0);
        result = 31 * result + (productType != null ? productType.hashCode() : 0);
        result = 31 * result + (productCategory != null ? productCategory.hashCode() : 0);
        result = 31 * result + (slug != null ? slug.hashCode() : 0);
        result = 31 * result + (phase != null ? phase.hashCode() : 0);
        result = 31 * result + (billingPeriod != null ? billingPeriod.hashCode() : 0);
        result = 31 * result + (startDate != null ? startDate.hashCode() : 0);
        result = 31 * result + (endDate != null ? endDate.hashCode() : 0);
        result = 31 * result + (amount != null ? amount.hashCode() : 0);
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        result = 31 * result + (linkedItemId != null ? linkedItemId.hashCode() : 0);
        return result;
    }
}
