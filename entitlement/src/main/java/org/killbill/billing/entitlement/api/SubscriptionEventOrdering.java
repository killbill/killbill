/*
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;
import org.killbill.billing.subscription.api.user.SubscriptionBaseTransition;
import org.killbill.commons.utils.Preconditions;
import org.killbill.commons.utils.annotation.VisibleForTesting;

//
// Compute all events based on blocking states events and base subscription events
// Note that:
// - base subscription events are already ordered for each Entitlement and so when we reorder at the bundle level we try not to break that initial ordering
// - blocking state events occur at various level (account, bundle and subscription) so for higher level, we need to dispatch that on each subscription.
//
public class SubscriptionEventOrdering extends EntitlementOrderingBase {

    private static final SubscriptionEventOrdering INSTANCE = new SubscriptionEventOrdering();

    private SubscriptionEventOrdering() {}

    public static List<SubscriptionEvent> sortedCopy(final Entitlement entitlement, final InternalTenantContext internalTenantContext) {
        return sortedCopy(List.of(entitlement), internalTenantContext);
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
        removeOverlappingSubscriptionEvents(result);

        return result;
    }

    // Compute the initial stream of events based on the subscription base events
    private LinkedList<SubscriptionEvent> computeSubscriptionBaseEvents(final Iterable<Entitlement> entitlements, final InternalTenantContext internalTenantContext) {
        final LinkedList<SubscriptionEvent> result = new LinkedList<SubscriptionEvent>();
        for (final Entitlement cur : entitlements) {
            Preconditions.checkState(cur instanceof DefaultEntitlement, "Entitlement %s is not a DefaultEntitlement", cur);
            final SubscriptionBase base = ((DefaultEntitlement) cur).getSubscriptionBase();
            final List<SubscriptionBaseTransition> baseTransitions = base.getAllTransitions(base.getIncludeDeletedEvents());
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
            case TRANSFER:
                return List.of(SubscriptionEventType.START_BILLING);
            case CHANGE:
                return List.of(SubscriptionEventType.CHANGE);
            case CANCEL:
            case EXPIRED:
                return List.of(SubscriptionEventType.STOP_BILLING);
            case PHASE:
                return List.of(SubscriptionEventType.PHASE);
            /*
             * Those can be ignored:
             */
            // Marker event
            case UNCANCEL:
                // Junction billing events-- that info is part of blocking states, we will get outside of subscription base
            case START_BILLING_DISABLED:
            case END_BILLING_DISABLED:
            default:
                return Collections.emptyList();
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

    @VisibleForTesting
    static SubscriptionEvent toSubscriptionEvent(final SubscriptionBaseTransition in, final SubscriptionEventType eventType, final InternalTenantContext internalTenantContext) {
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
}
