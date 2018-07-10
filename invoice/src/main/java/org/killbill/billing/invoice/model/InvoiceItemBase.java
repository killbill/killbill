/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

package org.killbill.billing.invoice.model;

import java.math.BigDecimal;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.entity.EntityBase;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.util.currency.KillBillMoney;

public abstract class InvoiceItemBase extends EntityBase implements InvoiceItem {

    /* Common to all items */
    protected final UUID invoiceId;
    protected final UUID accountId;
    protected final UUID childAccountId;
    protected final LocalDate startDate;
    protected final LocalDate endDate;
    protected final BigDecimal amount;
    protected final Currency currency;
    protected final String description;
    protected final InvoiceItemType invoiceItemType;

    /* Fixed and recurring specific */
    protected final UUID subscriptionId;
    protected final UUID bundleId;

    /* Recurring specific */
    protected final BigDecimal rate;

    /* RepairAdjInvoiceItem */
    protected final UUID linkedItemId;

    /* Usage details */
    protected final Integer quantity;
    protected final String itemDetails;

    public InvoiceItemBase(final UUID id, @Nullable final DateTime createdDate, final UUID invoiceId, final UUID accountId, @Nullable final UUID bundleId,
                           @Nullable final UUID subscriptionId, @Nullable final String description,
                           final LocalDate startDate, final LocalDate endDate, final BigDecimal amount, final BigDecimal rate, final Currency currency, final UUID reversedItemId, final InvoiceItemType invoiceItemType) {
        this(id, createdDate, invoiceId, accountId, null, bundleId, subscriptionId, description, startDate, endDate, amount, rate, currency, reversedItemId, null, null, invoiceItemType);
    }

    public InvoiceItemBase(final UUID id, @Nullable final DateTime createdDate, final UUID invoiceId, final UUID accountId, @Nullable final UUID bundleId,
                           @Nullable final UUID subscriptionId, @Nullable final String description,
                           final LocalDate startDate, final LocalDate endDate, final BigDecimal amount, final BigDecimal rate, final Currency currency, final UUID reversedItemId,
                           @Nullable final Integer quantity, @Nullable final String itemDetails, final InvoiceItemType invoiceItemType) {
        this(id, createdDate, invoiceId, accountId, null, bundleId, subscriptionId, description, startDate, endDate, amount, rate, currency, reversedItemId, quantity, itemDetails, invoiceItemType);
    }

    // For parent invoices
    public InvoiceItemBase(final UUID id, @Nullable final DateTime createdDate, final UUID invoiceId, final UUID accountId, final UUID childAccountId,
                           final BigDecimal amount, final Currency currency, final String description, final InvoiceItemType invoiceItemType) {
        this(id, createdDate, invoiceId, accountId, childAccountId, null, null, description, null, null, amount, null, currency, null, null, null, invoiceItemType);
    }

    private InvoiceItemBase(final UUID id, @Nullable final DateTime createdDate, final UUID invoiceId, final UUID accountId, @Nullable final UUID childAccountId, @Nullable final UUID bundleId,
                            @Nullable final UUID subscriptionId, @Nullable final String description,
                            @Nullable final LocalDate startDate, final LocalDate endDate, final BigDecimal amount, final BigDecimal rate, final Currency currency,
                            final UUID reversedItemId, @Nullable final Integer quantity, @Nullable final String itemDetails, final InvoiceItemType invoiceItemType) {
        super(id, createdDate, createdDate);
        this.invoiceId = invoiceId;
        this.accountId = accountId;
        this.childAccountId = childAccountId;
        this.subscriptionId = subscriptionId;
        this.bundleId = bundleId;
        this.description = description;
        this.startDate = startDate;
        this.endDate = endDate;
        this.amount = amount == null || currency == null ? amount : KillBillMoney.of(amount, currency);
        this.currency = currency;
        this.rate = rate;
        this.linkedItemId = reversedItemId;
        this.quantity = quantity;
        this.itemDetails = itemDetails;
        this.invoiceItemType = invoiceItemType;
    }

    @Override
    public UUID getInvoiceId() {
        return invoiceId;
    }

    @Override
    public UUID getBundleId() {
        return bundleId;
    }

    @Override
    public UUID getAccountId() {
        return accountId;
    }

    @Override
    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    @Override
    public BigDecimal getAmount() {
        return amount;
    }

    @Override
    public LocalDate getStartDate() {
        return startDate;
    }

    @Override
    public LocalDate getEndDate() {
        return endDate;
    }

    @Override
    public Currency getCurrency() {
        return currency;
    }

