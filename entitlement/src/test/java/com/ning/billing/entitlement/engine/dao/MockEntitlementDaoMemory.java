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
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.catalog.api.TimeUnit;
import com.ning.billing.config.EntitlementConfig;
import com.ning.billing.entitlement.api.SubscriptionFactory;
import com.ning.billing.entitlement.api.migration.AccountMigrationData;
import com.ning.billing.entitlement.api.migration.AccountMigrationData.BundleMigrationData;
import com.ning.billing.entitlement.api.migration.AccountMigrationData.SubscriptionMigrationData;
import com.ning.billing.entitlement.api.timeline.SubscriptionDataRepair;
import com.ning.billing.entitlement.api.user.DefaultSubscriptionFactory.SubscriptionBuilder;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionBundleData;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.engine.core.Engine;
import com.ning.billing.entitlement.engine.core.EntitlementNotificationKey;
import com.ning.billing.entitlement.events.EntitlementEvent;
import com.ning.billing.entitlement.events.EntitlementEvent.EventType;
import com.ning.billing.entitlement.events.user.ApiEvent;
import com.ning.billing.entitlement.events.user.ApiEventType;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.notificationq.NotificationKey;
import com.ning.billing.util.notificationq.NotificationQueue;
import com.ning.billing.util.notificationq.NotificationQueueService;
import com.ning.billing.util.notificationq.NotificationQueueService.NoSuchNotificationQueue;

public class MockEntitlementDaoMemory implements EntitlementDao, MockEntitlementDao {

    protected static final Logger log = LoggerFactory.getLogger(EntitlementDao.class);

    private final List<SubscriptionBundle> bundles;
    private final List<Subscription> subscriptions;
    private final TreeSet<EntitlementEvent> events;
    private final Clock clock;
    private final NotificationQueueService notificationQueueService;
    private final CatalogService catalogService;

    @Inject
    public MockEntitlementDaoMemory(final Clock clock, final EntitlementConfig config,
                                    final NotificationQueueService notificationQueueService,
                                    final CatalogService catalogService) {
        super();
        this.clock = clock;
        final EntitlementConfig config1 = config;
        this.catalogService = catalogService;
        this.notificationQueueService = notificationQueueService;
        this.bundles = new ArrayList<SubscriptionBundle>();
        this.subscriptions = new ArrayList<Subscription>();
        this.events = new TreeSet<EntitlementEvent>();
    }

    @Override
    public void reset() {
        bundles.clear();
        subscriptions.clear();
        events.clear();
    }

    @Override
    public List<SubscriptionBundle> getSubscriptionBundleForAccount(final UUID accountId) {
        final List<SubscriptionBundle> results = new ArrayList<SubscriptionBundle>();
        for (final SubscriptionBundle cur : bundles) {
            if (cur.getAccountId().equals(accountId)) {
                results.add(cur);
            }
        }
        return results;
    }

    @Override
    public SubscriptionBundle getSubscriptionBundleFromId(final UUID bundleId) {
        for (final SubscriptionBundle cur : bundles) {
            if (cur.getId().equals(bundleId)) {
                return cur;
            }
        }
        return null;
    }

    @Override
    public SubscriptionBundle getSubscriptionBundleFromKey(final String bundleKey) {
        for (final SubscriptionBundle cur : bundles) {
            if (cur.getKey().equals(bundleKey)) {
                return cur;
            }
        }
        return null;
    }

    @Override
    public SubscriptionBundle createSubscriptionBundle(final SubscriptionBundleData bundle, final CallContext context) {
        bundles.add(bundle);
        return getSubscriptionBundleFromId(bundle.getId());
    }

    @Override
    public Subscription getSubscriptionFromId(final SubscriptionFactory factory, final UUID subscriptionId) {
        for (final Subscription cur : subscriptions) {
            if (cur.getId().equals(subscriptionId)) {
                return buildSubscription(factory, (SubscriptionData) cur);
            }
        }
        return null;
    }

