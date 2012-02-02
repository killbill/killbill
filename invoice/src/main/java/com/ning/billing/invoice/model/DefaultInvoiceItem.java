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
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionTransition;
import com.ning.billing.invoice.api.InvoiceItem;
import org.joda.time.DateTime;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.util.UUID;

public class DefaultInvoiceItem implements InvoiceItem {
    private final UUID id;
    private final UUID invoiceId;
    private final UUID subscriptionId;
    private final String planName;
    private final String phaseName;
    private DateTime startDate;
    private DateTime endDate;
    private BigDecimal recurringAmount;
    private final BigDecimal recurringRate;
    private BigDecimal fixedAmount;
    private final Currency currency;

    public DefaultInvoiceItem(UUID invoiceId, UUID subscriptionId, String planName, String phaseName,
                              DateTime startDate, DateTime endDate,
                              BigDecimal recurringAmount, BigDecimal recurringRate,
                              BigDecimal fixedAmount, Currency currency) {
        this(UUID.randomUUID(), invoiceId, subscriptionId, planName, phaseName, startDate, endDate,
             recurringAmount, recurringRate, fixedAmount, currency);
    }

    public DefaultInvoiceItem(UUID id, UUID invoiceId, UUID subscriptionId, String planName, String phaseName,
                              DateTime startDate, DateTime endDate,
                              BigDecimal recurringAmount, BigDecimal recurringRate,
                              BigDecimal fixedAmount, Currency currency) {
        this.id = id;
        this.invoiceId = invoiceId;
        this.subscriptionId = subscriptionId;
        this.planName = planName;
        this.phaseName = phaseName;
        this.startDate = startDate;
        this.endDate = endDate;
        this.recurringAmount = recurringAmount;
        this.recurringRate = recurringRate;
        this.fixedAmount = fixedAmount;
        this.currency = currency;
    }

    public DefaultInvoiceItem(InvoiceItem that, UUID invoiceId) {
        this.id = UUID.randomUUID();
        this.invoiceId = invoiceId;
        this.subscriptionId = that.getSubscriptionId();
        this.planName = that.getPlanName();
        this.phaseName = that.getPhaseName();
        this.startDate = that.getStartDate();
        this.endDate = that.getEndDate();
        this.recurringAmount = that.getRecurringAmount();
        this.recurringRate = that.getRecurringRate();
        this.fixedAmount = that.getFixedAmount();
        this.currency = that.getCurrency();
    }

