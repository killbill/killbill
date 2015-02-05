/*
 * Copyright 2010-2013 Ning, Inc.
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
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
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
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;
import org.killbill.billing.subscription.api.user.SubscriptionBaseTransition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;

public class DefaultSubscriptionBundleTimeline implements SubscriptionBundleTimeline {

    private final Logger logger = LoggerFactory.getLogger(DefaultSubscriptionBundleTimeline.class);

    public static final String BILLING_SERVICE_NAME = "billing-service";
    public static final String ENT_BILLING_SERVICE_NAME = "entitlement+billing-service";

    private final List<SubscriptionEvent> events;
    private final UUID accountId;
    private final UUID bundleId;
    private final String externalKey;

    public DefaultSubscriptionBundleTimeline(final DateTimeZone accountTimeZone, final UUID accountId, final UUID bundleId, final String externalKey, final Collection<Entitlement> entitlements) {
        // Trust the incoming ordering here: blocking states were sorted using ProxyBlockingStateDao#sortedCopy
        final List<BlockingState> blockingStates = new LinkedList<BlockingState>();
        for (final Entitlement entitlement : entitlements) {
            blockingStates.addAll(((DefaultEntitlement) entitlement).getEventsStream().getBlockingStates());
        }
        this.accountId = accountId;
        this.bundleId = bundleId;
        this.externalKey = externalKey;
        this.events = computeEvents(entitlements, blockingStates, accountTimeZone);
    }

    @VisibleForTesting
    DefaultSubscriptionBundleTimeline(final DateTimeZone accountTimeZone, final UUID accountId, final UUID bundleId, final String externalKey, final Collection<Entitlement> entitlements, final List<BlockingState> allBlockingStates) {
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
    private List<SubscriptionEvent> computeEvents(final Collection<Entitlement> entitlements, final List<BlockingState> allBlockingStates, final DateTimeZone accountTimeZone) {
        // Extract ids for all entitlement in the list
        final Set<UUID> allEntitlementUUIDs = new TreeSet<UUID>(Collections2.transform(entitlements, new Function<Entitlement, UUID>() {
            @Override
            public UUID apply(final Entitlement input) {
                return input.getId();
            }
        }));

        // Compute base events across all entitlements
        final LinkedList<SubscriptionEvent> result = computeSubscriptionBaseEvents(entitlements, accountTimeZone);

        for (final BlockingState bs : allBlockingStates) {
            final List<SubscriptionEvent> newEvents = new ArrayList<SubscriptionEvent>();
            final int index = insertFromBlockingEvent(accountTimeZone, allEntitlementUUIDs, result, bs, bs.getEffectiveDate(), newEvents);
            insertAfterIndex(result, newEvents, index);
        }

        reOrderSubscriptionEventsOnSameDateByType(result);

        removeOverlappingSubscriptionEvents(result);

        return result;
    }

    // Make sure the argument supports the remove operation - hence expect a LinkedList, not a List
    private void removeOverlappingSubscriptionEvents(final LinkedList<SubscriptionEvent> events) {
        final Iterator<SubscriptionEvent> iterator = events.iterator();
        final Map<String, DefaultSubscriptionEvent> prevPerService = new HashMap<String, DefaultSubscriptionEvent>();
        while (iterator.hasNext()) {
            final DefaultSubscriptionEvent current = (DefaultSubscriptionEvent) iterator.next();
            final DefaultSubscriptionEvent prev = prevPerService.get(current.getServiceName());
            if (prev != null) {
                if (current.overlaps(prev)) {
                    iterator.remove();
                } else {
                    prevPerService.put(current.getServiceName(), current);
                }
            } else {
                prevPerService.put(current.getServiceName(), current);
            }
        }
    }

    //
    // All events have been inserted and should be at the right place, except that we want to ensure that events for a given subscription,
    // and for a given time are ordered by SubscriptionEventType.
    //
    // All this seems a little over complicated, and one wonders why we don't just shove all events and call Collections.sort on the list prior
    // to return:
    // - One explanation is that we don't know the events in advance and each time the new events to be inserted are computed from the current state
    //   of the stream, which requires ordering all along
    // - A careful reader will notice that the algorithm is N^2, -- so that we care so much considering we have very events -- but in addition to that
    //   the recursive path will be used very infrequently and when it is used, this will be probably just reorder with the prev event and that's it.
    //
    @VisibleForTesting
    protected void reOrderSubscriptionEventsOnSameDateByType(final List<SubscriptionEvent> events) {
        final int size = events.size();
        for (int i = 0; i < size; i++) {
            final SubscriptionEvent cur = events.get(i);
            final SubscriptionEvent next = (i < (size - 1)) ? events.get(i + 1) : null;

            final boolean shouldSwap = (next != null && shouldSwap(cur, next, true));
            final boolean shouldReverseSort = (next == null || shouldSwap);

            int currentIndex = i;
            if (shouldSwap) {
                Collections.swap(events, i, i + 1);
            }
            if (shouldReverseSort) {
                while (currentIndex >= 1) {
                    final SubscriptionEvent revCur = events.get(currentIndex);
                    final SubscriptionEvent other = events.get(currentIndex - 1);
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

    private Integer compareSubscriptionEventsForSameEffectiveDateAndEntitlementId(final SubscriptionEvent first, final SubscriptionEvent second) {
        // For consistency, make sure entitlement-service and billing-service events always happen in a
        // deterministic order (e.g. after other services for STOP events and before for START events)
        if ((DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME.equals(first.getServiceName()) ||
             DefaultSubscriptionBundleTimeline.BILLING_SERVICE_NAME.equals(first.getServiceName())) &&
            !(DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME.equals(second.getServiceName()) ||
              DefaultSubscriptionBundleTimeline.BILLING_SERVICE_NAME.equals(second.getServiceName()))) {
            // first is an entitlement-service or billing-service event, but not second
            if (first.getSubscriptionEventType().equals(SubscriptionEventType.START_ENTITLEMENT) ||
                first.getSubscriptionEventType().equals(SubscriptionEventType.START_BILLING) ||
                first.getSubscriptionEventType().equals(SubscriptionEventType.RESUME_ENTITLEMENT) ||
                first.getSubscriptionEventType().equals(SubscriptionEventType.RESUME_BILLING) ||
                first.getSubscriptionEventType().equals(SubscriptionEventType.PHASE) ||
                first.getSubscriptionEventType().equals(SubscriptionEventType.CHANGE)) {
                return -1;
            } else if (first.getSubscriptionEventType().equals(SubscriptionEventType.PAUSE_ENTITLEMENT) ||
                       first.getSubscriptionEventType().equals(SubscriptionEventType.PAUSE_BILLING) ||
                       first.getSubscriptionEventType().equals(SubscriptionEventType.STOP_ENTITLEMENT) ||
                       first.getSubscriptionEventType().equals(SubscriptionEventType.STOP_BILLING)) {
                return 1;
            } else {
                // Default behavior
                return -1;
            }
        } else if ((DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME.equals(second.getServiceName()) ||
                    DefaultSubscriptionBundleTimeline.BILLING_SERVICE_NAME.equals(second.getServiceName())) &&
                   !(DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME.equals(first.getServiceName()) ||
                     DefaultSubscriptionBundleTimeline.BILLING_SERVICE_NAME.equals(first.getServiceName()))) {
            // second is an entitlement-service or billing-service event, but not first
            if (second.getSubscriptionEventType().equals(SubscriptionEventType.START_ENTITLEMENT) ||
                second.getSubscriptionEventType().equals(SubscriptionEventType.START_BILLING) ||
                second.getSubscriptionEventType().equals(SubscriptionEventType.RESUME_ENTITLEMENT) ||
                second.getSubscriptionEventType().equals(SubscriptionEventType.RESUME_BILLING) ||
                second.getSubscriptionEventType().equals(SubscriptionEventType.PHASE) ||
                second.getSubscriptionEventType().equals(SubscriptionEventType.CHANGE)) {
                return 1;
            } else if (second.getSubscriptionEventType().equals(SubscriptionEventType.PAUSE_ENTITLEMENT) ||
                       second.getSubscriptionEventType().equals(SubscriptionEventType.PAUSE_BILLING) ||
                       second.getSubscriptionEventType().equals(SubscriptionEventType.STOP_ENTITLEMENT) ||
                       second.getSubscriptionEventType().equals(SubscriptionEventType.STOP_BILLING)) {
                return -1;
            } else {
                // Default behavior
                return 1;
            }
        } else if (first.getSubscriptionEventType().equals(SubscriptionEventType.START_ENTITLEMENT)) {
            // START_ENTITLEMENT is always first
            return -1;
        } else if (second.getSubscriptionEventType().equals(SubscriptionEventType.START_ENTITLEMENT)) {
            // START_ENTITLEMENT is always first
            return 1;
        } else if (first.getSubscriptionEventType().equals(SubscriptionEventType.STOP_BILLING)) {
            // STOP_BILLING is always last
            return 1;
        } else if (second.getSubscriptionEventType().equals(SubscriptionEventType.STOP_BILLING)) {
            // STOP_BILLING is always last
            return -1;
        } else if (first.getSubscriptionEventType().equals(SubscriptionEventType.START_BILLING)) {
            // START_BILLING is first after START_ENTITLEMENT
            return -1;
        } else if (second.getSubscriptionEventType().equals(SubscriptionEventType.START_BILLING)) {
            // START_BILLING is first after START_ENTITLEMENT
            return 1;
        } else if (first.getSubscriptionEventType().equals(SubscriptionEventType.STOP_ENTITLEMENT)) {
            // STOP_ENTITLEMENT is last after STOP_BILLING
            return 1;
        } else if (second.getSubscriptionEventType().equals(SubscriptionEventType.STOP_ENTITLEMENT)) {
            // STOP_ENTITLEMENT is last after STOP_BILLING
            return -1;
        } else {
            // Trust the current ordering
            return null;
        }
    }

    private boolean shouldSwap(final SubscriptionEvent cur, final SubscriptionEvent other, final boolean isAscending) {
        // For a given date, order by subscriptionId, and within subscription by event type
        final int idComp = cur.getEntitlementId().compareTo(other.getEntitlementId());
        final Integer comparison = compareSubscriptionEventsForSameEffectiveDateAndEntitlementId(cur, other);
        return (cur.getEffectiveDate().compareTo(other.getEffectiveDate()) == 0 &&
                ((isAscending &&
                  ((idComp > 0) ||
                   (idComp == 0 && comparison != null && comparison > 0))) ||
                 (!isAscending &&
                  ((idComp < 0) ||
                   (idComp == 0 && comparison != null && comparison < 0)))));
    }

    private void insertAfterIndex(final LinkedList<SubscriptionEvent> original, final List<SubscriptionEvent> newEvents, final int index) {
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

        return result;
    }

    private void insertSubscriptionEvent(final SubscriptionEvent event, final List<SubscriptionEvent> result) {
        int index = 0;
        for (final SubscriptionEvent cur : result) {
            final int compEffectiveDate = event.getEffectiveDate().compareTo(cur.getEffectiveDate());
            if (compEffectiveDate < 0) {
                // EffectiveDate is less than cur -> insert here
                break;
            } else if (compEffectiveDate == 0) {
                final int compUUID = event.getEntitlementId().compareTo(cur.getEntitlementId());
                if (compUUID < 0) {
                    // Same EffectiveDate but subscription are different, no need top sort further just return something deterministic
                    break;
                } else if (compUUID == 0) {
                    final int eventOrder = event.getSubscriptionEventType().ordinal() - cur.getSubscriptionEventType().ordinal();
                    if (eventOrder < 0) {
                        // Same EffectiveDate and same subscription, order by SubscriptionEventType;
                        break;
                    }

                    // Two identical events for the same subscription in the same day, trust createdDate
                    if (eventOrder == 0) {
                        final int compCreatedDate = (((DefaultSubscriptionEvent) event).getCreatedDate()).compareTo(((DefaultSubscriptionEvent) cur).getCreatedDate());
                        if (compCreatedDate <= 0) {
                            break;
                        }
                    }
                }
            }
            index++;
        }
        result.add(index, event);
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

    private static String getRealServiceNameForEntitlementOrExternalServiceName(final String originalServiceName, final SubscriptionEventType eventType) {
        final String serviceName;
        if (DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME.equals(originalServiceName)) {
            serviceName = getServiceName(eventType);
        } else {
            serviceName = originalServiceName;
        }
        return serviceName;
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
                                            (in.getPreviousPlan() != null ? in.getPreviousPlan().getRecurringBillingPeriod() : null),
                                            (in.getNextPlan() != null ? in.getNextPlan().getProduct() : null),
                                            in.getNextPlan(),
                                            in.getNextPhase(),
                                            in.getNextPriceList(),
                                            (in.getNextPlan() != null ? in.getNextPlan().getRecurringBillingPeriod() : null),
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
    private static final class TargetState {

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
                perServiceBlockingState.put(DefaultSubscriptionBundleTimeline.BILLING_SERVICE_NAME, fixedBlockingState);
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
