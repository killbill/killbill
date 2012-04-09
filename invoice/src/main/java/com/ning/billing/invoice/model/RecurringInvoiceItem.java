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

    public RecurringInvoiceItem(UUID invoiceId, UUID subscriptionId, String planName, String phaseName,
                                DateTime startDate, DateTime endDate,
                                BigDecimal amount, BigDecimal rate,
                                Currency currency,
                                DateTime createdDate) {
        this(UUID.randomUUID(), invoiceId, subscriptionId, planName, phaseName, startDate, endDate,
                amount, rate, currency, createdDate);
    }

    public RecurringInvoiceItem(UUID invoiceId, UUID subscriptionId, String planName, String phaseName,
                                DateTime startDate, DateTime endDate,
                                BigDecimal amount, BigDecimal rate,
                                Currency currency, UUID reversedItemId,
                                DateTime createdDate) {
        this(UUID.randomUUID(), invoiceId, subscriptionId, planName, phaseName, startDate, endDate,
                amount, rate, currency, reversedItemId, createdDate);
    }

    public RecurringInvoiceItem(UUID id, UUID invoiceId, UUID subscriptionId, String planName, String phaseName,
                                DateTime startDate, DateTime endDate,
                                BigDecimal amount, BigDecimal rate,
                                Currency currency,
                                DateTime createdDate) {
        super(id, invoiceId, subscriptionId, planName, phaseName, startDate, endDate, amount, currency, createdDate);

        this.rate = rate;
        this.reversedItemId = null;
    }

    public RecurringInvoiceItem(UUID id, UUID invoiceId, UUID subscriptionId, String planName, String phaseName,
                                DateTime startDate, DateTime endDate,
                                BigDecimal amount, BigDecimal rate,
                                Currency currency, UUID reversedItemId,
                                DateTime createdDate) {
        super(id, invoiceId, subscriptionId, planName, phaseName, startDate, endDate, amount, currency, createdDate);

        this.rate = rate;
        this.reversedItemId = reversedItemId;
    }

    @Override
    public InvoiceItem asCredit() {
        BigDecimal amountNegated = amount == null ? null : amount.negate();
        return new RecurringInvoiceItem(invoiceId, subscriptionId, planName, phaseName, startDate, endDate,
                amountNegated, rate, currency, id, createdDate);
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
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RecurringInvoiceItem that = (RecurringInvoiceItem) o;

        if (amount.compareTo(that.amount) != 0) return false;
        if (currency != that.currency) return false;
        if (startDate.compareTo(that.startDate) != 0) return false;
        if (endDate.compareTo(that.endDate) != 0) return false;
        if (!phaseName.equals(that.phaseName)) return false;
        if (!planName.equals(that.planName)) return false;
        if (rate.compareTo(that.rate) != 0) return false;
        if (reversedItemId != null ? !reversedItemId.equals(that.reversedItemId) : that.reversedItemId != null)
            return false;
        if (!subscriptionId.equals(that.subscriptionId)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = invoiceId.hashCode();
        result = 31 * result + subscriptionId.hashCode();
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

        return sb.toString();

//        StringBuilder sb = new StringBuilder();
//        sb.append("InvoiceItem = {").append("id = ").append(id.toString()).append(", ");
//        sb.append("invoiceId = ").append(invoiceId.toString()).append(", ");
//        sb.append("subscriptionId = ").append(subscriptionId.toString()).append(", ");
//        sb.append("planName = ").append(planName).append(", ");
//        sb.append("phaseName = ").append(phaseName).append(", ");
//        sb.append("startDate = ").append(startDate.toString()).append(", ");
//        if (endDate != null) {
//            sb.append("endDate = ").append(endDate.toString()).append(", ");
//        } else {
//            sb.append("endDate = null");
//        }
//        sb.append("recurringAmount = ");
//        if (amount == null) {
//            sb.append("null");
//        } else {
//            sb.append(amount.toString());
//        }
//        sb.append(", ");
//
//        sb.append("recurringRate = ");
//        if (rate == null) {
//            sb.append("null");
//        } else {
//            sb.append(rate.toString());
//        }
//        sb.append(", ");
//
//        sb.append("}");
//        return sb.toString();
    }
}
