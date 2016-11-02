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

package org.killbill.billing.subscription.api;

import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;

import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.PlanPhasePriceOverride;
import org.killbill.billing.catalog.api.PlanSpecifier;
import org.killbill.billing.catalog.api.PriceList;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.Blockable;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementSourceType;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.subscription.api.user.SubscriptionBaseApiException;
import org.killbill.billing.subscription.api.user.SubscriptionBaseTransition;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.entity.Entity;

public interface SubscriptionBase extends Entity, Blockable {

    public boolean cancel(final CallContext context)
            throws SubscriptionBaseApiException;

    public boolean cancelWithDate(final DateTime requestedDate, final CallContext context)
            throws SubscriptionBaseApiException;

    public boolean cancelWithPolicy(final BillingActionPolicy policy, final CallContext context)
            throws SubscriptionBaseApiException;

    public boolean uncancel(final CallContext context)
            throws SubscriptionBaseApiException;

    // Return the effective date of the change
    public DateTime changePlan(final PlanSpecifier spec, final List<PlanPhasePriceOverride> overrides, final CallContext context)
            throws SubscriptionBaseApiException;

    // Return the effective date of the change
    public DateTime changePlanWithDate(final PlanSpecifier spec, final List<PlanPhasePriceOverride> overrides, final DateTime requestedDate, final CallContext context)
            throws SubscriptionBaseApiException;

    // Return the effective date of the change
    public DateTime changePlanWithPolicy(final PlanSpecifier spec, final List<PlanPhasePriceOverride> overrides,
                                         final BillingActionPolicy policy, final CallContext context)
            throws SubscriptionBaseApiException;

    public UUID getBundleId();

    public EntitlementState getState();

    public EntitlementSourceType getSourceType();

    public DateTime getStartDate();

    public DateTime getEndDate();

    public DateTime getFutureEndDate();

    public Plan getCurrentPlan();

    public Plan getLastActivePlan();

    public PlanPhase getLastActivePhase();

    public PriceList getCurrentPriceList();

    public PlanPhase getCurrentPhase();

    public Product getLastActiveProduct();

    public PriceList getLastActivePriceList();

    public ProductCategory getLastActiveCategory();

    public BillingPeriod getLastActiveBillingPeriod();

    public DateTime getChargedThroughDate();

    public boolean isMigrated();

    public ProductCategory getCategory();

    public Integer getBillCycleDayLocal();

    public SubscriptionBaseTransition getPendingTransition();

    public SubscriptionBaseTransition getPreviousTransition();

    public List<SubscriptionBaseTransition> getAllTransitions();

    public DateTime getDateOfFirstRecurringNonZeroCharge();
}
