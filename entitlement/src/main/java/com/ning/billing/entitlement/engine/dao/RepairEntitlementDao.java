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
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import com.ning.billing.entitlement.api.migration.AccountMigrationData;
import com.ning.billing.entitlement.api.migration.AccountMigrationData.BundleMigrationData;
import com.ning.billing.entitlement.api.timeline.RepairEntitlementLifecycleDao;
import com.ning.billing.entitlement.api.timeline.SubscriptionDataRepair;
import com.ning.billing.entitlement.api.transfer.TransferCancelData;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionBundleData;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.events.EntitlementEvent;
import com.ning.billing.entitlement.exceptions.EntitlementError;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;

public class RepairEntitlementDao implements EntitlementDao, RepairEntitlementLifecycleDao {

    private static final String NOT_IMPLEMENTED = "Not implemented";

    private final ThreadLocal<Map<UUID, SubscriptionRepairEvent>> preThreadsInRepairSubscriptions = new ThreadLocal<Map<UUID, SubscriptionRepairEvent>>();

    private static final class EntitlementEventWithOrderingId {

        private final EntitlementEvent event;
        private final long orderingId;

        public EntitlementEventWithOrderingId(final EntitlementEvent event, final long orderingId) {
            this.event = event;
            this.orderingId = orderingId;
        }

