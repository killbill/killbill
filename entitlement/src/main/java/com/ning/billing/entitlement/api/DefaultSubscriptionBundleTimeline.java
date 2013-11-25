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

package com.ning.billing.entitlement.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.PriceList;
import com.ning.billing.catalog.api.Product;
import com.ning.billing.entitlement.DefaultEntitlementService;
import com.ning.billing.entitlement.block.BlockingChecker.BlockingAggregator;
import com.ning.billing.entitlement.block.DefaultBlockingChecker.DefaultBlockingAggregator;
import com.ning.billing.junction.DefaultBlockingState;
import com.ning.billing.subscription.api.SubscriptionBase;
import com.ning.billing.subscription.api.SubscriptionBaseTransitionType;
import com.ning.billing.subscription.api.user.SubscriptionBaseTransition;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;

public class DefaultSubscriptionBundleTimeline implements SubscriptionBundleTimeline {

    private final Logger logger = LoggerFactory.getLogger(DefaultSubscriptionBundleTimeline.class);

    // STEPH This is added to give us confidence the timeline we generate behaves as expected. Could be removed at some point
    private final static String TIMELINE_WARN_LOG = "Sanity Timeline: ";

    public static final String BILLING_SERVICE_NAME = "billing-service";
    public static final String ENT_BILLING_SERVICE_NAME = "entitlement+billing-service";

    private final List<SubscriptionEvent> events;
    private final UUID accountId;
    private final UUID bundleId;
    private final String externalKey;

    public DefaultSubscriptionBundleTimeline(final DateTimeZone accountTimeZone, final UUID accountId, final UUID bundleId, final String externalKey, final Collection<Entitlement> entitlements) {
        final Collection<BlockingState> blockingStates = new HashSet<BlockingState>();
        for (final Entitlement entitlement : entitlements) {
            blockingStates.addAll(((DefaultEntitlement) entitlement).getEventsStream().getSubscriptionEntitlementStates());
            blockingStates.addAll(((DefaultEntitlement) entitlement).getEventsStream().getBundleEntitlementStates());
            blockingStates.addAll(((DefaultEntitlement) entitlement).getEventsStream().getAccountEntitlementStates());
        }
        this.accountId = accountId;
        this.bundleId = bundleId;
        this.externalKey = externalKey;
        this.events = computeEvents(entitlements, new LinkedList<BlockingState>(blockingStates), accountTimeZone);
    }

    public DefaultSubscriptionBundleTimeline(final DateTimeZone accountTimeZone, final UUID accountId, final UUID bundleId, final String externalKey, final List<Entitlement> entitlements, List<BlockingState> allBlockingStates) {
        this.accountId = accountId;
        this.bundleId = bundleId;
        this.externalKey = externalKey;
        this.events = computeEvents(entitlements, allBlockingStates, accountTimeZone);
    }

