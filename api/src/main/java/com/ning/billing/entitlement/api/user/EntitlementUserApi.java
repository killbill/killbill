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

package com.ning.billing.entitlement.api.user;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;

import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.util.callcontext.CallContext;


public interface EntitlementUserApi {

    public SubscriptionBundle getBundleFromId(final UUID id) throws EntitlementUserApiException;

    public Subscription getSubscriptionFromId(final UUID id) throws EntitlementUserApiException;

    public List<SubscriptionBundle> getBundlesForKey(final String bundleKey) throws EntitlementUserApiException;

    public SubscriptionBundle getBundleForAccountAndKey(final UUID accountId, final String bundleKey) throws EntitlementUserApiException;

    public List<SubscriptionBundle> getBundlesForAccount(final UUID accountId);

    public List<Subscription> getSubscriptionsForBundle(final UUID bundleId);

    public List<Subscription> getSubscriptionsForAccountAndKey(final UUID accountId, final String bundleKey);

    public Subscription getBaseSubscription(final UUID bundleId) throws EntitlementUserApiException;

    public SubscriptionBundle createBundleForAccount(final UUID accountId, final String bundleKey, final CallContext context)
            throws EntitlementUserApiException;

    public Subscription createSubscription(final UUID bundleId, final PlanPhaseSpecifier spec, final DateTime requestedDate, final CallContext context)
            throws EntitlementUserApiException;

    public List<SubscriptionStatusDryRun> getDryRunChangePlanStatus(final UUID subscriptionId, @Nullable final String productName, final DateTime requestedDate)
            throws EntitlementUserApiException;

    public DateTime getNextBillingDate(final UUID account);
}
