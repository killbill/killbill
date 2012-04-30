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

import com.ning.billing.util.callcontext.CallContext;
import org.joda.time.DateTime;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;


public interface EntitlementUserApi {

    public SubscriptionBundle getBundleFromId(UUID id) throws EntitlementUserApiException;

    public Subscription getSubscriptionFromId(UUID id) throws EntitlementUserApiException;

    public SubscriptionBundle getBundleForKey(String bundleKey) throws EntitlementUserApiException;

    public List<SubscriptionBundle> getBundlesForAccount(UUID accountId);

    public List<Subscription> getSubscriptionsForBundle(UUID bundleId);

    public List<Subscription> getSubscriptionsForKey(String bundleKey);
    
    public Subscription getBaseSubscription(UUID bundleId);

    public SubscriptionBundle createBundleForAccount(UUID accountId, String bundleKey, CallContext context)
        throws EntitlementUserApiException;

    public Subscription createSubscription(UUID bundleId, PlanPhaseSpecifier spec, DateTime requestedDate, CallContext context)
        throws EntitlementUserApiException;

    public DateTime getNextBillingDate(UUID account);
}
