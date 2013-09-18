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

package com.ning.billing.util.svcapi.subscription;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;

import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.entitlement.api.EntitlementAOStatusDryRun;
import com.ning.billing.subscription.api.SubscriptionBase;
import com.ning.billing.subscription.api.user.SubscriptionBaseApiException;
import com.ning.billing.subscription.api.user.SubscriptionBaseBundle;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.events.EffectiveSubscriptionInternalEvent;


public interface SubscriptionBaseInternalApi {

    public SubscriptionBase createSubscription(final UUID bundleId, final PlanPhaseSpecifier spec, final DateTime requestedDateWithMs,
                                               final InternalCallContext context) throws SubscriptionBaseApiException;


    public SubscriptionBaseBundle createBundleForAccount(final UUID accountId, final String bundleName, final InternalCallContext context)
            throws SubscriptionBaseApiException;

    public SubscriptionBaseBundle getBundleForAccountAndKey(final UUID accountId, final String bundleKey, final InternalTenantContext context)
            throws SubscriptionBaseApiException;

    public List<SubscriptionBaseBundle> getBundlesForAccount(final UUID accountId, final InternalTenantContext context);

    public List<SubscriptionBaseBundle> getBundlesForKey(final String bundleKey, final InternalTenantContext context);

    public SubscriptionBaseBundle getActiveBundleForKey(final String bundleKey, final InternalTenantContext context) throws SubscriptionBaseApiException;

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
}
