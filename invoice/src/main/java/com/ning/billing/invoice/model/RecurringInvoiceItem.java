/*
 * Copyright 2010-2011 Ning, Inc.
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

package com.ning.billing.invoice.model;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.InvoiceItem;
import org.joda.time.DateTime;

import java.math.BigDecimal;
import java.util.UUID;

public class RecurringInvoiceItem extends InvoiceItemBase {
    private final BigDecimal rate;
    private final UUID reversedItemId;

    public RecurringInvoiceItem(UUID invoiceId, UUID accountId, UUID bundleId, UUID subscriptionId, String planName, String phaseName,
                                DateTime startDate, DateTime endDate,
                                BigDecimal amount, BigDecimal rate,
                                Currency currency) { 
        super(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, amount, currency);
        this.rate = rate;
        this.reversedItemId = null;
    }

    public RecurringInvoiceItem(UUID invoiceId, UUID accountId, UUID bundleId, UUID subscriptionId, String planName, String phaseName,
                                DateTime startDate, DateTime endDate,
                                BigDecimal amount, BigDecimal rate,
                                Currency currency, UUID reversedItemId) {
        super(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate,
                amount, currency);
        this.rate = rate;
        this.reversedItemId = reversedItemId;
    }

    public RecurringInvoiceItem(UUID id, UUID invoiceId, UUID accountId, UUID bundleId, UUID subscriptionId, String planName, String phaseName,
                                DateTime startDate, DateTime endDate,
                                BigDecimal amount, BigDecimal rate,
                                Currency currency,
                                String createdBy, DateTime createdDate) {
        super(id, invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, amount, currency, createdBy, createdDate);
        this.rate = rate;
        this.reversedItemId = null;
    }

    public RecurringInvoiceItem(UUID id, UUID invoiceId, UUID accountId, UUID bundleId, UUID subscriptionId, String planName, String phaseName,
                                DateTime startDate, DateTime endDate,
                                BigDecimal amount, BigDecimal rate,
                                Currency currency, UUID reversedItemId,
                                String createdBy, DateTime createdDate) {
        super(id, invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, amount, currency, createdBy, createdDate);
        this.rate = rate;
        this.reversedItemId = reversedItemId;
    }

    @Override
    public InvoiceItem asCredit() {
        BigDecimal amountNegated = amount == null ? null : amount.negate();

        return new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate,
                amountNegated, rate, currency, id);
    }

    @Override
    public String getDescription() {
        return String.format("%s from %s to %s", phaseName, startDate.toString(), endDate.toString());
    }

    public UUID getReversedItemId() {
        return reversedItemId;
    }

    public boolean reversesItem() {
        return (reversedItemId != null);
    }

    public BigDecimal getRate() {
        return rate;
    }

    @Override
    public int compareTo(InvoiceItem item) {
        if (item == null) {
            return -1;
        }
        if (!(item instanceof RecurringInvoiceItem)) {
            return -1;
        }

        RecurringInvoiceItem that = (RecurringInvoiceItem) item;
        int compareAccounts = getAccountId().compareTo(that.getAccountId());
        if (compareAccounts == 0 && bundleId != null) {
            int compareBundles = getBundleId().compareTo(that.getBundleId());
            if (compareBundles == 0 && subscriptionId != null) {

                int compareSubscriptions = getSubscriptionId().compareTo(that.getSubscriptionId());
                if (compareSubscriptions == 0) {
                    int compareStartDates = getStartDate().compareTo(that.getStartDate());
                    if (compareStartDates == 0) {
                        return getEndDate().compareTo(that.getEndDate());
                    } else {
                        return compareStartDates;
                    }
                } else {
                    return compareSubscriptions;
                }
            } else {
                return compareBundles;
            }
        } else {
            return compareAccounts;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RecurringInvoiceItem that = (RecurringInvoiceItem) o;

        if (accountId.compareTo(that.accountId) != 0) return false;
        if (amount.compareTo(that.amount) != 0) return false;
        if (currency != that.currency) return false;
        if (startDate.compareTo(that.startDate) != 0) return false;
        if (endDate.compareTo(that.endDate) != 0) return false;
        if (!phaseName.equals(that.phaseName)) return false;
        if (!planName.equals(that.planName)) return false;
        if (rate.compareTo(that.rate) != 0) return false;
        if (reversedItemId != null ? !reversedItemId.equals(that.reversedItemId) : that.reversedItemId != null)
            return false;
        if (subscriptionId != null ? !subscriptionId.equals(that.subscriptionId) : that.subscriptionId != null)
            return false;
        if (bundleId != null ? !bundleId.equals(that.bundleId) : that.bundleId != null)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = accountId.hashCode();
        result = 31 * result + (subscriptionId != null ? subscriptionId.hashCode() : 0);
        result = 31 * result + (bundleId != null ? bundleId.hashCode() : 0);
        result = 31 * result + planName.hashCode();
        result = 31 * result + phaseName.hashCode();
        result = 31 * result + startDate.hashCode();
        result = 31 * result + endDate.hashCode();
        result = 31 * result + amount.hashCode();
        result = 31 * result + rate.hashCode();
        result = 31 * result + currency.hashCode();
        result = 31 * result + (reversedItemId != null ? reversedItemId.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append(phaseName).append(", ");
        sb.append(startDate.toString()).append(", ");
        sb.append(endDate.toString()).append(", ");
        sb.append(amount.toString()).append(", ");
        sb.append("subscriptionId = ").append(subscriptionId == null ? null : subscriptionId.toString()).append(", ");
        sb.append("bundleId = ").append(bundleId == null ? null : bundleId.toString()).append(", ");

        return sb.toString();
    }
}
