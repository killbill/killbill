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
import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.entitlement.api.migration.AccountMigrationData;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionBundleData;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.events.EntitlementEvent;

public interface EntitlementDao {


    // Bundle apis
    public List<SubscriptionBundle> getSubscriptionBundleForAccount(UUID accountId);

    public SubscriptionBundle getSubscriptionBundleFromKey(String bundleKey);

    public SubscriptionBundle getSubscriptionBundleFromId(UUID bundleId);

    public SubscriptionBundle createSubscriptionBundle(SubscriptionBundleData bundle);

    public Subscription getSubscriptionFromId(UUID subscriptionId);

    // Account retrieval
    public UUID getAccountIdFromSubscriptionId(UUID subscriptionId);

    // Subscription retrieval
    public Subscription getBaseSubscription(UUID bundleId);

    public List<Subscription> getSubscriptions(UUID bundleId);

    public List<Subscription> getSubscriptionsForKey(String bundleKey);

    // Update
    public void updateSubscription(SubscriptionData subscription);

    // Event apis
    public void createNextPhaseEvent(UUID subscriptionId, EntitlementEvent nextPhase);

    public EntitlementEvent getEventById(UUID eventId);

    public List<EntitlementEvent> getEventsForSubscription(UUID subscriptionId);

    public List<EntitlementEvent> getPendingEventsForSubscription(UUID subscriptionId);

    // Subscription creation, cancellation, changePlan apis
    public void createSubscription(SubscriptionData subscription, List<EntitlementEvent> initialEvents);

    public void recreateSubscription(UUID subscriptionId, List<EntitlementEvent> recreateEvents);

    public void cancelSubscription(UUID subscriptionId, EntitlementEvent cancelEvent);

    public void uncancelSubscription(UUID subscriptionId, List<EntitlementEvent> uncancelEvents);

    public void changePlan(UUID subscriptionId, List<EntitlementEvent> changeEvents);

    public void migrate(UUID acountId, AccountMigrationData data);

    public void undoMigration(UUID accountId);

	}
