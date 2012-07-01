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

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import com.ning.billing.entitlement.api.SubscriptionFactory;
import com.ning.billing.entitlement.api.migration.AccountMigrationData;
import com.ning.billing.entitlement.api.timeline.RepairEntitlementLifecycleDao;
import com.ning.billing.entitlement.api.timeline.SubscriptionDataRepair;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionBundleData;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.events.EntitlementEvent;
import com.ning.billing.entitlement.exceptions.EntitlementError;
import com.ning.billing.util.callcontext.CallContext;

public class RepairEntitlementDao implements EntitlementDao, RepairEntitlementLifecycleDao {
    private static final String NOT_IMPLEMENTED = "Not implemented";

    private final ThreadLocal<Map<UUID, SubscriptionRepairEvent>> preThreadsInRepairSubscriptions = new ThreadLocal<Map<UUID, SubscriptionRepairEvent>>();

    private static final class SubscriptionRepairEvent {
        private final Set<EntitlementEvent> events;

        public SubscriptionRepairEvent(final List<EntitlementEvent> initialEvents) {
            events = new TreeSet<EntitlementEvent>(new Comparator<EntitlementEvent>() {
                @Override
                public int compare(final EntitlementEvent o1, final EntitlementEvent o2) {
                    return o1.compareTo(o2);
                }
            });
            if (initialEvents != null) {
                events.addAll(initialEvents);
            }
        }

        public Set<EntitlementEvent> getEvents() {
            return events;
        }

        public void addEvents(final List<EntitlementEvent> newEvents) {
            events.addAll(newEvents);
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
    public List<EntitlementEvent> getEventsForSubscription(final UUID subscriptionId) {
        final SubscriptionRepairEvent target = getRepairSubscriptionEvents(subscriptionId);
        return new LinkedList<EntitlementEvent>(target.getEvents());
    }

    @Override
    public void createSubscription(final SubscriptionData subscription,
                                   final List<EntitlementEvent> createEvents, final CallContext context) {
        addEvents(subscription.getId(), createEvents);
    }

    @Override
    public void recreateSubscription(final UUID subscriptionId,
                                     final List<EntitlementEvent> recreateEvents, final CallContext context) {
        addEvents(subscriptionId, recreateEvents);
    }

    @Override
    public void cancelSubscription(final UUID subscriptionId,
                                   final EntitlementEvent cancelEvent, final CallContext context, final int cancelSeq) {
        final long activeVersion = cancelEvent.getActiveVersion();
        addEvents(subscriptionId, Collections.singletonList(cancelEvent));
        final SubscriptionRepairEvent target = getRepairSubscriptionEvents(subscriptionId);
        boolean foundCancelEvent = false;
        for (final EntitlementEvent cur : target.getEvents()) {
            if (cur.getId().equals(cancelEvent.getId())) {
                foundCancelEvent = true;
            } else if (foundCancelEvent) {
                cur.setActiveVersion(activeVersion - 1);
            }
        }
    }

    @Override
    public void changePlan(final UUID subscriptionId,
                           final List<EntitlementEvent> changeEvents, final CallContext context) {
        addEvents(subscriptionId, changeEvents);
    }

    @Override
    public void initializeRepair(final UUID subscriptionId, final List<EntitlementEvent> initialEvents) {
        final Map<UUID, SubscriptionRepairEvent> map = getRepairMap();
        if (map.get(subscriptionId) == null) {
            final SubscriptionRepairEvent value = new SubscriptionRepairEvent(initialEvents);
            map.put(subscriptionId, value);
        } else {
            throw new EntitlementError(String.format("Unexpected SubscriptionRepairEvent %s for thread %s", subscriptionId, Thread.currentThread().getName()));
        }
    }

    @Override
    public void cleanup() {
        final Map<UUID, SubscriptionRepairEvent> map = getRepairMap();
        map.clear();
    }

    private void addEvents(final UUID subscriptionId, final List<EntitlementEvent> events) {
        final SubscriptionRepairEvent target = getRepairSubscriptionEvents(subscriptionId);
        target.addEvents(events);
    }

    @Override
    public void uncancelSubscription(final UUID subscriptionId,
                                     final List<EntitlementEvent> uncancelEvents, final CallContext context) {
        throw new EntitlementError(NOT_IMPLEMENTED);
    }

    @Override
    public List<SubscriptionBundle> getSubscriptionBundleForAccount(final UUID accountId) {
        throw new EntitlementError(NOT_IMPLEMENTED);
    }

    @Override
    public SubscriptionBundle getSubscriptionBundleFromKey(final String bundleKey) {
        throw new EntitlementError(NOT_IMPLEMENTED);
    }

    @Override
    public SubscriptionBundle getSubscriptionBundleFromId(final UUID bundleId) {
        throw new EntitlementError(NOT_IMPLEMENTED);
    }

    @Override
    public SubscriptionBundle createSubscriptionBundle(
            final SubscriptionBundleData bundle, final CallContext context) {
        throw new EntitlementError(NOT_IMPLEMENTED);
    }

    @Override
    public Subscription getSubscriptionFromId(final SubscriptionFactory factory,
                                              final UUID subscriptionId) {
        throw new EntitlementError(NOT_IMPLEMENTED);
    }

    @Override
    public UUID getAccountIdFromSubscriptionId(final UUID subscriptionId) {
        throw new EntitlementError(NOT_IMPLEMENTED);
    }

    @Override
    public Subscription getBaseSubscription(final SubscriptionFactory factory,
                                            final UUID bundleId) {
        throw new EntitlementError(NOT_IMPLEMENTED);
    }

    @Override
    public List<Subscription> getSubscriptions(final SubscriptionFactory factory,
                                               final UUID bundleId) {
        throw new EntitlementError(NOT_IMPLEMENTED);
    }

    @Override
    public List<Subscription> getSubscriptionsForKey(
            final SubscriptionFactory factory, final String bundleKey) {
        throw new EntitlementError(NOT_IMPLEMENTED);
    }

    @Override
    public void updateChargedThroughDate(final SubscriptionData subscription,
                                         final CallContext context) {
        throw new EntitlementError(NOT_IMPLEMENTED);
    }

    @Override
    public void createNextPhaseEvent(final UUID subscriptionId,
                                     final EntitlementEvent nextPhase, final CallContext context) {
        throw new EntitlementError(NOT_IMPLEMENTED);
    }

    @Override
    public EntitlementEvent getEventById(final UUID eventId) {
        throw new EntitlementError(NOT_IMPLEMENTED);
    }

    @Override
    public Map<UUID, List<EntitlementEvent>> getEventsForBundle(final UUID bundleId) {
        throw new EntitlementError(NOT_IMPLEMENTED);
    }

    @Override
    public List<EntitlementEvent> getPendingEventsForSubscription(
            final UUID subscriptionId) {
        throw new EntitlementError(NOT_IMPLEMENTED);
    }

    @Override
    public void migrate(final UUID accountId, final AccountMigrationData data,
                        final CallContext context) {
        throw new EntitlementError(NOT_IMPLEMENTED);
    }

    @Override
    public void repair(final UUID accountId, final UUID bundleId, final List<SubscriptionDataRepair> inRepair,
                       final CallContext context) {
        throw new EntitlementError(NOT_IMPLEMENTED);
    }
}
