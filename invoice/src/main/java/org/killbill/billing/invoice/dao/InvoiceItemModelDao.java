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

package org.killbill.billing.invoice.dao;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.util.UUIDs;
import org.killbill.billing.util.dao.TableName;
import org.killbill.billing.util.entity.dao.EntityModelDao;
import org.killbill.billing.util.entity.dao.EntityModelDaoBase;

public class InvoiceItemModelDao extends EntityModelDaoBase implements EntityModelDao<InvoiceItem> {

    private InvoiceItemType type;
    private UUID invoiceId;
    private UUID accountId;
    private UUID childAccountId;
    private UUID bundleId;
    private UUID subscriptionId;
    private String description;
    private String productName;
    private String planName;
    private String phaseName;
    private String usageName;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal amount;
    private BigDecimal rate;
    private Currency currency;
    private UUID linkedItemId;
    private Integer quantity;
    private String itemDetails;

    public InvoiceItemModelDao() { /* For the DAO mapper */ }

    public InvoiceItemModelDao(final UUID id, final DateTime createdDate, final InvoiceItemType type, final UUID invoiceId, final UUID accountId,
                               final UUID childAccountId, final UUID bundleId, final UUID subscriptionId, final String description, final String productName,
                               final String planName, final String phaseName, final String usageName, final LocalDate startDate, final LocalDate endDate,
                               final BigDecimal amount, final BigDecimal rate, final Currency currency, final UUID linkedItemId, final Integer quantity,
                               final String itemDetails) {


        super(id, createdDate, createdDate);
        this.type = type;
        this.invoiceId = invoiceId;
        this.accountId = accountId;
        this.childAccountId = childAccountId;
        this.bundleId = bundleId;
        this.subscriptionId = subscriptionId;
        this.description = description;
        this.productName = productName;
        this.planName = planName;
        this.phaseName = phaseName;
        this.usageName = usageName;
        this.startDate = startDate;
        this.endDate = endDate;
        this.amount = amount;
        this.rate = rate;
        this.currency = currency;
        this.linkedItemId = linkedItemId;
        this.quantity = quantity;
        this.itemDetails = itemDetails;
    }

    public InvoiceItemModelDao(final UUID id, final DateTime createdDate, final InvoiceItemType type, final UUID invoiceId, final UUID accountId,
                               final UUID childAccountId, final UUID bundleId, final UUID subscriptionId, final String description, final String productName, final String planName,
                               final String phaseName, final String usageName, final LocalDate startDate, final LocalDate endDate, final BigDecimal amount,
                               final BigDecimal rate, final Currency currency, final UUID linkedItemId) {
        this(id, createdDate, type, invoiceId, accountId, childAccountId, bundleId, subscriptionId, description, productName, planName, phaseName, usageName,
             startDate, endDate, amount, rate, currency, linkedItemId, null, null);

    }

    public InvoiceItemModelDao(final DateTime createdDate, final InvoiceItemType type, final UUID invoiceId, final UUID accountId,
                               final UUID bundleId, final UUID subscriptionId, final String description, final String productName, final String planName,
                               final String phaseName, final String usageName, final LocalDate startDate, final LocalDate endDate, final BigDecimal amount,
                               final BigDecimal rate, final Currency currency, final UUID linkedItemId, final Integer quantity, final String itemDetails) {
        this(UUIDs.randomUUID(), createdDate, type, invoiceId, accountId, null, bundleId, subscriptionId, description, productName, planName, phaseName, usageName,
             startDate, endDate, amount, rate, currency, linkedItemId, quantity, itemDetails);
    }

    public InvoiceItemModelDao(final DateTime createdDate, final InvoiceItemType type, final UUID invoiceId, final UUID accountId,
                               final UUID bundleId, final UUID subscriptionId, final String description, final String productName, final String planName,
                               final String phaseName, final String usageName, final LocalDate startDate, final LocalDate endDate, final BigDecimal amount,
                               final BigDecimal rate, final Currency currency, final UUID linkedItemId) {
        this(UUIDs.randomUUID(), createdDate, type, invoiceId, accountId, null, bundleId, subscriptionId, description, productName, planName, phaseName, usageName,
             startDate, endDate, amount, rate, currency, linkedItemId, null, null);
    }

    public InvoiceItemModelDao(final InvoiceItem invoiceItem) {
        this(invoiceItem.getId(), invoiceItem.getCreatedDate(), invoiceItem.getInvoiceItemType(), invoiceItem.getInvoiceId(), invoiceItem.getAccountId(), invoiceItem.getChildAccountId(), invoiceItem.getBundleId(),
             invoiceItem.getSubscriptionId(), invoiceItem.getDescription(), invoiceItem.getProductName(), invoiceItem.getPlanName(), invoiceItem.getPhaseName(), invoiceItem.getUsageName(), invoiceItem.getStartDate(), invoiceItem.getEndDate(),
             invoiceItem.getAmount(), invoiceItem.getRate(), invoiceItem.getCurrency(), invoiceItem.getLinkedItemId(), invoiceItem.getQuantity(), invoiceItem.getItemDetails());
    }

    public InvoiceItemType getType() {
        return type;
    }

