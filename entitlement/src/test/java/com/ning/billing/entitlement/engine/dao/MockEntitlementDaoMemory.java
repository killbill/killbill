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

import com.google.inject.Inject;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.catalog.api.TimeUnit;
import com.ning.billing.config.EntitlementConfig;

import com.ning.billing.entitlement.api.migration.AccountMigrationData;
import com.ning.billing.entitlement.api.migration.AccountMigrationData.BundleMigrationData;
import com.ning.billing.entitlement.api.migration.AccountMigrationData.SubscriptionMigrationData;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.api.user.SubscriptionBundleData;
import com.ning.billing.entitlement.api.user.SubscriptionFactory;

import com.ning.billing.entitlement.api.user.SubscriptionFactory.SubscriptionBuilder;
import com.ning.billing.entitlement.engine.core.Engine;
import com.ning.billing.entitlement.events.EntitlementEvent;
import com.ning.billing.entitlement.events.EntitlementEvent.EventType;
import com.ning.billing.entitlement.events.user.ApiEvent;
import com.ning.billing.entitlement.events.user.ApiEventType;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.notificationq.NotificationKey;
import com.ning.billing.util.notificationq.NotificationLifecycle;
import com.ning.billing.util.notificationq.NotificationQueue;
import com.ning.billing.util.notificationq.NotificationQueueService;
import com.ning.billing.util.notificationq.NotificationQueueService.NoSuchNotificationQueue;

