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

import com.ning.billing.entitlement.api.migration.AccountMigrationData;
import com.ning.billing.entitlement.api.migration.AccountMigrationData.BundleMigrationData;
import com.ning.billing.entitlement.api.timeline.SubscriptionDataRepair;
import com.ning.billing.entitlement.api.transfer.TransferCancelData;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionBundleData;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.events.EntitlementEvent;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;

public interface EntitlementDao {

    // Bundle apis
    public List<SubscriptionBundle> getSubscriptionBundleForAccount(UUID accountId, InternalTenantContext context);

    public List<SubscriptionBundle> getSubscriptionBundlesForKey(String bundleKey, InternalTenantContext context);

    public SubscriptionBundle getSubscriptionBundleFromAccountAndKey(UUID accountId, String bundleKey, InternalTenantContext context);

    public SubscriptionBundle getSubscriptionBundleFromId(UUID bundleId, InternalTenantContext context);

    public SubscriptionBundle createSubscriptionBundle(SubscriptionBundleData bundle, InternalCallContext context);

    public Subscription getSubscriptionFromId(UUID subscriptionId, InternalTenantContext context);

    // ACCOUNT retrieval
    public UUID getAccountIdFromSubscriptionId(UUID subscriptionId, InternalTenantContext context);

    // Subscription retrieval
    public Subscription getBaseSubscription(UUID bundleId, InternalTenantContext context);

    public List<Subscription> getSubscriptions(UUID bundleId, InternalTenantContext context);

    public List<Subscription> getSubscriptionsForAccountAndKey(UUID accountId, String bundleKey, InternalTenantContext context);

    // Update
    public void updateChargedThroughDate(SubscriptionData subscription, InternalCallContext context);

    // Event apis
    public void createNextPhaseEvent(SubscriptionData subscription, EntitlementEvent nextPhase, InternalCallContext context);

    public EntitlementEvent getEventById(UUID eventId, InternalTenantContext context);

    public Map<UUID, List<EntitlementEvent>> getEventsForBundle(UUID bundleId, InternalTenantContext context);

    public List<EntitlementEvent> getEventsForSubscription(UUID subscriptionId, InternalTenantContext context);

    public List<EntitlementEvent> getPendingEventsForSubscription(UUID subscriptionId, InternalTenantContext context);

    // Subscription creation, cancellation, changePlan apis
    public void createSubscription(SubscriptionData subscription, List<EntitlementEvent> initialEvents, InternalCallContext context);

    public void recreateSubscription(SubscriptionData subscription, List<EntitlementEvent> recreateEvents, InternalCallContext context);

    public void cancelSubscription(SubscriptionData subscription, EntitlementEvent cancelEvent, InternalCallContext context, int cancelSeq);

    public void cancelSubscriptions(final List<SubscriptionData> subscriptions, final List<EntitlementEvent> cancelEvents, final InternalCallContext context);

    public void uncancelSubscription(SubscriptionData subscription, List<EntitlementEvent> uncancelEvents, InternalCallContext context);

    public void changePlan(SubscriptionData subscription, List<EntitlementEvent> changeEvents, InternalCallContext context);

    public void migrate(UUID accountId, AccountMigrationData data, InternalCallContext context);

    public void transfer(UUID srcAccountId, UUID destAccountId, BundleMigrationData data, List<TransferCancelData> transferCancelData, InternalCallContext fromContext, InternalCallContext toContext);

    // Repair
    public void repair(UUID accountId, UUID bundleId, List<SubscriptionDataRepair> inRepair, InternalCallContext context);
}

