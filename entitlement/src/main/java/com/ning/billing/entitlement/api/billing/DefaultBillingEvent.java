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

package com.ning.billing.entitlement.api.billing;

import java.math.BigDecimal;

import org.joda.time.DateTime;

import com.ning.billing.account.api.Account;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionEventTransition;
import com.ning.billing.entitlement.api.user.SubscriptionEventTransition.SubscriptionTransitionType;
import com.ning.billing.entitlement.api.user.SubscriptionTransitionData;

public class DefaultBillingEvent implements BillingEvent {
    final private Account account;
    final private int billCycleDay;
    final private Subscription subscription;
    final private DateTime effectiveDate;
    final private PlanPhase planPhase;
    final private Plan plan;
    final private BigDecimal fixedPrice;
    final private BigDecimal recurringPrice;
    final private Currency currency;
    final private String description;
    final private BillingModeType billingModeType;
    final private BillingPeriod billingPeriod;
    final private SubscriptionTransitionType type;
    final private Long totalOrdering;

    public DefaultBillingEvent(Account account, SubscriptionEventTransition transition, Subscription subscription, int billCycleDay, Currency currency) throws CatalogApiException {
        this.account = account;
        this.billCycleDay = billCycleDay;
        this.subscription = subscription;
        effectiveDate = transition.getEffectiveTransitionTime();
        planPhase = (transition.getTransitionType() != SubscriptionTransitionType.CANCEL) ?
                transition.getNextPhase() : transition.getPreviousPhase();
        plan = (transition.getTransitionType() != SubscriptionTransitionType.CANCEL) ?
                transition.getNextPlan() : transition.getPreviousPlan();
        fixedPrice = (transition.getNextPhase() == null) ? null :
        		(transition.getNextPhase().getFixedPrice() == null) ? null :
                        transition.getNextPhase().getFixedPrice().getPrice(currency);
        recurringPrice = (transition.getNextPhase() == null) ? null :
                (transition.getNextPhase().getRecurringPrice() == null) ? null :
                        transition.getNextPhase().getRecurringPrice().getPrice(currency);
        this.currency = currency;
        description = transition.getTransitionType().toString();
        billingModeType = BillingModeType.IN_ADVANCE;
        billingPeriod =  (transition.getTransitionType() != SubscriptionTransitionType.CANCEL) ?
                transition.getNextPhase().getBillingPeriod() : transition.getPreviousPhase().getBillingPeriod();
        type = transition.getTransitionType();
        totalOrdering = ((SubscriptionTransitionData) transition).getTotalOrdering();
    }

    public DefaultBillingEvent(Account account, Subscription subscription, DateTime effectiveDate, Plan plan, PlanPhase planPhase,
                               BigDecimal fixedPrice, BigDecimal recurringPrice, Currency currency,
                               BillingPeriod billingPeriod, int billCycleDay, BillingModeType billingModeType,
                               String description, long totalOrdering, SubscriptionTransitionType type) {
        this.account = account;
        this.subscription = subscription;
        this.effectiveDate = effectiveDate;
        this.plan = plan;
        this.planPhase = planPhase;
        this.fixedPrice = fixedPrice;
        this.recurringPrice = recurringPrice;
        this.currency = currency;
        this.billingPeriod = billingPeriod;
        this.billCycleDay = billCycleDay;
        this.billingModeType = billingModeType;
        this.description = description;
        this.type = type;
        this.totalOrdering = totalOrdering;
    }


    @Override
    public int compareTo(BillingEvent e1) {
    	 if (!getSubscription().getId().equals(e1.getSubscription().getId())) { // First order by subscription
    		 return getSubscription().getId().compareTo(e1.getSubscription().getId());
    	 } else { // subscriptions are the same
    		 if (! getEffectiveDate().equals(e1.getEffectiveDate())) { // Secondly order by date
                 return getEffectiveDate().compareTo(e1.getEffectiveDate());
    		 } else { // dates and subscriptions are the same
    			 return getTotalOrdering().compareTo(e1.getTotalOrdering());
    		 }
    	 }
    }

    @Override
    public Account getAccount() {
         return account;
    }

    @Override
    public int getBillCycleDay() {
         return billCycleDay;
    }

    @Override
    public Subscription getSubscription() {
        return subscription;
    }

    @Override
    public DateTime getEffectiveDate() {
        return effectiveDate;
    }

    @Override
    public PlanPhase getPlanPhase() {
        return planPhase;
    }

    @Override
    public Plan getPlan() {
        return plan;
    }

    @Override
    public BillingPeriod getBillingPeriod() {
        return billingPeriod;
    }

    @Override
    public BillingModeType getBillingMode() {
        return billingModeType;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public BigDecimal getFixedPrice() {
        return fixedPrice;
    }

    @Override
    public BigDecimal getRecurringPrice() {
        return recurringPrice;
    }

    @Override
    public Currency getCurrency() {
        return currency;
    }

    @Override
    public SubscriptionTransitionType getTransitionType() {
        return type;
    }

    @Override
    public Long getTotalOrdering() {
        return totalOrdering;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("BillingEvent {subscriptionId = ").append(subscription.getId().toString()).append(", ");
        sb.append("plan = ").append(plan.getName()).append(", ");
        sb.append("phase = ").append(planPhase.getName()).append(", ");
        sb.append("effectiveDate = ").append(effectiveDate.toString()).append(", ");
        sb.append("billCycleDay = ").append(billCycleDay).append(", ");
        sb.append("recurringPrice = ");

        try {
            sb.append(recurringPrice.toString());
        } catch (Exception e) {
            sb.append("null");
        }

        sb.append(", ");
        sb.append("fixedPrice = ");

        try {
            sb.append(fixedPrice.toString());
        } catch (Exception e) {
            sb.append("null");
        }
        sb.append(", ");

        sb.append("currency = ").append(currency.toString()).append(", ");
        sb.append("billingPeriod = ").append(billingPeriod.toString());
        sb.append(", ");
        sb.append("totalOrdering = ").append(getTotalOrdering().toString());
        sb.append("}");

        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DefaultBillingEvent that = (DefaultBillingEvent) o;

        if (billCycleDay != that.billCycleDay) return false;
        if (billingModeType != that.billingModeType) return false;
        if (billingPeriod != that.billingPeriod) return false;
        if (currency != that.currency) return false;
        if (!description.equals(that.description)) return false;
        if (!effectiveDate.equals(that.effectiveDate)) return false;
        if (fixedPrice != null ? !fixedPrice.equals(that.fixedPrice) : that.fixedPrice != null) return false;
        if (!plan.equals(that.plan)) return false;
        if (!planPhase.equals(that.planPhase)) return false;
        if (recurringPrice != null ? !recurringPrice.equals(that.recurringPrice) : that.recurringPrice != null)
            return false;
        if (!subscription.equals(that.subscription)) return false;
        if (!totalOrdering.equals(that.totalOrdering)) return false;
        if (type != that.type) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = billCycleDay;
        result = 31 * result + subscription.hashCode();
        result = 31 * result + effectiveDate.hashCode();
        result = 31 * result + planPhase.hashCode();
        result = 31 * result + plan.hashCode();
        result = 31 * result + (fixedPrice != null ? fixedPrice.hashCode() : 0);
        result = 31 * result + (recurringPrice != null ? recurringPrice.hashCode() : 0);
        result = 31 * result + currency.hashCode();
        result = 31 * result + description.hashCode();
        result = 31 * result + billingModeType.hashCode();
        result = 31 * result + billingPeriod.hashCode();
        result = 31 * result + type.hashCode();
        result = 31 * result + totalOrdering.hashCode();
        return result;
    }
}