    @Override
    public BigDecimal getRate() {
        return rate;
    }

    @Override
    public UUID getLinkedItemId() {
        return linkedItemId;
    }

    @Override
    public UUID getChildAccountId() {
        return childAccountId;
    }

    @Override
    public String getProductName() {
        return null;
    }

    @Override
    public String getPrettyProductName() {
        return null;
    }

    @Override
    public String getPlanName() {
        return null;
    }

    @Override
    public String getPrettyPlanName() {
        return null;
    }

    @Override
    public String getPhaseName() {
        return null;
    }

    @Override
    public String getPrettyPhaseName() {
        return null;
    }

    @Override
    public String getUsageName() {
        return null;
    }

    @Override
    public String getPrettyUsageName() {
        return null;
    }

    @Override
    public Integer getQuantity() {
        return quantity;
    }

    @Override
    public String getItemDetails() {
        return itemDetails;
    }

    @Override
    public boolean equals(final Object o) {

        if (!matches(o)) {
            return false;
        }
        final InvoiceItemBase that = (InvoiceItemBase) o;
        if (!super.equals(that)) {
            return false;
        }
        if (invoiceId != null ? !invoiceId.equals(that.invoiceId) : that.invoiceId != null) {
            return false;
        }
        if (linkedItemId != null ? !linkedItemId.equals(that.linkedItemId) : that.linkedItemId != null) {
            return false;
        }
        if (description != null ? !description.equals(that.description) : that.description != null) {
            return false;
        }
        return true;
    }

    @Override
    public boolean matches(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof InvoiceItemBase)) {
            return false;
        }

        final InvoiceItemBase that = (InvoiceItemBase) o;

        if (getInvoiceItemType() != null ? !getInvoiceItemType().equals(that.getInvoiceItemType()) : that.getInvoiceItemType() != null) {
            return false;
        }
        if (accountId != null ? !accountId.equals(that.accountId) : that.accountId != null) {
            return false;
        }
        if (childAccountId != null ? !childAccountId.equals(that.childAccountId) : that.childAccountId != null) {
            return false;
        }
        if (bundleId != null ? !bundleId.equals(that.bundleId) : that.bundleId != null) {
            return false;
        }
        if (subscriptionId != null ? !subscriptionId.equals(that.subscriptionId) : that.subscriptionId != null) {
            return false;
        }
        if (safeCompareTo(startDate, that.startDate) != 0) {
            return false;
        }
        if (safeCompareTo(endDate, that.endDate) != 0) {
            return false;
        }
        if (safeCompareTo(amount, that.amount) != 0) {
            return false;
        }
        if (safeCompareTo(rate, that.rate) != 0) {
            return false;
        }
        if (currency != that.currency) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (invoiceId != null ? invoiceId.hashCode() : 0);
        result = 31 * result + (accountId != null ? accountId.hashCode() : 0);
        result = 31 * result + (childAccountId != null ? childAccountId.hashCode() : 0);
        result = 31 * result + (startDate != null ? startDate.hashCode() : 0);
        result = 31 * result + (endDate != null ? endDate.hashCode() : 0);
        result = 31 * result + (amount != null ? amount.hashCode() : 0);
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        result = 31 * result + (subscriptionId != null ? subscriptionId.hashCode() : 0);
        result = 31 * result + (bundleId != null ? bundleId.hashCode() : 0);
        result = 31 * result + (description != null ? description.hashCode() : 0);
        result = 31 * result + (rate != null ? rate.hashCode() : 0);
        result = 31 * result + (linkedItemId != null ? linkedItemId.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        // Note: we don't use all fields here, as the output would be overwhelming
        // (we output all invoice items as they are generated).
        final StringBuilder sb = new StringBuilder();
        sb.append(getInvoiceItemType());
        sb.append("{");
        if (startDate != null) {
            sb.append("startDate=").append(startDate);
        }
        if (endDate != null) {
            sb.append("endDate=").append(endDate);
        }
        if (amount != null) {
            sb.append("amount=").append(amount);
        }
        if (rate != null) {
            sb.append("rate=").append(rate);
        }
        if (subscriptionId != null) {
            sb.append("subscriptionId=").append(subscriptionId);
        }
        if (linkedItemId != null) {
            sb.append("linkedItemId=").append(linkedItemId);
        }
        sb.append('}');
        return sb.toString();
    }

    @Override
    public InvoiceItemType getInvoiceItemType() {
        return invoiceItemType;
    }

    @Override
    public String getDescription() {
        return description;
    }
}