    //
    // Compute all events based on blocking states events and base subscription events
    // Note that:
    // - base subscription events are already ordered for each Entitlement and so when we reorder at the bundle level we try not to break that initial ordering
    // - blocking state events occur at various level (account, bundle and subscription) so for higher level, we need to dispatch that on each subscription.
    //
    private List<SubscriptionEvent> computeEvents(final Collection<Entitlement> entitlements, List<BlockingState> allBlockingStates, final DateTimeZone accountTimeZone) {

        // Extract ids for all entitlement in the list
        final Set<UUID> allEntitlementUUIDs = new TreeSet(Collections2.transform(entitlements, new Function<Entitlement, UUID>() {
            @Override
            public UUID apply(final Entitlement input) {
                return input.getId();
            }
        }));

        // Compute base events across all entitlements
        final LinkedList<SubscriptionEvent> result = computeSubscriptionBaseEvents(entitlements, accountTimeZone);

        // Order allBlockingStates  events by effectiveDate, createdDate, uuid, service, serviceState
        Collections.sort(allBlockingStates, new Comparator<BlockingState>() {
            @Override
            public int compare(final BlockingState o1, final BlockingState o2) {
                final int effectivedComp = o1.getEffectiveDate().compareTo(o2.getEffectiveDate());
                if (effectivedComp != 0) {
                    return effectivedComp;
                }
                // For the same effectiveDate we want to first return events from ENTITLEMENT service first
                final int serviceNameComp = o1.getService().compareTo(o2.getService());
                if (serviceNameComp != 0) {
                    if (o1.getService().equals(DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME)) {
                        return -1;
                    } else if (o2.getService().equals(DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME)) {
                        return 1;
                    } else {
                        return serviceNameComp;
                    }
                }
                // Order by subscription just to get something deterministic
                final int uuidComp = o1.getBlockedId().compareTo(o2.getBlockedId());
                if (uuidComp != 0) {
                    return uuidComp;
                }
                // And then finally state
                final int serviceStateComp = o1.getStateName().compareTo(o2.getStateName());
                if (serviceStateComp != 0) {
                    return serviceStateComp;
                }
                final int createdDateComp = o1.getCreatedDate().compareTo(o2.getCreatedDate());
                if (createdDateComp != 0) {
                    return createdDateComp;
                }
                logger.warn(TIMELINE_WARN_LOG + "Detected two identical blockingStates events for blockableId = " + o1.getBlockedId() +
                            ", type = " + o1.getType() + ", ");
                // Underministic-- not sure that will ever happen. Once we are confident this never happens we should thrown IllegalException
                return 0;
            }
        });

        for (BlockingState bs : allBlockingStates) {

            final List<SubscriptionEvent> newEvents = new ArrayList<SubscriptionEvent>();
            int index = insertFromBlockingEvent(accountTimeZone, allEntitlementUUIDs, result, bs, bs.getEffectiveDate(), newEvents);
            insertAfterIndex(result, newEvents, index);
        }
        reOrderSubscriptionEventsOnSameDateByType(result);
        return result;
    }

    //
    // All events have been inserted and should be at the right place, except that we want to ensure that events for a given subscription,
    // and for a given time are ordered by SubscriptionEventType.
    //
    // All this seems a little over complicated, and one wonders why we don't just shove all events and call Collections.sort on the list prior
    // to return:
    // - One explanation is that we don't know the events in advance and each time the new events to be inserted are computed from the current state
    //   of the stream, which requires ordering all along
    // - A careful reader will notice that the algorithm is N^2, -- so that we care so much considering we have very events-- but in addition to that
    //   the recursive path will be used very infrequently and when it is used, this will be probably just reorder with the prev event and that's it.
    //
    @VisibleForTesting
    protected void reOrderSubscriptionEventsOnSameDateByType(final List<SubscriptionEvent> events) {
        final int size = events.size();
        for (int i = 0; i < size; i++) {
            final DefaultSubscriptionEvent cur = (DefaultSubscriptionEvent) events.get(i);
            final DefaultSubscriptionEvent next = (i < (size - 1)) ? (DefaultSubscriptionEvent) events.get(i + 1) : null;

            final boolean shouldSwap = (next != null && shouldSwap(cur, next, true));
            final boolean shouldReverseSort = (next == null || shouldSwap);

            int currentIndex = i;
            if (shouldSwap) {
                Collections.swap(events, i, i + 1);
            }
            if (shouldReverseSort) {
                while (currentIndex >= 1) {
                    final DefaultSubscriptionEvent revCur = (DefaultSubscriptionEvent) events.get(currentIndex);
                    final DefaultSubscriptionEvent other = (DefaultSubscriptionEvent) events.get(currentIndex - 1);
                    if (shouldSwap(revCur, other, false)) {
                        Collections.swap(events, currentIndex, currentIndex - 1);
                    }
                    if (revCur.getEffectiveDate().compareTo(other.getEffectiveDate()) != 0) {
                        break;
                    }
                    currentIndex--;
                }
            }
        }
    }

