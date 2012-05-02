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

    private final ThreadLocal<Map<UUID, SubscriptionRepairEvent>> preThreadsInRepairSubscriptions = new ThreadLocal<Map<UUID, SubscriptionRepairEvent>>();
    
    private final static class SubscriptionRepairEvent {
        
        private final Set<EntitlementEvent> events;
        
        public SubscriptionRepairEvent(List<EntitlementEvent> initialEvents) {
            events = new TreeSet<EntitlementEvent>(new Comparator<EntitlementEvent>() {
                @Override
                public int compare(EntitlementEvent o1, EntitlementEvent o2) {
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
        
        public void addEvents(List<EntitlementEvent> newEvents) {
            events.addAll(newEvents);
        }
    }
    
    private Map<UUID, SubscriptionRepairEvent> getRepairMap() {
        if (preThreadsInRepairSubscriptions.get() == null) {
            preThreadsInRepairSubscriptions.set(new HashMap<UUID, SubscriptionRepairEvent>());
        }
        return preThreadsInRepairSubscriptions.get();
    }
    
    private SubscriptionRepairEvent getRepairSubscriptionEvents(UUID subscriptionId) {
        Map<UUID, SubscriptionRepairEvent> map = getRepairMap();
        return map.get(subscriptionId);
    }
    
    @Override
    public List<EntitlementEvent> getEventsForSubscription(UUID subscriptionId) {
        SubscriptionRepairEvent target =  getRepairSubscriptionEvents(subscriptionId);
        return new LinkedList<EntitlementEvent>(target.getEvents());
    }

    @Override
    public void createSubscription(SubscriptionData subscription,
            List<EntitlementEvent> createEvents, CallContext context) {
        addEvents(subscription.getId(), createEvents);
    }

    @Override
    public void recreateSubscription(UUID subscriptionId,
            List<EntitlementEvent> recreateEvents, CallContext context) {
        addEvents(subscriptionId, recreateEvents);
    }

    @Override
    public void cancelSubscription(UUID subscriptionId,
            EntitlementEvent cancelEvent, CallContext context, int cancelSeq) {
        long activeVersion = cancelEvent.getActiveVersion();
        addEvents(subscriptionId, Collections.singletonList(cancelEvent));
        SubscriptionRepairEvent target =  getRepairSubscriptionEvents(subscriptionId);
        boolean foundCancelEvent = false;
        for (EntitlementEvent cur : target.getEvents()) {
            if (cur.getId().equals(cancelEvent.getId())) {
                foundCancelEvent = true;
            } else if (foundCancelEvent) { 
                cur.setActiveVersion(activeVersion - 1);
            }
        }
    }

    
    @Override
    public void changePlan(UUID subscriptionId,
            List<EntitlementEvent> changeEvents, CallContext context) {
        addEvents(subscriptionId, changeEvents);        
    }

    @Override
    public void initializeRepair(UUID subscriptionId, List<EntitlementEvent> initialEvents) {
        Map<UUID, SubscriptionRepairEvent> map = getRepairMap();
        if (map.get(subscriptionId) == null) {
            SubscriptionRepairEvent value = new SubscriptionRepairEvent(initialEvents);
            map.put(subscriptionId, value);
        } else {
            throw new EntitlementError(String.format("Unexpected SubscriptionRepairEvent %s for thread %s", subscriptionId, Thread.currentThread().getName()));
        }
    }

    @Override
    public void cleanup() {
        Map<UUID, SubscriptionRepairEvent> map = getRepairMap();
        map.clear();
    }

    
    private void addEvents(UUID subscriptionId, List<EntitlementEvent> events) {
        SubscriptionRepairEvent target =  getRepairSubscriptionEvents(subscriptionId);
        target.addEvents(events);        
    }

    
    @Override
    public void uncancelSubscription(UUID subscriptionId,
            List<EntitlementEvent> uncancelEvents, CallContext context) {
        throw new EntitlementError("Not implemented");        
    }
    
    @Override
    public List<SubscriptionBundle> getSubscriptionBundleForAccount(UUID accountId) {
        throw new EntitlementError("Not implemented");
    }

    @Override
    public SubscriptionBundle getSubscriptionBundleFromKey(String bundleKey) {
        throw new EntitlementError("Not implemented");
    }

    @Override
    public SubscriptionBundle getSubscriptionBundleFromId(UUID bundleId) {
        throw new EntitlementError("Not implemented");
    }

    @Override
    public SubscriptionBundle createSubscriptionBundle(
            SubscriptionBundleData bundle, CallContext context) {
        throw new EntitlementError("Not implemented");
    }

    @Override
    public Subscription getSubscriptionFromId(SubscriptionFactory factory,
            UUID subscriptionId) {
        throw new EntitlementError("Not implemented");
    }

    @Override
    public UUID getAccountIdFromSubscriptionId(UUID subscriptionId) {
        throw new EntitlementError("Not implemented");
    }

    @Override
    public Subscription getBaseSubscription(SubscriptionFactory factory,
            UUID bundleId) {
        throw new EntitlementError("Not implemented");
    }

    @Override
    public List<Subscription> getSubscriptions(SubscriptionFactory factory,
            UUID bundleId) {
        throw new EntitlementError("Not implemented");
    }

    @Override
    public List<Subscription> getSubscriptionsForKey(
            SubscriptionFactory factory, String bundleKey) {
        throw new EntitlementError("Not implemented");
    }

    @Override
    public void updateChargedThroughDate(SubscriptionData subscription,
            CallContext context) {
        throw new EntitlementError("Not implemented");
    }

    @Override
    public void createNextPhaseEvent(UUID subscriptionId,
            EntitlementEvent nextPhase, CallContext context) {
        throw new EntitlementError("Not implemented");
    }

    @Override
    public EntitlementEvent getEventById(UUID eventId) {
        throw new EntitlementError("Not implemented");
    }

    @Override
    public Map<UUID, List<EntitlementEvent>> getEventsForBundle(UUID bundleId) {
        throw new EntitlementError("Not implemented");
    }


    @Override
    public List<EntitlementEvent> getPendingEventsForSubscription(
            UUID subscriptionId) {
        throw new EntitlementError("Not implemented");
    }


    @Override
    public void migrate(UUID accountId, AccountMigrationData data,
            CallContext context) {
        throw new EntitlementError("Not implemented");
    }

    @Override
    public void saveCustomFields(SubscriptionData subscription,
            CallContext context) {
        throw new EntitlementError("Not implemented");
    }

    @Override
    public void repair(UUID accountId, UUID bundleId, List<SubscriptionDataRepair> inRepair,
            CallContext context) {
        throw new EntitlementError("Not implemented");
    }
}
