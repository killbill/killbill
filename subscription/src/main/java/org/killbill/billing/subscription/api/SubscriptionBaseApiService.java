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
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanChangeResult;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.subscription.api.user.DefaultSubscriptionBase;
import org.killbill.billing.subscription.api.user.SubscriptionBaseApiException;
import org.killbill.billing.subscription.api.user.SubscriptionBuilder;
import org.killbill.billing.subscription.events.SubscriptionBaseEvent;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.TenantContext;

public interface SubscriptionBaseApiService {

    public DefaultSubscriptionBase createPlan(SubscriptionBuilder builder, Plan plan, PhaseType initialPhase,
                                              String realPriceList, DateTime requestedDate, DateTime effectiveDate, DateTime processedDate,
                                              CallContext context)
            throws SubscriptionBaseApiException;

    @Deprecated
    public boolean recreatePlan(DefaultSubscriptionBase subscription, PlanPhaseSpecifier spec, DateTime requestedDateWithMs, CallContext context)
            throws SubscriptionBaseApiException;

    public boolean cancel(DefaultSubscriptionBase subscription, CallContext context)
            throws SubscriptionBaseApiException;

    public boolean cancelWithRequestedDate(DefaultSubscriptionBase subscription, DateTime requestedDate, CallContext context)
            throws SubscriptionBaseApiException;

    public boolean cancelWithPolicy(DefaultSubscriptionBase subscription, BillingActionPolicy policy, CallContext context)
            throws SubscriptionBaseApiException;

    public boolean uncancel(DefaultSubscriptionBase subscription, CallContext context)
            throws SubscriptionBaseApiException;

    // Return the effective date of the change
    public DateTime changePlan(DefaultSubscriptionBase subscription, String productName, BillingPeriod term,
                               String priceList, CallContext context)
            throws SubscriptionBaseApiException;

    // Return the effective date of the change
    public DateTime changePlanWithRequestedDate(DefaultSubscriptionBase subscription, String productName, BillingPeriod term,
                                                String priceList, DateTime requestedDate, CallContext context)
            throws SubscriptionBaseApiException;

    // Return the effective date of the change
    public DateTime changePlanWithPolicy(DefaultSubscriptionBase subscription, String productName, BillingPeriod term,
                                         String priceList, BillingActionPolicy policy, CallContext context)
            throws SubscriptionBaseApiException;

    public int cancelAddOnsIfRequired(final Product baseProduct, final UUID bundleId, final DateTime effectiveDate, final CallContext context) throws CatalogApiException;

    public PlanChangeResult getPlanChangeResult(final DefaultSubscriptionBase subscription, final String productName,
                                                final BillingPeriod term, final String priceList, final DateTime effectiveDate, TenantContext context) throws SubscriptionBaseApiException;

        //
    // Lower level APIs for dryRun functionality
    //
    public List<SubscriptionBaseEvent> getEventsOnCreation(UUID bundleId, UUID subscriptionId, DateTime alignStartDate, DateTime bundleStartDate, long activeVersion,
                                                           Plan plan, PhaseType initialPhase,
                                                           String realPriceList, DateTime requestedDate, DateTime effectiveDate, DateTime processedDate,
                                                           boolean reCreate, TenantContext context)
            throws CatalogApiException, SubscriptionBaseApiException;

    public List<SubscriptionBaseEvent> getEventsOnChangePlan(DefaultSubscriptionBase subscription, Plan newPlan,
                                                             String newPriceList, DateTime requestedDate, DateTime effectiveDate, DateTime processedDate,
                                                             boolean addCancellationAddOnForEventsIfRequired, TenantContext context)
            throws CatalogApiException, SubscriptionBaseApiException;

    public List<SubscriptionBaseEvent> getEventsOnCancelPlan(final DefaultSubscriptionBase subscription,
                                                             final DateTime requestedDate, final DateTime effectiveDate, final DateTime processedDate,
                                                             final boolean addCancellationAddOnForEventsIfRequired, final TenantContext context) throws CatalogApiException;
}