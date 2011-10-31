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
import org.joda.time.DateTime;

import java.math.BigDecimal;
import java.util.UUID;

public class InvoiceItem implements Comparable<InvoiceItem> {
    private final UUID subscriptionId;
    private DateTime startDate;
    private DateTime endDate;
    private final String description;
    private BigDecimal amount;
    private final BigDecimal rate;
    private final Currency currency;

    public InvoiceItem(UUID subscriptionId, DateTime startDate, DateTime endDate, String description, BigDecimal amount, BigDecimal rate, Currency currency) {
        this.subscriptionId = subscriptionId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.description = description;
        this.amount = amount;
        this.rate = rate;
        this.currency = currency;
    }

    public InvoiceItem asCredit() {
        return new InvoiceItem(subscriptionId, startDate, endDate, description, amount.negate(), rate, currency);
    }

    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    public DateTime getStartDate() {
        return startDate;
    }

    public DateTime getEndDate() {
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

    @Override
    public int compareTo(InvoiceItem invoiceItem) {
        int compareSubscriptions = getSubscriptionId().compareTo(invoiceItem.getSubscriptionId());

        if (compareSubscriptions == 0) {
            return getStartDate().compareTo(invoiceItem.getStartDate());
        } else {
            return compareSubscriptions;
        }
    }

    // TODO: deal with error cases
    public void subtract(InvoiceItem that) {
        if (this.startDate.equals(that.startDate) && this.endDate.equals(that.endDate)) {
            this.startDate = this.endDate;
            this.amount = this.amount.subtract(that.amount);
        } else {
            if (this.startDate.equals(that.startDate)) {
                this.startDate = that.endDate;
                this.amount = this.amount.subtract(that.amount);
            }

            if (this.endDate.equals(that.endDate)) {
                this.endDate = that.startDate;
                this.amount = this.amount.subtract(that.amount);
            }
        }
    }

    public boolean duplicates(InvoiceItem that) {
        if(!this.getSubscriptionId().equals(that.getSubscriptionId())) {return false;}
        if(!this.getRate().equals(that.getRate())) {return false;}
        if(!this.getCurrency().equals(that.getCurrency())) {return false;}

        DateRange thisDateRange = new DateRange(this.getStartDate(), this.getEndDate());
        return thisDateRange.contains(that.getStartDate()) && thisDateRange.contains(that.getEndDate());
    }
}
