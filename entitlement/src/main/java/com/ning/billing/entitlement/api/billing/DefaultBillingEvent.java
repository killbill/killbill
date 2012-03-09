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

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.InternationalPrice;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionTransition;
import com.ning.billing.entitlement.api.user.SubscriptionTransition.SubscriptionTransitionType;
import com.ning.billing.entitlement.api.user.SubscriptionTransitionData;

public class DefaultBillingEvent implements BillingEvent {
	Logger log = LoggerFactory.getLogger(DefaultBillingEvent.class);

    final private int billCycleDay;
    final private Subscription subscription;
    final private DateTime effectiveDate;
    final private PlanPhase planPhase;
    final private Plan plan;
    final private InternationalPrice fixedPrice;
    final private InternationalPrice recurringPrice;
    final private String description;
    final private BillingModeType billingModeType;
    final private BillingPeriod billingPeriod;
    final private SubscriptionTransitionType type;
    final private long totalOrdering;

    public DefaultBillingEvent(SubscriptionTransition transition, Subscription subscription, int billCycleDay) {
        this.billCycleDay = billCycleDay;
        this.subscription = subscription;
        effectiveDate = transition.getEffectiveTransitionTime();
        planPhase = (transition.getTransitionType() != SubscriptionTransitionType.CANCEL) ?
                transition.getNextPhase() : transition.getPreviousPhase();
        plan = (transition.getTransitionType() != SubscriptionTransitionType.CANCEL) ?
                transition.getNextPlan() : transition.getPreviousPlan();
        fixedPrice = (transition.getNextPhase() == null) ? null :
        		transition.getNextPhase().getFixedPrice();
        recurringPrice = (transition.getNextPhase() == null) ? null :
        	transition.getNextPhase().getRecurringPrice();
        description = transition.getTransitionType().toString();
        billingModeType = BillingModeType.IN_ADVANCE;
        billingPeriod =  (transition.getTransitionType() != SubscriptionTransitionType.CANCEL) ?
                transition.getNextPhase().getBillingPeriod() : transition.getPreviousPhase().getBillingPeriod();
        type = transition.getTransitionType();
        totalOrdering = ((SubscriptionTransitionData) transition).getTotalOrdering();
    }

    // Intended for test only
    public DefaultBillingEvent(Subscription subscription, DateTime effectiveDate, Plan plan, PlanPhase planPhase, InternationalPrice fixedPrice,
            InternationalPrice recurringPrice, BillingPeriod billingPeriod, int billCycleDay, BillingModeType billingModeType, String description,
            long totalOrdering, SubscriptionTransitionType type) {
        this.subscription = subscription;
        this.effectiveDate = effectiveDate;
        this.plan = plan;
        this.planPhase = planPhase;
        this.fixedPrice = fixedPrice;
        this.recurringPrice = recurringPrice;
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
    			 if (!getTransitionType().equals(e1.getTransitionType())) { // Finally compare by transition type
    				 return getTransitionType().ordinal() - e1.getTransitionType().ordinal();
    			 } else {
    				 return hashCode() - e1.hashCode();
    			 }
    		 }
    	 }
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
    public InternationalPrice getFixedPrice() {
        return fixedPrice;
    }

    @Override
    public InternationalPrice getRecurringPrice() {
        return recurringPrice;
    }

    @Override
    public SubscriptionTransitionType getTransitionType() {
        return type;
    }

    @Override
    public long getTotalOrdering() {
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
        sb.append("recurringPrice(USD) = ");

        try {
            sb.append(recurringPrice.getPrice(Currency.USD).toString());
        } catch (Exception e) {
            sb.append("null");
        }

        sb.append(", ");
        sb.append("fixedPrice(USD) = ");

        try {
            sb.append(fixedPrice.getPrice(Currency.USD).toString());
        } catch (Exception e) {
            sb.append("null");
        }

        sb.append(", ");

        sb.append("billingPeriod = ").append(billingPeriod.toString());
        sb.append("}");

        return sb.toString();
    }
}