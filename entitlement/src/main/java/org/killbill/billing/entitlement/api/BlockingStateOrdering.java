/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.entitlement.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.PriceList;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.entitlement.DefaultEntitlementService;
import org.killbill.billing.entitlement.block.BlockingChecker.BlockingAggregator;
import org.killbill.billing.entitlement.block.DefaultBlockingChecker.DefaultBlockingAggregator;
import org.killbill.billing.junction.DefaultBlockingState;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

// Given an event stream (across one or multiple entitlements), insert the blocking events at the right place
public class BlockingStateOrdering extends EntitlementOrderingBase {

    private static final BlockingStateOrdering INSTANCE = new BlockingStateOrdering();

    private BlockingStateOrdering() {}

    public static void insertSorted(final Iterable<Entitlement> entitlements, final DateTimeZone accountTimeZone, final LinkedList<SubscriptionEvent> result) {
        INSTANCE.computeEvents(entitlements, accountTimeZone, result);
    }

    private void computeEvents(final Iterable<Entitlement> entitlements, final DateTimeZone accountTimeZone, final LinkedList<SubscriptionEvent> result) {
        final Collection<UUID> allEntitlementUUIDs = new HashSet<UUID>();
        final Collection<BlockingState> blockingStates = new LinkedList<BlockingState>();
        for (final Entitlement entitlement : entitlements) {
            allEntitlementUUIDs.add(entitlement.getId());
            Preconditions.checkState(entitlement instanceof DefaultEntitlement, "Entitlement %s is not a DefaultEntitlement", entitlement);
            blockingStates.addAll(((DefaultEntitlement) entitlement).getEventsStream().getBlockingStates());
        }

        // Trust the incoming ordering here: blocking states were sorted using ProxyBlockingStateDao#sortedCopy
        for (final BlockingState bs : blockingStates) {
            final List<SubscriptionEvent> newEvents = new ArrayList<SubscriptionEvent>();
            final int index = insertFromBlockingEvent(accountTimeZone, allEntitlementUUIDs, result, bs, bs.getEffectiveDate(), newEvents);
            insertAfterIndex(result, newEvents, index);
        }
    }

    // Returns the index and the newEvents generated from the incoming blocking state event. Those new events will all be created for the same effectiveDate and should be ordered.
    private int insertFromBlockingEvent(final DateTimeZone accountTimeZone, final Collection<UUID> allEntitlementUUIDs, final List<SubscriptionEvent> result, final BlockingState bs, final DateTime bsEffectiveDate, final List<SubscriptionEvent> newEvents) {
        // Keep the current state per entitlement
        final Map<UUID, TargetState> targetStates = new HashMap<UUID, TargetState>();
        for (final UUID cur : allEntitlementUUIDs) {
            targetStates.put(cur, new TargetState());
        }

        //
        // Find out where to insert next event, and calculate current state for each entitlement at the position where we stop.
        //
        int index = -1;
        final Iterator<SubscriptionEvent> it = result.iterator();
        // Where we need to insert in that stream
        DefaultSubscriptionEvent curInsertion = null;
        while (it.hasNext()) {
            final DefaultSubscriptionEvent cur = (DefaultSubscriptionEvent) it.next();
            final int compEffectiveDate = bsEffectiveDate.compareTo(cur.getEffectiveDateTime());
            final boolean shouldContinue = (compEffectiveDate >= 0);
            if (!shouldContinue) {
                break;
            }
            index++;

            final TargetState curTargetState = targetStates.get(cur.getEntitlementId());
            switch (cur.getSubscriptionEventType()) {
                case START_ENTITLEMENT:
                    curTargetState.setEntitlementStarted();
                    break;
                case STOP_ENTITLEMENT:
                    curTargetState.setEntitlementStopped();
                    break;
                case START_BILLING:
                    curTargetState.setBillingStarted();
                    break;
                case PAUSE_BILLING:
                case PAUSE_ENTITLEMENT:
                case RESUME_ENTITLEMENT:
                case RESUME_BILLING:
                case SERVICE_STATE_CHANGE:
                    curTargetState.addEntitlementEvent(cur);
                    break;
                case STOP_BILLING:
                    curTargetState.setBillingStopped();
                    break;
            }
            curInsertion = cur;
        }

        // Extract the list of targets based on the type of blocking state
        final List<UUID> targetEntitlementIds = bs.getType() == BlockingStateType.SUBSCRIPTION ? ImmutableList.<UUID>of(bs.getBlockedId()) :
                                                ImmutableList.<UUID>copyOf(allEntitlementUUIDs);

        // For each target compute the new events that should be inserted in the stream
        for (final UUID targetEntitlementId : targetEntitlementIds) {
            final SubscriptionEvent[] prevNext = findPrevNext(result, targetEntitlementId, curInsertion);
            final TargetState curTargetState = targetStates.get(targetEntitlementId);

            final List<SubscriptionEventType> eventTypes = curTargetState.addStateAndReturnEventTypes(bs);
            for (final SubscriptionEventType t : eventTypes) {
                newEvents.add(toSubscriptionEvent(prevNext[0], prevNext[1], targetEntitlementId, bs, t, accountTimeZone));
            }
        }
        return index;
    }

