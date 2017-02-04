/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

package org.killbill.billing.subscription.api;

import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanChangeResult;
import org.killbill.billing.catalog.api.PlanPhasePriceOverride;
import org.killbill.billing.catalog.api.PlanSpecifier;
import org.killbill.billing.subscription.api.user.DefaultSubscriptionBase;
import org.killbill.billing.subscription.api.user.DefaultSubscriptionBaseWithAddOns;
import org.killbill.billing.subscription.api.user.SubscriptionAndAddOnsSpecifier;
import org.killbill.billing.subscription.api.user.SubscriptionBaseApiException;
import org.killbill.billing.subscription.api.user.SubscriptionBuilder;
import org.killbill.billing.subscription.events.SubscriptionBaseEvent;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.TenantContext;

public interface SubscriptionBaseApiService {

    public DefaultSubscriptionBase createPlan(SubscriptionBuilder builder, Plan plan, PhaseType initialPhase,
                                              String realPriceList, DateTime effectiveDate, DateTime processedDate,
                                              CallContext context)
            throws SubscriptionBaseApiException;

    public List<SubscriptionBaseWithAddOns> createPlansWithAddOns(UUID accountId, Iterable<SubscriptionAndAddOnsSpecifier> subscriptionsAndAddOns, CallContext context)
            throws SubscriptionBaseApiException;

    public boolean cancel(DefaultSubscriptionBase subscription, CallContext context)
            throws SubscriptionBaseApiException;

    public boolean cancelWithRequestedDate(DefaultSubscriptionBase subscription, DateTime requestedDate, CallContext context)
            throws SubscriptionBaseApiException;

    public boolean cancelWithPolicy(DefaultSubscriptionBase subscription, BillingActionPolicy policy, DateTimeZone accountTimeZone, int accountBillCycleDayLocal, CallContext context)
            throws SubscriptionBaseApiException;

    public boolean cancelWithPolicyNoValidation(Iterable<DefaultSubscriptionBase> subscriptions, BillingActionPolicy policy, DateTimeZone accountTimeZone, int accountBillCycleDayLocal, InternalCallContext context)
            throws SubscriptionBaseApiException;

    public boolean uncancel(DefaultSubscriptionBase subscription, CallContext context)
            throws SubscriptionBaseApiException;

    // Return the effective date of the change
    public DateTime dryRunChangePlan(DefaultSubscriptionBase subscription, PlanSpecifier spec, DateTime requestedDate, BillingActionPolicy policy, TenantContext context) throws SubscriptionBaseApiException;

    // Return the effective date of the change
    public DateTime changePlan(DefaultSubscriptionBase subscription, PlanSpecifier spec, List<PlanPhasePriceOverride> overrides, CallContext context)
            throws SubscriptionBaseApiException;

    // Return the effective date of the change
    public DateTime changePlanWithRequestedDate(DefaultSubscriptionBase subscription, PlanSpecifier spec,
                                                List<PlanPhasePriceOverride> overrides, DateTime requestedDate, CallContext context)
            throws SubscriptionBaseApiException;

    // Return the effective date of the change
    public DateTime changePlanWithPolicy(DefaultSubscriptionBase subscription, PlanSpecifier spec,
                                         List<PlanPhasePriceOverride> overrides, BillingActionPolicy policy, CallContext context)
            throws SubscriptionBaseApiException;

    public int handleBasePlanEvent(final DefaultSubscriptionBase subscription, final SubscriptionBaseEvent event, final CallContext context) throws CatalogApiException;

    public PlanChangeResult getPlanChangeResult(final DefaultSubscriptionBase subscription, PlanSpecifier spec, final DateTime effectiveDate, TenantContext context) throws SubscriptionBaseApiException;

    //
    // Lower level APIs for dryRun functionality
    //
    public List<SubscriptionBaseEvent> getEventsOnCreation(UUID bundleId, UUID subscriptionId, DateTime alignStartDate, DateTime bundleStartDate,
                                                           Plan plan, PhaseType initialPhase,
                                                           String realPriceList, DateTime effectiveDate, DateTime processedDate,
                                                           InternalTenantContext context)
            throws CatalogApiException, SubscriptionBaseApiException;

    public List<SubscriptionBaseEvent> getEventsOnChangePlan(DefaultSubscriptionBase subscription, Plan newPlan,
                                                             String newPriceList, DateTime effectiveDate, DateTime processedDate,
                                                             boolean addCancellationAddOnForEventsIfRequired, InternalTenantContext context)
            throws CatalogApiException, SubscriptionBaseApiException;

    public List<SubscriptionBaseEvent> getEventsOnCancelPlan(final DefaultSubscriptionBase subscription,
                                                             final DateTime effectiveDate, final DateTime processedDate,
                                                             final boolean addCancellationAddOnForEventsIfRequired, final InternalTenantContext internalTenantContext) throws CatalogApiException;
}
