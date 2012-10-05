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
package com.ning.billing.util.svcapi.entitlement;

import java.util.List;
import java.util.UUID;

import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.util.callcontext.InternalTenantContext;


public interface EntitlementInternalApi {

    public List<SubscriptionBundle> getBundlesForAccount(UUID accountId, InternalTenantContext context);

    public List<Subscription> getSubscriptionsForBundle(UUID bundleId, InternalTenantContext context);

    public Subscription getBaseSubscription(UUID bundleId, InternalTenantContext context) throws EntitlementUserApiException;

    public Subscription getSubscriptionFromId(UUID id, InternalTenantContext context) throws EntitlementUserApiException;

    public SubscriptionBundle getBundleFromId(UUID id, InternalTenantContext context) throws EntitlementUserApiException;

}