    // Extract prev and next events in the stream events for that particular target subscription from the insertionEvent
    private SubscriptionEvent[] findPrevNext(final List<SubscriptionEvent> events, final UUID targetEntitlementId, final SubscriptionEvent insertionEvent) {
        // Find prev/next event for the same entitlement
        final SubscriptionEvent[] result = new DefaultSubscriptionEvent[2];
        if (insertionEvent == null) {
            result[0] = null;
            result[1] = !events.isEmpty() ? events.get(0) : null;
            return result;
        }

        final Iterator<SubscriptionEvent> it = events.iterator();
        DefaultSubscriptionEvent prev = null;
        DefaultSubscriptionEvent next = null;
        boolean foundCur = false;
        while (it.hasNext()) {
            final DefaultSubscriptionEvent tmp = (DefaultSubscriptionEvent) it.next();
            if (tmp.getEntitlementId().equals(targetEntitlementId)) {
                if (!foundCur) {
                    prev = tmp;
                } else {
                    next = tmp;
                    break;
                }
            }
            // Check both the id and the event type because of multiplexing
            if (tmp.getId().equals(insertionEvent.getId()) &&
                tmp.getSubscriptionEventType().equals(insertionEvent.getSubscriptionEventType())) {
                foundCur = true;
            }
        }
        result[0] = prev;
        result[1] = next;
        return result;
    }

