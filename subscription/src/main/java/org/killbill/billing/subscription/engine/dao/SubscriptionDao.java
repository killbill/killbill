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

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
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
import org.killbill.billing.subscription.catalog.SubscriptionCatalog;
import org.killbill.billing.subscription.engine.dao.model.SubscriptionBundleModelDao;
import org.killbill.billing.subscription.events.SubscriptionBaseEvent;
import org.killbill.billing.util.api.AuditLevel;
import org.killbill.billing.util.audit.AuditLogWithHistory;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.billing.util.entity.dao.EntityDao;

public interface SubscriptionDao extends EntityDao<SubscriptionBundleModelDao, SubscriptionBaseBundle, SubscriptionApiException> {

    // Bundle apis
    List<SubscriptionBaseBundle> getSubscriptionBundleForAccount(UUID accountId, InternalTenantContext context);

    List<SubscriptionBaseBundle> getSubscriptionBundlesForKey(String bundleKey, InternalTenantContext context);

    Pagination<SubscriptionBundleModelDao> searchSubscriptionBundles(String searchKey, Long offset, Long limit, InternalTenantContext context);

    Iterable<UUID> getNonAOSubscriptionIdsForKey(String bundleKey, InternalTenantContext context);

    SubscriptionBaseBundle getSubscriptionBundlesForAccountAndKey(UUID accountId, String bundleKey, InternalTenantContext context);

    SubscriptionBaseBundle getSubscriptionBundleFromId(UUID bundleId, InternalTenantContext context);

    SubscriptionBaseBundle createSubscriptionBundle(DefaultSubscriptionBaseBundle bundle, final SubscriptionCatalog catalog, final boolean renameCancelledBundleIfExist, InternalCallContext context) throws SubscriptionBaseApiException;

    SubscriptionBase getSubscriptionFromId(UUID subscriptionId, final SubscriptionCatalog catalog, InternalTenantContext context) throws CatalogApiException;

    SubscriptionBase getSubscriptionFromExternalKey(String externalKey, final SubscriptionCatalog catalog, InternalTenantContext context) throws CatalogApiException;

    UUID getBundleIdFromSubscriptionId(UUID subscriptionId, InternalTenantContext context);

    UUID getSubscriptionIdFromSubscriptionExternalKey(String externalKey, InternalTenantContext context) throws SubscriptionBaseApiException;

    // SubscriptionBase retrieval
    SubscriptionBase getBaseSubscription(UUID bundleId, final SubscriptionCatalog catalog, InternalTenantContext context) throws CatalogApiException;

    List<DefaultSubscriptionBase> getSubscriptions(UUID bundleId, List<SubscriptionBaseEvent> dryRunEvents, final SubscriptionCatalog catalog, InternalTenantContext context) throws CatalogApiException;

    Map<UUID, List<DefaultSubscriptionBase>> getSubscriptionsForAccount(final SubscriptionCatalog catalog,  LocalDate cutoffDt, InternalTenantContext context) throws CatalogApiException;

    Map<UUID, List<DefaultSubscriptionBase>> getSubscriptionsFromAccountId(@Nullable final LocalDate cutoffDt, final InternalTenantContext context);

    // Update
    void updateChargedThroughDates(final Map<DateTime, List<UUID>> chargeThroughDates, final InternalCallContext context);

    // Event apis
    void createNextPhaseEvent(DefaultSubscriptionBase subscription, SubscriptionBaseEvent readyPhaseEvent, SubscriptionBaseEvent nextPhase, InternalCallContext context);

    SubscriptionBaseEvent getEventById(UUID eventId, InternalTenantContext context);

    List<SubscriptionBaseEvent> getEventsForSubscription(UUID subscriptionId, InternalTenantContext context);

    List<SubscriptionBaseEvent> getPendingEventsForSubscription(UUID subscriptionId, InternalTenantContext context);

    // SubscriptionBase creation, cancellation, changePlanWithRequestedDate apis

    List<SubscriptionBaseEvent> createSubscriptionsWithAddOns(List<SubscriptionBaseWithAddOns> subscriptions, Map<UUID, List<SubscriptionBaseEvent>> initialEventsMap, final SubscriptionCatalog catalog, InternalCallContext context);

    void cancelSubscriptionsOnBasePlanEvent(DefaultSubscriptionBase subscription, SubscriptionBaseEvent event, List<DefaultSubscriptionBase> subscriptions, List<SubscriptionBaseEvent> cancelEvents, final SubscriptionCatalog catalog, InternalCallContext context);

    void notifyOnBasePlanEvent(final DefaultSubscriptionBase subscription, final SubscriptionBaseEvent event, final SubscriptionCatalog catalog, final InternalCallContext context);

    void cancelSubscriptions(List<DefaultSubscriptionBase> subscriptions, List<SubscriptionBaseEvent> cancelEvents, final SubscriptionCatalog catalog, InternalCallContext context);

    void uncancelSubscription(DefaultSubscriptionBase subscription, List<SubscriptionBaseEvent> uncancelEvents, InternalCallContext context);

    void changePlan(DefaultSubscriptionBase subscription, List<SubscriptionBaseEvent> changeEvents, List<DefaultSubscriptionBase> subscriptionsToBeCancelled, List<SubscriptionBaseEvent> cancelEvents, final SubscriptionCatalog catalog, InternalCallContext context);

    void undoChangePlan(DefaultSubscriptionBase subscription, List<SubscriptionBaseEvent> undoChangePlanEvents, InternalCallContext context);

    void transfer(UUID srcAccountId, UUID destAccountId, BundleTransferData data, List<TransferCancelData> transferCancelData, final SubscriptionCatalog catalog, InternalCallContext fromContext, InternalCallContext toContext);

    void updateBundleExternalKey(UUID bundleId, String externalKey, InternalCallContext context);

    void createBCDChangeEvent(DefaultSubscriptionBase subscription, SubscriptionBaseEvent bcdEvent, final SubscriptionCatalog catalog, InternalCallContext context);

    List<AuditLogWithHistory> getSubscriptionBundleAuditLogsWithHistoryForId(UUID bundleId, AuditLevel auditLevel, InternalTenantContext context);

    List<AuditLogWithHistory> getSubscriptionAuditLogsWithHistoryForId(UUID subscriptionId, AuditLevel auditLevel, InternalTenantContext context);

    List<AuditLogWithHistory> getSubscriptionEventAuditLogsWithHistoryForId(UUID eventId, AuditLevel auditLevel, InternalTenantContext context);

}

