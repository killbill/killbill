/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanChangeResult;
import org.killbill.billing.catalog.api.PlanSpecifier;
import org.killbill.billing.entitlement.api.EntitlementSpecifier;
import org.killbill.billing.subscription.api.user.DefaultSubscriptionBase;
import org.killbill.billing.subscription.api.user.SubscriptionAndAddOnsSpecifier;
import org.killbill.billing.subscription.api.user.SubscriptionBaseApiException;
import org.killbill.billing.subscription.catalog.SubscriptionCatalog;
import org.killbill.billing.subscription.events.SubscriptionBaseEvent;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.TenantContext;

public interface SubscriptionBaseApiService {

    public List<SubscriptionBaseWithAddOns> createPlansWithAddOns(UUID accountId, Iterable<SubscriptionAndAddOnsSpecifier> subscriptionsAndAddOns, SubscriptionCatalog catalog, CallContext context)
            throws SubscriptionBaseApiException;

    public boolean cancel(DefaultSubscriptionBase subscription, CallContext context)
            throws SubscriptionBaseApiException;

    public boolean cancelWithRequestedDate(DefaultSubscriptionBase subscription, DateTime requestedDate, CallContext context)
            throws SubscriptionBaseApiException;

    public boolean cancelWithPolicy(DefaultSubscriptionBase subscription, BillingActionPolicy policy, CallContext context)
            throws SubscriptionBaseApiException;

    public boolean cancelWithPolicyNoValidationAndCatalog(Iterable<DefaultSubscriptionBase> subscriptions, BillingActionPolicy policy, SubscriptionCatalog catalog, InternalCallContext context)
            throws SubscriptionBaseApiException;

    public boolean uncancel(DefaultSubscriptionBase subscription, CallContext context)
            throws SubscriptionBaseApiException;

    // Return the effective date of the change
    public DateTime dryRunChangePlan(DefaultSubscriptionBase subscription, EntitlementSpecifier spec, DateTime requestedDate, BillingActionPolicy policy, TenantContext context) throws SubscriptionBaseApiException;

    // Return the effective date of the change
    public DateTime changePlan(DefaultSubscriptionBase subscription, EntitlementSpecifier spec, CallContext context)
            throws SubscriptionBaseApiException;

    // Return the effective date of the change
    public DateTime changePlanWithRequestedDate(DefaultSubscriptionBase subscription, EntitlementSpecifier spec,
                                                DateTime requestedDate, CallContext context)
            throws SubscriptionBaseApiException;

    // Return the effective date of the change
    public DateTime changePlanWithPolicy(DefaultSubscriptionBase subscription, EntitlementSpecifier spec,
                                         BillingActionPolicy policy, CallContext context)
            throws SubscriptionBaseApiException;

    public int handleBasePlanEvent(final DefaultSubscriptionBase subscription, final SubscriptionBaseEvent event, SubscriptionCatalog catalog, final CallContext context) throws CatalogApiException;

    public PlanChangeResult getPlanChangeResult(final DefaultSubscriptionBase subscription, PlanSpecifier spec, final DateTime effectiveDate, TenantContext context) throws SubscriptionBaseApiException;

    //
    // Lower level APIs for dryRun functionality
    //

    public List<SubscriptionBaseEvent> getEventsOnCreation(UUID subscriptionId, DateTime alignStartDate, DateTime bundleStartDate,
                                                           Plan plan, PhaseType initialPhase,
                                                           String realPriceList, DateTime effectiveDate,
                                                           Integer bcd,
                                                           SubscriptionCatalog catalog,
                                                           InternalTenantContext context)
            throws CatalogApiException, SubscriptionBaseApiException;

    public List<SubscriptionBaseEvent> getEventsOnChangePlan(DefaultSubscriptionBase subscription, Plan newPlan,
                                                             String newPriceList, DateTime effectiveDate,
                                                             boolean addCancellationAddOnForEventsIfRequired,
                                                             Integer bcd,
                                                             PhaseType requestedPhaseType,
                                                             SubscriptionCatalog catalog,
                                                             InternalTenantContext context)
            throws CatalogApiException, SubscriptionBaseApiException;

    public List<SubscriptionBaseEvent> getEventsOnCancelPlan(final DefaultSubscriptionBase subscription,
                                                             final DateTime effectiveDate,
                                                             final boolean addCancellationAddOnForEventsIfRequired,
                                                             final SubscriptionCatalog catalog,
                                                             final InternalTenantContext internalTenantContext) throws CatalogApiException;

    boolean undoChangePlan(DefaultSubscriptionBase defaultSubscriptionBase, CallContext context) throws SubscriptionBaseApiException;

    int getAccountBCD(InternalTenantContext context) throws SubscriptionBaseApiException;

    // If we expose more than one config, we should instead return the SubscriptionConfig object
    boolean isEffectiveDateForExistingSubscriptionsAlignedToBCD(final InternalTenantContext tenantContext);
}