    private boolean shouldSwap(DefaultSubscriptionEvent cur, DefaultSubscriptionEvent other, boolean isAscending) {

        // For a given date, order by subscriptionId, and within subscription by event type
        final int idComp = cur.getEntitlementId().compareTo(other.getEntitlementId());
        return (cur.getEffectiveDate().compareTo(other.getEffectiveDate()) == 0 &&
                ((isAscending &&
                  ((idComp > 0) ||
                   (idComp == 0 && cur.getSubscriptionEventType().ordinal() > other.getSubscriptionEventType().ordinal()))) ||
                 (!isAscending &&
                  ((idComp < 0) ||
                   (idComp == 0 && cur.getSubscriptionEventType().ordinal() < other.getSubscriptionEventType().ordinal())))));
    }

    private void insertAfterIndex(final LinkedList<SubscriptionEvent> original, final List<SubscriptionEvent> newEvents, int index) {

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
    // Returns the index and the newEvents generated from the incoming blocking state event. Those new events will all created for the same effectiveDate and should be ordered but
    // reOrderSubscriptionEventsOnSameDateByType would reorder them anyway if this was not the case.
    //
    private int insertFromBlockingEvent(final DateTimeZone accountTimeZone, final Set<UUID> allEntitlementUUIDs, final List<SubscriptionEvent> result, final BlockingState bs, final DateTime bsEffectiveDate, final List<SubscriptionEvent> newEvents) {

        // Keep the current state per entitlement
        final Map<UUID, TargetState> targetStates = new HashMap<UUID, TargetState>();
        for (UUID cur : allEntitlementUUIDs) {
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
            DefaultSubscriptionEvent cur = (DefaultSubscriptionEvent) it.next();
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
        for (UUID target : targetEntitlementIds) {

            final SubscriptionEvent[] prevNext = findPrevNext(result, target, curInsertion);
            final TargetState curTargetState = targetStates.get(target);

            final List<SubscriptionEventType> eventTypes = curTargetState.addStateAndReturnEventTypes(bs);
            for (SubscriptionEventType t : eventTypes) {
                newEvents.add(toSubscriptionEvent(prevNext[0], prevNext[1], target, bs, t, accountTimeZone));
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
            result[1] = events.size() > 0 ? events.get(0) : null;
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
            if (tmp.getId().equals(insertionEvent.getId())) {
                foundCur = true;
            }
        }
        result[0] = prev;
        result[1] = next;
        return result;
    }

    // Compute the initial stream of events based on the subscription base events
    private LinkedList<SubscriptionEvent> computeSubscriptionBaseEvents(final Collection<Entitlement> entitlements, final DateTimeZone accountTimeZone) {
        final LinkedList<SubscriptionEvent> result = new LinkedList<SubscriptionEvent>();
        for (final Entitlement cur : entitlements) {
            final SubscriptionBase base = ((DefaultEntitlement) cur).getSubscriptionBase();
            final List<SubscriptionBaseTransition> baseTransitions = base.getAllTransitions();
            for (final SubscriptionBaseTransition tr : baseTransitions) {
                final List<SubscriptionEventType> eventTypes = toEventTypes(tr.getTransitionType());
                for (final SubscriptionEventType eventType : eventTypes) {
                    final SubscriptionEvent event = toSubscriptionEvent(tr, eventType, accountTimeZone);
                    insertSubscriptionEvent(event, result);
                }
            }
        }
        sanitizeForBaseRecreateEvents(result);
        return result;
    }

    //
    // Old version of code would use CANCEL/RE_CREATE to simulate PAUSE_BILLING/RESUME_BILLING
    // (Relies on the assumption that there is no blocking_state event matching that CACNEL event so:
    // 1. The STOP_BILLING (coming from the row CANCEL event) should be transformed into a PAUSE_BILLING
    // 2. We also add a PAUSE_ENTITLEMENT at the same time as the PAUSE_BILLING
    //
    private void sanitizeForBaseRecreateEvents(final LinkedList<SubscriptionEvent> input) {
        final Collection<UUID> guiltyEntitlementIds = new TreeSet<UUID>();
        final ListIterator<SubscriptionEvent> it = input.listIterator(input.size());
        while (it.hasPrevious()) {
            final SubscriptionEvent cur = it.previous();
            if (cur.getSubscriptionEventType() == SubscriptionEventType.RESUME_BILLING) {
                guiltyEntitlementIds.add(cur.getEntitlementId());
                continue;
            }
            if (cur.getSubscriptionEventType() == SubscriptionEventType.STOP_BILLING &&
                guiltyEntitlementIds.contains(cur.getEntitlementId())) {
                guiltyEntitlementIds.remove(cur.getEntitlementId());
                final SubscriptionEvent correctedBillingEvent = new DefaultSubscriptionEvent((DefaultSubscriptionEvent) cur, SubscriptionEventType.PAUSE_BILLING);
                it.set(correctedBillingEvent);

                // Old versions of the code won't have an associated event in blocking_states - we need to add one on the fly
                final SubscriptionEvent correctedEntitlementEvent = new DefaultSubscriptionEvent((DefaultSubscriptionEvent) cur, SubscriptionEventType.PAUSE_ENTITLEMENT);
                it.add(correctedEntitlementEvent);
            }
        }
    }

    private void insertSubscriptionEvent(final SubscriptionEvent event, final LinkedList<SubscriptionEvent> result) {
        int index = 0;
        for (SubscriptionEvent cur : result) {
            int compEffectiveDate = event.getEffectiveDate().compareTo(cur.getEffectiveDate());
            if (compEffectiveDate < 0) {
                // EffectiveDate is less than cur -> insert here
                break;
            } else if (compEffectiveDate == 0) {

                int compUUID = event.getEntitlementId().compareTo(cur.getEntitlementId());
                if (compUUID < 0) {
                    // Same EffectiveDate but subscription are different, no need top sort further just return something deterministic
                    break;
                } else if (compUUID == 0) {

                    int eventOrder = event.getSubscriptionEventType().ordinal() - cur.getSubscriptionEventType().ordinal();
                    if (eventOrder < 0) {
                        // Same EffectiveDate and same subscription, order by SubscriptionEventType;
                        break;
                    }

                    // Two identical event for the same subscription at the same time, this sounds like some data issue
                    if (eventOrder == 0) {
                        logger.warn(TIMELINE_WARN_LOG + "Detected identical events type = " + event.getSubscriptionEventType() + " ids = " +
                                    event.getId() + ", " + cur.getId() + " for subscription " + cur.getEntitlementId());
                        break;
                    }
                }
            }
            index++;
        }
        result.add(index, event);
    }

    private SubscriptionEvent toSubscriptionEvent(final SubscriptionEvent prev, final SubscriptionEvent next, final UUID entitlementId, final BlockingState in, final SubscriptionEventType eventType, final DateTimeZone accountTimeZone) {
        return new DefaultSubscriptionEvent(in.getId(),
                                            entitlementId,
                                            in.getEffectiveDate(),
                                            in.getCreatedDate(),
                                            eventType,
                                            in.isBlockEntitlement(),
                                            in.isBlockBilling(),
                                            in.getService(),
                                            in.getStateName(),
                                            // We look for the next for the 'prev' meaning we we are headed to, but if this is null -- for example on cancellation we get the prev which gives the correct state.
                                            prev != null ? (prev.getNextProduct() != null ? prev.getNextProduct() : prev.getPrevProduct()) : null,
                                            prev != null ? (prev.getNextPlan() != null ? prev.getNextPlan() : prev.getPrevPlan()) : null,
                                            prev != null ? (prev.getNextPhase() != null ? prev.getNextPhase() : prev.getPrevPhase()) : null,
                                            prev != null ? (prev.getNextPriceList() != null ? prev.getNextPriceList() : prev.getPrevPriceList()) : null,
                                            prev != null ? (prev.getNextBillingPeriod() != null ? prev.getNextBillingPeriod() : prev.getPrevBillingPeriod()) : null,
                                            next != null ? next.getPrevProduct() : null,
                                            next != null ? next.getPrevPlan() : null,
                                            next != null ? next.getPrevPhase() : null,
                                            next != null ? next.getPrevPriceList() : null,
                                            next != null ? next.getPrevBillingPeriod() : null,
                                            in.getCreatedDate(),
                                            accountTimeZone);
    }

    private SubscriptionEvent toSubscriptionEvent(final SubscriptionBaseTransition in, final SubscriptionEventType eventType, final DateTimeZone accountTimeZone) {
        return new DefaultSubscriptionEvent(in.getId(),
                                            in.getSubscriptionId(),
                                            in.getEffectiveTransitionTime(),
                                            in.getRequestedTransitionTime(),
                                            eventType,
                                            false,
                                            false,
                                            getServiceName(eventType),
                                            eventType.toString(),
                                            (in.getPreviousPlan() != null ? in.getPreviousPlan().getProduct() : null),
                                            in.getPreviousPlan(),
                                            in.getPreviousPhase(),
                                            in.getPreviousPriceList(),
                                            (in.getPreviousPlan() != null ? in.getPreviousPlan().getBillingPeriod() : null),
                                            (in.getNextPlan() != null ? in.getNextPlan().getProduct() : null),
                                            in.getNextPlan(),
                                            in.getNextPhase(),
                                            in.getNextPriceList(),
                                            (in.getNextPlan() != null ? in.getNextPlan().getBillingPeriod() : null),
                                            in.getCreatedDate(),
                                            accountTimeZone);
    }

    private static String getServiceName(final SubscriptionEventType type) {
        switch (type) {
            case START_BILLING:
            case PAUSE_BILLING:
            case RESUME_BILLING:
            case STOP_BILLING:
                return BILLING_SERVICE_NAME;

            case PHASE:
            case CHANGE:
                return ENT_BILLING_SERVICE_NAME;

            default:
                return DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME;
        }
    }

    private List<SubscriptionEventType> toEventTypes(final SubscriptionBaseTransitionType in) {
        switch (in) {
            case CREATE:
                return ImmutableList.<SubscriptionEventType>of(SubscriptionEventType.START_ENTITLEMENT, SubscriptionEventType.START_BILLING);
            case TRANSFER:
                return ImmutableList.<SubscriptionEventType>of(SubscriptionEventType.START_ENTITLEMENT, SubscriptionEventType.START_BILLING);
            case MIGRATE_ENTITLEMENT:
                return ImmutableList.<SubscriptionEventType>of(SubscriptionEventType.START_ENTITLEMENT);
            case MIGRATE_BILLING:
                return ImmutableList.<SubscriptionEventType>of(SubscriptionEventType.START_BILLING);
            case CHANGE:
                return ImmutableList.<SubscriptionEventType>of(SubscriptionEventType.CHANGE);
            case CANCEL:
                return ImmutableList.<SubscriptionEventType>of(SubscriptionEventType.STOP_BILLING);
            case PHASE:
                return ImmutableList.<SubscriptionEventType>of(SubscriptionEventType.PHASE);
            // This is the old way of pausing billing; not used any longer, but kept for compatibility reason. We return both RESUME_ENTITLEMENT and RESUME_BILLING
            // and will rely on the sanitizeForBaseRecreateEvents method to transform the STOP_BILLING (coming from CANCEL) into the correct events.
            //
            case RE_CREATE:
                return ImmutableList.<SubscriptionEventType>of(SubscriptionEventType.RESUME_ENTITLEMENT, SubscriptionEventType.RESUME_BILLING);
            /*
             * Those can be ignored:
             */
            // Marker event
            case UNCANCEL:
                // Junction billing events-- that info is part of blocking states, we will get outside of subscription base
            case START_BILLING_DISABLED:
            case END_BILLING_DISABLED:
            default:
                return ImmutableList.<SubscriptionEventType>of();
        }
    }

    @Override
    public UUID getAccountId() {
        return accountId;
    }

    @Override
    public UUID getBundleId() {
        return bundleId;
    }

    @Override
    public String getExternalKey() {
        return externalKey;
    }

    @Override
    public List<SubscriptionEvent> getSubscriptionEvents() {
        return events;
    }

    //
    // Internal class to keep the state associated with each subscription
    //
    private final static class TargetState {

        private boolean isEntitlementStarted;
        private boolean isEntitlementStopped;
        private boolean isBillingStarted;
        private boolean isBillingStopped;
        private Map<String, BlockingState> perServiceBlockingState;

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
            final BlockingState converted = new DefaultBlockingState(e.getEntitlementId(), BlockingStateType.SUBSCRIPTION,
                                                                     e.getServiceStateName(), e.getServiceName(), false, e.isBlockedEntitlement(), e.isBlockedBilling(),
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
            perServiceBlockingState.put(fixedBlockingState.getService(), fixedBlockingState);
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

            if (!shouldResumeEntitlement && !shouldBlockEntitlement && !shouldBlockEntitlement && !shouldBlockBilling && !fixedBlockingState.getService().equals(DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME)) {
                result.add(SubscriptionEventType.SERVICE_STATE_CHANGE);
            }
            return result;
        }

        private BlockingAggregator getState() {
            final DefaultBlockingAggregator aggrBefore = new DefaultBlockingAggregator();
            for (BlockingState cur : perServiceBlockingState.values()) {
                aggrBefore.or(cur);
            }
            return aggrBefore;
        }
    }

    protected static final class DefaultSubscriptionEvent implements SubscriptionEvent {

        private final UUID id;
        private final UUID entitlementId;
        private final DateTime effectiveDate;
        private final DateTime requestedDate;
        private final SubscriptionEventType eventType;
        private final boolean isBlockingEntitlement;
        private final boolean isBlockingBilling;
        private final String serviceName;
        private final String serviceStateName;
        private final Product prevProduct;
        private final Plan prevPlan;
        private final PlanPhase prevPlanPhase;
        private final PriceList prevPriceList;
        private final BillingPeriod prevBillingPeriod;
        private final Product nextProduct;
        private final Plan nextPlan;
        private final PlanPhase nextPlanPhase;
        private final PriceList nextPriceList;
        private final BillingPeriod nextBillingPeriod;
        private final DateTime createdDate;
        private final DateTimeZone accountTimeZone;

        public DefaultSubscriptionEvent(final UUID id,
                                        final UUID entitlementId,
                                        final DateTime effectiveDate,
                                        final DateTime requestedDate,
                                        final SubscriptionEventType eventType,
                                        final boolean blockingEntitlement,
                                        final boolean blockingBilling,
                                        final String serviceName,
                                        final String serviceStateName,
                                        final Product prevProduct,
                                        final Plan prevPlan,
                                        final PlanPhase prevPlanPhase,
                                        final PriceList prevPriceList,
                                        final BillingPeriod prevBillingPeriod,
                                        final Product nextProduct,
                                        final Plan nextPlan,
                                        final PlanPhase nextPlanPhase,
                                        final PriceList nextPriceList,
                                        final BillingPeriod nextBillingPeriod,
                                        final DateTime createDate,
                                        final DateTimeZone accountTimeZone) {
            this.id = id;
            this.entitlementId = entitlementId;
            this.effectiveDate = effectiveDate;
            this.requestedDate = requestedDate;
            this.eventType = eventType;
            this.isBlockingEntitlement = blockingEntitlement;
            this.isBlockingBilling = blockingBilling;
            this.serviceName = serviceName;
            this.serviceStateName = serviceStateName;
            this.prevProduct = prevProduct;
            this.prevPlan = prevPlan;
            this.prevPlanPhase = prevPlanPhase;
            this.prevPriceList = prevPriceList;
            this.prevBillingPeriod = prevBillingPeriod;
            this.nextProduct = nextProduct;
            this.nextPlan = nextPlan;
            this.nextPlanPhase = nextPlanPhase;
            this.nextPriceList = nextPriceList;
            this.nextBillingPeriod = nextBillingPeriod;
            this.createdDate = createDate;
            this.accountTimeZone = accountTimeZone;
        }

        private DefaultSubscriptionEvent(DefaultSubscriptionEvent copy, SubscriptionEventType newEventType) {
            this(copy.getId(),
                 copy.getEntitlementId(),
                 copy.getEffectiveDateTime(),
                 copy.getRequestedDateTime(),
                 newEventType,
                 copy.isBlockedEntitlement(),
                 copy.isBlockedBilling(),
                 copy.getServiceName(),
                 copy.getServiceStateName(),
                 copy.getPrevProduct(),
                 copy.getPrevPlan(),
                 copy.getPrevPhase(),
                 copy.getPrevPriceList(),
                 copy.getPrevBillingPeriod(),
                 copy.getNextProduct(),
                 copy.getNextPlan(),
                 copy.getNextPhase(),
                 copy.getNextPriceList(),
                 copy.getNextBillingPeriod(),
                 copy.getCreatedDate(),
                 copy.getAccountTimeZone());
        }

        public DateTimeZone getAccountTimeZone() {
            return accountTimeZone;
        }

        public DateTime getEffectiveDateTime() {
            return effectiveDate;
        }

        public DateTime getRequestedDateTime() {
            return requestedDate;
        }

        @Override
        public UUID getId() {
            return id;
        }

        @Override
        public UUID getEntitlementId() {
            return entitlementId;
        }

        @Override
        public LocalDate getEffectiveDate() {
            return effectiveDate != null ? new LocalDate(effectiveDate, accountTimeZone) : null;
        }

        @Override
        public LocalDate getRequestedDate() {
            return requestedDate != null ? new LocalDate(requestedDate, accountTimeZone) : null;
        }

        @Override
        public SubscriptionEventType getSubscriptionEventType() {
            return eventType;
        }

        @Override
        public boolean isBlockedBilling() {
            return isBlockingBilling;
        }

        @Override
        public boolean isBlockedEntitlement() {
            return isBlockingEntitlement;
        }

        @Override
        public String getServiceName() {
            return serviceName;
        }

        @Override
        public String getServiceStateName() {
            return serviceStateName;
        }

        @Override
        public Product getPrevProduct() {
            return prevProduct;
        }

        @Override
        public Plan getPrevPlan() {
            return prevPlan;
        }

        @Override
        public PlanPhase getPrevPhase() {
            return prevPlanPhase;
        }

        @Override
        public PriceList getPrevPriceList() {
            return prevPriceList;
        }

        @Override
        public BillingPeriod getPrevBillingPeriod() {
            return prevBillingPeriod;
        }

        @Override
        public Product getNextProduct() {
            return nextProduct;
        }

        @Override
        public Plan getNextPlan() {
            return nextPlan;
        }

        @Override
        public PlanPhase getNextPhase() {
            return nextPlanPhase;
        }

        @Override
        public PriceList getNextPriceList() {
            return nextPriceList;
        }

        @Override
        public BillingPeriod getNextBillingPeriod() {
            return nextBillingPeriod;
        }

        public DateTime getCreatedDate() {
            return createdDate;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            final DefaultSubscriptionEvent that = (DefaultSubscriptionEvent) o;

            if (isBlockingBilling != that.isBlockingBilling) {
                return false;
            }
            if (isBlockingEntitlement != that.isBlockingEntitlement) {
                return false;
            }
            if (createdDate != null ? !createdDate.equals(that.createdDate) : that.createdDate != null) {
                return false;
            }
            if (effectiveDate != null ? !effectiveDate.equals(that.effectiveDate) : that.effectiveDate != null) {
                return false;
            }
            if (entitlementId != null ? !entitlementId.equals(that.entitlementId) : that.entitlementId != null) {
                return false;
            }
            if (eventType != that.eventType) {
                return false;
            }
            if (id != null ? !id.equals(that.id) : that.id != null) {
                return false;
            }
            if (nextBillingPeriod != that.nextBillingPeriod) {
                return false;
            }
            if (nextPlan != null ? !nextPlan.equals(that.nextPlan) : that.nextPlan != null) {
                return false;
            }
            if (nextPlanPhase != null ? !nextPlanPhase.equals(that.nextPlanPhase) : that.nextPlanPhase != null) {
                return false;
            }
            if (nextPriceList != null ? !nextPriceList.equals(that.nextPriceList) : that.nextPriceList != null) {
                return false;
            }
            if (nextProduct != null ? !nextProduct.equals(that.nextProduct) : that.nextProduct != null) {
                return false;
            }
            if (prevBillingPeriod != that.prevBillingPeriod) {
                return false;
            }
            if (prevPlan != null ? !prevPlan.equals(that.prevPlan) : that.prevPlan != null) {
                return false;
            }
            if (prevPlanPhase != null ? !prevPlanPhase.equals(that.prevPlanPhase) : that.prevPlanPhase != null) {
                return false;
            }
            if (prevPriceList != null ? !prevPriceList.equals(that.prevPriceList) : that.prevPriceList != null) {
                return false;
            }
            if (prevProduct != null ? !prevProduct.equals(that.prevProduct) : that.prevProduct != null) {
                return false;
            }
            if (requestedDate != null ? !requestedDate.equals(that.requestedDate) : that.requestedDate != null) {
                return false;
            }
            if (serviceName != null ? !serviceName.equals(that.serviceName) : that.serviceName != null) {
                return false;
            }
            if (serviceStateName != null ? !serviceStateName.equals(that.serviceStateName) : that.serviceStateName != null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = id != null ? id.hashCode() : 0;
            result = 31 * result + (entitlementId != null ? entitlementId.hashCode() : 0);
            result = 31 * result + (effectiveDate != null ? effectiveDate.hashCode() : 0);
            result = 31 * result + (requestedDate != null ? requestedDate.hashCode() : 0);
            result = 31 * result + (eventType != null ? eventType.hashCode() : 0);
            result = 31 * result + (isBlockingEntitlement ? 1 : 0);
            result = 31 * result + (isBlockingBilling ? 1 : 0);
            result = 31 * result + (serviceName != null ? serviceName.hashCode() : 0);
            result = 31 * result + (serviceStateName != null ? serviceStateName.hashCode() : 0);
            result = 31 * result + (prevProduct != null ? prevProduct.hashCode() : 0);
            result = 31 * result + (prevPlan != null ? prevPlan.hashCode() : 0);
            result = 31 * result + (prevPlanPhase != null ? prevPlanPhase.hashCode() : 0);
            result = 31 * result + (prevPriceList != null ? prevPriceList.hashCode() : 0);
            result = 31 * result + (prevBillingPeriod != null ? prevBillingPeriod.hashCode() : 0);
            result = 31 * result + (nextProduct != null ? nextProduct.hashCode() : 0);
            result = 31 * result + (nextPlan != null ? nextPlan.hashCode() : 0);
            result = 31 * result + (nextPlanPhase != null ? nextPlanPhase.hashCode() : 0);
            result = 31 * result + (nextPriceList != null ? nextPriceList.hashCode() : 0);
            result = 31 * result + (nextBillingPeriod != null ? nextBillingPeriod.hashCode() : 0);
            result = 31 * result + (createdDate != null ? createdDate.hashCode() : 0);
            return result;
        }
    }
}
