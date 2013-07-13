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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import com.ning.billing.subscription.api.migration.AccountMigrationData;
import com.ning.billing.subscription.api.migration.AccountMigrationData.BundleMigrationData;
import com.ning.billing.subscription.api.timeline.RepairSubscriptionLifecycleDao;
import com.ning.billing.subscription.api.timeline.SubscriptionDataRepair;
import com.ning.billing.subscription.api.transfer.TransferCancelData;
import com.ning.billing.subscription.api.user.SubscriptionBundleData;
import com.ning.billing.subscription.api.user.SubscriptionData;
import com.ning.billing.subscription.events.SubscriptionEvent;
import com.ning.billing.subscription.exceptions.SubscriptionError;
import com.ning.billing.subscription.api.user.Subscription;
import com.ning.billing.subscription.api.user.SubscriptionBundle;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;

public class RepairSubscriptionDao implements SubscriptionDao, RepairSubscriptionLifecycleDao {

    private static final String NOT_IMPLEMENTED = "Not implemented";

    private final ThreadLocal<Map<UUID, SubscriptionRepairEvent>> preThreadsInRepairSubscriptions = new ThreadLocal<Map<UUID, SubscriptionRepairEvent>>();

    private static final class SubscriptionEventWithOrderingId {

        private final SubscriptionEvent event;
        private final long orderingId;

        public SubscriptionEventWithOrderingId(final SubscriptionEvent event, final long orderingId) {
            this.event = event;
            this.orderingId = orderingId;
        }

        public SubscriptionEvent getEvent() {
            return event;
        }

        public long getOrderingId() {
            return orderingId;
        }

        @Override
        public String toString() {
            final StringBuilder tmp = new StringBuilder();
            tmp.append("[");
            tmp.append(event.getType());
            tmp.append(": effDate=");
            tmp.append(event.getEffectiveDate());
            tmp.append(", subId=");
            tmp.append(event.getSubscriptionId());
            tmp.append(", ordering=");
            tmp.append(event.getTotalOrdering());
            tmp.append("]");
            return tmp.toString();
        }
    }

    private static final class SubscriptionRepairEvent {

        private final Set<SubscriptionEventWithOrderingId> events;
        private long curOrderingId;

        public SubscriptionRepairEvent(final List<SubscriptionEvent> initialEvents) {
            this.events = new TreeSet<SubscriptionEventWithOrderingId>(new Comparator<SubscriptionEventWithOrderingId>() {
                @Override
                public int compare(final SubscriptionEventWithOrderingId o1, final SubscriptionEventWithOrderingId o2) {
                    // Work around jdk7 change: compare(o1, o1) is now invoked when inserting the first element
                    // See:
                    // - http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=5045147
                    // - http://hg.openjdk.java.net/jdk7/tl/jdk/rev/bf37edb38fbb
                    if (o1 == o2) {
                        return 0;
                    }

                    final int result = o1.getEvent().getEffectiveDate().compareTo(o2.getEvent().getEffectiveDate());
                    if (result == 0) {
                        if (o1.getOrderingId() < o2.getOrderingId()) {
                            return -1;
                        } else if (o1.getOrderingId() > o2.getOrderingId()) {
                            return 1;
                        } else {
                            throw new RuntimeException(String.format(" Repair subscription events should not have the same orderingId %s, %s ", o1, o2));
                        }
                    }
                    return result;
                }
            });

            this.curOrderingId = 0;

            if (initialEvents != null) {
                addEvents(initialEvents);
            }
        }

        public List<SubscriptionEvent> getEvents() {
            return new ArrayList<SubscriptionEvent>(Collections2.transform(events, new Function<SubscriptionEventWithOrderingId, SubscriptionEvent>() {
                @Override
                public SubscriptionEvent apply(SubscriptionEventWithOrderingId in) {
                    return in.getEvent();
                }
            }));
        }

        public void addEvents(final List<SubscriptionEvent> newEvents) {
            for (final SubscriptionEvent cur : newEvents) {
                events.add(new SubscriptionEventWithOrderingId(cur, curOrderingId++));
            }
        }
    }

    private Map<UUID, SubscriptionRepairEvent> getRepairMap() {
        if (preThreadsInRepairSubscriptions.get() == null) {
            preThreadsInRepairSubscriptions.set(new HashMap<UUID, SubscriptionRepairEvent>());
        }
        return preThreadsInRepairSubscriptions.get();
    }

    private SubscriptionRepairEvent getRepairSubscriptionEvents(final UUID subscriptionId) {
        final Map<UUID, SubscriptionRepairEvent> map = getRepairMap();
        return map.get(subscriptionId);
    }

    @Override
    public List<SubscriptionEvent> getEventsForSubscription(final UUID subscriptionId, final InternalTenantContext context) {
        final SubscriptionRepairEvent target = getRepairSubscriptionEvents(subscriptionId);
        return new LinkedList<SubscriptionEvent>(target.getEvents());
    }

    @Override
    public void createSubscription(final SubscriptionData subscription, final List<SubscriptionEvent> createEvents, final InternalCallContext context) {
        addEvents(subscription.getId(), createEvents);
    }

    @Override
    public void recreateSubscription(final SubscriptionData subscription, final List<SubscriptionEvent> recreateEvents, final InternalCallContext context) {
        addEvents(subscription.getId(), recreateEvents);
    }

    @Override
    public void cancelSubscription(final SubscriptionData subscription, final SubscriptionEvent cancelEvent, final InternalCallContext context, final int cancelSeq) {
        final UUID subscriptionId = subscription.getId();
        final long activeVersion = cancelEvent.getActiveVersion();
        addEvents(subscriptionId, Collections.singletonList(cancelEvent));
        final SubscriptionRepairEvent target = getRepairSubscriptionEvents(subscriptionId);
        boolean foundCancelEvent = false;
        for (final SubscriptionEvent cur : target.getEvents()) {
            if (cur.getId().equals(cancelEvent.getId())) {
                foundCancelEvent = true;
            } else if (foundCancelEvent) {
                cur.setActiveVersion(activeVersion - 1);
            }
        }
    }

