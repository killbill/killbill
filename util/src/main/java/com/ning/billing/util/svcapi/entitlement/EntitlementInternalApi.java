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

import org.joda.time.DateTime;
import org.joda.time.LocalDate;

import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.events.EffectiveSubscriptionInternalEvent;


public interface EntitlementInternalApi {

    public List<SubscriptionBundle> getBundlesForAccount(final UUID accountId, final InternalTenantContext context);

    public List<Subscription> getSubscriptionsForBundle(final UUID bundleId, final InternalTenantContext context);

    public Subscription getBaseSubscription(final UUID bundleId, final InternalTenantContext context) throws EntitlementUserApiException;

    public Subscription getSubscriptionFromId(final UUID id, final InternalTenantContext context) throws EntitlementUserApiException;

    public SubscriptionBundle getBundleFromId(final UUID id, final InternalTenantContext context) throws EntitlementUserApiException;

    public UUID getAccountIdFromSubscriptionId(final UUID subscriptionId, final InternalTenantContext context) throws EntitlementUserApiException;

    public void setChargedThroughDate(final UUID subscriptionId, final DateTime chargedThruDate, final InternalCallContext context);

    public List<EffectiveSubscriptionInternalEvent> getAllTransitions(final Subscription subscription, final InternalTenantContext context);

    public List<EffectiveSubscriptionInternalEvent> getBillingTransitions(final Subscription subscription, final InternalTenantContext context);
}