    private SubscriptionEvent toSubscriptionEvent(@Nullable final SubscriptionEvent prev, @Nullable final SubscriptionEvent next,
                                                  final UUID entitlementId, final BlockingState in, final SubscriptionEventType eventType, final DateTimeZone accountTimeZone) {
        final Product prevProduct;
        final Plan prevPlan;
        final PlanPhase prevPlanPhase;
        final PriceList prevPriceList;
        final BillingPeriod prevBillingPeriod;
        // Enforce prev = null for start events
        if (prev == null || SubscriptionEventType.START_ENTITLEMENT.equals(eventType) || SubscriptionEventType.START_BILLING.equals(eventType)) {
            prevProduct = null;
            prevPlan = null;
            prevPlanPhase = null;
            prevPriceList = null;
            prevBillingPeriod = null;
        } else {
            // We look for the next for the 'prev' meaning we we are headed to, but if this is null -- for example on cancellation we get the prev which gives the correct state.
            prevProduct = (prev.getNextProduct() != null ? prev.getNextProduct() : prev.getPrevProduct());
            prevPlan = (prev.getNextPlan() != null ? prev.getNextPlan() : prev.getPrevPlan());
            prevPlanPhase = (prev.getNextPhase() != null ? prev.getNextPhase() : prev.getPrevPhase());
            prevPriceList = (prev.getNextPriceList() != null ? prev.getNextPriceList() : prev.getPrevPriceList());
            prevBillingPeriod = (prev.getNextBillingPeriod() != null ? prev.getNextBillingPeriod() : prev.getPrevBillingPeriod());
        }

        final Product nextProduct;
        final Plan nextPlan;
        final PlanPhase nextPlanPhase;
        final PriceList nextPriceList;
        final BillingPeriod nextBillingPeriod;
        if (SubscriptionEventType.PAUSE_ENTITLEMENT.equals(eventType) || SubscriptionEventType.PAUSE_BILLING.equals(eventType) ||
            SubscriptionEventType.RESUME_ENTITLEMENT.equals(eventType) || SubscriptionEventType.RESUME_BILLING.equals(eventType) ||
            (SubscriptionEventType.SERVICE_STATE_CHANGE.equals(eventType) && (prev == null || (!SubscriptionEventType.STOP_ENTITLEMENT.equals(prev.getSubscriptionEventType()) && !SubscriptionEventType.STOP_BILLING.equals(prev.getSubscriptionEventType()))))) {
            // Enforce next = prev for pause/resume events as well as service changes
            nextProduct = prevProduct;
            nextPlan = prevPlan;
            nextPlanPhase = prevPlanPhase;
            nextPriceList = prevPriceList;
            nextBillingPeriod = prevBillingPeriod;
        } else if (next == null) {
            // Enforce next = null for stop events
            if (prev == null || SubscriptionEventType.STOP_ENTITLEMENT.equals(eventType) || SubscriptionEventType.STOP_BILLING.equals(eventType)) {
                nextProduct = null;
                nextPlan = null;
                nextPlanPhase = null;
                nextPriceList = null;
                nextBillingPeriod = null;
            } else {
                nextProduct = prev.getNextProduct();
                nextPlan = prev.getNextPlan();
                nextPlanPhase = prev.getNextPhase();
                nextPriceList = prev.getNextPriceList();
                nextBillingPeriod = prev.getNextBillingPeriod();
            }
        } else {
            nextProduct = next.getNextProduct();
            nextPlan = next.getNextPlan();
            nextPlanPhase = next.getNextPhase();
            nextPriceList = next.getNextPriceList();
            nextBillingPeriod = next.getNextBillingPeriod();
        }

        // See https://github.com/killbill/killbill/issues/135
        final String serviceName = getRealServiceNameForEntitlementOrExternalServiceName(in.getService(), eventType);

        return new DefaultSubscriptionEvent(in.getId(),
                                            entitlementId,
                                            in.getEffectiveDate(),
                                            in.getCreatedDate(),
                                            eventType,
                                            in.isBlockEntitlement(),
                                            in.isBlockBilling(),
                                            serviceName,
                                            in.getStateName(),
                                            prevProduct,
                                            prevPlan,
                                            prevPlanPhase,
                                            prevPriceList,
                                            prevBillingPeriod,
                                            nextProduct,
                                            nextPlan,
                                            nextPlanPhase,
                                            nextPriceList,
                                            nextBillingPeriod,
                                            in.getCreatedDate(),
                                            accountTimeZone);
    }

    private void insertAfterIndex(final LinkedList<SubscriptionEvent> original, final Collection<SubscriptionEvent> newEvents, final int index) {
        final boolean firstPosition = (index == -1);
        final boolean lastPosition = (index == original.size() - 1);
        if (lastPosition || firstPosition) {
            for (final SubscriptionEvent cur : newEvents) {
                if (lastPosition) {
                    original.addLast(cur);
                } else {
                    original.addFirst(cur);
                }
            }
        } else {
            original.addAll(index + 1, newEvents);
        }
    }

    //
    // Internal class to keep the state associated with each subscription
    //
    private static final class TargetState {

        private final Map<String, BlockingState> perServiceBlockingState;

        private boolean isEntitlementStarted;
        private boolean isEntitlementStopped;
        private boolean isBillingStarted;
        private boolean isBillingStopped;

        public TargetState() {
            this.isEntitlementStarted = false;
            this.isEntitlementStopped = false;
            this.isBillingStarted = false;
            this.isBillingStopped = false;
            this.perServiceBlockingState = new HashMap<String, BlockingState>();
        }

        public void setEntitlementStarted() {
            isEntitlementStarted = true;
        }

        public void setEntitlementStopped() {
            isEntitlementStopped = true;
        }

        public void setBillingStarted() {
            isBillingStarted = true;
        }

        public void setBillingStopped() {
            isBillingStopped = true;
        }

