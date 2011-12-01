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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.catalog.api.TimeUnit;
import com.ning.billing.config.EntitlementConfig;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.api.user.SubscriptionBuilder;
import com.ning.billing.entitlement.api.user.SubscriptionBundleData;
import com.ning.billing.entitlement.events.EntitlementEvent;
import com.ning.billing.entitlement.events.EntitlementEvent.EventType;
import com.ning.billing.entitlement.events.EventLifecycle.EventLifecycleState;
import com.ning.billing.entitlement.events.user.ApiEventType;
import com.ning.billing.entitlement.events.user.ApiEvent;
import com.ning.billing.util.clock.Clock;

public class MockEntitlementDaoMemory implements EntitlementDao, MockEntitlementDao {

    protected final static Logger log = LoggerFactory.getLogger(EntitlementDao.class);

    private final List<SubscriptionBundle> bundles;
    private final List<Subscription> subscriptions;
    private final TreeSet<EntitlementEvent> events;
    private final Clock clock;
    private final EntitlementConfig config;



    @Inject
    public MockEntitlementDaoMemory(Clock clock, EntitlementConfig config) {
        super();
        this.clock = clock;
        this.config = config;
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
    public Subscription createSubscription(SubscriptionData subscription, List<EntitlementEvent> initalEvents) {

        synchronized(events) {
            events.addAll(initalEvents);
        }
        Subscription updatedSubscription = buildSubscription(subscription);
        subscriptions.add(updatedSubscription);
        return updatedSubscription;
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
                        cur.getProcessingState() == EventLifecycleState.AVAILABLE &&
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


    @Override
    public List<EntitlementEvent> getEventsReady(UUID ownerId, int sequenceId) {
        synchronized(events) {
            List<EntitlementEvent> readyList = new LinkedList<EntitlementEvent>();
            for (EntitlementEvent cur : events) {
                if (cur.isAvailableForProcessing(clock.getUTCNow())) {

                    if (cur.getOwner() != null) {
                        log.warn(String.format("EventProcessor %s stealing event %s from %s", ownerId, cur, cur.getOwner()));
                    }
                    cur.setOwner(ownerId);
                    cur.setNextAvailableDate(clock.getUTCNow().plus(config.getDaoClaimTimeMs()));
                    cur.setProcessingState(EventLifecycleState.IN_PROCESSING);
                    readyList.add(cur);
                }
            }
            Collections.sort(readyList);
            return readyList;
        }
    }

    @Override
    public void clearEventsReady(UUID ownerId, Collection<EntitlementEvent> cleared) {
        synchronized(events) {
            for (EntitlementEvent cur : cleared) {
                if (cur.getOwner().equals(ownerId)) {
                    cur.setProcessingState(EventLifecycleState.PROCESSED);
                } else {
                    log.warn(String.format("EventProcessor %s trying to clear event %s that it does not own", ownerId, cur));
                }
            }
        }
    }

    private Subscription buildSubscription(SubscriptionData in) {
        return new SubscriptionData(new SubscriptionBuilder(in), true);
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
        }
    }

    private void insertEvent(EntitlementEvent event) {
        synchronized(events) {
            events.add(event);
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
                        cur.getProcessingState() == EventLifecycleState.AVAILABLE) {
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
                        cur.getProcessingState() == EventLifecycleState.AVAILABLE) {
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

}
