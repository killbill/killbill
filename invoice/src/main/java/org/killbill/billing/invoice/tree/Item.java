/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

package org.killbill.billing.invoice.tree;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.Days;
import org.joda.time.LocalDate;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.generator.InvoiceDateUtils;
import org.killbill.billing.invoice.model.RecurringInvoiceItem;
import org.killbill.billing.invoice.model.RepairAdjInvoiceItem;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

/**
 * An generic invoice item that contains all pertinent fields regarding of its InvoiceItemType.
 * <p/>
 * It contains an action that determines what to do when building the tree (whether in normal or merge mode). It also
 * keeps track of current adjusted and repair amount so subsequent repair can be limited to what is left.
 */
public class Item {

    private final UUID id;
    private final UUID accountId;
    private final UUID bundleId;
    private final UUID subscriptionId;
    private final UUID targetInvoiceId;
    private final UUID invoiceId;
    private final String productName;
    private final String planName;
    private final String phaseName;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final BigDecimal amount;
    private final BigDecimal rate;
    private final Currency currency;
    private final DateTime createdDate;
    private final UUID linkedId;

    private BigDecimal currentRepairedAmount;
    private BigDecimal adjustedAmount;

    private final ItemAction action;

    public enum ItemAction {
        ADD,
        CANCEL
    }

    public Item(final Item item, final ItemAction action) {
        this.id = item.id;
        this.accountId = item.accountId;
        this.bundleId = item.bundleId;
        this.subscriptionId = item.subscriptionId;
        this.targetInvoiceId = item.targetInvoiceId;
        this.invoiceId = item.invoiceId;
        this.productName = item.productName;
        this.planName = item.planName;
        this.phaseName = item.phaseName;
        this.startDate = item.startDate;
        this.endDate = item.endDate;
        this.amount = item.amount;
        this.rate = item.rate;
        this.currency = item.currency;
        // In merge mode, the reverse item needs to correctly point to itself (repair of original item)
        this.linkedId = action == ItemAction.ADD ? item.linkedId : this.id;
        this.createdDate = item.createdDate;
        this.currentRepairedAmount = item.currentRepairedAmount;
        this.adjustedAmount = item.adjustedAmount;

        this.action = action;
    }

    public Item(final InvoiceItem item, final UUID targetInvoiceId, final ItemAction action) {
        this(item, item.getStartDate(), item.getEndDate(), targetInvoiceId, action);
    }

    public Item(final InvoiceItem item, final LocalDate startDate, final LocalDate endDate, final UUID targetInvoiceId, final ItemAction action) {
        this.id = item.getId();
        this.accountId = item.getAccountId();
        this.bundleId = item.getBundleId();
        this.subscriptionId = item.getSubscriptionId();
        this.targetInvoiceId = targetInvoiceId;
        this.invoiceId = item.getInvoiceId();
        this.productName = item.getProductName();
        this.planName = item.getPlanName();
        this.phaseName = item.getPhaseName();
        this.startDate = startDate;
        this.endDate = endDate;
        this.amount = item.getAmount().abs();
        this.rate = item.getRate();
        this.currency = item.getCurrency();
        this.linkedId = item.getLinkedItemId();
        this.createdDate = item.getCreatedDate();
        this.action = action;

        this.currentRepairedAmount = BigDecimal.ZERO;
        this.adjustedAmount = BigDecimal.ZERO;
    }

    public InvoiceItem toInvoiceItem() {
        return toProratedInvoiceItem(startDate, endDate);
    }