        public EntitlementEvent getEvent() {
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

        private final Set<EntitlementEventWithOrderingId> events;
        private long curOrderingId;

        public SubscriptionRepairEvent(final List<EntitlementEvent> initialEvents) {
            this.events = new TreeSet<EntitlementEventWithOrderingId>(new Comparator<EntitlementEventWithOrderingId>() {
                @Override
                public int compare(final EntitlementEventWithOrderingId o1, final EntitlementEventWithOrderingId o2) {
                    final int result = o1.getEvent().getEffectiveDate().compareTo(o2.getEvent().getEffectiveDate());
                    if (result == 0) {
                        if (o1.getOrderingId() < o2.getOrderingId()) {
                            return -1;
                        } else if (o1.getOrderingId() > o2.getOrderingId()) {
                            return 1;
                        } else {
                            throw new RuntimeException(String.format(" Repair entitlement events should not have the same orderingId %s, %s ", o1, o2));
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

        public List<EntitlementEvent> getEvents() {
            return new ArrayList<EntitlementEvent>(Collections2.transform(events, new Function<EntitlementEventWithOrderingId, EntitlementEvent>() {
                @Override
                public EntitlementEvent apply(EntitlementEventWithOrderingId in) {
                    return in.getEvent();
                }
            }));
        }

        public void addEvents(final List<EntitlementEvent> newEvents) {
            for (final EntitlementEvent cur : newEvents) {
                events.add(new EntitlementEventWithOrderingId(cur, curOrderingId++));
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
    public List<EntitlementEvent> getEventsForSubscription(final UUID subscriptionId, final InternalTenantContext context) {
        final SubscriptionRepairEvent target = getRepairSubscriptionEvents(subscriptionId);
        return new LinkedList<EntitlementEvent>(target.getEvents());
    }

    @Override
    public void createSubscription(final SubscriptionData subscription, final List<EntitlementEvent> createEvents, final InternalCallContext context) {
        addEvents(subscription.getId(), createEvents);
    }

    @Override
    public void recreateSubscription(final SubscriptionData subscription, final List<EntitlementEvent> recreateEvents, final InternalCallContext context) {
        addEvents(subscription.getId(), recreateEvents);
    }

    @Override
    public void cancelSubscription(final SubscriptionData subscription, final EntitlementEvent cancelEvent, final InternalCallContext context, final int cancelSeq) {
        final UUID subscriptionId = subscription.getId();
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
    public void cancelSubscriptions(final List<SubscriptionData> subscriptions, final List<EntitlementEvent> cancelEvents, final InternalCallContext context) {
    }

    @Override
    public void changePlan(final SubscriptionData subscription, final List<EntitlementEvent> changeEvents, final InternalCallContext context) {
        addEvents(subscription.getId(), changeEvents);
    }

    @Override
    public void initializeRepair(final UUID subscriptionId, final List<EntitlementEvent> initialEvents, final InternalTenantContext context) {
        final Map<UUID, SubscriptionRepairEvent> map = getRepairMap();
        if (map.get(subscriptionId) == null) {
            final SubscriptionRepairEvent value = new SubscriptionRepairEvent(initialEvents);
            map.put(subscriptionId, value);
        } else {
            throw new EntitlementError(String.format("Unexpected SubscriptionRepairEvent %s for thread %s", subscriptionId, Thread.currentThread().getName()));
        }
    }

    @Override
    public void cleanup(final InternalTenantContext context) {
        final Map<UUID, SubscriptionRepairEvent> map = getRepairMap();
        map.clear();
    }

    private void addEvents(final UUID subscriptionId, final List<EntitlementEvent> events) {
        final SubscriptionRepairEvent target = getRepairSubscriptionEvents(subscriptionId);
        target.addEvents(events);
    }

    @Override
    public void uncancelSubscription(final SubscriptionData subscription, final List<EntitlementEvent> uncancelEvents, final InternalCallContext context) {
        throw new EntitlementError(NOT_IMPLEMENTED);
    }

    @Override
    public List<SubscriptionBundle> getSubscriptionBundleForAccount(final UUID accountId, final InternalTenantContext context) {
        throw new EntitlementError(NOT_IMPLEMENTED);
    }

    @Override
    public SubscriptionBundle getSubscriptionBundleFromAccountAndKey(final UUID accountId, final String bundleKey, final InternalTenantContext context) {
        throw new EntitlementError(NOT_IMPLEMENTED);
    }

    @Override
    public SubscriptionBundle getSubscriptionBundleFromId(final UUID bundleId, final InternalTenantContext context) {
        throw new EntitlementError(NOT_IMPLEMENTED);
    }

    @Override
    public SubscriptionBundle createSubscriptionBundle(final SubscriptionBundleData bundle, final InternalCallContext context) {
        throw new EntitlementError(NOT_IMPLEMENTED);
    }

    @Override
    public Subscription getSubscriptionFromId(final UUID subscriptionId, final InternalTenantContext context) {
        throw new EntitlementError(NOT_IMPLEMENTED);
    }

    @Override
    public UUID getAccountIdFromSubscriptionId(final UUID subscriptionId, final InternalTenantContext context) {
        throw new EntitlementError(NOT_IMPLEMENTED);
    }

    @Override
    public Subscription getBaseSubscription(final UUID bundleId, final InternalTenantContext context) {
        throw new EntitlementError(NOT_IMPLEMENTED);
    }

    @Override
    public List<Subscription> getSubscriptions(final UUID bundleId, final InternalTenantContext context) {
        throw new EntitlementError(NOT_IMPLEMENTED);
    }

    @Override
    public List<Subscription> getSubscriptionsForAccountAndKey(final UUID accountId,
                                                               final String bundleKey, final InternalTenantContext context) {
        throw new EntitlementError(NOT_IMPLEMENTED);
    }

    @Override
    public void updateChargedThroughDate(final SubscriptionData subscription, final InternalCallContext context) {
        throw new EntitlementError(NOT_IMPLEMENTED);
    }

    @Override
    public void createNextPhaseEvent(final SubscriptionData subscription, final EntitlementEvent nextPhase, final InternalCallContext context) {
        throw new EntitlementError(NOT_IMPLEMENTED);
    }

    @Override
    public EntitlementEvent getEventById(final UUID eventId, final InternalTenantContext context) {
        throw new EntitlementError(NOT_IMPLEMENTED);
    }

    @Override
    public Map<UUID, List<EntitlementEvent>> getEventsForBundle(final UUID bundleId, final InternalTenantContext context) {
        throw new EntitlementError(NOT_IMPLEMENTED);
    }

    @Override
    public List<EntitlementEvent> getPendingEventsForSubscription(final UUID subscriptionId, final InternalTenantContext context) {
        throw new EntitlementError(NOT_IMPLEMENTED);
    }

    @Override
    public void migrate(final UUID accountId, final AccountMigrationData data, final InternalCallContext context) {
        throw new EntitlementError(NOT_IMPLEMENTED);
    }

    @Override
    public void repair(final UUID accountId, final UUID bundleId, final List<SubscriptionDataRepair> inRepair, final InternalCallContext context) {
        throw new EntitlementError(NOT_IMPLEMENTED);
    }

    @Override
    public void transfer(final UUID srcAccountId, final UUID destAccountId, final BundleMigrationData data,
                         final List<TransferCancelData> transferCancelData, final InternalCallContext fromContext,
                         final InternalCallContext toContext) {
        throw new EntitlementError(NOT_IMPLEMENTED);
    }

    @Override
    public List<SubscriptionBundle> getSubscriptionBundlesForKey(final String bundleKey, final InternalTenantContext context) {
        throw new EntitlementError(NOT_IMPLEMENTED);
    }
}
