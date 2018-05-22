/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.subscription.engine.dao;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.Catalog;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.entitlement.api.SubscriptionApiException;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseWithAddOns;
import org.killbill.billing.subscription.api.transfer.BundleTransferData;
import org.killbill.billing.subscription.api.transfer.TransferCancelData;
import org.killbill.billing.subscription.api.user.DefaultSubscriptionBase;
import org.killbill.billing.subscription.api.user.DefaultSubscriptionBaseBundle;
import org.killbill.billing.subscription.api.user.SubscriptionBaseApiException;
import org.killbill.billing.subscription.api.user.SubscriptionBaseBundle;
import org.killbill.billing.subscription.engine.dao.model.SubscriptionBundleModelDao;
import org.killbill.billing.subscription.events.SubscriptionBaseEvent;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.billing.util.entity.dao.EntityDao;

public interface SubscriptionDao extends EntityDao<SubscriptionBundleModelDao, SubscriptionBaseBundle, SubscriptionApiException> {

    // Bundle apis
    public List<SubscriptionBaseBundle> getSubscriptionBundleForAccount(UUID accountId, InternalTenantContext context);

    public List<SubscriptionBaseBundle> getSubscriptionBundlesForKey(String bundleKey, InternalTenantContext context);

    public Pagination<SubscriptionBundleModelDao> searchSubscriptionBundles(String searchKey, Long offset, Long limit, InternalTenantContext context);

    public Iterable<UUID> getNonAOSubscriptionIdsForKey(String bundleKey, InternalTenantContext context);

    public SubscriptionBaseBundle getSubscriptionBundlesForAccountAndKey(UUID accountId, String bundleKey, InternalTenantContext context);

    public SubscriptionBaseBundle getSubscriptionBundleFromId(UUID bundleId, InternalTenantContext context);

    public SubscriptionBaseBundle createSubscriptionBundle(DefaultSubscriptionBaseBundle bundle, final Catalog catalog, final boolean renameCancelledBundleIfExist, InternalCallContext context) throws SubscriptionBaseApiException;

    public SubscriptionBase getSubscriptionFromId(UUID subscriptionId, final Catalog catalog, InternalTenantContext context) throws CatalogApiException;

    public UUID getBundleIdFromSubscriptionId(UUID subscriptionId, InternalTenantContext context);

    // SubscriptionBase retrieval
    public SubscriptionBase getBaseSubscription(UUID bundleId, final Catalog catalog, InternalTenantContext context) throws CatalogApiException;

    public List<DefaultSubscriptionBase> getSubscriptions(UUID bundleId, List<SubscriptionBaseEvent> dryRunEvents, final Catalog catalog, InternalTenantContext context) throws CatalogApiException;

    public Map<UUID, List<DefaultSubscriptionBase>> getSubscriptionsForAccount(final Catalog catalog, InternalTenantContext context) throws CatalogApiException;

    // Update
    public void updateChargedThroughDate(DefaultSubscriptionBase subscription, InternalCallContext context);

    // Event apis
    public void createNextPhaseEvent(DefaultSubscriptionBase subscription, SubscriptionBaseEvent readyPhaseEvent, SubscriptionBaseEvent nextPhase, InternalCallContext context);

    public SubscriptionBaseEvent getEventById(UUID eventId, InternalTenantContext context);

    public List<SubscriptionBaseEvent> getEventsForSubscription(UUID subscriptionId, InternalTenantContext context);

    public List<SubscriptionBaseEvent> getPendingEventsForSubscription(UUID subscriptionId, InternalTenantContext context);

    // SubscriptionBase creation, cancellation, changePlanWithRequestedDate apis

    public List<SubscriptionBaseEvent> createSubscriptionsWithAddOns(List<SubscriptionBaseWithAddOns> subscriptions, Map<UUID, List<SubscriptionBaseEvent>> initialEventsMap, final Catalog catalog, InternalCallContext context);

    public void cancelSubscriptionsOnBasePlanEvent(DefaultSubscriptionBase subscription, SubscriptionBaseEvent event, List<DefaultSubscriptionBase> subscriptions, List<SubscriptionBaseEvent> cancelEvents, final Catalog catalog, InternalCallContext context);

    public void notifyOnBasePlanEvent(final DefaultSubscriptionBase subscription, final SubscriptionBaseEvent event, final Catalog catalog, final InternalCallContext context);

    public void cancelSubscriptions(List<DefaultSubscriptionBase> subscriptions, List<SubscriptionBaseEvent> cancelEvents, final Catalog catalog, InternalCallContext context);

    public void uncancelSubscription(DefaultSubscriptionBase subscription, List<SubscriptionBaseEvent> uncancelEvents, InternalCallContext context);

    public void changePlan(DefaultSubscriptionBase subscription, List<SubscriptionBaseEvent> changeEvents, List<DefaultSubscriptionBase> subscriptionsToBeCancelled, List<SubscriptionBaseEvent> cancelEvents, final Catalog catalog, InternalCallContext context);

    public void undoChangePlan(DefaultSubscriptionBase subscription, List<SubscriptionBaseEvent> undoChangePlanEvents, InternalCallContext context);

    public void transfer(UUID srcAccountId, UUID destAccountId, BundleTransferData data, List<TransferCancelData> transferCancelData, final Catalog catalog, InternalCallContext fromContext, InternalCallContext toContext);

    public void updateBundleExternalKey(UUID bundleId, String externalKey, InternalCallContext context);

    public void createBCDChangeEvent(DefaultSubscriptionBase subscription, SubscriptionBaseEvent bcdEvent, final Catalog catalog, InternalCallContext context);
}