    public InvoiceItem toProratedInvoiceItem(final LocalDate newStartDate, final LocalDate newEndDate) {

        int nbTotalDays = Days.daysBetween(startDate, endDate).getDays();
        final boolean prorated = !(newStartDate.compareTo(startDate) == 0 && newEndDate.compareTo(endDate) == 0);

        // Pro-ration is built by using the startDate, endDate and amount of this item instead of using the rate and a potential full period.
        final BigDecimal positiveAmount = prorated ? InvoiceDateUtils.calculateProrationBetweenDates(newStartDate, newEndDate, nbTotalDays)
                                                                     .multiply(amount) : amount;

        if (action == ItemAction.ADD) {
            return new RecurringInvoiceItem(id, createdDate, invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, newStartDate, newEndDate, positiveAmount, rate, currency);
        } else {
            // We first compute the maximum amount after adjustment and that sets the amount limit of how much can be repaired.
            final BigDecimal maxAvailableAmountForRepair = getNetAmount();
            final BigDecimal positiveAmountForRepair = positiveAmount.compareTo(maxAvailableAmountForRepair) <= 0 ? positiveAmount : maxAvailableAmountForRepair;
            return positiveAmountForRepair.compareTo(BigDecimal.ZERO) > 0 ? new RepairAdjInvoiceItem(targetInvoiceId, accountId, newStartDate, newEndDate, positiveAmountForRepair.negate(), currency, linkedId) : null;
        }
    }

    public void incrementAdjustedAmount(final BigDecimal increment) {
        Preconditions.checkState(increment.compareTo(BigDecimal.ZERO) > 0, "Invalid adjustment increment='%s', item=%s", increment, this);
        adjustedAmount = adjustedAmount.add(increment);
    }

    public void incrementCurrentRepairedAmount(final BigDecimal increment) {
        Preconditions.checkState(increment.compareTo(BigDecimal.ZERO) > 0, "Invalid repair increment='%s', item=%s", increment, this);
        currentRepairedAmount = currentRepairedAmount.add(increment);
    }

    @JsonIgnore
    public BigDecimal getNetAmount() {
        return amount.subtract(adjustedAmount).subtract(currentRepairedAmount);
    }

    public ItemAction getAction() {
        return action;
    }