    @Override
    public UUID getAccountIdFromSubscriptionId(final UUID subscriptionId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Subscription> getSubscriptionsForKey(final SubscriptionFactory factory, final String bundleKey) {

        for (final SubscriptionBundle cur : bundles) {
            if (cur.getKey().equals(bundleKey)) {
                return getSubscriptions(factory, cur.getId());
            }
        }
        return Collections.emptyList();
    }


    @Override
    public void createSubscription(final SubscriptionData subscription, final List<EntitlementEvent> initialEvents,
                                   final CallContext context) {
        synchronized (events) {
            events.addAll(initialEvents);
            for (final EntitlementEvent cur : initialEvents) {
                recordFutureNotificationFromTransaction(null, cur.getEffectiveDate(), new EntitlementNotificationKey(cur.getId()));
            }
        }
        final Subscription updatedSubscription = buildSubscription(null, subscription);
        subscriptions.add(updatedSubscription);
    }

    @Override
    public void recreateSubscription(final SubscriptionData subscription, final List<EntitlementEvent> recreateEvents, final CallContext context) {
        synchronized (events) {
            events.addAll(recreateEvents);
            for (final EntitlementEvent cur : recreateEvents) {
                recordFutureNotificationFromTransaction(null, cur.getEffectiveDate(), new EntitlementNotificationKey(cur.getId()));
            }
        }
    }

    @Override
    public List<Subscription> getSubscriptions(final SubscriptionFactory factory, final UUID bundleId) {

        final List<Subscription> results = new ArrayList<Subscription>();
        for (final Subscription cur : subscriptions) {
            if (cur.getBundleId().equals(bundleId)) {
                results.add(buildSubscription(factory, (SubscriptionData) cur));
            }
        }
        return results;
    }

    @Override
    public List<EntitlementEvent> getEventsForSubscription(final UUID subscriptionId) {
        synchronized (events) {
            final List<EntitlementEvent> results = new LinkedList<EntitlementEvent>();
            for (final EntitlementEvent cur : events) {
                if (cur.getSubscriptionId().equals(subscriptionId)) {
                    results.add(cur);
                }
            }
            return results;
        }
    }

    @Override
    public List<EntitlementEvent> getPendingEventsForSubscription(final UUID subscriptionId) {
        synchronized (events) {
            final List<EntitlementEvent> results = new LinkedList<EntitlementEvent>();
            for (final EntitlementEvent cur : events) {
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
    public Subscription getBaseSubscription(final SubscriptionFactory factory, final UUID bundleId) {
        for (final Subscription cur : subscriptions) {
            if (cur.getBundleId().equals(bundleId) &&
                    cur.getCurrentPlan().getProduct().getCategory() == ProductCategory.BASE) {
                return buildSubscription(factory, (SubscriptionData) cur);
            }
        }
        return null;
    }

    @Override
    public void createNextPhaseEvent(final SubscriptionData subscription, final EntitlementEvent nextPhase,
                                     final CallContext context) {
        cancelNextPhaseEvent(subscription.getId());
        insertEvent(nextPhase);
    }


    private Subscription buildSubscription(final SubscriptionFactory factory, final SubscriptionData in) {
        if (factory != null) {
            return factory.createSubscription(new SubscriptionBuilder(in), getEventsForSubscription(in.getId()));
        } else {
            final SubscriptionData subscription = new SubscriptionData(new SubscriptionBuilder(in), null, clock);
            if (events.size() > 0) {
                subscription.rebuildTransitions(getEventsForSubscription(in.getId()), catalogService.getFullCatalog());
            }
            return subscription;
        }
    }

    @Override
    public void updateChargedThroughDate(final SubscriptionData subscription, final CallContext context) {

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
    public void cancelSubscription(final SubscriptionData subscription, final EntitlementEvent cancelEvent,
                                   final CallContext context, final int seqId) {
        synchronized (events) {
            cancelNextPhaseEvent(subscription.getId());
            insertEvent(cancelEvent);
        }
    }

    @Override
    public void changePlan(final SubscriptionData subscription, final List<EntitlementEvent> changeEvents,
                           final CallContext context) {
        synchronized (events) {
            final UUID subscriptionId = subscription.getId();
            cancelNextChangeEvent(subscriptionId);
            cancelNextPhaseEvent(subscriptionId);
            events.addAll(changeEvents);
            for (final EntitlementEvent cur : changeEvents) {
                recordFutureNotificationFromTransaction(null, cur.getEffectiveDate(), new EntitlementNotificationKey(cur.getId()));
            }
        }
    }

    private void insertEvent(final EntitlementEvent event) {
        synchronized (events) {
            events.add(event);
            recordFutureNotificationFromTransaction(null, event.getEffectiveDate(), new EntitlementNotificationKey(event.getId()));
        }
    }


    private void cancelNextPhaseEvent(final UUID subscriptionId) {

        final Subscription curSubscription = getSubscriptionFromId(null, subscriptionId);
        if (curSubscription.getCurrentPhase() == null ||
                curSubscription.getCurrentPhase().getDuration().getUnit() == TimeUnit.UNLIMITED) {
            return;
        }

        synchronized (events) {

            final Iterator<EntitlementEvent> it = events.descendingIterator();
            while (it.hasNext()) {
                final EntitlementEvent cur = it.next();
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

            final Iterator<EntitlementEvent> it = events.descendingIterator();
            while (it.hasNext()) {
                final EntitlementEvent cur = it.next();
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
    public void uncancelSubscription(final SubscriptionData subscription, final List<EntitlementEvent> uncancelEvents,
                                     final CallContext context) {

        synchronized (events) {
            boolean foundCancel = false;
            final Iterator<EntitlementEvent> it = events.descendingIterator();
            while (it.hasNext()) {
                final EntitlementEvent cur = it.next();
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
                for (final EntitlementEvent cur : uncancelEvents) {
                    insertEvent(cur);
                }
            }
        }
    }


    @Override
    public void migrate(final UUID accountId, final AccountMigrationData accountData, final CallContext context) {
        synchronized (events) {

            for (final BundleMigrationData curBundle : accountData.getData()) {
                final SubscriptionBundleData bundleData = curBundle.getData();
                for (final SubscriptionMigrationData curSubscription : curBundle.getSubscriptions()) {
                    final SubscriptionData subData = curSubscription.getData();
                    for (final EntitlementEvent curEvent : curSubscription.getInitialEvents()) {
                        events.add(curEvent);
                        recordFutureNotificationFromTransaction(null, curEvent.getEffectiveDate(), new EntitlementNotificationKey(curEvent.getId()));

                    }
                    subscriptions.add(subData);
                }
                bundles.add(bundleData);
            }
        }
    }

    @Override
    public EntitlementEvent getEventById(final UUID eventId) {
        synchronized (events) {
            for (final EntitlementEvent cur : events) {
                if (cur.getId().equals(eventId)) {
                    return cur;
                }
            }
        }
        return null;
    }

    private void recordFutureNotificationFromTransaction(final Transmogrifier transactionalDao, final DateTime effectiveDate, final NotificationKey notificationKey) {
        try {
            final NotificationQueue subscriptionEventQueue = notificationQueueService.getNotificationQueue(Engine.ENTITLEMENT_SERVICE_NAME,
                                                                                                     Engine.NOTIFICATION_QUEUE_NAME);
            subscriptionEventQueue.recordFutureNotificationFromTransaction(transactionalDao, effectiveDate, notificationKey);
        } catch (NoSuchNotificationQueue e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);            
        }
    }

    @Override
    public Map<UUID, List<EntitlementEvent>> getEventsForBundle(final UUID bundleId) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void repair(final UUID accountId, final UUID bundleId, final List<SubscriptionDataRepair> inRepair,
                       final CallContext context) {
    }
}
