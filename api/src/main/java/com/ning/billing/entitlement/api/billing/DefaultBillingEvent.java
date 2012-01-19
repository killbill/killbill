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
import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.InternationalPrice;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionTransition;

public class DefaultBillingEvent implements BillingEvent {
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

    public DefaultBillingEvent(SubscriptionTransition transition, Subscription subscription, int billCycleDay) {
        this.billCycleDay = billCycleDay;
        this.subscription = subscription;
        effectiveDate = transition.getEffectiveTransitionTime();
        planPhase = transition.getNextPhase();
        plan = transition.getNextPlan();
        fixedPrice = transition.getNextPhase().getFixedPrice();
        recurringPrice = transition.getNextPhase().getRecurringPrice();
        description = transition.getTransitionType().toString();
        billingModeType=BillingModeType.IN_ADVANCE;
        billingPeriod = transition.getNextPhase().getBillingPeriod();

    }

    // Intended for test only
    public DefaultBillingEvent(Subscription subscription, DateTime effectiveDate, Plan plan, PlanPhase planPhase, InternationalPrice fixedPrice,
            InternationalPrice recurringPrice, BillingPeriod billingPeriod, int billCycleDay, BillingModeType billingModeType, String description) {
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
    }

    @Override
    public int compareTo(BillingEvent e1) {
        if (getSubscription().getId().equals(e1.getSubscription().getId())) {
            if (getEffectiveDate().equals(e1.getEffectiveDate())) { //ordering with a HashSet fails if we get equality
                return hashCode() - e1.hashCode();
            } else {
                return getEffectiveDate().compareTo(e1.getEffectiveDate());
            }
        } else {
            return getSubscription().getId().compareTo(e1.getSubscription().getId());
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
    public BigDecimal getFixedPrice(Currency currency) throws CatalogApiException {
        return  fixedPrice != null ? fixedPrice.getPrice(currency) : new BigDecimal(0);
    }

    @Override
    public BigDecimal getRecurringPrice(Currency currency) throws CatalogApiException {
        return  recurringPrice != null ? recurringPrice.getPrice(currency) : new BigDecimal(0);
    }

}