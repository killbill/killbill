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
import com.ning.billing.entitlement.api.user.ISubscription;
import com.ning.billing.entitlement.api.user.ISubscriptionBundle;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.events.IEvent;
import com.ning.billing.entitlement.events.IEvent.EventType;
import com.ning.billing.entitlement.events.IEventLyfecycle.IEventLyfecycleState;
import com.ning.billing.entitlement.events.phase.IPhaseEvent;
import com.ning.billing.entitlement.events.phase.PhaseEvent;
import com.ning.billing.entitlement.events.user.ApiEventType;
import com.ning.billing.entitlement.events.user.IUserEvent;
import com.ning.billing.entitlement.glue.IEntitlementConfig;
import com.ning.billing.util.clock.IClock;

public class EntitlementDaoMemoryMock implements IEntitlementDao, IEntitlementDaoMock {

    protected final static Logger log = LoggerFactory.getLogger(IEntitlementDao.class);

    private final List<ISubscriptionBundle> bundles;
    private final List<ISubscription> subscriptions;
    private final TreeSet<IEvent> events;
    private final IClock clock;
    private final IEntitlementConfig config;

    @Inject
    public EntitlementDaoMemoryMock(IClock clock, IEntitlementConfig config) {
        super();
        this.clock = clock;
        this.config = config;
        this.bundles = new ArrayList<ISubscriptionBundle>();
        this.subscriptions = new ArrayList<ISubscription>();
        this.events = new TreeSet<IEvent>();
    }

    @Override
    public void reset() {
        bundles.clear();
        subscriptions.clear();
        events.clear();
    }

    @Override
    public List<ISubscriptionBundle> getSubscriptionBundleForAccount(UUID accountId) {
        List<ISubscriptionBundle> results = new ArrayList<ISubscriptionBundle>();
        for (ISubscriptionBundle cur : bundles) {
            if (cur.getAccountId().equals(accountId)) {
                results.add(cur);
            }
        }
        return results;
    }

    @Override
    public ISubscriptionBundle getSubscriptionBundleFromId(UUID bundleId) {
        for (ISubscriptionBundle cur : bundles) {
            if (cur.getId().equals(bundleId)) {
                return cur;
            }
        }
        return null;
    }

    @Override
    public ISubscriptionBundle createSubscriptionBundle(SubscriptionBundle bundle) {
        bundles.add(bundle);
        return getSubscriptionBundleFromId(bundle.getId());
    }

    @Override
    public ISubscription getSubscriptionFromId(UUID subscriptionId) {
        for (ISubscription cur : subscriptions) {
            if (cur.getId().equals(subscriptionId)) {
                return buildSubscription((Subscription) cur);
            }
        }
        return null;
    }

    @Override
    public ISubscription createSubscription(Subscription subscription, List<IEvent> initalEvents) {

        synchronized(events) {
            events.addAll(initalEvents);
        }
        ISubscription updatedSubscription = buildSubscription(subscription);
        subscriptions.add(updatedSubscription);
        return updatedSubscription;
    }

    @Override
    public List<ISubscription> getSubscriptions(UUID bundleId) {

        List<ISubscription> results = new ArrayList<ISubscription>();
        for (ISubscription cur : subscriptions) {
            if (cur.getBundleId().equals(bundleId)) {
                results.add(buildSubscription((Subscription) cur));
            }
        }
        return results;
    }

    @Override
    public List<IEvent> getEventsForSubscription(UUID subscriptionId) {
        synchronized(events) {
            List<IEvent> results = new LinkedList<IEvent>();
            for (IEvent cur : events) {
                if (cur.getSubscriptionId().equals(subscriptionId)) {
                    results.add(cur);
                }
            }
            return results;
        }
    }

    @Override
    public List<IEvent> getPendingEventsForSubscription(UUID subscriptionId) {
        synchronized(events) {
            List<IEvent> results = new LinkedList<IEvent>();
            for (IEvent cur : events) {
                if (cur.isActive() &&
                        cur.getProcessingState() == IEventLyfecycleState.AVAILABLE &&
                            cur.getSubscriptionId().equals(subscriptionId)) {
                    results.add(cur);
                }
            }
            return results;
        }
    }


    @Override
    public ISubscription getBaseSubscription(UUID bundleId) {
        for (ISubscription cur : subscriptions) {
            if (cur.getBundleId().equals(bundleId) &&
                    cur.getCurrentPlan().getProduct().getCategory() == ProductCategory.BASE) {
                return buildSubscription((Subscription) cur);
            }
        }
        return null;
    }

