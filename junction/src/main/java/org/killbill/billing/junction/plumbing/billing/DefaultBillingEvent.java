/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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

package org.killbill.billing.junction.plumbing.billing;

import java.math.BigDecimal;
import java.util.List;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Catalog;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.Usage;
import org.killbill.billing.events.SubscriptionInternalEvent;
import org.killbill.billing.junction.BillingEvent;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class DefaultBillingEvent implements BillingEvent {

    private final int billCycleDayLocal;
    private final SubscriptionBase subscription;
    private final DateTime effectiveDate;
    private final PlanPhase planPhase;
    private final Plan plan;
    private final BigDecimal fixedPrice;
    private final Currency currency;
    private final String description;
    private final BillingPeriod billingPeriod;
    private final SubscriptionBaseTransitionType type;
    private final Long totalOrdering;

    private final List<Usage> usages;

    private final Catalog catalog;
    private final boolean isDisableEvent;
    private final PlanPhase nextPlanPhase;

    private final DateTime catalogEffectiveDate;

    public DefaultBillingEvent(final SubscriptionInternalEvent transition,
                               final SubscriptionBase subscription,
                               final int billCycleDayLocal,
                               final Currency currency,
                               final Catalog catalog) throws CatalogApiException {
        final boolean isActive = transition.getTransitionType() != SubscriptionBaseTransitionType.CANCEL;

        if (isActive) {
            final String planName = transition.getNextPlan();
            this.plan = (planName != null) ? catalog.findPlan(planName, transition.getEffectiveTransitionTime(), transition.getSubscriptionStartDate()) : null;

            final String planPhaseName = transition.getNextPhase();
            this.planPhase = (planPhaseName != null && this.plan != null) ? this.plan.findPhase(planPhaseName) : null;
            this.nextPlanPhase = this.planPhase;

            this.billingPeriod = getRecurringBillingPeriod(nextPlanPhase);
        } else {
            final String planName = transition.getPreviousPlan();
            this.plan = (planName != null) ? catalog.findPlan(planName, transition.getEffectiveTransitionTime(), transition.getSubscriptionStartDate()) : null;
            final Plan prevPlan = this.plan;

            final String planPhaseName = transition.getPreviousPhase();
            this.planPhase = (planPhaseName != null && this.plan != null) ? this.plan.findPhase(planPhaseName) : null;
            this.nextPlanPhase = null;

            final String prevPhaseName = transition.getPreviousPhase();
            final PlanPhase prevPlanPhase = (prevPhaseName != null && prevPlan != null) ? prevPlan.findPhase(prevPhaseName) : null;
            this.billingPeriod = getRecurringBillingPeriod(prevPlanPhase);
        }
        this.catalogEffectiveDate = plan == null ? null : new DateTime(plan.getCatalog().getEffectiveDate());

        this.billCycleDayLocal = billCycleDayLocal;
        this.catalog = catalog;
        this.currency = currency;
        this.description = transition.getTransitionType().toString();
        this.effectiveDate = transition.getEffectiveTransitionTime();
        this.fixedPrice = transition.getTransitionType() != SubscriptionBaseTransitionType.BCD_CHANGE ? getFixedPrice(nextPlanPhase, currency) : null;
        this.isDisableEvent = false;
        this.subscription = subscription;
        this.totalOrdering = transition.getTotalOrdering();
        this.type = transition.getTransitionType();
        this.usages = initializeUsage(isActive);
    }

    public DefaultBillingEvent(final SubscriptionBase subscription, final DateTime effectiveDate, final boolean isActive,
                               final Plan plan, final PlanPhase planPhase, final BigDecimal fixedPrice,
                               final Currency currency,
                               final BillingPeriod billingPeriod, final int billCycleDayLocal,
                               final String description, final long totalOrdering, final SubscriptionBaseTransitionType type,
                               final Catalog catalog,
                               final boolean isDisableEvent) throws CatalogApiException {
        this.catalog = catalog;
        this.subscription = subscription;
        this.effectiveDate = effectiveDate;
        this.plan = plan;
        this.planPhase = planPhase;
        this.fixedPrice = fixedPrice;
        this.currency = currency;
        this.billingPeriod = billingPeriod;
        this.billCycleDayLocal = billCycleDayLocal;
        this.description = description;
        this.type = type;
        this.totalOrdering = totalOrdering;
        this.usages = initializeUsage(isActive);
        this.isDisableEvent = isDisableEvent;
        this.nextPlanPhase = isDisableEvent ? null : planPhase;
        this.catalogEffectiveDate = plan != null ? new DateTime(plan.getCatalog().getEffectiveDate()) : null;
    }

    @Override
    public int compareTo(final BillingEvent e1) {
        if (!getSubscription().getId().equals(e1.getSubscription().getId())) { // First order by subscription
            return getSubscription().getId().compareTo(e1.getSubscription().getId());
        } else { // subscriptions are the same
            if (!getEffectiveDate().equals(e1.getEffectiveDate())) { // Secondly order by date
                return getEffectiveDate().compareTo(e1.getEffectiveDate());
            } else { // dates and subscriptions are the same
                // If an subscription event and an overdue event happen at the exact same time,
                // we assume we want the subscription event before the overdue event when entering
                // the overdue period, and vice-versa when exiting the overdue period
                if (SubscriptionBaseTransitionType.START_BILLING_DISABLED.equals(getTransitionType())) {
                    if (SubscriptionBaseTransitionType.END_BILLING_DISABLED.equals(e1.getTransitionType())) {
                        // Make sure to always have START before END
                        return -1;
                    } else {
                        return 1;
                    }
                } else if (SubscriptionBaseTransitionType.START_BILLING_DISABLED.equals(e1.getTransitionType())) {
                    if (SubscriptionBaseTransitionType.END_BILLING_DISABLED.equals(getTransitionType())) {
                        // Make sure to always have START before END
                        return 1;
                    } else {
                        return -1;
                    }
                } else if (SubscriptionBaseTransitionType.END_BILLING_DISABLED.equals(getTransitionType())) {
                    if (SubscriptionBaseTransitionType.START_BILLING_DISABLED.equals(e1.getTransitionType())) {
                        // Make sure to always have START before END
                        return 1;
                    } else {
                        return -1;
                    }
                } else if (SubscriptionBaseTransitionType.END_BILLING_DISABLED.equals(e1.getTransitionType())) {
                    if (SubscriptionBaseTransitionType.START_BILLING_DISABLED.equals(getTransitionType())) {
                        // Make sure to always have START before END
                        return -1;
                    } else {
                        return 1;
                    }
                } else {
                    return getTotalOrdering().compareTo(e1.getTotalOrdering());
                }
            }
        }
    }

    @Override
    public int getBillCycleDayLocal() {
        return billCycleDayLocal;
    }

    @Override
    public SubscriptionBase getSubscription() {
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
    public String getDescription() {
        return description;
    }

    @Override
    public BigDecimal getFixedPrice() {
        return fixedPrice;
    }

    @Override
    public BigDecimal getRecurringPrice(final DateTime effectiveDate) throws CatalogApiException {
        if (isDisableEvent || nextPlanPhase == null) {
            return null;
        }
        final PlanPhase effectivePlanPhase = effectiveDate != null ? catalog.findPhase(nextPlanPhase.getName(), effectiveDate, subscription.getStartDate()) : nextPlanPhase;
        return getRecurringPrice(effectivePlanPhase, currency);
    }

    @Override
    public Currency getCurrency() {
        return currency;
    }

    @Override
    public SubscriptionBaseTransitionType getTransitionType() {
        return type;
    }

    @Override
    public Long getTotalOrdering() {
        return totalOrdering;
    }

    @Override
    public List<Usage> getUsages() {
        return usages;
    }

    @Override
    public String toString() {
        // Note: we don't use all fields here, as the output would be overwhelming
        // (these events are printed in the logs in junction and invoice).
        final StringBuilder sb = new StringBuilder();
        sb.append("DefaultBillingEvent");
        sb.append("{type=").append(type);
        sb.append(", effectiveDate=").append(effectiveDate);
        sb.append(", planPhaseName=").append(planPhase.getName());
        sb.append(", subscriptionId=").append(subscription.getId());
        sb.append(", totalOrdering=").append(totalOrdering);
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

        final DefaultBillingEvent that = (DefaultBillingEvent) o;

        if (billCycleDayLocal != that.billCycleDayLocal) {
            return false;
        }
        if (billingPeriod != that.billingPeriod) {
            return false;
        }
        if (currency != that.currency) {
            return false;
        }
        if (fixedPrice != null ? !fixedPrice.equals(that.fixedPrice) : that.fixedPrice != null) {
            return false;
        }
        if (description != null ? !description.equals(that.description) : that.description != null) {
            return false;
        }
        if (effectiveDate != null ? !effectiveDate.equals(that.effectiveDate) : that.effectiveDate != null) {
            return false;
        }
        if (plan != null ? !plan.equals(that.plan) : that.plan != null) {
            return false;
        }
        if (planPhase != null ? !planPhase.equals(that.planPhase) : that.planPhase != null) {
            return false;
        }
        if (subscription != null ? !subscription.equals(that.subscription) : that.subscription != null) {
            return false;
        }
        if (totalOrdering != null ? !totalOrdering.equals(that.totalOrdering) : that.totalOrdering != null) {
            return false;
        }
        if (type != that.type) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = 31 * billCycleDayLocal;
        result = 31 * result + (subscription != null ? subscription.hashCode() : 0);
        result = 31 * result + (fixedPrice != null ? fixedPrice.hashCode() : 0);
        result = 31 * result + (effectiveDate != null ? effectiveDate.hashCode() : 0);
        result = 31 * result + (planPhase != null ? planPhase.hashCode() : 0);
        result = 31 * result + (plan != null ? plan.hashCode() : 0);
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        result = 31 * result + (description != null ? description.hashCode() : 0);
        result = 31 * result + (billingPeriod != null ? billingPeriod.hashCode() : 0);
        result = 31 * result + (type != null ? type.hashCode() : 0);
        result = 31 * result + (totalOrdering != null ? totalOrdering.hashCode() : 0);
        return result;
    }

    private BigDecimal getFixedPrice(@Nullable final PlanPhase nextPhase, final Currency currency) throws CatalogApiException {
        return (nextPhase != null && nextPhase.getFixed() != null && nextPhase.getFixed().getPrice() != null) ? nextPhase.getFixed().getPrice().getPrice(currency) : null;
    }

    private BigDecimal getRecurringPrice(@Nullable final PlanPhase nextPhase, final Currency currency) throws CatalogApiException {
        return (nextPhase != null && nextPhase.getRecurring() != null && nextPhase.getRecurring().getRecurringPrice() != null) ? nextPhase.getRecurring().getRecurringPrice().getPrice(currency) : null;
    }

    private BillingPeriod getRecurringBillingPeriod(@Nullable final PlanPhase nextPhase) {
        if (nextPhase == null) {
            return BillingPeriod.NO_BILLING_PERIOD;
        }
        return nextPhase.getRecurring() != null ? nextPhase.getRecurring().getBillingPeriod() : BillingPeriod.NO_BILLING_PERIOD;
    }

    private List<Usage> initializeUsage(final boolean isActive) {
        List<Usage> result = ImmutableList.<Usage>of();
        if (!isActive) {
            return result;
        }
        if (planPhase != null && planPhase.getUsages().length > 0) {
            result = Lists.newArrayList();
            for (Usage usage : planPhase.getUsages()) {
                result.add(usage);
            }
        }
        return result;
    }

    @Override
    public DateTime getCatalogEffectiveDate() {
        return catalogEffectiveDate;
    }
}
