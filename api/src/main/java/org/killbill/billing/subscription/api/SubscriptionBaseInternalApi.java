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

package org.killbill.billing.subscription.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;

import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.entitlement.api.EntitlementAOStatusDryRun;
import org.killbill.billing.events.EffectiveSubscriptionInternalEvent;
import org.killbill.billing.subscription.api.user.SubscriptionBaseApiException;
import org.killbill.billing.subscription.api.user.SubscriptionBaseBundle;
import org.killbill.billing.util.entity.Pagination;

public interface SubscriptionBaseInternalApi {

    public SubscriptionBase createSubscription(final UUID bundleId, final PlanPhaseSpecifier spec, final DateTime requestedDateWithMs,
                                               final InternalCallContext context) throws SubscriptionBaseApiException;

    public SubscriptionBaseBundle createBundleForAccount(final UUID accountId, final String bundleName, final InternalCallContext context)
            throws SubscriptionBaseApiException;

    public List<SubscriptionBaseBundle> getBundlesForAccountAndKey(final UUID accountId, final String bundleKey, final InternalTenantContext context)
            throws SubscriptionBaseApiException;

    public List<SubscriptionBaseBundle> getBundlesForAccount(final UUID accountId, final InternalTenantContext context);

    public List<SubscriptionBaseBundle> getBundlesForKey(final String bundleKey, final InternalTenantContext context);

    public Pagination<SubscriptionBaseBundle> getBundles(final Long offset, final Long limit, final InternalTenantContext context);

    public Pagination<SubscriptionBaseBundle> searchBundles(final String searchKey, final Long offset, final Long limit, final InternalTenantContext context);

    public Iterable<UUID> getNonAOSubscriptionIdsForKey(final String bundleKey, final InternalTenantContext context);

    public List<SubscriptionBase> getSubscriptionsForBundle(final UUID bundleId, final InternalTenantContext context);

    public Map<UUID, List<SubscriptionBase>> getSubscriptionsForAccount(final InternalTenantContext context);

    public SubscriptionBase getBaseSubscription(final UUID bundleId, final InternalTenantContext context) throws SubscriptionBaseApiException;

    public SubscriptionBase getSubscriptionFromId(final UUID id, final InternalTenantContext context) throws SubscriptionBaseApiException;

    public SubscriptionBaseBundle getBundleFromId(final UUID id, final InternalTenantContext context) throws SubscriptionBaseApiException;

    public UUID getAccountIdFromSubscriptionId(final UUID subscriptionId, final InternalTenantContext context) throws SubscriptionBaseApiException;

    public void setChargedThroughDate(final UUID subscriptionId, final DateTime chargedThruDate, final InternalCallContext context);

    public List<EffectiveSubscriptionInternalEvent> getAllTransitions(final SubscriptionBase subscription, final InternalTenantContext context);

    public List<EffectiveSubscriptionInternalEvent> getBillingTransitions(final SubscriptionBase subscription, final InternalTenantContext context);

    public DateTime getNextBillingDate(final UUID accountId, final InternalTenantContext context);

    public List<EntitlementAOStatusDryRun> getDryRunChangePlanStatus(final UUID subscriptionId, @Nullable final String baseProductName,
                                                                     final DateTime requestedDate, final InternalTenantContext context) throws SubscriptionBaseApiException;

    public void updateExternalKey(final UUID bundleId, final String newExternalKey, final InternalCallContext context);
}
