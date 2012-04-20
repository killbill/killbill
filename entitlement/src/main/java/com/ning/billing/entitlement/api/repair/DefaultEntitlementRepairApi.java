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
package com.ning.billing.entitlement.api.repair;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.joda.time.DateTime;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.ning.billing.ErrorCode;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.SubscriptionFactory;
import com.ning.billing.entitlement.api.SubscriptionTransitionType;
import com.ning.billing.entitlement.api.repair.SubscriptionRepair.ExistingEvent;
import com.ning.billing.entitlement.api.repair.SubscriptionRepair.NewEvent;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionBundleData;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.api.user.DefaultSubscriptionFactory.SubscriptionBuilder;
import com.ning.billing.entitlement.api.user.SubscriptionTransitionData;
import com.ning.billing.entitlement.engine.dao.EntitlementDao;
import com.ning.billing.entitlement.events.EntitlementEvent;
import com.ning.billing.entitlement.events.EntitlementEvent.EventType;
import com.ning.billing.entitlement.exceptions.EntitlementError;
import com.ning.billing.entitlement.glue.EntitlementModule;
import com.ning.billing.util.callcontext.CallContext;

public class DefaultEntitlementRepairApi implements EntitlementRepairApi {

    private final EntitlementDao dao;
    private final SubscriptionFactory factory;
    private final RepairEntitlementLifecycleDao repairDao;

    @Inject
    public DefaultEntitlementRepairApi(@Named(EntitlementModule.REPAIR_NAMED) final SubscriptionFactory factory,
            @Named(EntitlementModule.REPAIR_NAMED) final RepairEntitlementLifecycleDao repairDao, final EntitlementDao dao) {
        this.dao = dao;
        this.repairDao = repairDao;
        this.factory = factory;
    }


    @Override
    public BundleRepair getBundleRepair(final UUID bundleId) 
    throws EntitlementRepairException {

        SubscriptionBundle bundle = dao.getSubscriptionBundleFromId(bundleId);
        if (bundle == null) {
            throw new EntitlementRepairException(ErrorCode.ENT_REPAIR_UNKNOWN_BUNDLE, bundleId);
        }
        final List<Subscription> subscriptions = dao.getSubscriptions(factory, bundleId);
        if (subscriptions.size() == 0) {
            throw new EntitlementRepairException(ErrorCode.ENT_REPAIR_NO_ACTIVE_SUBSCRIPTIONS, bundleId);
        }
        final String viewId = getViewId(((SubscriptionBundleData) bundle).getLastSysUpdateTime(), subscriptions);
        final List<SubscriptionRepair> repairs = createGetSubscriptionRepairList(subscriptions, Collections.<SubscriptionRepair>emptyList()); 
        return createGetBundleRepair(bundleId, viewId, repairs);
    }
    

