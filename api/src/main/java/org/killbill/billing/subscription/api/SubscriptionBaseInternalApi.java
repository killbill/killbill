/*
 * Copyright 2010-2011 Ning, Inc.
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

package org.killbill.billing.subscription.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.PlanPhasePriceOverride;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.PlanSpecifier;
import org.killbill.billing.entitlement.api.BaseEntitlementWithAddOnsSpecifier;
import org.killbill.billing.entitlement.api.EntitlementAOStatusDryRun;
import org.killbill.billing.events.EffectiveSubscriptionInternalEvent;
import org.killbill.billing.invoice.api.DryRunArguments;
import org.killbill.billing.subscription.api.user.SubscriptionBaseApiException;
import org.killbill.billing.subscription.api.user.SubscriptionBaseBundle;
import org.killbill.billing.util.entity.Pagination;

public interface SubscriptionBaseInternalApi {

    public SubscriptionBase createSubscription(UUID bundleId, PlanPhaseSpecifier spec, List<PlanPhasePriceOverride> overrides, DateTime requestedDateWithMs,
                                               final boolean isMigrated, InternalCallContext context) throws SubscriptionBaseApiException;

    public List<SubscriptionBaseWithAddOns> createBaseSubscriptionsWithAddOns(UUID accountId, Iterable<BaseEntitlementWithAddOnsSpecifier> baseEntitlementWithAddOnsSpecifier,
                                                                              InternalCallContext contextWithValidAccountRecordId) throws SubscriptionBaseApiException;

    public void cancelBaseSubscriptions(Iterable<SubscriptionBase> subscriptions, BillingActionPolicy policy, int accountBillCycleDayLocal, InternalCallContext context) throws SubscriptionBaseApiException;

    public SubscriptionBaseBundle createBundleForAccount(UUID accountId, String bundleName, InternalCallContext context)
            throws SubscriptionBaseApiException;

    public List<SubscriptionBaseBundle> getBundlesForAccountAndKey(UUID accountId, String bundleKey, InternalTenantContext context)
            throws SubscriptionBaseApiException;

    public List<SubscriptionBaseBundle> getBundlesForAccount(UUID accountId, InternalTenantContext context);

    public List<SubscriptionBaseBundle> getBundlesForKey(String bundleKey, InternalTenantContext context);

    public Pagination<SubscriptionBaseBundle> getBundles(Long offset, Long limit, InternalTenantContext context);

    public Pagination<SubscriptionBaseBundle> searchBundles(String searchKey, Long offset, Long limit, InternalTenantContext context);

    public Iterable<UUID> getNonAOSubscriptionIdsForKey(String bundleKey, InternalTenantContext context);

    public List<SubscriptionBase> getSubscriptionsForBundle(UUID bundleId, DryRunArguments dryRunArguments, InternalTenantContext context)
            throws SubscriptionBaseApiException;

    public Map<UUID, List<SubscriptionBase>> getSubscriptionsForAccount(InternalTenantContext context) throws SubscriptionBaseApiException;

    public SubscriptionBase getBaseSubscription(UUID bundleId, InternalTenantContext context) throws SubscriptionBaseApiException;

    public SubscriptionBase getSubscriptionFromId(UUID id, InternalTenantContext context) throws SubscriptionBaseApiException;

    public SubscriptionBaseBundle getBundleFromId(UUID id, InternalTenantContext context) throws SubscriptionBaseApiException;

    public UUID getAccountIdFromSubscriptionId(UUID subscriptionId, InternalTenantContext context) throws SubscriptionBaseApiException;

    public void setChargedThroughDate(UUID subscriptionId, DateTime chargedThruDate, InternalCallContext context) throws SubscriptionBaseApiException;

    public List<EffectiveSubscriptionInternalEvent> getAllTransitions(SubscriptionBase subscription, InternalTenantContext context);

    public List<EffectiveSubscriptionInternalEvent> getBillingTransitions(SubscriptionBase subscription, InternalTenantContext context);

    public DateTime getDryRunChangePlanEffectiveDate(SubscriptionBase subscription, PlanSpecifier spec, DateTime requestedDate, BillingActionPolicy policy, List<PlanPhasePriceOverride> overrides, InternalCallContext context) throws SubscriptionBaseApiException, CatalogApiException;

    public List<EntitlementAOStatusDryRun> getDryRunChangePlanStatus(UUID subscriptionId, @Nullable String baseProductName,
                                                                     DateTime requestedDate, InternalTenantContext context) throws SubscriptionBaseApiException;

    public void updateExternalKey(UUID bundleId, String newExternalKey, InternalCallContext context);

    public Map<UUID, DateTime> getNextFutureEventForSubscriptions(final List<SubscriptionBaseTransitionType> eventTypes, final InternalCallContext internalCallContext);


    public void updateBCD(final UUID subscriptionId, final int bcd, @Nullable final LocalDate effectiveFromDate, final InternalCallContext internalCallContext) throws SubscriptionBaseApiException;

    public int getDefaultBillCycleDayLocal(final Map<UUID, Integer> bcdCache, final SubscriptionBase subscription, final SubscriptionBase baseSubscription, final PlanPhaseSpecifier planPhaseSpecifier, final int accountBillCycleDayLocal, final DateTime effectiveDate, final InternalTenantContext context) throws SubscriptionBaseApiException;
}
