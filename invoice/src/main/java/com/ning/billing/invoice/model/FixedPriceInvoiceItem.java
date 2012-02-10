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

public class FixedPriceInvoiceItem implements InvoiceItem {
    private final UUID id;
    private final UUID invoiceId;
    private final UUID subscriptionId;
    private final String planName;
    private final String phaseName;
    private DateTime date;
    private BigDecimal amount;
    private final Currency currency;

    public FixedPriceInvoiceItem(UUID invoiceId, UUID subscriptionId, String planName, String phaseName,
                                DateTime date, BigDecimal amount, Currency currency) {
        this(UUID.randomUUID(), invoiceId, subscriptionId, planName, phaseName,
             date, amount, currency);
    }

    public FixedPriceInvoiceItem(UUID id, UUID invoiceId, UUID subscriptionId, String planName, String phaseName,
                                DateTime date, BigDecimal amount, Currency currency) {
        this.id = id;
        this.invoiceId = invoiceId;
        this.subscriptionId = subscriptionId;
        this.planName = planName;
        this.phaseName = phaseName;
        this.date = date;
        this.amount = amount;
        this.currency = currency;
    }

//    public FixedPriceInvoiceItem(FixedPriceInvoiceItem that, UUID invoiceId) {
//        this.id = UUID.randomUUID();
//        this.invoiceId = invoiceId;
//        this.subscriptionId = that.getSubscriptionId();
//        this.planName = that.getPlanName();
//        this.phaseName = that.getPhaseName();
//        this.date = that.getDate();
//        this.amount = that.getAmount();
//        this.currency = that.getCurrency();
//    }

    @Override
    public InvoiceItem asCredit() {
        throw new UnsupportedOperationException();
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
    public String getPlanName() {
        return planName;
    }

    @Override
    public String getPhaseName() {
        return phaseName;
    }

    @Override
    public String getDescription() {
        return String.format("%s (fixed price) on %s", getPhaseName(), getDate().toString());
    }

    @Override
    public BigDecimal getAmount() {
        return amount;
    }

    @Override
    public int hashCode() {
        int result = subscriptionId != null ? subscriptionId.hashCode() : 0;
        result = 31 * result + (planName != null ? planName.hashCode() : 0);
        result = 31 * result + (phaseName != null ? phaseName.hashCode() : 0);
        result = 31 * result + (date != null ? date.hashCode() : 0);
        result = 31 * result + (amount != null ? amount.hashCode() : 0);
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        return result;
    }

    public DateTime getDate() {
        return date;
    }

    @Override
    public Currency getCurrency() {
        return currency;
    }

    @Override
    public int compareTo(InvoiceItem item) {
        if (!(item instanceof FixedPriceInvoiceItem)) {
            return 1;
        }

        FixedPriceInvoiceItem that = (FixedPriceInvoiceItem) item;
        int compareSubscriptions = getSubscriptionId().compareTo(that.getSubscriptionId());

        if (compareSubscriptions == 0) {
            return getDate().compareTo(that.getDate());
        } else {
            return compareSubscriptions;
        }
    }

//    @Override
//    public void subtract(InvoiceItem that) {
//        // for now, do nothing -- fixed price items aren't subtracted
//        return;
//    }
//
//    @Override
//    public boolean duplicates(InvoiceItem item) {
//        if (!(item instanceof FixedPriceInvoiceItem)) {return false;}
//
//        FixedPriceInvoiceItem that = (FixedPriceInvoiceItem) item;
//        if (!this.getSubscriptionId().equals(that.getSubscriptionId())) {return false;}
//
//        if (!this.planName.equals(that.getPlanName())) {return false;}
//        if (!this.phaseName.equals(that.getPhaseName())) {return false;}
//
//        if (!compareNullableBigDecimal(this.getAmount(), that.getAmount())) {return false;}
//
//        if (!this.getCurrency().equals(that.getCurrency())) {return false;}
//
//        return (this.date.compareTo(that.getDate()) == 0);
//    }
//
//    private boolean compareNullableBigDecimal(@Nullable BigDecimal value1, @Nullable BigDecimal value2) {
//        if ((value1 == null) && (value2 != null)) {return false;}
//        if ((value1 != null) && (value2 == null)) {return false;}
//
//        if ((value1 != null) && (value2 != null)) {
//            if (value1.compareTo(value2) != 0) {return false;}
//        }
//
//        return true;
//    }
//
//    /**
//     * indicates whether the supplied item is a cancelling item for this item
//     * @param item  the InvoiceItem to be examined
//     * @return true if the two invoice items cancel each other out (same subscription, same date range, sum of amounts = 0)
//     */
//    @Override
//    public boolean cancels(InvoiceItem item) {
//        if (!(item instanceof FixedPriceInvoiceItem)) {return false;}
//
//        FixedPriceInvoiceItem that = (FixedPriceInvoiceItem) item;
//
//        if(!this.getSubscriptionId().equals(that.getSubscriptionId())) {return false;}
//        if(!this.getDate().equals(that.getDate())) {return false;}
//
//        if (!safeCheckForZeroSum(this.getAmount(), that.getAmount())) {return false;}
//
//        if(!this.getCurrency().equals(that.getCurrency())) {return false;}
//
//        return true;
//    }
//
//    private boolean safeCheckForZeroSum(final BigDecimal value1, final BigDecimal value2) {
//        if ((value1 == null) && (value2 == null)) {return true;}
//        if ((value1 == null) ^ (value2 == null)) {return false;}
//        return (value1.add(value2).compareTo(BigDecimal.ZERO) == 0);
//    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("InvoiceItem = {").append("id = ").append(id.toString()).append(", ");
        sb.append("invoiceId = ").append(invoiceId.toString()).append(", ");
        sb.append("subscriptionId = ").append(subscriptionId.toString()).append(", ");
        sb.append("planName = ").append(planName).append(", ");
        sb.append("phaseName = ").append(phaseName).append(", ");
        sb.append("date = ").append(date.toString()).append(", ");

        sb.append("amount = ");
        if (amount == null) {
            sb.append("null");
        } else {
            sb.append(amount.toString());
        }

        sb.append("}");
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FixedPriceInvoiceItem that = (FixedPriceInvoiceItem) o;

        if (amount != null ? !amount.equals(that.amount) : that.amount != null) return false;
        if (currency != that.currency) return false;
        if (date != null ? !date.equals(that.date) : that.date != null) return false;
        if (phaseName != null ? !phaseName.equals(that.phaseName) : that.phaseName != null) return false;
        if (planName != null ? !planName.equals(that.planName) : that.planName != null) return false;
        if (subscriptionId != null ? !subscriptionId.equals(that.subscriptionId) : that.subscriptionId != null)
            return false;

        return true;
    }
}
