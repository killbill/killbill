/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.CatalogService;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.catalog.api.TimeUnit;
import org.killbill.billing.dao.MockNonEntityDao;
import org.killbill.billing.entitlement.api.SubscriptionApiException;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseWithAddOns;
import org.killbill.billing.subscription.api.transfer.BundleTransferData;
import org.killbill.billing.subscription.api.transfer.TransferCancelData;
import org.killbill.billing.subscription.api.user.DefaultEffectiveSubscriptionEvent;
import org.killbill.billing.subscription.api.user.DefaultSubscriptionBase;
import org.killbill.billing.subscription.api.user.DefaultSubscriptionBaseBundle;
import org.killbill.billing.subscription.api.user.SubscriptionBaseBundle;
import org.killbill.billing.subscription.api.user.SubscriptionBaseTransitionData;
import org.killbill.billing.subscription.api.user.SubscriptionBuilder;
import org.killbill.billing.subscription.engine.core.DefaultSubscriptionBaseService;
import org.killbill.billing.subscription.engine.core.SubscriptionNotificationKey;
import org.killbill.billing.subscription.engine.dao.model.SubscriptionBundleModelDao;
import org.killbill.billing.subscription.events.SubscriptionBaseEvent;
import org.killbill.billing.subscription.events.SubscriptionBaseEvent.EventType;
import org.killbill.billing.subscription.events.user.ApiEvent;
import org.killbill.billing.subscription.events.user.ApiEventType;
import org.killbill.billing.util.entity.DefaultPagination;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.billing.util.entity.dao.EntitySqlDaoWrapperFactory;
import org.killbill.billing.util.entity.dao.MockEntityDaoBase;
import org.killbill.bus.api.BusEvent;
import org.killbill.bus.api.PersistentBus;
import org.killbill.bus.api.PersistentBus.EventBusException;
import org.killbill.clock.Clock;
import org.killbill.notificationq.api.NotificationEvent;
import org.killbill.notificationq.api.NotificationQueue;
import org.killbill.notificationq.api.NotificationQueueService;
import org.killbill.notificationq.api.NotificationQueueService.NoSuchNotificationQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;

public class MockSubscriptionDaoMemory extends MockEntityDaoBase<SubscriptionBundleModelDao, SubscriptionBaseBundle, SubscriptionApiException> implements SubscriptionDao {

    protected static final Logger log = LoggerFactory.getLogger(SubscriptionDao.class);

    private final List<SubscriptionBaseBundle> bundles;
    private final List<SubscriptionBase> subscriptions;
    private final TreeSet<SubscriptionBaseEvent> events;

    private final MockNonEntityDao mockNonEntityDao;
    private final Clock clock;
    private final NotificationQueueService notificationQueueService;
    private final PersistentBus eventBus;
    private final CatalogService catalogService;

    @Inject
    public MockSubscriptionDaoMemory(final MockNonEntityDao mockNonEntityDao,
                                     final Clock clock,
                                     final NotificationQueueService notificationQueueService,
                                     final PersistentBus eventBus,
                                     final CatalogService catalogService) {
        super();
        this.mockNonEntityDao = mockNonEntityDao;
        this.clock = clock;
        this.catalogService = catalogService;
        this.notificationQueueService = notificationQueueService;
        this.eventBus = eventBus;
        this.bundles = new ArrayList<SubscriptionBaseBundle>();
        this.subscriptions = new ArrayList<SubscriptionBase>();
        this.events = new TreeSet<SubscriptionBaseEvent>();
    }

    public void reset() {
        bundles.clear();
        subscriptions.clear();
        events.clear();
    }

    @Override
    public List<SubscriptionBaseBundle> getSubscriptionBundleForAccount(final UUID accountId, final InternalTenantContext context) {
        final List<SubscriptionBaseBundle> results = new ArrayList<SubscriptionBaseBundle>();
        for (final SubscriptionBaseBundle cur : bundles) {
            if (cur.getAccountId().equals(accountId)) {
                results.add(cur);
            }
        }
        return results;
    }

