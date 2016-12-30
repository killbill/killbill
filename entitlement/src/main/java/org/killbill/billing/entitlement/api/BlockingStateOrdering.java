/*
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import org.killbill.billing.callcontext.InternalTenantContext;
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
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

// Given an event stream (across one or multiple entitlements), insert the blocking events at the right place
public class BlockingStateOrdering extends EntitlementOrderingBase {

    private static final BlockingStateOrdering INSTANCE = new BlockingStateOrdering();

    private BlockingStateOrdering() {}

    public static void insertSorted(final Iterable<Entitlement> entitlements, final InternalTenantContext internalTenantContext, final LinkedList<SubscriptionEvent> inputAndOutputResult) {
        INSTANCE.computeEvents(entitlements, internalTenantContext, inputAndOutputResult);

    }

    private void computeEvents(final Iterable<Entitlement> entitlements, final InternalTenantContext internalTenantContext, final LinkedList<SubscriptionEvent> inputAndOutputResult) {
        final Collection<UUID> allEntitlementUUIDs = new HashSet<UUID>();
        final Collection<BlockingState> blockingStates = new LinkedList<BlockingState>();
        for (final Entitlement entitlement : entitlements) {
            allEntitlementUUIDs.add(entitlement.getId());
            Preconditions.checkState(entitlement instanceof DefaultEntitlement, "Entitlement %s is not a DefaultEntitlement", entitlement);
            blockingStates.addAll(((DefaultEntitlement) entitlement).getEventsStream().getBlockingStates());
        }

        final SupportForOlderVersionThan_0_17_X backwardCompatibleContext = new SupportForOlderVersionThan_0_17_X(inputAndOutputResult, blockingStates);

        // Trust the incoming ordering here: blocking states were sorted using ProxyBlockingStateDao#sortedCopy
        for (final BlockingState currentBlockingState : blockingStates) {
            final List<SubscriptionEvent> outputNewEvents = new ArrayList<SubscriptionEvent>();
            final int index = insertFromBlockingEvent(allEntitlementUUIDs, currentBlockingState, inputAndOutputResult, backwardCompatibleContext, internalTenantContext, outputNewEvents);
            insertAfterIndex(inputAndOutputResult, outputNewEvents, index);
        }
        backwardCompatibleContext.addMissing_START_ENTITLEMENT(inputAndOutputResult, internalTenantContext);
    }

    // Returns the index and the newEvents generated from the incoming blocking state event. Those new events will all be created for the same effectiveDate and should be ordered.
    private int insertFromBlockingEvent(final Collection<UUID> allEntitlementUUIDs, final BlockingState currentBlockingState, final List<SubscriptionEvent> inputExistingEvents, final SupportForOlderVersionThan_0_17_X backwardCompatibleContext, final InternalTenantContext internalTenantContext, final Collection<SubscriptionEvent> outputNewEvents) {
        // Keep the current state per entitlement
        final Map<UUID, TargetState> targetStates = new HashMap<UUID, TargetState>();
        for (final UUID cur : allEntitlementUUIDs) {
            targetStates.put(cur, new TargetState());
        }

        //
        // Find out where to insert next event, and calculate current state for each entitlement at the position where we stop.
        //
        int index = -1;
        final Iterator<SubscriptionEvent> it = inputExistingEvents.iterator();
        // Where we need to insert in that stream
        DefaultSubscriptionEvent curInsertion = null;
        while (it.hasNext()) {
            final DefaultSubscriptionEvent cur = (DefaultSubscriptionEvent) it.next();
            final int compEffectiveDate = currentBlockingState.getEffectiveDate().compareTo(cur.getEffectiveDateTime());

            final boolean shouldContinue;
            switch (compEffectiveDate) {
                case -1:
                    shouldContinue = false;
                    break;
                case 0:
                    // In case of exact same date, we want to make sure that a START_ENTITLEMENT event gets correctly populated when the STOP_BILLING is also on the same date
                    if (currentBlockingState.getStateName().equals(DefaultEntitlementApi.ENT_STATE_START) && cur.getSubscriptionEventType() != SubscriptionEventType.STOP_BILLING) {
                        shouldContinue = false;
                    } else {
                        shouldContinue = true;
                    }
                    break;
                case 1:
                    shouldContinue = true;
                    break;
                default:
                    // Make compiler happy
                    throw new IllegalStateException("Cannot reach statement");
            }
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
                    // For older subscriptions we miss the START_ENTITLEMENT (the START_BILLING marks both start of billing and entitlement)
                    if (backwardCompatibleContext.isOlderEntitlement(cur.getEntitlementId())) {
                        curTargetState.setEntitlementStarted();
                    }
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
        final List<UUID> targetEntitlementIds = currentBlockingState.getType() == BlockingStateType.SUBSCRIPTION ? ImmutableList.<UUID>of(currentBlockingState.getBlockedId()) :
                                                ImmutableList.<UUID>copyOf(allEntitlementUUIDs);

        // For each target compute the new events that should be inserted in the stream
        for (final UUID targetEntitlementId : targetEntitlementIds) {
            final SubscriptionEvent[] prevNext = findPrevNext(inputExistingEvents, targetEntitlementId, curInsertion);
            final TargetState curTargetState = targetStates.get(targetEntitlementId);

            final List<SubscriptionEventType> eventTypes = curTargetState.addStateAndReturnEventTypes(currentBlockingState);
            for (final SubscriptionEventType t : eventTypes) {
                outputNewEvents.add(toSubscriptionEvent(prevNext[0], prevNext[1], targetEntitlementId, currentBlockingState, t, internalTenantContext));
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
                                                  final UUID entitlementId, final BlockingState in, final SubscriptionEventType eventType,
                                                  final InternalTenantContext internalTenantContext) {
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
        if (SubscriptionEventType.PAUSE_ENTITLEMENT.equals(eventType) ||
            SubscriptionEventType.PAUSE_BILLING.equals(eventType) ||
            SubscriptionEventType.RESUME_ENTITLEMENT.equals(eventType) ||
            SubscriptionEventType.RESUME_BILLING.equals(eventType) ||
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
        } else if (prev != null && (SubscriptionEventType.START_ENTITLEMENT.equals(eventType) || SubscriptionEventType.START_BILLING.equals(eventType))) {
            // For start events, next is actually the prev (e.g. the trial, not the phase)
            nextProduct = prev.getNextProduct();
            nextPlan = prev.getNextPlan();
            nextPlanPhase = prev.getNextPhase();
            nextPriceList = prev.getNextPriceList();
            nextBillingPeriod = prev.getNextBillingPeriod();
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
                                            internalTenantContext);
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
                                                                              (bs.isBlockEntitlement() && isEntitlementStarted &&  !isEntitlementStopped),
                                                                              (bs.isBlockBilling() && isBillingStarted && !isBillingStopped),
                                                                              bs.getEffectiveDate());

            final List<SubscriptionEventType> result = new ArrayList<SubscriptionEventType>(4);
            if (fixedBlockingState.getStateName().equals(DefaultEntitlementApi.ENT_STATE_START)) {
                isEntitlementStarted = true;
                result.add(SubscriptionEventType.START_ENTITLEMENT);
                return result;
            } else if (fixedBlockingState.getStateName().equals(DefaultEntitlementApi.ENT_STATE_CANCELLED)) {
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

            final boolean shouldResumeEntitlement = isEntitlementStarted &&  !isEntitlementStopped && stateBefore.isBlockEntitlement() && !stateAfter.isBlockEntitlement();
            if (shouldResumeEntitlement) {
                result.add(SubscriptionEventType.RESUME_ENTITLEMENT);
            }
            final boolean shouldResumeBilling = isBillingStarted && !isBillingStopped && stateBefore.isBlockBilling() && !stateAfter.isBlockBilling();
            if (shouldResumeBilling) {
                result.add(SubscriptionEventType.RESUME_BILLING);
            }

            final boolean shouldBlockEntitlement = isEntitlementStarted &&  !isEntitlementStopped && !stateBefore.isBlockEntitlement() && stateAfter.isBlockEntitlement();
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

    //
    // The logic to add the missing START_ENTITLEMENT for older subscriptions is contained in this class. When we want/need to drop backward compatibility we can
    // simply drop this class and where it is called.
    //
    private static class SupportForOlderVersionThan_0_17_X {

        private final Set<UUID> olderEntitlementSet;

        public SupportForOlderVersionThan_0_17_X(final List<SubscriptionEvent> initialEntitlementEvents, final Collection<BlockingState> blockingStates) {
            this.olderEntitlementSet = computeOlderEntitlementSet(initialEntitlementEvents, blockingStates);
        }

        public boolean isOlderEntitlement(final UUID entitlementId) {
            return olderEntitlementSet.contains(entitlementId);
        }

        public void addMissing_START_ENTITLEMENT(final LinkedList<SubscriptionEvent> inputAndOutputResult, final InternalTenantContext internalTenantContext) {

            // Insert missing START_ENTITLEMENT right before START_BILLING (same event as START_BILLING but with different type=START_ENTITLEMENT to be compatible with old code)
            final ListIterator<SubscriptionEvent> it = inputAndOutputResult.listIterator();
            while (it.hasNext()) {
                final SubscriptionEvent cur = it.next();
                if (cur.getSubscriptionEventType() == SubscriptionEventType.START_BILLING && olderEntitlementSet.contains(cur.getEntitlementId())) {
                    final SubscriptionEvent newEntitlementStartEvent = new DefaultSubscriptionEvent(cur.getId(),
                                                                                                    cur.getEntitlementId(),
                                                                                                    internalTenantContext.toUTCDateTime(cur.getEffectiveDate()),
                                                                                                    SubscriptionEventType.START_ENTITLEMENT,
                                                                                                    false,
                                                                                                    false,
                                                                                                    DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME,
                                                                                                    SubscriptionEventType.START_ENTITLEMENT.toString(),
                                                                                                    cur.getPrevProduct(),
                                                                                                    cur.getPrevPlan(),
                                                                                                    cur.getPrevPhase(),
                                                                                                    cur.getPrevPriceList(),
                                                                                                    cur.getPrevBillingPeriod(),
                                                                                                    cur.getNextProduct(),
                                                                                                    cur.getNextPlan(),
                                                                                                    cur.getNextPhase(),
                                                                                                    cur.getNextPriceList(),
                                                                                                    cur.getNextBillingPeriod(),
                                                                                                    internalTenantContext.toUTCDateTime(cur.getEffectiveDate()),
                                                                                                    internalTenantContext);
                    it.previous();
                    it.add(newEntitlementStartEvent);
                    it.next();
                }
            }
        }

        private Set<UUID> computeOlderEntitlementSet(final List<SubscriptionEvent> initialEntitlementEvents, final Collection<BlockingState> blockingStates) {

            final Set<UUID> START_BILLING_entitlementIdSet = ImmutableSet.copyOf(Iterables.transform(Iterables.filter(initialEntitlementEvents, new Predicate<SubscriptionEvent>() {
                @Override
                public boolean apply(final SubscriptionEvent input) {
                    return input.getSubscriptionEventType() == SubscriptionEventType.START_BILLING;
                }
            }), new Function<SubscriptionEvent, UUID>() {
                @Override
                public UUID apply(final SubscriptionEvent input) {
                    return input.getEntitlementId();
                }
            }));

            final Set<UUID> ENT_STATE_START_entitlementIdSet = ImmutableSet.copyOf(Iterables.transform(Iterables.filter(blockingStates, new Predicate<BlockingState>() {
                @Override
                public boolean apply(final BlockingState input) {
                    return input.getService().equals(DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME) && input.getStateName().equals(DefaultEntitlementApi.ENT_STATE_START);
                }
            }), new Function<BlockingState, UUID>() {
                @Override
                public UUID apply(final BlockingState input) {
                    return input.getBlockedId();
                }
            }));

            return Sets.<UUID>difference(START_BILLING_entitlementIdSet, ENT_STATE_START_entitlementIdSet);
        }
    }

}