    @Override
    public void createNextPhaseEvent(UUID subscriptionId, IEvent nextPhase) {
        cancelNextPhaseEvent(subscriptionId);
        insertEvent(nextPhase);
    }


    @Override
    public List<IEvent> getEventsReady(UUID ownerId, int sequenceId) {
        synchronized(events) {
            List<IEvent> readyList = new LinkedList<IEvent>();
            for (IEvent cur : events) {
                if (cur.isAvailableForProcessing(clock.getUTCNow())) {

                    if (cur.getOwner() != null) {
                        log.warn(String.format("EventProcessor %s stealing event %s from %s", ownerId, cur, cur.getOwner()));
                    }
                    cur.setOwner(ownerId);
                    cur.setNextAvailableDate(clock.getUTCNow().plus(config.getDaoClaimTimeMs()));
                    cur.setProcessingState(IEventLyfecycleState.IN_PROCESSING);
                    readyList.add(cur);
                }
            }
            Collections.sort(readyList);
            return readyList;
        }
    }

    @Override
    public void clearEventsReady(UUID ownerId, List<IEvent> cleared) {
        synchronized(events) {
            for (IEvent cur : cleared) {
                if (cur.getOwner().equals(ownerId)) {
                    cur.setProcessingState(IEventLyfecycleState.PROCESSED);
                } else {
                    log.warn(String.format("EventProcessor %s trying to clear event %s that it does not own", ownerId, cur));
                }
            }
        }
    }

    private ISubscription buildSubscription(Subscription in) {
        return new Subscription(in.getId(), in.getBundleId(), in.getCategory(), in.getBundleStartDate(),
                in.getStartDate(), in.getChargedThroughDate(), in.getPaidThroughDate(), in.getActiveVersion());
    }

    @Override
    public void updateSubscription(Subscription subscription) {

        boolean found = false;
        Iterator<ISubscription> it = subscriptions.iterator();
        while (it.hasNext()) {
            ISubscription cur = it.next();
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
    public void cancelSubscription(UUID subscriptionId, IEvent cancelEvent) {
        synchronized (cancelEvent) {
            cancelNextPhaseEvent(subscriptionId);
            insertEvent(cancelEvent);
        }
    }

    @Override
    public void changePlan(UUID subscriptionId, List<IEvent> changeEvents) {
        synchronized(events) {
            cancelNextChangeEvent(subscriptionId);
            cancelNextPhaseEvent(subscriptionId);
            events.addAll(changeEvents);
        }
    }

    private void insertEvent(IEvent event) {
        synchronized(events) {
            events.add(event);
        }
    }

    private void cancelNextPhaseEvent(UUID subscriptionId) {

        ISubscription curSubscription = getSubscriptionFromId(subscriptionId);
        if (curSubscription.getCurrentPhase() == null ||
                curSubscription.getCurrentPhase().getDuration().getUnit() == TimeUnit.UNLIMITED) {
            return;
        }

        synchronized(events) {

            Iterator<IEvent> it = events.descendingIterator();
            while (it.hasNext()) {
                IEvent cur = it.next();
                if (cur.getSubscriptionId() != subscriptionId) {
                    continue;
                }
                if (cur.getType() == EventType.PHASE &&
                        cur.getProcessingState() == IEventLyfecycleState.AVAILABLE) {
                    cur.deactivate();
                    break;
                }
            }
        }
    }


    private void cancelNextChangeEvent(UUID subscriptionId) {

        synchronized(events) {

            Iterator<IEvent> it = events.descendingIterator();
            while (it.hasNext()) {
                IEvent cur = it.next();
                if (cur.getSubscriptionId() != subscriptionId) {
                    continue;
                }
                if (cur.getType() == EventType.API_USER &&
                        ApiEventType.CHANGE == ((IUserEvent) cur).getEventType() &&
                        cur.getProcessingState() == IEventLyfecycleState.AVAILABLE) {
                    cur.deactivate();
                    break;
                }
            }
        }
    }

    @Override
    public void uncancelSubscription(UUID subscriptionId, List<IEvent> uncancelEvents) {

        synchronized (events) {
            boolean foundCancel = false;
            Iterator<IEvent> it = events.descendingIterator();
            while (it.hasNext()) {
                IEvent cur = it.next();
                if (cur.getSubscriptionId() != subscriptionId) {
                    continue;
                }
                if (cur.getType() == EventType.API_USER &&
                        ((IUserEvent) cur).getEventType() == ApiEventType.CANCEL) {
                    cur.deactivate();
                    foundCancel = true;
                    break;
                }
            }
            if (foundCancel) {
                for (IEvent cur : uncancelEvents) {
                    insertEvent(cur);
                }
            }
        }
    }
}
