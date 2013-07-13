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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.catalog.api.TimeUnit;
import com.ning.billing.clock.Clock;
import com.ning.billing.subscription.api.migration.AccountMigrationData;
import com.ning.billing.subscription.api.migration.AccountMigrationData.BundleMigrationData;
import com.ning.billing.subscription.api.migration.AccountMigrationData.SubscriptionMigrationData;
import com.ning.billing.subscription.api.timeline.SubscriptionDataRepair;
import com.ning.billing.subscription.api.transfer.TransferCancelData;
import com.ning.billing.subscription.api.user.SubscriptionBuilder;
import com.ning.billing.subscription.api.user.SubscriptionBundleData;
import com.ning.billing.subscription.api.user.SubscriptionData;
import com.ning.billing.subscription.engine.core.DefaultSubscriptionService;
import com.ning.billing.subscription.engine.core.SubscriptionNotificationKey;
import com.ning.billing.subscription.events.SubscriptionEvent;
import com.ning.billing.subscription.events.SubscriptionEvent.EventType;
import com.ning.billing.subscription.events.user.ApiEvent;
import com.ning.billing.subscription.events.user.ApiEventType;
import com.ning.billing.notificationq.api.NotificationEvent;
import com.ning.billing.notificationq.api.NotificationQueue;
import com.ning.billing.notificationq.api.NotificationQueueService;
import com.ning.billing.notificationq.api.NotificationQueueService.NoSuchNotificationQueue;
import com.ning.billing.subscription.api.user.Subscription;
import com.ning.billing.subscription.api.user.SubscriptionBundle;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.entity.dao.EntitySqlDao;
import com.ning.billing.util.entity.dao.EntitySqlDaoWrapperFactory;

import com.google.inject.Inject;

public class MockSubscriptionDaoMemory implements SubscriptionDao {

    protected static final Logger log = LoggerFactory.getLogger(SubscriptionDao.class);

    private final List<SubscriptionBundle> bundles;
    private final List<Subscription> subscriptions;
    private final TreeSet<SubscriptionEvent> events;
    private final Clock clock;
    private final NotificationQueueService notificationQueueService;
    private final CatalogService catalogService;

    @Inject
    public MockSubscriptionDaoMemory(final Clock clock,
                                     final NotificationQueueService notificationQueueService,
                                     final CatalogService catalogService) {
        super();
        this.clock = clock;
        this.catalogService = catalogService;
        this.notificationQueueService = notificationQueueService;
        this.bundles = new ArrayList<SubscriptionBundle>();
        this.subscriptions = new ArrayList<Subscription>();
        this.events = new TreeSet<SubscriptionEvent>();
    }

    public void reset() {
        bundles.clear();
        subscriptions.clear();
        events.clear();
    }

    @Override
    public List<SubscriptionBundle> getSubscriptionBundleForAccount(final UUID accountId, final InternalTenantContext context) {
        final List<SubscriptionBundle> results = new ArrayList<SubscriptionBundle>();
        for (final SubscriptionBundle cur : bundles) {
            if (cur.getAccountId().equals(accountId)) {
                results.add(cur);
            }
        }
        return results;
    }

    @Override
    public List<SubscriptionBundle> getSubscriptionBundlesForKey(final String bundleKey, final InternalTenantContext context) {
        final List<SubscriptionBundle> results = new ArrayList<SubscriptionBundle>();
        for (final SubscriptionBundle cur : bundles) {
            if (cur.getExternalKey().equals(bundleKey)) {
                results.add(cur);
            }
        }
        return results;
    }

    @Override
    public SubscriptionBundle getSubscriptionBundleFromId(final UUID bundleId, final InternalTenantContext context) {
        for (final SubscriptionBundle cur : bundles) {
            if (cur.getId().equals(bundleId)) {
                return cur;
            }
        }
        return null;
    }

    @Override
    public SubscriptionBundle getSubscriptionBundleFromAccountAndKey(final UUID accountId, final String bundleKey, final InternalTenantContext context) {
        for (final SubscriptionBundle cur : bundles) {
            if (cur.getExternalKey().equals(bundleKey) && cur.getAccountId().equals(accountId)) {
                return cur;
            }
        }
        return null;
    }

    @Override
    public SubscriptionBundle createSubscriptionBundle(final SubscriptionBundleData bundle, final InternalCallContext context) {
        bundles.add(bundle);
        return getSubscriptionBundleFromId(bundle.getId(), context);
    }

    @Override
    public Subscription getSubscriptionFromId(final UUID subscriptionId, final InternalTenantContext context) {
        for (final Subscription cur : subscriptions) {
            if (cur.getId().equals(subscriptionId)) {
                return buildSubscription((SubscriptionData) cur, context);
            }
        }
        return null;
    }