    public UUID getLinkedId() {
        return linkedId;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public UUID getId() {
        return id;
    }

    public Currency getCurrency() {
        return currency;
    }

    /**
     * Compare two items to check whether there are the same kind; that is whether or not they build for the same product/plan.
     *
     * @param other item to compare with
     * @return
     */
    public boolean isSameKind(final Item other) {

        final InvoiceItem otherItem = other.toInvoiceItem();

        // See https://github.com/killbill/killbill/issues/286
        return otherItem != null &&
               !id.equals(otherItem.getId()) &&
               // Finally, for the tricky part... In case of complete repairs, the new invoiceItem will always meet all of the
               // following conditions: same type, subscription, start date. Depending on the catalog configuration, the end
               // date check could also match (e.g. repair from annual to monthly). For that scenario, we need to default
               // to catalog checks (the rate check is a lame check for versioned catalogs).
               MoreObjects.firstNonNull(planName, "").equals(MoreObjects.firstNonNull(otherItem.getPlanName(), "")) &&
               MoreObjects.firstNonNull(phaseName, "").equals(MoreObjects.firstNonNull(otherItem.getPhaseName(), "")) &&
               MoreObjects.firstNonNull(rate, BigDecimal.ZERO).compareTo(MoreObjects.firstNonNull(otherItem.getRate(), BigDecimal.ZERO)) == 0;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Item{");
        sb.append("id=").append(id);
        sb.append(", accountId=").append(accountId);
        sb.append(", bundleId=").append(bundleId);
        sb.append(", subscriptionId=").append(subscriptionId);
        sb.append(", targetInvoiceId=").append(targetInvoiceId);
        sb.append(", invoiceId=").append(invoiceId);
        sb.append(", productName='").append(productName).append('\'');
        sb.append(", planName='").append(planName).append('\'');
        sb.append(", phaseName='").append(phaseName).append('\'');
        sb.append(", startDate=").append(startDate);
        sb.append(", endDate=").append(endDate);
        sb.append(", amount=").append(amount);
        sb.append(", rate=").append(rate);
        sb.append(", currency=").append(currency);
        sb.append(", createdDate=").append(createdDate);
        sb.append(", linkedId=").append(linkedId);
        sb.append(", currentRepairedAmount=").append(currentRepairedAmount);
        sb.append(", adjustedAmount=").append(adjustedAmount);
        sb.append(", action=").append(action);
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

        final Item item = (Item) o;

        if (accountId != null ? !accountId.equals(item.accountId) : item.accountId != null) {
            return false;
        }
        if (action != item.action) {
            return false;
        }
        if (adjustedAmount != null ? adjustedAmount.compareTo(item.adjustedAmount) != 0 : item.adjustedAmount != null) {
            return false;
        }
        if (amount != null ? amount.compareTo(item.amount) != 0 : item.amount != null) {
            return false;
        }
        if (bundleId != null ? !bundleId.equals(item.bundleId) : item.bundleId != null) {
            return false;
        }
        if (createdDate != null ? createdDate.compareTo(item.createdDate) != 0 : item.createdDate != null) {
            return false;
        }
        if (currency != item.currency) {
            return false;
        }
        if (currentRepairedAmount != null ? currentRepairedAmount.compareTo(item.currentRepairedAmount) != 0 : item.currentRepairedAmount != null) {
            return false;
        }
        if (endDate != null ? endDate.compareTo(item.endDate) != 0 : item.endDate != null) {
            return false;
        }
        if (id != null ? !id.equals(item.id) : item.id != null) {
            return false;
        }
        if (invoiceId != null ? !invoiceId.equals(item.invoiceId) : item.invoiceId != null) {
            return false;
        }
        if (linkedId != null ? !linkedId.equals(item.linkedId) : item.linkedId != null) {
            return false;
        }
        if (phaseName != null ? !phaseName.equals(item.phaseName) : item.phaseName != null) {
            return false;
        }
        if (planName != null ? !planName.equals(item.planName) : item.planName != null) {
            return false;
        }
        if (productName != null ? !productName.equals(item.productName) : item.productName != null) {
            return false;
        }
        if (rate != null ? rate.compareTo(item.rate) != 0 : item.rate != null) {
            return false;
        }
        if (startDate != null ? startDate.compareTo(item.startDate) != 0 : item.startDate != null) {
            return false;
        }
        if (subscriptionId != null ? !subscriptionId.equals(item.subscriptionId) : item.subscriptionId != null) {
            return false;
        }
        if (targetInvoiceId != null ? !targetInvoiceId.equals(item.targetInvoiceId) : item.targetInvoiceId != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (accountId != null ? accountId.hashCode() : 0);
        result = 31 * result + (bundleId != null ? bundleId.hashCode() : 0);
        result = 31 * result + (subscriptionId != null ? subscriptionId.hashCode() : 0);
        result = 31 * result + (targetInvoiceId != null ? targetInvoiceId.hashCode() : 0);
        result = 31 * result + (invoiceId != null ? invoiceId.hashCode() : 0);
        result = 31 * result + (planName != null ? planName.hashCode() : 0);
        result = 31 * result + (phaseName != null ? phaseName.hashCode() : 0);
        result = 31 * result + (startDate != null ? startDate.hashCode() : 0);
        result = 31 * result + (endDate != null ? endDate.hashCode() : 0);
        result = 31 * result + (amount != null ? amount.hashCode() : 0);
        result = 31 * result + (rate != null ? rate.hashCode() : 0);
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        result = 31 * result + (createdDate != null ? createdDate.hashCode() : 0);
        result = 31 * result + (linkedId != null ? linkedId.hashCode() : 0);
        result = 31 * result + (currentRepairedAmount != null ? currentRepairedAmount.hashCode() : 0);
        result = 31 * result + (adjustedAmount != null ? adjustedAmount.hashCode() : 0);
        result = 31 * result + (action != null ? action.hashCode() : 0);
        return result;
    }
}
