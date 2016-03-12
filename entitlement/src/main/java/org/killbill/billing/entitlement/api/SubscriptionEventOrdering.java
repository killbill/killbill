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

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.entitlement.DefaultEntitlementService;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;
import org.killbill.billing.subscription.api.user.SubscriptionBaseTransition;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

//
// Compute all events based on blocking states events and base subscription events
// Note that:
// - base subscription events are already ordered for each Entitlement and so when we reorder at the bundle level we try not to break that initial ordering
// - blocking state events occur at various level (account, bundle and subscription) so for higher level, we need to dispatch that on each subscription.
//
public class SubscriptionEventOrdering extends EntitlementOrderingBase {

    @VisibleForTesting
    static final SubscriptionEventOrdering INSTANCE = new SubscriptionEventOrdering();

    private SubscriptionEventOrdering() {}

    public static List<SubscriptionEvent> sortedCopy(final Entitlement entitlement, final InternalTenantContext internalTenantContext) {
        return sortedCopy(ImmutableList.<Entitlement>of(entitlement), internalTenantContext);
    }

    public static List<SubscriptionEvent> sortedCopy(final Iterable<Entitlement> entitlements, final InternalTenantContext internalTenantContext) {
        return INSTANCE.computeEvents(entitlements, internalTenantContext);
    }

    private List<SubscriptionEvent> computeEvents(final Iterable<Entitlement> entitlements, final InternalTenantContext internalTenantContext) {
        // Compute base events across all entitlements (already ordered per entitlement)
        final LinkedList<SubscriptionEvent> result = computeSubscriptionBaseEvents(entitlements, internalTenantContext);

        // Add blocking states at the right place
        BlockingStateOrdering.insertSorted(entitlements, internalTenantContext, result);

        // Final cleanups
        reOrderSubscriptionEventsOnSameDateByType(result);
        removeOverlappingSubscriptionEvents(result);

        return result;
    }

    // Compute the initial stream of events based on the subscription base events
    private LinkedList<SubscriptionEvent> computeSubscriptionBaseEvents(final Iterable<Entitlement> entitlements, final InternalTenantContext internalTenantContext) {
        final LinkedList<SubscriptionEvent> result = new LinkedList<SubscriptionEvent>();
        for (final Entitlement cur : entitlements) {
            Preconditions.checkState(cur instanceof DefaultEntitlement, "Entitlement %s is not a DefaultEntitlement", cur);
            final SubscriptionBase base = ((DefaultEntitlement) cur).getSubscriptionBase();
            final List<SubscriptionBaseTransition> baseTransitions = base.getAllTransitions();
            for (final SubscriptionBaseTransition tr : baseTransitions) {
                final List<SubscriptionEventType> eventTypes = toEventTypes(tr.getTransitionType());
                for (final SubscriptionEventType eventType : eventTypes) {
                    final SubscriptionEvent event = toSubscriptionEvent(tr, eventType, internalTenantContext);
                    insertSubscriptionEvent(event, result);
                }
            }
        }

        return result;
    }

    private List<SubscriptionEventType> toEventTypes(final SubscriptionBaseTransitionType in) {
        switch (in) {
            case CREATE:
                return ImmutableList.<SubscriptionEventType>of(SubscriptionEventType.START_BILLING);
            case TRANSFER:
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

    private SubscriptionEvent toSubscriptionEvent(final SubscriptionBaseTransition in, final SubscriptionEventType eventType, final InternalTenantContext internalTenantContext) {
        return new DefaultSubscriptionEvent(in.getId(),
                                            in.getSubscriptionId(),
                                            in.getEffectiveTransitionTime(),
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
                                            internalTenantContext);
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

    private Integer compareSubscriptionEventsForSameEffectiveDateAndEntitlementId(final SubscriptionEvent first, final SubscriptionEvent second) {
        // For consistency, make sure entitlement-service and billing-service events always happen in a
        // deterministic order (e.g. after other services for STOP events and before for START events)
        if ((DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME.equals(first.getServiceName()) ||
             BILLING_SERVICE_NAME.equals(first.getServiceName())) &&
            !(DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME.equals(second.getServiceName()) ||
              BILLING_SERVICE_NAME.equals(second.getServiceName()))) {
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
                    BILLING_SERVICE_NAME.equals(second.getServiceName())) &&
                   !(DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME.equals(first.getServiceName()) ||
                     BILLING_SERVICE_NAME.equals(first.getServiceName()))) {
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
}