    @Override
    public UUID getAccountIdFromSubscriptionId(final UUID subscriptionId, final InternalTenantContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Subscription> getSubscriptionsForAccountAndKey(final UUID accountId, final String bundleKey, final InternalTenantContext context) {

        for (final SubscriptionBundle cur : bundles) {
            if (cur.getExternalKey().equals(bundleKey) && cur.getAccountId().equals(bundleKey)) {
                return getSubscriptions(cur.getId(), context);
            }
        }
        return Collections.emptyList();
    }

    @Override
    public void createSubscription(final SubscriptionData subscription, final List<SubscriptionEvent> initialEvents,
                                   final InternalCallContext context) {
        synchronized (events) {
            events.addAll(initialEvents);
            for (final SubscriptionEvent cur : initialEvents) {
                recordFutureNotificationFromTransaction(null, cur.getEffectiveDate(), new SubscriptionNotificationKey(cur.getId()), context);
            }
        }
        final Subscription updatedSubscription = buildSubscription(subscription, context);
        subscriptions.add(updatedSubscription);
    }

    @Override
    public void recreateSubscription(final SubscriptionData subscription, final List<SubscriptionEvent> recreateEvents, final InternalCallContext context) {
        synchronized (events) {
            events.addAll(recreateEvents);
            for (final SubscriptionEvent cur : recreateEvents) {
                recordFutureNotificationFromTransaction(null, cur.getEffectiveDate(), new SubscriptionNotificationKey(cur.getId()), context);
            }
        }
    }

    @Override
    public List<Subscription> getSubscriptions(final UUID bundleId, final InternalTenantContext context) {
        final List<Subscription> results = new ArrayList<Subscription>();
        for (final Subscription cur : subscriptions) {
            if (cur.getBundleId().equals(bundleId)) {
                results.add(buildSubscription((SubscriptionData) cur, context));
            }
        }
        return results;
    }

    @Override
    public List<SubscriptionEvent> getEventsForSubscription(final UUID subscriptionId, final InternalTenantContext context) {
        synchronized (events) {
            final List<SubscriptionEvent> results = new LinkedList<SubscriptionEvent>();
            for (final SubscriptionEvent cur : events) {
                if (cur.getSubscriptionId().equals(subscriptionId)) {
                    results.add(cur);
                }
            }
            return results;
        }
    }

    @Override
    public List<SubscriptionEvent> getPendingEventsForSubscription(final UUID subscriptionId, final InternalTenantContext context) {
        synchronized (events) {
            final List<SubscriptionEvent> results = new LinkedList<SubscriptionEvent>();
            for (final SubscriptionEvent cur : events) {
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
    public Subscription getBaseSubscription(final UUID bundleId, final InternalTenantContext context) {
        for (final Subscription cur : subscriptions) {
            if (cur.getBundleId().equals(bundleId) &&
                cur.getCurrentPlan().getProduct().getCategory() == ProductCategory.BASE) {
                return buildSubscription((SubscriptionData) cur, context);
            }
        }
        return null;
    }

    @Override
    public void createNextPhaseEvent(final SubscriptionData subscription, final SubscriptionEvent nextPhase, final InternalCallContext context) {
        cancelNextPhaseEvent(subscription.getId(), context);
        insertEvent(nextPhase, context);
    }

    private Subscription buildSubscription(final SubscriptionData in, final InternalTenantContext context) {
        final SubscriptionData subscription = new SubscriptionData(new SubscriptionBuilder(in), null, clock);
        if (events.size() > 0) {
            subscription.rebuildTransitions(getEventsForSubscription(in.getId(), context), catalogService.getFullCatalog());
        }
        return subscription;

    }

    @Override
    public void updateChargedThroughDate(final SubscriptionData subscription, final InternalCallContext context) {
        boolean found = false;
        final Iterator<Subscription> it = subscriptions.iterator();
        while (it.hasNext()) {
            final Subscription cur = it.next();
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
    public void cancelSubscription(final SubscriptionData subscription, final SubscriptionEvent cancelEvent,
                                   final InternalCallContext context, final int seqId) {
        synchronized (events) {
            cancelNextPhaseEvent(subscription.getId(), context);
            insertEvent(cancelEvent, context);
        }
    }

    @Override
    public void cancelSubscriptions(final List<SubscriptionData> subscriptions, final List<SubscriptionEvent> cancelEvents, final InternalCallContext context) {
        synchronized (events) {
            for (int i = 0; i < subscriptions.size(); i++) {
                cancelSubscription(subscriptions.get(i), cancelEvents.get(i), context, 0);
            }
        }
    }

    @Override
    public void changePlan(final SubscriptionData subscription, final List<SubscriptionEvent> changeEvents, final InternalCallContext context) {
        synchronized (events) {
            cancelNextChangeEvent(subscription.getId());
            cancelNextPhaseEvent(subscription.getId(), context);
            events.addAll(changeEvents);
            for (final SubscriptionEvent cur : changeEvents) {
                recordFutureNotificationFromTransaction(null, cur.getEffectiveDate(), new SubscriptionNotificationKey(cur.getId()), context);
            }
        }
    }

    private void insertEvent(final SubscriptionEvent event, final InternalCallContext context) {
        synchronized (events) {
            events.add(event);
            recordFutureNotificationFromTransaction(null, event.getEffectiveDate(), new SubscriptionNotificationKey(event.getId()), context);
        }
    }

    private void cancelNextPhaseEvent(final UUID subscriptionId, final InternalTenantContext context) {
        final Subscription curSubscription = getSubscriptionFromId(subscriptionId, context);
        if (curSubscription.getCurrentPhase() == null ||
            curSubscription.getCurrentPhase().getDuration().getUnit() == TimeUnit.UNLIMITED) {
            return;
        }

        synchronized (events) {

            final Iterator<SubscriptionEvent> it = events.descendingIterator();
            while (it.hasNext()) {
                final SubscriptionEvent cur = it.next();
                if (cur.getSubscriptionId() != subscriptionId) {
                    continue;
                }
                if (cur.getType() == EventType.PHASE &&
                    cur.getEffectiveDate().isAfter(clock.getUTCNow())) {
                    cur.deactivate();
                    break;
                }

            }
        }
    }

    private void cancelNextChangeEvent(final UUID subscriptionId) {

        synchronized (events) {

            final Iterator<SubscriptionEvent> it = events.descendingIterator();
            while (it.hasNext()) {
                final SubscriptionEvent cur = it.next();
                if (cur.getSubscriptionId() != subscriptionId) {
                    continue;
                }
                if (cur.getType() == EventType.API_USER &&
                    ApiEventType.CHANGE == ((ApiEvent) cur).getEventType() &&
                    cur.getEffectiveDate().isAfter(clock.getUTCNow())) {
                    cur.deactivate();
                    break;
                }
            }
        }
    }

    @Override
    public void uncancelSubscription(final SubscriptionData subscription, final List<SubscriptionEvent> uncancelEvents,
                                     final InternalCallContext context) {

        synchronized (events) {
            boolean foundCancel = false;
            final Iterator<SubscriptionEvent> it = events.descendingIterator();
            while (it.hasNext()) {
                final SubscriptionEvent cur = it.next();
                if (cur.getSubscriptionId() != subscription.getId()) {
                    continue;
                }
                if (cur.getType() == EventType.API_USER &&
                    ((ApiEvent) cur).getEventType() == ApiEventType.CANCEL) {
                    cur.deactivate();
                    foundCancel = true;
                    break;
                }
            }
            if (foundCancel) {
                for (final SubscriptionEvent cur : uncancelEvents) {
                    insertEvent(cur, context);
                }
            }
        }
    }

    @Override
    public void migrate(final UUID accountId, final AccountMigrationData accountData, final InternalCallContext context) {
        synchronized (events) {

            for (final BundleMigrationData curBundle : accountData.getData()) {
                final SubscriptionBundleData bundleData = curBundle.getData();
                for (final SubscriptionMigrationData curSubscription : curBundle.getSubscriptions()) {
                    final SubscriptionData subData = curSubscription.getData();
                    for (final SubscriptionEvent curEvent : curSubscription.getInitialEvents()) {
                        events.add(curEvent);
                        recordFutureNotificationFromTransaction(null, curEvent.getEffectiveDate(),
                                                                new SubscriptionNotificationKey(curEvent.getId()), context);

                    }
                    subscriptions.add(subData);
                }
                bundles.add(bundleData);
            }
        }
    }

    @Override
    public SubscriptionEvent getEventById(final UUID eventId, final InternalTenantContext context) {
        synchronized (events) {
            for (final SubscriptionEvent cur : events) {
                if (cur.getId().equals(eventId)) {
                    return cur;
                }
            }
        }
        return null;
    }

    private void recordFutureNotificationFromTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> transactionalDao, final DateTime effectiveDate,
                                                         final NotificationEvent notificationKey, final InternalCallContext context) {
        try {
            final NotificationQueue subscriptionEventQueue = notificationQueueService.getNotificationQueue(DefaultSubscriptionService.ENTITLEMENT_SERVICE_NAME,
                                                                                                           DefaultSubscriptionService.NOTIFICATION_QUEUE_NAME);
            subscriptionEventQueue.recordFutureNotificationFromTransaction(null, effectiveDate, notificationKey, context.getUserToken(), context.getAccountRecordId(), context.getTenantRecordId());
        } catch (NoSuchNotificationQueue e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Map<UUID, List<SubscriptionEvent>> getEventsForBundle(final UUID bundleId, final InternalTenantContext context) {
        return null;
    }

    @Override
    public void repair(final UUID accountId, final UUID bundleId, final List<SubscriptionDataRepair> inRepair,
                       final InternalCallContext context) {
    }

    @Override
    public void transfer(final UUID srcAccountId, final UUID destAccountId, final BundleMigrationData data,
                         final List<TransferCancelData> transferCancelData, final InternalCallContext fromContext,
                         final InternalCallContext toContext) {
    }
}
