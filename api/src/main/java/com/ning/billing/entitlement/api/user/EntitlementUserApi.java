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

import com.ning.billing.account.api.Account;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PhaseType;
import org.joda.time.DateTime;

import java.util.List;
import java.util.UUID;


public interface EntitlementUserApi {

    public SubscriptionBundle getBundleFromId(UUID id);

    public Subscription getSubscriptionFromId(UUID id);

    public List<SubscriptionBundle> getBundlesForAccount(UUID accountId);

    public List<Subscription> getSubscriptionsForBundle(UUID bundleId);

    public List<Subscription> getSubscriptionsForKey(String bundleKey);

    public SubscriptionBundle createBundleForAccount(Account account, String bundleKey)
        throws EntitlementUserApiException;


    public Subscription createSubscription(UUID bundleId, String productName, BillingPeriod term, String priceList, PhaseType initialPhase, DateTime requestedDate)
        throws EntitlementUserApiException;
}