    @Override
    public List<SubscriptionBaseBundle> getSubscriptionBundlesForKey(final String bundleKey, final InternalTenantContext context) {
        final List<SubscriptionBaseBundle> results = new ArrayList<SubscriptionBaseBundle>();
        for (final SubscriptionBaseBundle cur : bundles) {
            if (cur.getExternalKey().equals(bundleKey)) {
                results.add(cur);
            }
        }
        return results;
    }

    @Override
    public Pagination<SubscriptionBundleModelDao> searchSubscriptionBundles(final String searchKey, final Long offset, final Long limit, final InternalTenantContext context) {
        final List<SubscriptionBundleModelDao> results = new LinkedList<SubscriptionBundleModelDao>();
        int maxNbRecords = 0;
        for (final SubscriptionBundleModelDao bundleModelDao : getAll(context)) {
            maxNbRecords++;
            if (bundleModelDao.getId().toString().equals(searchKey) ||
                bundleModelDao.getExternalKey().equals(searchKey) ||
                bundleModelDao.getAccountId().toString().equals(searchKey)) {
                results.add(bundleModelDao);
            }
        }

        return DefaultPagination.<SubscriptionBundleModelDao>build(offset, limit, maxNbRecords, results);
    }

    @Override
    public List<UUID> getNonAOSubscriptionIdsForKey(final String bundleKey, final InternalTenantContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SubscriptionBaseBundle getSubscriptionBundleFromId(final UUID bundleId, final InternalTenantContext context) {
        for (final SubscriptionBaseBundle cur : bundles) {
            if (cur.getId().equals(bundleId)) {
                return cur;
            }
        }
        return null;
    }

    @Override
    public List<SubscriptionBaseBundle> getSubscriptionBundlesForAccountAndKey(final UUID accountId, final String bundleKey, final InternalTenantContext context) {
        final List<SubscriptionBaseBundle> results = new ArrayList<SubscriptionBaseBundle>();
        for (final SubscriptionBaseBundle cur : bundles) {
            if (cur.getExternalKey().equals(bundleKey) && cur.getAccountId().equals(accountId)) {
                results.add(cur);
            }
        }
        return results;
    }

    @Override
    public SubscriptionBaseBundle createSubscriptionBundle(final DefaultSubscriptionBaseBundle bundle, final InternalCallContext context) {
        bundles.add(bundle);
        mockNonEntityDao.addTenantRecordIdMapping(bundle.getId(), context);
        return getSubscriptionBundleFromId(bundle.getId(), context);
    }

    @Override
    public SubscriptionBase getSubscriptionFromId(final UUID subscriptionId, final InternalTenantContext context) {
        for (final SubscriptionBase cur : subscriptions) {
            if (cur.getId().equals(subscriptionId)) {
                return buildSubscription((DefaultSubscriptionBase) cur, context);
            }
        }
        return null;
    }

    @Override
    public UUID getAccountIdFromSubscriptionId(final UUID subscriptionId, final InternalTenantContext context) {
        throw new UnsupportedOperationException();
    }

    /*
    @Override
    public List<SubscriptionBase> getSubscriptionsForAccountAndKey(final UUID accountId, final String bundleKey, final InternalTenantContext callcontext) {

        for (final SubscriptionBaseBundle cur : bundles) {
            if (cur.getExternalKey().equals(bundleKey) && cur.getAccountId().equals(bundleKey)) {
                return getSubscriptions(cur.getId(), callcontext);
            }
        }
        return Collections.emptyList();
    }
    */

    @Override
    public void createSubscription(final DefaultSubscriptionBase subscription, final List<SubscriptionBaseEvent> initialEvents,
                                   final InternalCallContext context) {
        synchronized (events) {
            events.addAll(initialEvents);
            for (final SubscriptionBaseEvent cur : initialEvents) {
                recordFutureNotificationFromTransaction(null, cur.getEffectiveDate(), new SubscriptionNotificationKey(cur.getId()), context);
            }
        }
        final SubscriptionBase updatedSubscription = buildSubscription(subscription, context);
        subscriptions.add(updatedSubscription);
        mockNonEntityDao.addTenantRecordIdMapping(updatedSubscription.getId(), context);
    }

    @Override
    public void createSubscriptionsWithAddOns(final List<SubscriptionBaseWithAddOns> subscriptions,
                                              final Map<UUID, List<SubscriptionBaseEvent>> initialEventsMap,
                                              final InternalCallContext context) {
        synchronized (events) {
            for (final SubscriptionBaseWithAddOns subscription : subscriptions) {
                for (final SubscriptionBase subscriptionBase : subscription.getSubscriptionBaseList()) {
                    final List<SubscriptionBaseEvent> initialEvents = initialEventsMap.get(subscriptionBase.getId());
                    events.addAll(initialEvents);
                    for (final SubscriptionBaseEvent cur : initialEvents) {
                        recordFutureNotificationFromTransaction(null, cur.getEffectiveDate(), new SubscriptionNotificationKey(cur.getId()), context);
                    }
                    final SubscriptionBase updatedSubscription = buildSubscription((DefaultSubscriptionBase) subscriptionBase, context);
                    this.subscriptions.add(updatedSubscription);
                    mockNonEntityDao.addTenantRecordIdMapping(updatedSubscription.getId(), context);
                }
            }
        }
    }

    @Override
    public List<SubscriptionBase> getSubscriptions(final UUID bundleId, final List<SubscriptionBaseEvent> dryRunEvents, final InternalTenantContext context) {
        final List<SubscriptionBase> results = new ArrayList<SubscriptionBase>();
        for (final SubscriptionBase cur : subscriptions) {
            if (cur.getBundleId().equals(bundleId)) {
                results.add(buildSubscription((DefaultSubscriptionBase) cur, context));
            }
        }
        return results;
    }

    @Override
    public Map<UUID, List<SubscriptionBase>> getSubscriptionsForAccount(final InternalTenantContext context) {
        final Map<UUID, List<SubscriptionBase>> results = new HashMap<UUID, List<SubscriptionBase>>();
        for (final SubscriptionBase cur : subscriptions) {
            if (results.get(cur.getBundleId()) == null) {
                results.put(cur.getBundleId(), new LinkedList<SubscriptionBase>());
            }
            results.get(cur.getBundleId()).add(buildSubscription((DefaultSubscriptionBase) cur, context));
        }
        return results;
    }

    @Override
    public List<SubscriptionBaseEvent> getEventsForSubscription(final UUID subscriptionId, final InternalTenantContext context) {
        synchronized (events) {
            final List<SubscriptionBaseEvent> results = new LinkedList<SubscriptionBaseEvent>();
            for (final SubscriptionBaseEvent cur : events) {
                if (cur.getSubscriptionId().equals(subscriptionId)) {
                    results.add(cur);
                }
            }
            return results;
        }
    }

    @Override
    public List<SubscriptionBaseEvent> getPendingEventsForSubscription(final UUID subscriptionId, final InternalTenantContext context) {
        synchronized (events) {
            final List<SubscriptionBaseEvent> results = new LinkedList<SubscriptionBaseEvent>();
            for (final SubscriptionBaseEvent cur : events) {
                if (cur.isActive() &&
                    cur.getEffectiveDate().isAfter(clock.getUTCNow()) &&
                    cur.getSubscriptionId().equals(subscriptionId)) {
                    results.add(cur);
                }
            }
            return results;
        }
    }

    @Override
    public SubscriptionBase getBaseSubscription(final UUID bundleId, final InternalTenantContext context) {
        for (final SubscriptionBase cur : subscriptions) {
            if (cur.getBundleId().equals(bundleId) &&
                cur.getCurrentPlan().getProduct().getCategory() == ProductCategory.BASE) {
                return buildSubscription((DefaultSubscriptionBase) cur, context);
            }
        }
        return null;
    }

    @Override
    public void createNextPhaseEvent(final DefaultSubscriptionBase subscription, final SubscriptionBaseEvent readyPhaseEvent, final SubscriptionBaseEvent nextPhase, final InternalCallContext context) {
        cancelNextPhaseEvent(subscription.getId(), context);
        insertEvent(nextPhase, context);
        notifyBusOfEffectiveImmediateChange(subscription, readyPhaseEvent, 0, context);
    }

    private SubscriptionBase buildSubscription(final DefaultSubscriptionBase in, final InternalTenantContext context) {
        final DefaultSubscriptionBase subscription = new DefaultSubscriptionBase(new SubscriptionBuilder(in), null, clock);
        if (events.size() > 0) {
            try {
                subscription.rebuildTransitions(getEventsForSubscription(in.getId(), context), catalogService.getFullCatalog(true, true, context));
            } catch (final CatalogApiException e) {
                log.warn("Failed to rebuild subscription", e);
            }
        }
        return subscription;

    }

    @Override
    public void updateChargedThroughDate(final DefaultSubscriptionBase subscription, final InternalCallContext context) {
        boolean found = false;
        final Iterator<SubscriptionBase> it = subscriptions.iterator();
        while (it.hasNext()) {
            final SubscriptionBase cur = it.next();
            if (cur.getId().equals(subscription.getId())) {
                found = true;
                it.remove();
                break;
            }
        }
        if (found) {
            subscriptions.add(subscription);
        }
    }

    @Override
    public void cancelSubscriptionsOnBasePlanEvent(final DefaultSubscriptionBase subscription, final SubscriptionBaseEvent event, final List<DefaultSubscriptionBase> subscriptions, final List<SubscriptionBaseEvent> cancelEvents, final InternalCallContext context) {
        cancelSubscriptions(subscriptions, cancelEvents, context);
        notifyBusOfEffectiveImmediateChange(subscription, event, subscriptions.size(), context);
    }

    @Override
    public void notifyOnBasePlanEvent(final DefaultSubscriptionBase subscription, final SubscriptionBaseEvent event, final InternalCallContext context) {
        notifyBusOfEffectiveImmediateChange(subscription, event, subscriptions.size(), context);
    }

    @Override
    public void cancelSubscriptions(final List<DefaultSubscriptionBase> subscriptions, final List<SubscriptionBaseEvent> cancelEvents, final InternalCallContext context) {
        synchronized (events) {
            for (int i = 0; i < subscriptions.size(); i++) {
                cancelNextPhaseEvent(subscriptions.get(i).getId(), context);
                insertEvent(cancelEvents.get(i), context);
            }
        }
    }

    @Override
    public void changePlan(final DefaultSubscriptionBase subscription, final List<SubscriptionBaseEvent> changeEvents, final List<DefaultSubscriptionBase> subscriptionsToBeCancelled, final List<SubscriptionBaseEvent> cancelEvents, final InternalCallContext context) {
        synchronized (events) {
            cancelNextChangeEvent(subscription.getId());
            cancelNextPhaseEvent(subscription.getId(), context);
            events.addAll(changeEvents);
            for (final SubscriptionBaseEvent cur : changeEvents) {
                recordFutureNotificationFromTransaction(null, cur.getEffectiveDate(), new SubscriptionNotificationKey(cur.getId()), context);
            }
        }

        cancelSubscriptions(subscriptionsToBeCancelled, cancelEvents, context);
    }

    private void insertEvent(final SubscriptionBaseEvent event, final InternalCallContext context) {
        synchronized (events) {
            events.add(event);
            mockNonEntityDao.addTenantRecordIdMapping(event.getId(), context);
            recordFutureNotificationFromTransaction(null, event.getEffectiveDate(), new SubscriptionNotificationKey(event.getId()), context);
        }
    }

    private void cancelNextPhaseEvent(final UUID subscriptionId, final InternalTenantContext context) {
        final SubscriptionBase curSubscription = getSubscriptionFromId(subscriptionId, context);
        if (curSubscription.getCurrentPhase() == null ||
            curSubscription.getCurrentPhase().getDuration().getUnit() == TimeUnit.UNLIMITED) {
            return;
        }

        synchronized (events) {

            final Iterator<SubscriptionBaseEvent> it = events.descendingIterator();
            while (it.hasNext()) {
                final SubscriptionBaseEvent cur = it.next();
                if (cur.getSubscriptionId() != subscriptionId) {
                    continue;
                }
                if (cur.getType() == EventType.PHASE &&
                    cur.getEffectiveDate().isAfter(clock.getUTCNow())) {
                    it.remove();
                    break;
                }

            }
        }
    }

    private void cancelNextChangeEvent(final UUID subscriptionId) {

        synchronized (events) {

            final Iterator<SubscriptionBaseEvent> it = events.descendingIterator();
            while (it.hasNext()) {
                final SubscriptionBaseEvent cur = it.next();
                if (cur.getSubscriptionId() != subscriptionId) {
                    continue;
                }
                if (cur.getType() == EventType.API_USER &&
                    ApiEventType.CHANGE == ((ApiEvent) cur).getApiEventType() &&
                    cur.getEffectiveDate().isAfter(clock.getUTCNow())) {
                    it.remove();
                    break;
                }
            }
        }
    }

    @Override
    public void uncancelSubscription(final DefaultSubscriptionBase subscription, final List<SubscriptionBaseEvent> uncancelEvents,
                                     final InternalCallContext context) {

        synchronized (events) {
            boolean foundCancel = false;
            final Iterator<SubscriptionBaseEvent> it = events.descendingIterator();
            while (it.hasNext()) {
                final SubscriptionBaseEvent cur = it.next();
                if (cur.getSubscriptionId() != subscription.getId()) {
                    continue;
                }
                if (cur.getType() == EventType.API_USER &&
                    ((ApiEvent) cur).getApiEventType() == ApiEventType.CANCEL) {
                    it.remove();
                    foundCancel = true;
                    break;
                }
            }
            if (foundCancel) {
                for (final SubscriptionBaseEvent cur : uncancelEvents) {
                    insertEvent(cur, context);
                }
            }
        }
    }

    @Override
    public SubscriptionBaseEvent getEventById(final UUID eventId, final InternalTenantContext context) {
        synchronized (events) {
            for (final SubscriptionBaseEvent cur : events) {
                if (cur.getId().equals(eventId)) {
                    return cur;
                }
            }
        }
        return null;
    }

    private void recordFutureNotificationFromTransaction(final EntitySqlDaoWrapperFactory transactionalDao, final DateTime effectiveDate,
                                                         final NotificationEvent notificationKey, final InternalCallContext context) {
        try {
            final NotificationQueue subscriptionEventQueue = notificationQueueService.getNotificationQueue(DefaultSubscriptionBaseService.SUBSCRIPTION_SERVICE_NAME,
                                                                                                           DefaultSubscriptionBaseService.NOTIFICATION_QUEUE_NAME);
            subscriptionEventQueue.recordFutureNotificationFromTransaction(null, effectiveDate, notificationKey, context.getUserToken(), context.getAccountRecordId(), context.getTenantRecordId());
        } catch (final NoSuchNotificationQueue e) {
            throw new RuntimeException(e);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void notifyBusOfEffectiveImmediateChange(final DefaultSubscriptionBase subscription, final SubscriptionBaseEvent immediateEvent, final int seqId, final InternalCallContext context) {
        try {
            final SubscriptionBaseTransitionData transition = subscription.getTransitionFromEvent(immediateEvent, seqId);
            final BusEvent busEvent = new DefaultEffectiveSubscriptionEvent(transition,
                                                                            subscription.getAlignStartDate(),
                                                                            context.getUserToken(),
                                                                            context.getAccountRecordId(),
                                                                            context.getTenantRecordId());

            eventBus.post(busEvent);
        } catch (final EventBusException e) {
            log.warn("Failed to post effective event for subscription " + subscription.getId(), e);
        }
    }


    @Override
    public void transfer(final UUID srcAccountId, final UUID destAccountId, final BundleTransferData data,
                         final List<TransferCancelData> transferCancelData, final InternalCallContext fromContext,
                         final InternalCallContext toContext) {
    }

    @Override
    public void updateBundleExternalKey(final UUID bundleId, final String externalKey, final InternalCallContext context) {
    }

    @Override
    public void createBCDChangeEvent(final DefaultSubscriptionBase subscription, final SubscriptionBaseEvent bcdEvent, final InternalCallContext context) {

    }

}