        public void addEntitlementEvent(final SubscriptionEvent e) {
            final String serviceName = getRealServiceNameForEntitlementOrExternalServiceName(e.getServiceName(), e.getSubscriptionEventType());
            final BlockingState lastBlockingStateForService = perServiceBlockingState.get(serviceName);

            // Assume the event has no impact on changes - TODO this is wrong for SERVICE_STATE_CHANGE
            final boolean blockChange = lastBlockingStateForService != null && lastBlockingStateForService.isBlockChange();
            // For block entitlement or billing, override the previous state
            final boolean blockedEntitlement = e.isBlockedEntitlement();
            final boolean blockedBilling = e.isBlockedBilling();

            final BlockingState converted = new DefaultBlockingState(e.getEntitlementId(),
                                                                     BlockingStateType.SUBSCRIPTION,
                                                                     e.getServiceStateName(),
                                                                     serviceName,
                                                                     blockChange,
                                                                     blockedEntitlement,
                                                                     blockedBilling,
                                                                     ((DefaultSubscriptionEvent) e).getEffectiveDateTime());
            perServiceBlockingState.put(converted.getService(), converted);
        }

        //
        // From the current state of that subscription, compute the effect of the new state based on the incoming blockingState event
        //
        private List<SubscriptionEventType> addStateAndReturnEventTypes(final BlockingState bs) {
            // Turn off isBlockedEntitlement and isBlockedBilling if there was not start event
            final BlockingState fixedBlockingState = new DefaultBlockingState(bs.getBlockedId(),
                                                                              bs.getType(),
                                                                              bs.getStateName(),
                                                                              bs.getService(),
                                                                              bs.isBlockChange(),
                                                                              (bs.isBlockEntitlement() && isEntitlementStarted && !isEntitlementStopped),
                                                                              (bs.isBlockBilling() && isBillingStarted && !isBillingStopped),
                                                                              bs.getEffectiveDate());

            final List<SubscriptionEventType> result = new ArrayList<SubscriptionEventType>(4);
            if (fixedBlockingState.getStateName().equals(DefaultEntitlementApi.ENT_STATE_CANCELLED)) {
                isEntitlementStopped = true;
                result.add(SubscriptionEventType.STOP_ENTITLEMENT);
                return result;
            }

            //
            // We look at the effect of the incoming event for the specific service, and then recompute the state after so we can compare if anything has changed
            // across all services
            //
            final BlockingAggregator stateBefore = getState();
            if (DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME.equals(fixedBlockingState.getService())) {
                // Some blocking states will be added as entitlement-service and billing-service via addEntitlementEvent
                // (see above). Because of it, we need to multiplex entitlement events here.
                // TODO - this is magic and fragile. We should revisit how we create this state machine.
                perServiceBlockingState.put(DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME, fixedBlockingState);
                perServiceBlockingState.put(BILLING_SERVICE_NAME, fixedBlockingState);
            } else {
                perServiceBlockingState.put(fixedBlockingState.getService(), fixedBlockingState);
            }
            final BlockingAggregator stateAfter = getState();

            final boolean shouldResumeEntitlement = isEntitlementStarted && !isEntitlementStopped && stateBefore.isBlockEntitlement() && !stateAfter.isBlockEntitlement();
            if (shouldResumeEntitlement) {
                result.add(SubscriptionEventType.RESUME_ENTITLEMENT);
            }
            final boolean shouldResumeBilling = isBillingStarted && !isBillingStopped && stateBefore.isBlockBilling() && !stateAfter.isBlockBilling();
            if (shouldResumeBilling) {
                result.add(SubscriptionEventType.RESUME_BILLING);
            }

            final boolean shouldBlockEntitlement = isEntitlementStarted && !isEntitlementStopped && !stateBefore.isBlockEntitlement() && stateAfter.isBlockEntitlement();
            if (shouldBlockEntitlement) {
                result.add(SubscriptionEventType.PAUSE_ENTITLEMENT);
            }
            final boolean shouldBlockBilling = isBillingStarted && !isBillingStopped && !stateBefore.isBlockBilling() && stateAfter.isBlockBilling();
            if (shouldBlockBilling) {
                result.add(SubscriptionEventType.PAUSE_BILLING);
            }

            if (!shouldResumeEntitlement && !shouldResumeBilling && !shouldBlockEntitlement && !shouldBlockBilling && !fixedBlockingState.getService().equals(DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME)) {
                result.add(SubscriptionEventType.SERVICE_STATE_CHANGE);
            }
            return result;
        }

        private BlockingAggregator getState() {
            final DefaultBlockingAggregator aggrBefore = new DefaultBlockingAggregator();
            for (final BlockingState cur : perServiceBlockingState.values()) {
                aggrBefore.or(cur);
            }
            return aggrBefore;
        }
    }
}
