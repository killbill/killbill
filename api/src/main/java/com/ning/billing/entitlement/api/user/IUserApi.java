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

import com.ning.billing.account.api.IAccount;
import com.ning.billing.catalog.api.BillingPeriod;


public interface IUserApi {

    public void initialize(List<IApiListener> listeners);

    public ISubscriptionBundle getBundleFromId(UUID id);

    public ISubscription getSubscriptionFromId(UUID id);

    public List<ISubscriptionBundle> getBundlesForAccount(UUID accountId);

    public List<ISubscription> getSubscriptionsForBundle(UUID bundleId);

    public ISubscriptionBundle createBundleForAccount(IAccount account, String bundleName)
        throws EntitlementUserApiException;

    public ISubscription createSubscription(UUID bundleId, String productName, BillingPeriod term, String planSet)
        throws EntitlementUserApiException;
}