    @Override
    public void cancelSubscriptions(final List<SubscriptionData> subscriptions, final List<SubscriptionEvent> cancelEvents, final InternalCallContext context) {
    }

    @Override
    public void changePlan(final SubscriptionData subscription, final List<SubscriptionEvent> changeEvents, final InternalCallContext context) {
        addEvents(subscription.getId(), changeEvents);
    }

    @Override
    public void initializeRepair(final UUID subscriptionId, final List<SubscriptionEvent> initialEvents, final InternalTenantContext context) {
        final Map<UUID, SubscriptionRepairEvent> map = getRepairMap();
        if (map.get(subscriptionId) == null) {
            final SubscriptionRepairEvent value = new SubscriptionRepairEvent(initialEvents);
            map.put(subscriptionId, value);
        } else {
            throw new SubscriptionError(String.format("Unexpected SubscriptionRepairEvent %s for thread %s", subscriptionId, Thread.currentThread().getName()));
        }
    }

    @Override
    public void cleanup(final InternalTenantContext context) {
        final Map<UUID, SubscriptionRepairEvent> map = getRepairMap();
        map.clear();
    }

    private void addEvents(final UUID subscriptionId, final List<SubscriptionEvent> events) {
        final SubscriptionRepairEvent target = getRepairSubscriptionEvents(subscriptionId);
        target.addEvents(events);
    }

    @Override
    public void uncancelSubscription(final SubscriptionData subscription, final List<SubscriptionEvent> uncancelEvents, final InternalCallContext context) {
        throw new SubscriptionError(NOT_IMPLEMENTED);
    }

    @Override
    public List<SubscriptionBundle> getSubscriptionBundleForAccount(final UUID accountId, final InternalTenantContext context) {
        throw new SubscriptionError(NOT_IMPLEMENTED);
    }

    @Override
    public SubscriptionBundle getSubscriptionBundleFromAccountAndKey(final UUID accountId, final String bundleKey, final InternalTenantContext context) {
        throw new SubscriptionError(NOT_IMPLEMENTED);
    }

    @Override
    public SubscriptionBundle getSubscriptionBundleFromId(final UUID bundleId, final InternalTenantContext context) {
        throw new SubscriptionError(NOT_IMPLEMENTED);
    }

    @Override
    public SubscriptionBundle createSubscriptionBundle(final SubscriptionBundleData bundle, final InternalCallContext context) {
        throw new SubscriptionError(NOT_IMPLEMENTED);
    }

    @Override
    public Subscription getSubscriptionFromId(final UUID subscriptionId, final InternalTenantContext context) {
        throw new SubscriptionError(NOT_IMPLEMENTED);
    }

    @Override
    public UUID getAccountIdFromSubscriptionId(final UUID subscriptionId, final InternalTenantContext context) {
        throw new SubscriptionError(NOT_IMPLEMENTED);
    }

    @Override
    public Subscription getBaseSubscription(final UUID bundleId, final InternalTenantContext context) {
        throw new SubscriptionError(NOT_IMPLEMENTED);
    }

    @Override
    public List<Subscription> getSubscriptions(final UUID bundleId, final InternalTenantContext context) {
        throw new SubscriptionError(NOT_IMPLEMENTED);
    }

    @Override
    public List<Subscription> getSubscriptionsForAccountAndKey(final UUID accountId,
                                                               final String bundleKey, final InternalTenantContext context) {
        throw new SubscriptionError(NOT_IMPLEMENTED);
    }

    @Override
    public void updateChargedThroughDate(final SubscriptionData subscription, final InternalCallContext context) {
        throw new SubscriptionError(NOT_IMPLEMENTED);
    }

    @Override
    public void createNextPhaseEvent(final SubscriptionData subscription, final SubscriptionEvent nextPhase, final InternalCallContext context) {
        throw new SubscriptionError(NOT_IMPLEMENTED);
    }

    @Override
    public SubscriptionEvent getEventById(final UUID eventId, final InternalTenantContext context) {
        throw new SubscriptionError(NOT_IMPLEMENTED);
    }

    @Override
    public Map<UUID, List<SubscriptionEvent>> getEventsForBundle(final UUID bundleId, final InternalTenantContext context) {
        throw new SubscriptionError(NOT_IMPLEMENTED);
    }

    @Override
    public List<SubscriptionEvent> getPendingEventsForSubscription(final UUID subscriptionId, final InternalTenantContext context) {
        throw new SubscriptionError(NOT_IMPLEMENTED);
    }

    @Override
    public void migrate(final UUID accountId, final AccountMigrationData data, final InternalCallContext context) {
        throw new SubscriptionError(NOT_IMPLEMENTED);
    }

    @Override
    public void repair(final UUID accountId, final UUID bundleId, final List<SubscriptionDataRepair> inRepair, final InternalCallContext context) {
        throw new SubscriptionError(NOT_IMPLEMENTED);
    }

    @Override
    public void transfer(final UUID srcAccountId, final UUID destAccountId, final BundleMigrationData data,
                         final List<TransferCancelData> transferCancelData, final InternalCallContext fromContext,
                         final InternalCallContext toContext) {
        throw new SubscriptionError(NOT_IMPLEMENTED);
    }

    @Override
    public List<SubscriptionBundle> getSubscriptionBundlesForKey(final String bundleKey, final InternalTenantContext context) {
        throw new SubscriptionError(NOT_IMPLEMENTED);
    }
}