    @Override
    public InvoiceItem asCredit(UUID invoiceId) {
        BigDecimal recurringAmountNegated = recurringAmount == null ? null : recurringAmount.negate();
        BigDecimal fixedAmountNegated = fixedAmount == null ? null : fixedAmount.negate();
        return new DefaultInvoiceItem(invoiceId, subscriptionId, planName, phaseName, startDate, endDate,
                                      recurringAmountNegated, recurringRate, fixedAmountNegated, currency);
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
    public DateTime getStartDate() {
        return startDate;
    }

    @Override
    public DateTime getEndDate() {
        return endDate;
    }

    @Override
    public BigDecimal getRecurringAmount() {
        return recurringAmount;
    }

    @Override
    public BigDecimal getRecurringRate() {
        return recurringRate;
    }

    @Override
    public BigDecimal getFixedAmount() {
        return fixedAmount;
    }

    @Override
    public Currency getCurrency() {
        return currency;
    }

    @Override
    public int compareTo(InvoiceItem that) {
        int compareSubscriptions = getSubscriptionId().compareTo(that.getSubscriptionId());

        if (compareSubscriptions == 0) {
            // move null end dates to the end of the set
            if ((this.endDate != null) && (that.getEndDate() == null)) {
                return -1;
            }

            if ((this.endDate == null) && (that.getEndDate() != null)) {
                return 1;
            }

            int compareStartDates = getStartDate().compareTo(that.getStartDate());
            return compareStartDates;
        } else {
            return compareSubscriptions;
        }
    }

    // TODO: deal with error cases
    @Override
    public void subtract(InvoiceItem that) {
        if (this.endDate == null) {
            // this is a fixed price item; set the fixed amount to null
            if (this.fixedAmount.compareTo(that.getFixedAmount()) == 0) {
                this.fixedAmount = null;
            }

            return;
        }

        if (this.startDate.equals(that.getStartDate()) && this.endDate.equals(that.getEndDate())) {
            this.startDate = this.endDate;
                this.recurringAmount = safeSubtract(this.recurringAmount, that.getRecurringAmount());
        } else {
            if (this.startDate.equals(that.getStartDate())) {
                this.startDate = that.getEndDate();
                this.recurringAmount = safeSubtract(this.recurringAmount, that.getRecurringAmount());
            }

            if (this.endDate.equals(that.getEndDate())) {
                this.endDate = that.getStartDate();
                this.recurringAmount = safeSubtract(this.recurringAmount, that.getRecurringAmount());
            }
        }
    }

    private BigDecimal safeSubtract(BigDecimal minuend, BigDecimal subtrahend) {
        // minuend - subtrahend == difference
        if (minuend == null) {
            if (subtrahend == null) {
                return BigDecimal.ZERO;
            } else {
                return subtrahend.negate();
            }
        } else {
            if (subtrahend == null) {
                return minuend;
            } else {
                return minuend.subtract(subtrahend);
            }
        }

    }

    @Override
    public boolean duplicates(InvoiceItem that) {
        if (!this.getSubscriptionId().equals(that.getSubscriptionId())) {return false;}

        if (!this.planName.equals(that.getPlanName())) {return false;}
        if (!this.phaseName.equals(that.getPhaseName())) {return false;}

        if (!compareNullableBigDecimal(this.getRecurringRate(), that.getRecurringRate())) {return false;}

        if (!this.getCurrency().equals(that.getCurrency())) {return false;}

        if ((this.endDate == null) && (that.getEndDate() == null) && (this.startDate.compareTo(that.getStartDate()) == 0)) {
            return true;
        }

        DateRange thisDateRange = new DateRange(this.getStartDate(), this.getEndDate());
        return thisDateRange.contains(that.getStartDate()) && (that.getEndDate() == null || thisDateRange.contains(that.getEndDate()));
    }

    private boolean compareNullableBigDecimal(@Nullable BigDecimal value1, @Nullable BigDecimal value2) {
        if ((value1 == null) && (value2 != null)) {return false;}
        if ((value1 != null) && (value2 == null)) {return false;}

        if ((value1 != null) && (value2 != null)) {
            if (!value1.equals(value2)) {return false;}
        }

        return true;
    }

    /**
     * indicates whether the supplied item is a cancelling item for this item
     * @param that  the InvoiceItem to be examined
     * @return true if the two invoice items cancel each other out (same subscription, same date range, sum of amounts = 0)
     */
    @Override
    public boolean cancels(InvoiceItem that) {
        if(!this.getSubscriptionId().equals(that.getSubscriptionId())) {return false;}
        if(!this.getEndDate().equals(that.getEndDate())) {return false;}
        if(!this.getStartDate().equals(that.getStartDate())) {return false;}

        if (!safeCheckForZeroSum(this.getRecurringAmount(), that.getRecurringAmount())) {return false;}
        if(!this.getRecurringRate().equals(that.getRecurringRate())) {return false;}

        if (!safeCheckForZeroSum(this.getFixedAmount(), that.getFixedAmount())) {return false;}
        if(!this.getCurrency().equals(that.getCurrency())) {return false;}

        return true;
    }

    private boolean safeCheckForZeroSum(final BigDecimal value1, final BigDecimal value2) {
        if ((value1 == null) && (value2 == null)) {return true;}
        if ((value1 == null) ^ (value2 == null)) {return false;}
        return (value1.add(value2).compareTo(BigDecimal.ZERO) == 0);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("InvoiceItem = {").append("id = ").append(id.toString()).append(", ");
        sb.append("invoiceId = ").append(invoiceId.toString()).append(", ");
        sb.append("subscriptionId = ").append(subscriptionId.toString()).append(", ");
        sb.append("planName = ").append(planName).append(", ");
        sb.append("phaseName = ").append(phaseName).append(", ");
        sb.append("startDate = ").append(startDate.toString()).append(", ");
        sb.append("endDate = ").append(startDate.toString()).append(", ");
        sb.append("recurringAmount = ");
        if (recurringAmount == null) {
            sb.append("null");
        } else {
            sb.append(recurringAmount.toString());
        }
        sb.append(", ");

        sb.append("recurringRate = ");
        if (recurringRate == null) {
            sb.append("null");
        } else {
            sb.append(recurringRate.toString());
        }
        sb.append(", ");

        sb.append("fixedAmount = ");
        if (fixedAmount == null) {
            sb.append("null");
        } else {
            sb.append(fixedAmount.toString());
        }

        sb.append("}");
        return sb.toString();
    }
}
