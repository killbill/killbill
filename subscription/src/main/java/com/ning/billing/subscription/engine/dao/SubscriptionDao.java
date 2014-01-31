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

import com.ning.billing.callcontext.InternalCallContext;
import com.ning.billing.callcontext.InternalTenantContext;
import com.ning.billing.entitlement.api.SubscriptionApiException;
import com.ning.billing.subscription.api.SubscriptionBase;
import com.ning.billing.subscription.api.migration.AccountMigrationData;
import com.ning.billing.subscription.api.migration.AccountMigrationData.BundleMigrationData;
import com.ning.billing.subscription.api.timeline.SubscriptionDataRepair;
import com.ning.billing.subscription.api.transfer.TransferCancelData;
import com.ning.billing.subscription.api.user.DefaultSubscriptionBase;
import com.ning.billing.subscription.api.user.DefaultSubscriptionBaseBundle;
import com.ning.billing.subscription.api.user.SubscriptionBaseBundle;
import com.ning.billing.subscription.engine.dao.model.SubscriptionBundleModelDao;
import com.ning.billing.subscription.events.SubscriptionBaseEvent;
import com.ning.billing.util.entity.Pagination;
import com.ning.billing.util.entity.dao.EntityDao;

public interface SubscriptionDao extends EntityDao<SubscriptionBundleModelDao, SubscriptionBaseBundle, SubscriptionApiException> {

    // Bundle apis
    public List<SubscriptionBaseBundle> getSubscriptionBundleForAccount(UUID accountId, InternalTenantContext context);

    public List<SubscriptionBaseBundle> getSubscriptionBundlesForKey(String bundleKey, InternalTenantContext context);

    public Pagination<SubscriptionBundleModelDao> searchSubscriptionBundles(String searchKey, Long offset, Long limit, InternalTenantContext context);

    public Iterable<UUID> getNonAOSubscriptionIdsForKey(String bundleKey, InternalTenantContext context);

    public List<SubscriptionBaseBundle> getSubscriptionBundlesForAccountAndKey(UUID accountId, String bundleKey, InternalTenantContext context);

    public SubscriptionBaseBundle getSubscriptionBundleFromId(UUID bundleId, InternalTenantContext context);

    public SubscriptionBaseBundle createSubscriptionBundle(DefaultSubscriptionBaseBundle bundle, InternalCallContext context);

    public SubscriptionBase getSubscriptionFromId(UUID subscriptionId, InternalTenantContext context);

    // ACCOUNT retrieval
    public UUID getAccountIdFromSubscriptionId(UUID subscriptionId, InternalTenantContext context);

    // SubscriptionBase retrieval
    public SubscriptionBase getBaseSubscription(UUID bundleId, InternalTenantContext context);

    public List<SubscriptionBase> getSubscriptions(UUID bundleId, InternalTenantContext context);

    public Map<UUID, List<SubscriptionBase>> getSubscriptionsForAccount(InternalTenantContext context);

    // Update
    public void updateChargedThroughDate(DefaultSubscriptionBase subscription, InternalCallContext context);

    // Event apis
    public void createNextPhaseEvent(DefaultSubscriptionBase subscription, SubscriptionBaseEvent nextPhase, InternalCallContext context);

    public SubscriptionBaseEvent getEventById(UUID eventId, InternalTenantContext context);

    public Map<UUID, List<SubscriptionBaseEvent>> getEventsForBundle(UUID bundleId, InternalTenantContext context);

    public List<SubscriptionBaseEvent> getEventsForSubscription(UUID subscriptionId, InternalTenantContext context);

    public List<SubscriptionBaseEvent> getPendingEventsForSubscription(UUID subscriptionId, InternalTenantContext context);

    // SubscriptionBase creation, cancellation, changePlanWithRequestedDate apis
    public void createSubscription(DefaultSubscriptionBase subscription, List<SubscriptionBaseEvent> initialEvents, InternalCallContext context);

    public void recreateSubscription(DefaultSubscriptionBase subscription, List<SubscriptionBaseEvent> recreateEvents, InternalCallContext context);

    public void cancelSubscription(DefaultSubscriptionBase subscription, SubscriptionBaseEvent cancelEvent, InternalCallContext context, int cancelSeq);

    public void cancelSubscriptions(List<DefaultSubscriptionBase> subscriptions, List<SubscriptionBaseEvent> cancelEvents, InternalCallContext context);

    public void uncancelSubscription(DefaultSubscriptionBase subscription, List<SubscriptionBaseEvent> uncancelEvents, InternalCallContext context);

    public void changePlan(DefaultSubscriptionBase subscription, List<SubscriptionBaseEvent> changeEvents, InternalCallContext context);

    public void migrate(UUID accountId, AccountMigrationData data, InternalCallContext context);

    public void transfer(UUID srcAccountId, UUID destAccountId, BundleMigrationData data, List<TransferCancelData> transferCancelData, InternalCallContext fromContext, InternalCallContext toContext);

    public void updateBundleExternalKey(UUID bundleId, String externalKey, InternalCallContext context);

    // Repair
    public void repair(UUID accountId, UUID bundleId, List<SubscriptionDataRepair> inRepair, InternalCallContext context);
}