import org.joda.time.DateTime;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MockEntitlementDaoMemory implements EntitlementDao, MockEntitlementDao {

    protected final static Logger log = LoggerFactory.getLogger(EntitlementDao.class);

    private final List<SubscriptionBundle> bundles;
    private final List<Subscription> subscriptions;
    private final TreeSet<EntitlementEvent> events;
    private final Clock clock;
    private final EntitlementConfig config;
    private final SubscriptionFactory factory;
    private final NotificationQueueService notificationQueueService;

    @Inject
    public MockEntitlementDaoMemory(Clock clock, EntitlementConfig config, SubscriptionFactory factory, NotificationQueueService notificationQueueService) {
        super();
        this.clock = clock;
        this.config = config;
        this.factory = factory;
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
    public List<SubscriptionBundle> getSubscriptionBundleForAccount(UUID accountId) {
        List<SubscriptionBundle> results = new ArrayList<SubscriptionBundle>();
        for (SubscriptionBundle cur : bundles) {
            if (cur.getAccountId().equals(accountId)) {
                results.add(cur);
            }
        }
        return results;
    }

    @Override
    public SubscriptionBundle getSubscriptionBundleFromId(UUID bundleId) {
        for (SubscriptionBundle cur : bundles) {
            if (cur.getId().equals(bundleId)) {
                return cur;
            }
        }
        return null;
    }

    @Override
    public SubscriptionBundle getSubscriptionBundleFromKey(String bundleKey) {
        for (SubscriptionBundle cur : bundles) {
            if (cur.getKey().equals(bundleKey)) {
                return cur;
            }
        }
        return null;
    }


    @Override
    public SubscriptionBundle createSubscriptionBundle(SubscriptionBundleData bundle) {
        bundles.add(bundle);
        return getSubscriptionBundleFromId(bundle.getId());
    }

    @Override
    public Subscription getSubscriptionFromId(UUID subscriptionId) {
        for (Subscription cur : subscriptions) {
            if (cur.getId().equals(subscriptionId)) {
                return buildSubscription((SubscriptionData) cur);
            }
        }
        return null;
    }

    @Override
    public List<Subscription> getSubscriptionsForKey(String bundleKey) {

        for (SubscriptionBundle cur : bundles) {
            if (cur.getKey().equals(bundleKey)) {
                return getSubscriptions(cur.getId());
            }
        }
        return Collections.emptyList();
    }


    @Override
    public void createSubscription(SubscriptionData subscription, List<EntitlementEvent> initalEvents) {

        synchronized(events) {
            events.addAll(initalEvents);
            for (final EntitlementEvent cur : initalEvents) {
                recordFutureNotificationFromTransaction(null, cur.getEffectiveDate(), new NotificationKey() {
                    @Override
                    public String toString() {
                        return cur.getId().toString();
                    }
                });
            }
        }
        Subscription updatedSubscription = buildSubscription(subscription);
        subscriptions.add(updatedSubscription);
    }

    @Override
    public List<Subscription> getSubscriptions(UUID bundleId) {

        List<Subscription> results = new ArrayList<Subscription>();
        for (Subscription cur : subscriptions) {
            if (cur.getBundleId().equals(bundleId)) {
                results.add(buildSubscription((SubscriptionData) cur));
            }
        }
        return results;
    }

    @Override
    public List<EntitlementEvent> getEventsForSubscription(UUID subscriptionId) {
        synchronized(events) {
            List<EntitlementEvent> results = new LinkedList<EntitlementEvent>();
            for (EntitlementEvent cur : events) {
                if (cur.getSubscriptionId().equals(subscriptionId)) {
                    results.add(cur);
                }
            }
            return results;
        }
    }

    @Override
    public List<EntitlementEvent> getPendingEventsForSubscription(UUID subscriptionId) {
        synchronized(events) {
            List<EntitlementEvent> results = new LinkedList<EntitlementEvent>();
            for (EntitlementEvent cur : events) {
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
    public Subscription getBaseSubscription(UUID bundleId) {
        for (Subscription cur : subscriptions) {
            if (cur.getBundleId().equals(bundleId) &&
                    cur.getCurrentPlan().getProduct().getCategory() == ProductCategory.BASE) {
                return buildSubscription((SubscriptionData) cur);
            }
        }
        return null;
    }

    @Override
    public void createNextPhaseEvent(UUID subscriptionId, EntitlementEvent nextPhase) {
        cancelNextPhaseEvent(subscriptionId);
        insertEvent(nextPhase);
    }



    private Subscription buildSubscription(SubscriptionData in) {
        return factory.createSubscription(new SubscriptionBuilder(in), getEventsForSubscription(in.getId()));
    }

    @Override
    public void updateSubscription(SubscriptionData subscription) {

        boolean found = false;
        Iterator<Subscription> it = subscriptions.iterator();
        while (it.hasNext()) {
            Subscription cur = it.next();
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
    public void cancelSubscription(UUID subscriptionId, EntitlementEvent cancelEvent) {
        synchronized (cancelEvent) {
            cancelNextPhaseEvent(subscriptionId);
            insertEvent(cancelEvent);
        }
    }

    @Override
    public void changePlan(UUID subscriptionId, List<EntitlementEvent> changeEvents) {
        synchronized(events) {
            cancelNextChangeEvent(subscriptionId);
            cancelNextPhaseEvent(subscriptionId);
            events.addAll(changeEvents);
            for (final EntitlementEvent cur : changeEvents) {
                recordFutureNotificationFromTransaction(null, cur.getEffectiveDate(), new NotificationKey() {
                    @Override
                    public String toString() {
                        return cur.getId().toString();
                    }
                });
            }
        }
    }

    private void insertEvent(final EntitlementEvent event) {
        synchronized(events) {
            events.add(event);
            recordFutureNotificationFromTransaction(null, event.getEffectiveDate(), new NotificationKey() {
                @Override
                public String toString() {
                    return event.getId().toString();
                }
            });
        }
    }

    private void cancelNextPhaseEvent(UUID subscriptionId) {

        Subscription curSubscription = getSubscriptionFromId(subscriptionId);
        if (curSubscription.getCurrentPhase() == null ||
                curSubscription.getCurrentPhase().getDuration().getUnit() == TimeUnit.UNLIMITED) {
            return;
        }

        synchronized(events) {

            Iterator<EntitlementEvent> it = events.descendingIterator();
            while (it.hasNext()) {
                EntitlementEvent cur = it.next();
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


    private void cancelNextChangeEvent(UUID subscriptionId) {

        synchronized(events) {

            Iterator<EntitlementEvent> it = events.descendingIterator();
            while (it.hasNext()) {
                EntitlementEvent cur = it.next();
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
    public void uncancelSubscription(UUID subscriptionId, List<EntitlementEvent> uncancelEvents) {

        synchronized (events) {
            boolean foundCancel = false;
            Iterator<EntitlementEvent> it = events.descendingIterator();
            while (it.hasNext()) {
                EntitlementEvent cur = it.next();
                if (cur.getSubscriptionId() != subscriptionId) {
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
                for (EntitlementEvent cur : uncancelEvents) {
                    insertEvent(cur);
                }
            }
        }
    }


    @Override
    public void migrate(final UUID accountId, final AccountMigrationData accountData) {
        synchronized(events) {

            undoMigration(accountId);

            for (BundleMigrationData curBundle : accountData.getData()) {
                SubscriptionBundleData bundleData = curBundle.getData();
                for (SubscriptionMigrationData curSubscription : curBundle.getSubscriptions()) {
                    SubscriptionData subData = curSubscription.getData();
                    for (final EntitlementEvent curEvent : curSubscription.getInitialEvents()) {
                        events.add(curEvent);
                        recordFutureNotificationFromTransaction(null, curEvent.getEffectiveDate(), new NotificationKey() {
                            @Override
                            public String toString() {
                                return curEvent.getId().toString();
                            }
                        });

                    }
                    subscriptions.add(subData);
                }
                bundles.add(bundleData);
            }
        }
    }

    @Override
    public void undoMigration(UUID accountId) {
        synchronized(events) {

            List<SubscriptionBundle> allBundles = getSubscriptionBundleForAccount(accountId);
            for (SubscriptionBundle bundle : allBundles) {
                List<Subscription> allSubscriptions = getSubscriptions(bundle.getId());
                for (Subscription subscription : allSubscriptions) {
                    List<EntitlementEvent> allEvents = getEventsForSubscription(subscription.getId());
                    for (EntitlementEvent event : allEvents) {
                        events.remove(event);
                    }
                    subscriptions.remove(subscription);
                }
                bundles.remove(bundle);
            }
        }

    }

    @Override
    public EntitlementEvent getEventById(UUID eventId) {
        synchronized(events) {
            for (EntitlementEvent cur : events) {
                if (cur.getId().equals(eventId)) {
                    return cur;
                }
            }
        }
        return null;
    }

    private void recordFutureNotificationFromTransaction(final Transmogrifier transactionalDao, final DateTime effectiveDate, final NotificationKey notificationKey) {
        try {
            NotificationQueue subscritionEventQueue = notificationQueueService.getNotificationQueue(Engine.ENTITLEMENT_SERVICE_NAME,
                Engine.NOTIFICATION_QUEUE_NAME);
            subscritionEventQueue.recordFutureNotificationFromTransaction(transactionalDao, effectiveDate, notificationKey);
        } catch (NoSuchNotificationQueue e) {
            throw new RuntimeException(e);
        }
    }
}