    public void setType(final InvoiceItemType type) {
        this.type = type;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(final UUID invoiceId) {
        this.invoiceId = invoiceId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public void setAccountId(final UUID accountId) {
        this.accountId = accountId;
    }

    public UUID getChildAccountId() {
        return childAccountId;
    }

    public void setChildAccountId(final UUID childAccountId) {
        this.childAccountId = childAccountId;
    }

    public UUID getBundleId() {
        return bundleId;
    }

    public void setBundleId(final UUID bundleId) {
        this.bundleId = bundleId;
    }

    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    public void setSubscriptionId(final UUID subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(final String productName) {
        this.productName = productName;
    }

    public String getPlanName() {
        return planName;
    }

    public void setPlanName(final String planName) {
        this.planName = planName;
    }

    public String getPhaseName() {
        return phaseName;
    }

    public void setPhaseName(final String phaseName) {
        this.phaseName = phaseName;
    }

    public String getUsageName() {
        return usageName;
    }

    public void setUsageName(final String usageName) {
        this.usageName = usageName;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(final LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(final LocalDate endDate) {
        this.endDate = endDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(final BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public void setRate(final BigDecimal rate) {
        this.rate = rate;
    }

    public Currency getCurrency() {
        return currency;
    }

    public void setCurrency(final Currency currency) {
        this.currency = currency;
    }

    public UUID getLinkedItemId() {
        return linkedItemId;
    }

    public void setLinkedItemId(final UUID linkedItemId) {
        this.linkedItemId = linkedItemId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(final Integer quantity) {
        this.quantity = quantity;
    }

    public String getItemDetails() {
        return itemDetails;
    }

    public void setItemDetails(final String itemDetails) {
        this.itemDetails = itemDetails;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("InvoiceItemModelDao{");
        sb.append("type=").append(type);
        sb.append(", invoiceId=").append(invoiceId);
        sb.append(", accountId=").append(accountId);
        sb.append(", childAccountId=").append(childAccountId);
        sb.append(", bundleId=").append(bundleId);
        sb.append(", subscriptionId=").append(subscriptionId);
        sb.append(", description='").append(description).append('\'');
        sb.append(", productName='").append(productName).append('\'');
        sb.append(", planName='").append(planName).append('\'');
        sb.append(", phaseName='").append(phaseName).append('\'');
        sb.append(", usageName='").append(usageName).append('\'');
        sb.append(", startDate=").append(startDate);
        sb.append(", endDate=").append(endDate);
        sb.append(", amount=").append(amount);
        sb.append(", rate=").append(rate);
        sb.append(", currency=").append(currency);
        sb.append(", linkedItemId=").append(linkedItemId);
        sb.append(", quantity=").append(quantity);
        sb.append(", itemDetails=").append(itemDetails);
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
        if (childAccountId != null ? !childAccountId.equals(that.childAccountId) : that.childAccountId != null) {
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
        if (description != null ? !description.equals(that.description) : that.description != null) {
            return false;
        }
        if (endDate != null ? endDate.compareTo(that.endDate) != 0 : that.endDate != null) {
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
        if (productName != null ? !productName.equals(that.productName) : that.productName != null) {
            return false;
        }
        if (usageName != null ? !usageName.equals(that.usageName) : that.usageName != null) {
            return false;
        }
        if (rate != null ? rate.compareTo(that.rate) != 0 : that.rate != null) {
            return false;
        }
        if (startDate != null ? startDate.compareTo(that.startDate) != 0 : that.startDate != null) {
            return false;
        }
        if (subscriptionId != null ? !subscriptionId.equals(that.subscriptionId) : that.subscriptionId != null) {
            return false;
        }
        if (type != that.type) {
            return false;
        }
        if (quantity != null ? !quantity.equals(that.quantity) : that.quantity != null) {
            return false;
        }
        if (itemDetails != null ? !itemDetails.equals(that.itemDetails) : that.itemDetails != null) {
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
        result = 31 * result + (childAccountId != null ? childAccountId.hashCode() : 0);
        result = 31 * result + (bundleId != null ? bundleId.hashCode() : 0);
        result = 31 * result + (subscriptionId != null ? subscriptionId.hashCode() : 0);
        result = 31 * result + (description != null ? description.hashCode() : 0);
        result = 31 * result + (productName != null ? productName.hashCode() : 0);
        result = 31 * result + (planName != null ? planName.hashCode() : 0);
        result = 31 * result + (phaseName != null ? phaseName.hashCode() : 0);
        result = 31 * result + (usageName != null ? usageName.hashCode() : 0);
        result = 31 * result + (startDate != null ? startDate.hashCode() : 0);
        result = 31 * result + (endDate != null ? endDate.hashCode() : 0);
        result = 31 * result + (amount != null ? amount.hashCode() : 0);
        result = 31 * result + (rate != null ? rate.hashCode() : 0);
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        result = 31 * result + (linkedItemId != null ? linkedItemId.hashCode() : 0);
        result = 31 * result + (quantity != null ? quantity.hashCode() : 0);
        result = 31 * result + (itemDetails != null ? itemDetails.hashCode() : 0);
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
