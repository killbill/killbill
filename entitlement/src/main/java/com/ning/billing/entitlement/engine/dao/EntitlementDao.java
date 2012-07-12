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

package com.ning.billing.entitlement.engine.dao;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ning.billing.entitlement.api.SubscriptionFactory;
import com.ning.billing.entitlement.api.migration.AccountMigrationData;
import com.ning.billing.entitlement.api.timeline.SubscriptionDataRepair;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionBundleData;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.events.EntitlementEvent;
import com.ning.billing.util.callcontext.CallContext;

public interface EntitlementDao {
    // Bundle apis
    public List<SubscriptionBundle> getSubscriptionBundleForAccount(final UUID accountId);

    public SubscriptionBundle getSubscriptionBundleFromKey(final String bundleKey);

    public SubscriptionBundle getSubscriptionBundleFromId(final UUID bundleId);

    public SubscriptionBundle createSubscriptionBundle(final SubscriptionBundleData bundle, CallContext context);

    public Subscription getSubscriptionFromId(final SubscriptionFactory factory, final UUID subscriptionId);

    // ACCOUNT retrieval
    public UUID getAccountIdFromSubscriptionId(final UUID subscriptionId);

    // Subscription retrieval
    public Subscription getBaseSubscription(final SubscriptionFactory factory, final UUID bundleId);

    public List<Subscription> getSubscriptions(final SubscriptionFactory factory, final UUID bundleId);

    public List<Subscription> getSubscriptionsForKey(final SubscriptionFactory factory, final String bundleKey);

    // Update
    public void updateChargedThroughDate(final SubscriptionData subscription, final CallContext context);

    // Event apis
    public void createNextPhaseEvent(final SubscriptionData subscription, final EntitlementEvent nextPhase, final CallContext context);

    public EntitlementEvent getEventById(final UUID eventId);

    public Map<UUID, List<EntitlementEvent>> getEventsForBundle(final UUID bundleId);

    public List<EntitlementEvent> getEventsForSubscription(final UUID subscriptionId);

    public List<EntitlementEvent> getPendingEventsForSubscription(final UUID subscriptionId);

    // Subscription creation, cancellation, changePlan apis
    public void createSubscription(final SubscriptionData subscription, final List<EntitlementEvent> initialEvents, final CallContext context);

    public void recreateSubscription(final SubscriptionData subscription, final List<EntitlementEvent> recreateEvents, final CallContext context);

    public void cancelSubscription(final SubscriptionData subscription, final EntitlementEvent cancelEvent, final CallContext context, final int cancelSeq);

    public void uncancelSubscription(final SubscriptionData subscription, final List<EntitlementEvent> uncancelEvents, final CallContext context);

    public void changePlan(final SubscriptionData subscription, final List<EntitlementEvent> changeEvents, final CallContext context);

    public void migrate(final UUID accountId, final AccountMigrationData data, final CallContext context);

    // Repair
    public void repair(final UUID accountId, final UUID bundleId, final List<SubscriptionDataRepair> inRepair, final CallContext context);
}

