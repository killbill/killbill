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

public class DefaultInvoiceItem implements InvoiceItem {
    private final UUID id;
    private final UUID invoiceId;
    private final UUID subscriptionId;
    private DateTime startDate;
    private DateTime endDate;
    private final String description;
    private BigDecimal amount;
    private final BigDecimal rate;
    private final Currency currency;

    public DefaultInvoiceItem(UUID invoiceId, UUID subscriptionId, DateTime startDate, DateTime endDate, String description, BigDecimal amount, BigDecimal rate, Currency currency) {
        this(UUID.randomUUID(), invoiceId, subscriptionId, startDate, endDate, description, amount, rate, currency);
    }

    public DefaultInvoiceItem(UUID id, UUID invoiceId, UUID subscriptionId, DateTime startDate, DateTime endDate, String description, BigDecimal amount, BigDecimal rate, Currency currency) {
        this.id = id;
        this.invoiceId = invoiceId;
        this.subscriptionId = subscriptionId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.description = description;
        this.amount = amount;
        this.rate = rate;
        this.currency = currency;
    }

    public DefaultInvoiceItem(InvoiceItem that, UUID invoiceId) {
        this.id = UUID.randomUUID();
        this.invoiceId = invoiceId;
        this.subscriptionId = that.getSubscriptionId();
        this.startDate = that.getStartDate();
        this.endDate = that.getEndDate();
        this.description = that.getDescription();
        this.amount = that.getAmount();
        this.rate = that.getRate();
        this.currency = that.getCurrency();
    }

    @Override
    public InvoiceItem asCredit(UUID invoiceId) {
        return new DefaultInvoiceItem(invoiceId, subscriptionId, startDate, endDate, description, amount.negate(), rate, currency);
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public UUID getInvoiceId() {
        return invoiceId;
    }

    @Override
    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    @Override
    public DateTime getStartDate() {
        return startDate;
    }

    @Override
    public DateTime getEndDate() {
        return endDate;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public BigDecimal getAmount() {
        return amount;
    }

    @Override
    public BigDecimal getRate() {
        return rate;
    }

    @Override
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
    @Override
    public void subtract(InvoiceItem that) {
        if (this.startDate.equals(that.getStartDate()) && this.endDate.equals(that.getEndDate())) {
            this.startDate = this.endDate;
            this.amount = this.amount.subtract(that.getAmount());
        } else {
            if (this.startDate.equals(that.getStartDate())) {
                this.startDate = that.getEndDate();
                this.amount = this.amount.subtract(that.getAmount());
            }

            if (this.endDate.equals(that.getEndDate())) {
                this.endDate = that.getStartDate();
                this.amount = this.amount.subtract(that.getAmount());
            }
        }
    }

    @Override
    public boolean duplicates(InvoiceItem that) {
        if(!this.getSubscriptionId().equals(that.getSubscriptionId())) {return false;}
        if(!this.getRate().equals(that.getRate())) {return false;}
        if(!this.getCurrency().equals(that.getCurrency())) {return false;}

        DateRange thisDateRange = new DateRange(this.getStartDate(), this.getEndDate());
        return thisDateRange.contains(that.getStartDate()) && thisDateRange.contains(that.getEndDate());
    }

    /**
     * indicates whether the supplied item is a cancelling item for this item
     * @param that
     * @return
     */
    @Override
    public boolean cancels(InvoiceItem that) {
        if(!this.getSubscriptionId().equals(that.getSubscriptionId())) {return false;}
        if(!this.getEndDate().equals(that.getEndDate())) {return false;}
        if(!this.getStartDate().equals(that.getStartDate())) {return false;}
        if(!this.getAmount().equals(that.getAmount().negate())) {return false;}
        if(!this.getRate().equals(that.getRate())) {return false;}
        if(!this.getCurrency().equals(that.getCurrency())) {return false;}

        return true;
    }
}
