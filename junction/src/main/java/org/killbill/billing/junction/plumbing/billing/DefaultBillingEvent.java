/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.junction.plumbing.billing;

import java.math.BigDecimal;
import java.util.List;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.killbill.billing.account.api.ImmutableAccountData;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Catalog;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.Usage;
import org.killbill.billing.events.EffectiveSubscriptionInternalEvent;
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
    private final DateTimeZone timeZone;

    private final List<Usage> usages;

    private final Catalog catalog;
    private final boolean isDisableEvent;
    private final PlanPhase nextPlanPhase;

    public DefaultBillingEvent(final ImmutableAccountData account, final EffectiveSubscriptionInternalEvent transition, final SubscriptionBase subscription, final int billCycleDayLocal, final Currency currency, final Catalog catalog) throws CatalogApiException {

        this.catalog = catalog;

        final boolean isActive = transition.getTransitionType() != SubscriptionBaseTransitionType.CANCEL;

        this.billCycleDayLocal = billCycleDayLocal;
        this.subscription = subscription;
        this.effectiveDate = transition.getEffectiveTransitionTime();
        final String planPhaseName = isActive ? transition.getNextPhase() : transition.getPreviousPhase();
        this.planPhase = (planPhaseName != null) ? catalog.findPhase(planPhaseName, transition.getEffectiveTransitionTime(), transition.getSubscriptionStartDate()) : null;

        final String planName = isActive ? transition.getNextPlan() : transition.getPreviousPlan();
        this.plan = (planName != null) ? catalog.findPlan(planName, transition.getEffectiveTransitionTime(), transition.getSubscriptionStartDate()) : null;

        final String nextPhaseName = transition.getNextPhase();
        this.nextPlanPhase = (nextPhaseName != null) ? catalog.findPhase(nextPhaseName, transition.getEffectiveTransitionTime(), transition.getSubscriptionStartDate()) : null;

        final String prevPhaseName = transition.getPreviousPhase();
        final PlanPhase prevPlanPhase = (prevPhaseName != null) ? catalog.findPhase(prevPhaseName, transition.getEffectiveTransitionTime(), transition.getSubscriptionStartDate()) : null;

        this.fixedPrice = transition.getTransitionType() != SubscriptionBaseTransitionType.BCD_CHANGE ? getFixedPrice(nextPlanPhase, currency) : null;
        this.currency = currency;
        this.description = transition.getTransitionType().toString();
        this.billingPeriod = getRecurringBillingPeriod(isActive ? nextPlanPhase : prevPlanPhase);
        this.type = transition.getTransitionType();
        this.totalOrdering = transition.getTotalOrdering();
        this.timeZone = account.getTimeZone();
        this.usages = initializeUsage(isActive);
        this.isDisableEvent = false;
    }

    public DefaultBillingEvent(final SubscriptionBase subscription, final DateTime effectiveDate, final boolean isActive,
                               final Plan plan, final PlanPhase planPhase, final BigDecimal fixedPrice,
                               final Currency currency,
                               final BillingPeriod billingPeriod, final int billCycleDayLocal,
                               final String description, final long totalOrdering, final SubscriptionBaseTransitionType type, final DateTimeZone timeZone,
                               final Catalog catalog,
                               final boolean isDisableEvent) {
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
        this.timeZone = timeZone;
        this.usages = initializeUsage(isActive);
        this.isDisableEvent = isDisableEvent;
        this.nextPlanPhase = isDisableEvent ? null : planPhase;
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
    public DateTimeZone getTimeZone() {
        return timeZone;
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
        if (timeZone != null ? !timeZone.equals(that.timeZone) : that.timeZone != null) {
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
        result = 31 * result + (timeZone != null ? timeZone.hashCode() : 0);
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
}