    @Override
    public BundleRepair repairBundle(final BundleRepair input, final boolean dryRun, final CallContext context)
    throws EntitlementRepairException {

        try {

            SubscriptionBundle bundle = dao.getSubscriptionBundleFromId(input.getBundleId());
            if (bundle == null) {
                throw new EntitlementRepairException(ErrorCode.ENT_REPAIR_UNKNOWN_BUNDLE, input.getBundleId());
            }

            // Subscriptions are ordered with BASE subscription first-- if exists
            final List<Subscription> subscriptions = dao.getSubscriptions(factory, input.getBundleId());
            if (subscriptions.size() == 0) {
                throw new EntitlementRepairException(ErrorCode.ENT_REPAIR_NO_ACTIVE_SUBSCRIPTIONS, input.getBundleId());
            }

            final String viewId = getViewId(((SubscriptionBundleData) bundle).getLastSysUpdateTime(), subscriptions);
            if (!viewId.equals(input.getViewId())) {
                throw new EntitlementRepairException(ErrorCode.ENT_REPAIR_VIEW_CHANGED,input.getBundleId(), input.getViewId(), viewId);
            }

            DateTime firstDeletedBPEventTime = null;
            DateTime lastRemainingBPEventTime = null;

            boolean isBasePlanRecreate = false;
            DateTime newBundleStartDate = null;

            SubscriptionDataRepair baseSubscriptionRepair = null;
            List<SubscriptionDataRepair> addOnSubscriptionInRepair = new LinkedList<SubscriptionDataRepair>();
            List<SubscriptionDataRepair> inRepair =  new LinkedList<SubscriptionDataRepair>();
            for (Subscription cur : subscriptions) {

                SubscriptionRepair curRepair = findAndCreateSubscriptionRepair(cur.getId(), input.getSubscriptions());
                if (curRepair != null) {
                    SubscriptionDataRepair curData = ((SubscriptionDataRepair) cur);
                    List<EntitlementEvent> remaining = getRemainingEventsAndValidateDeletedEvents(curData, firstDeletedBPEventTime, curRepair.getDeletedEvents());

                    
                    
                    boolean isPlanRecreate = (curRepair.getNewEvents().size() > 0 
                            && (curRepair.getNewEvents().get(0).getSubscriptionTransitionType() == SubscriptionTransitionType.CREATE 
                                    || curRepair.getNewEvents().get(0).getSubscriptionTransitionType() == SubscriptionTransitionType.RE_CREATE));
                    
                    DateTime newSubscriptionStartDate = isPlanRecreate ? curRepair.getNewEvents().get(0).getRequestedDate() : null;
                    
                    if (isPlanRecreate && remaining.size() != 0) {
                        throw new EntitlementRepairException(ErrorCode.ENT_REPAIR_SUB_RECREATE_NOT_EMPTY, cur.getId(), cur.getBundleId());
                    }
                    
                    if (cur.getCategory() == ProductCategory.BASE) {

                        int bpTransitionSize =((SubscriptionData) cur).getAllTransitions().size();
                        lastRemainingBPEventTime = (remaining.size() > 0) ? curData.getAllTransitions().get(remaining.size() - 1).getEffectiveTransitionTime() : null;
                        firstDeletedBPEventTime =  (remaining.size() < bpTransitionSize) ? curData.getAllTransitions().get(remaining.size()).getEffectiveTransitionTime() : null;

                        isBasePlanRecreate = isPlanRecreate;
                        newBundleStartDate = newSubscriptionStartDate;
                    }

                    if (curRepair.getNewEvents().size() > 0) {
                        DateTime lastRemainingEventTime = (remaining.size() == 0) ? null : curData.getAllTransitions().get(remaining.size() - 1).getEffectiveTransitionTime();
                        validateFirstNewEvent(curData, curRepair.getNewEvents().get(0), lastRemainingBPEventTime, lastRemainingEventTime);
                    }


                    SubscriptionDataRepair sRepair = createSubscriptionDataRepair(curData, newBundleStartDate, newSubscriptionStartDate, remaining);
                    repairDao.initializeRepair(curData.getId(), remaining);
                    inRepair.add(sRepair);
                    if (sRepair.getCategory() == ProductCategory.ADD_ON) {
                        addOnSubscriptionInRepair.add(sRepair);
                    } else if (sRepair.getCategory() == ProductCategory.BASE) {
                        baseSubscriptionRepair = sRepair;
                    }
                }
            }

            validateBasePlanRecreate(isBasePlanRecreate, subscriptions, input.getSubscriptions());
            validateInputSubscriptionsKnown(subscriptions, input.getSubscriptions());

            TreeSet<NewEvent> newEventSet = new TreeSet<SubscriptionRepair.NewEvent>(new Comparator<NewEvent>() {
                @Override
                public int compare(NewEvent o1, NewEvent o2) {
                    return o1.getRequestedDate().compareTo(o2.getRequestedDate());
                }
            });
            for (SubscriptionRepair cur : input.getSubscriptions()) {
                for (NewEvent e : cur.getNewEvents()) {
                    newEventSet.add(new DefaultNewEvent(cur.getId(), e.getPlanPhaseSpecifier(), e.getRequestedDate(), e.getSubscriptionTransitionType()));    
                }
            }

            Iterator<NewEvent> it = newEventSet.iterator();
            while (it.hasNext()) {
                DefaultNewEvent cur = (DefaultNewEvent) it.next();
                SubscriptionDataRepair curDataRepair = findSubscriptionDataRepair(cur.getSubscriptionId(), inRepair);
                if (curDataRepair == null) {
                    throw new EntitlementRepairException(ErrorCode.ENT_REPAIR_UNKNOWN_SUBSCRIPTION, cur.getSubscriptionId());
                }
                curDataRepair.addNewRepairEvent(cur, baseSubscriptionRepair, addOnSubscriptionInRepair, context);
            }

            if (dryRun) {
                final List<SubscriptionRepair> repairs = createGetSubscriptionRepairList(subscriptions, convertDataRepair(inRepair)); 
                return createGetBundleRepair(input.getBundleId(), input.getViewId(), repairs);
            } else {
                dao.repair(input.getBundleId(), inRepair, context);
                bundle = dao.getSubscriptionBundleFromId(input.getBundleId());
                String newViewId = getViewId(((SubscriptionBundleData) bundle).getLastSysUpdateTime(), subscriptions);
                final List<SubscriptionRepair> repairs = createGetSubscriptionRepairList(subscriptions, Collections.<SubscriptionRepair>emptyList()); 
                return createGetBundleRepair(input.getBundleId(), viewId, repairs);
            }
        } finally {
            repairDao.cleanup();
        }
    }

    
    private void validateBasePlanRecreate(boolean isBasePlanRecreate, List<Subscription> subscriptions, List<SubscriptionRepair> input) 
        throws EntitlementRepairException  {
        
        if (!isBasePlanRecreate) {
            return;
        }
        if (subscriptions.size() != input.size()) {
            throw new EntitlementRepairException(ErrorCode.ENT_REPAIR_BP_RECREATE_MISSING_AO, subscriptions.get(0).getBundleId());
        }
        for (SubscriptionRepair cur : input) {
            if (cur.getNewEvents().size() != 0 
                    && (cur.getNewEvents().get(0).getSubscriptionTransitionType() != SubscriptionTransitionType.CREATE
                            && cur.getNewEvents().get(0).getSubscriptionTransitionType() != SubscriptionTransitionType.RE_CREATE)) {
                throw new EntitlementRepairException(ErrorCode.ENT_REPAIR_BP_RECREATE_MISSING_AO_CREATE, subscriptions.get(0).getBundleId());
            }
        }
    }
    
    
    private void validateInputSubscriptionsKnown(List<Subscription> subscriptions, List<SubscriptionRepair> input)
        throws EntitlementRepairException {

        for (SubscriptionRepair cur : input) {
            boolean found = false;
            for (Subscription s : subscriptions) {
                if (s.getId().equals(cur.getId())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new EntitlementRepairException(ErrorCode.ENT_REPAIR_UNKNOWN_SUBSCRIPTION, cur.getId());
            }
        }
    }

    private void validateFirstNewEvent(final SubscriptionData data, final NewEvent firstNewEvent, final DateTime lastBPRemainingTime, final DateTime lastRemainingTime) 
    throws EntitlementRepairException {
        if (lastBPRemainingTime != null &&
                firstNewEvent.getRequestedDate().isBefore(lastBPRemainingTime)) {
            throw new EntitlementRepairException(ErrorCode.ENT_REPAIR_NEW_AO_EVENT_BEFORE_BP, firstNewEvent.getPlanPhaseSpecifier().toString(), data.getId());
        }
        if (lastRemainingTime != null &&
                firstNewEvent.getRequestedDate().isBefore(lastRemainingTime)) {
            throw new EntitlementRepairException(ErrorCode.ENT_REPAIR_INVALID_NEW_AO_EVENT, firstNewEvent.getPlanPhaseSpecifier().toString(), data.getId());
        }

    }

    private List<EntitlementEvent> getRemainingEventsAndValidateDeletedEvents(final SubscriptionDataRepair data, final DateTime firstBPDeletedTime,
            final List<SubscriptionRepair.DeletedEvent> deletedEvents) 
            throws EntitlementRepairException  {

        if (deletedEvents == null || deletedEvents.size() == 0) {
            return data.getEvents();
        }

        int nbDeleted = 0;
        LinkedList<EntitlementEvent> result = new LinkedList<EntitlementEvent>();
        for (EntitlementEvent cur : data.getEvents()) {

            boolean foundDeletedEvent = false;
            for (SubscriptionRepair.DeletedEvent d : deletedEvents) {
                if (cur.getId().equals(d.getEventId())) {
                    foundDeletedEvent = true;
                    nbDeleted++;
                    break;
                }
            }
            if (!foundDeletedEvent && nbDeleted > 0) {
                throw new EntitlementRepairException(ErrorCode.ENT_REPAIR_INVALID_DELETE_SET, cur.getId(), data.getId());
            }
            if (firstBPDeletedTime != null && 
                    ! cur.getEffectiveDate().isBefore(firstBPDeletedTime) &&
                    ! foundDeletedEvent) {
                throw new EntitlementRepairException(ErrorCode.ENT_REPAIR_MISSING_AO_DELETE_EVENT, cur.getId(), data.getId());                
            }

            if (nbDeleted == 0) {
                result.add(cur);
            }
        }
        if (nbDeleted != deletedEvents.size()) {
            for (SubscriptionRepair.DeletedEvent d : deletedEvents) {
                boolean found = false;
                for (SubscriptionTransitionData cur : data.getAllTransitions()) {
                    if (cur.getId().equals(d.getEventId())) {
                        found = true;
                        continue;
                    }
                }
                if (!found) {
                    throw new EntitlementRepairException(ErrorCode.ENT_REPAIR_NON_EXISTENT_DELETE_EVENT, d.getEventId(), data.getId());
                }
            }

        }
        return result;
    }

    
    private String getViewId(DateTime lastUpdateBundleDate, List<Subscription> subscriptions) {
        StringBuilder tmp = new StringBuilder();
        long lastOrderedId = -1;
        for (Subscription cur : subscriptions) {
            lastOrderedId = lastOrderedId < ((SubscriptionData) cur).getLastEventOrderedId() ? ((SubscriptionData) cur).getLastEventOrderedId() : lastOrderedId;
        }
        tmp.append(lastOrderedId);
        tmp.append("-");
        tmp.append(lastUpdateBundleDate.toDate().getTime());
        return tmp.toString();
    }

    private BundleRepair createGetBundleRepair(final UUID bundleId, final String viewId, final List<SubscriptionRepair> repairList) {
        return new BundleRepair() {
            @Override
            public String getViewId() {
                return viewId;
            }
            @Override
            public List<SubscriptionRepair> getSubscriptions() {
                return repairList;
            }
            @Override
            public UUID getBundleId() {
                return bundleId;
            }
        };

    }

    private List<SubscriptionRepair> createGetSubscriptionRepairList(final List<Subscription> subscriptions, final List<SubscriptionRepair> inRepair) {

        final List<SubscriptionRepair> result = new LinkedList<SubscriptionRepair>();
        Set<UUID> repairIds = new TreeSet<UUID>();
        for (final SubscriptionRepair cur : inRepair) {
            repairIds.add(cur.getId());
            result.add(cur);
        }
        for (final Subscription cur : subscriptions) {
            if ( !repairIds.contains(cur.getId())) { 
                result.add(new DefaultSubscriptionRepair((SubscriptionData) cur));
            }
        }
        return result;
    }


    private List<SubscriptionRepair> convertDataRepair(List<SubscriptionDataRepair> input) {
        List<SubscriptionRepair> result = new LinkedList<SubscriptionRepair>();
        for (SubscriptionDataRepair cur : input) {
            result.add(new DefaultSubscriptionRepair(cur));
        }
        return result;
    }

    private SubscriptionDataRepair findSubscriptionDataRepair(final UUID targetId, final List<SubscriptionDataRepair> input) {
        for (SubscriptionDataRepair cur : input) {
            if (cur.getId().equals(targetId)) {
                return cur;
            }
        }
        return null;
    }


    private SubscriptionDataRepair createSubscriptionDataRepair(final SubscriptionData curData, final DateTime newBundleStartDate, final DateTime newSubscriptionStartDate, final List<EntitlementEvent> initialEvents) {
        SubscriptionBuilder builder = new SubscriptionBuilder(curData);
        builder.setActiveVersion(curData.getActiveVersion() + 1);
        if (newBundleStartDate != null) {
            builder.setBundleStartDate(newBundleStartDate);
        }
        if (newSubscriptionStartDate != null) {
            builder.setStartDate(newSubscriptionStartDate);
        }
        if (initialEvents.size() > 0) {
            for (EntitlementEvent cur : initialEvents) {
                cur.setActiveVersion(builder.getActiveVersion());
            }
        }
        SubscriptionDataRepair result = (SubscriptionDataRepair) factory.createSubscription(builder, initialEvents);
        return result;
    }


    private SubscriptionRepair findAndCreateSubscriptionRepair(final UUID target, final List<SubscriptionRepair> input) {
        for (SubscriptionRepair cur : input) {
            if (target.equals(cur.getId())) {
                return new DefaultSubscriptionRepair(cur);
            }
        }
        return null;
    }
}

