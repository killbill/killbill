/*
 * Copyright 2010-2013 Ning, Inc.
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

package com.ning.billing.subscription.engine.dao;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ning.billing.subscription.api.migration.AccountMigrationData;
import com.ning.billing.subscription.api.migration.AccountMigrationData.BundleMigrationData;
import com.ning.billing.subscription.api.timeline.SubscriptionDataRepair;
import com.ning.billing.subscription.api.transfer.TransferCancelData;
import com.ning.billing.subscription.api.user.SubscriptionBaseBundle;
import com.ning.billing.subscription.api.user.SubscriptionBundleData;
import com.ning.billing.subscription.api.user.SubscriptionData;
import com.ning.billing.subscription.events.SubscriptionEvent;
import com.ning.billing.subscription.api.SubscriptionBase;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;

public interface SubscriptionDao {

    // Bundle apis
    public List<SubscriptionBaseBundle> getSubscriptionBundleForAccount(UUID accountId, InternalTenantContext context);

    public List<SubscriptionBaseBundle> getSubscriptionBundlesForKey(String bundleKey, InternalTenantContext context);

    public SubscriptionBaseBundle getSubscriptionBundleFromAccountAndKey(UUID accountId, String bundleKey, InternalTenantContext context);

    public SubscriptionBaseBundle getSubscriptionBundleFromId(UUID bundleId, InternalTenantContext context);

    public SubscriptionBaseBundle createSubscriptionBundle(SubscriptionBundleData bundle, InternalCallContext context);

    public SubscriptionBase getSubscriptionFromId(UUID subscriptionId, InternalTenantContext context);

    // ACCOUNT retrieval
    public UUID getAccountIdFromSubscriptionId(UUID subscriptionId, InternalTenantContext context);

    // SubscriptionBase retrieval
    public SubscriptionBase getBaseSubscription(UUID bundleId, InternalTenantContext context);

    public List<SubscriptionBase> getSubscriptions(UUID bundleId, InternalTenantContext context);

    public List<SubscriptionBase> getSubscriptionsForAccountAndKey(UUID accountId, String bundleKey, InternalTenantContext context);

    // Update
    public void updateChargedThroughDate(SubscriptionData subscription, InternalCallContext context);

    // Event apis
    public void createNextPhaseEvent(SubscriptionData subscription, SubscriptionEvent nextPhase, InternalCallContext context);

    public SubscriptionEvent getEventById(UUID eventId, InternalTenantContext context);

    public Map<UUID, List<SubscriptionEvent>> getEventsForBundle(UUID bundleId, InternalTenantContext context);

    public List<SubscriptionEvent> getEventsForSubscription(UUID subscriptionId, InternalTenantContext context);

    public List<SubscriptionEvent> getPendingEventsForSubscription(UUID subscriptionId, InternalTenantContext context);

    // SubscriptionBase creation, cancellation, changePlan apis
    public void createSubscription(SubscriptionData subscription, List<SubscriptionEvent> initialEvents, InternalCallContext context);

    public void recreateSubscription(SubscriptionData subscription, List<SubscriptionEvent> recreateEvents, InternalCallContext context);

    public void cancelSubscription(SubscriptionData subscription, SubscriptionEvent cancelEvent, InternalCallContext context, int cancelSeq);

    public void cancelSubscriptions(final List<SubscriptionData> subscriptions, final List<SubscriptionEvent> cancelEvents, final InternalCallContext context);

    public void uncancelSubscription(SubscriptionData subscription, List<SubscriptionEvent> uncancelEvents, InternalCallContext context);

    public void changePlan(SubscriptionData subscription, List<SubscriptionEvent> changeEvents, InternalCallContext context);

    public void migrate(UUID accountId, AccountMigrationData data, InternalCallContext context);

    public void transfer(UUID srcAccountId, UUID destAccountId, BundleMigrationData data, List<TransferCancelData> transferCancelData, InternalCallContext fromContext, InternalCallContext toContext);

    // Repair
    public void repair(UUID accountId, UUID bundleId, List<SubscriptionDataRepair> inRepair, InternalCallContext context);
}

