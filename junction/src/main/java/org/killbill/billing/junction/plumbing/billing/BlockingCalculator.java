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

package org.killbill.billing.junction.plumbing.billing;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Catalog;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.entitlement.api.BlockingState;
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.killbill.billing.junction.BillingEvent;
import org.killbill.billing.junction.BlockingInternalApi;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.inject.Inject;

public class BlockingCalculator {

    private static final AtomicLong globaltotalOrder = new AtomicLong();

    private final BlockingInternalApi blockingApi;

    @Inject
    public BlockingCalculator(final BlockingInternalApi blockingApi) {
        this.blockingApi = blockingApi;
    }

    /**
     * Given a set of billing events, add corresponding blocking (overdue) billing events.
     *
     * @param billingEvents the original list of billing events to update (without overdue events)
     */
    public boolean insertBlockingEvents(final SortedSet<BillingEvent> billingEvents, final Set<UUID> skippedSubscriptions, final Catalog catalog, final InternalTenantContext context) throws CatalogApiException {
        if (billingEvents.size() <= 0) {
            return false;
        }

        final Hashtable<UUID, List<SubscriptionBase>> bundleMap = createBundleSubscriptionMap(billingEvents);

        final SortedSet<BillingEvent> billingEventsToAdd = new TreeSet<BillingEvent>();
        final SortedSet<BillingEvent> billingEventsToRemove = new TreeSet<BillingEvent>();

        final List<BlockingState> blockingEvents = blockingApi.getBlockingAllForAccount(catalog, context);

        final Iterable<BlockingState> accountBlockingEvents = Iterables.filter(blockingEvents, new Predicate<BlockingState>() {
            @Override
            public boolean apply(final BlockingState input) {
                return BlockingStateType.ACCOUNT == input.getType();
            }
        });

        final Map<UUID, List<BlockingState>> perBundleBlockingEvents = getPerTypeBlockingEvents(BlockingStateType.SUBSCRIPTION_BUNDLE, blockingEvents);
        final Map<UUID, List<BlockingState>> perSubscriptionBlockingEvents = getPerTypeBlockingEvents(BlockingStateType.SUBSCRIPTION, blockingEvents);

        for (final UUID bundleId : bundleMap.keySet()) {

            final List<BlockingState> bundleBlockingEvents = perBundleBlockingEvents.get(bundleId) != null ? perBundleBlockingEvents.get(bundleId) : ImmutableList.<BlockingState>of();

            for (final SubscriptionBase subscription : bundleMap.get(bundleId)) {
                // Avoid inserting additional events for subscriptions that don't even have a START event
                if (skippedSubscriptions.contains(subscription.getId())) {
                    continue;
                }

                final List<BlockingState> subscriptionBlockingEvents = perSubscriptionBlockingEvents.get(subscription.getId()) != null ? perSubscriptionBlockingEvents.get(subscription.getId()) : ImmutableList.<BlockingState>of();
                final List<BlockingState> aggregateSubscriptionBlockingEvents = getAggregateBlockingEventsPerSubscription(subscription.getEndDate(), subscriptionBlockingEvents, bundleBlockingEvents, accountBlockingEvents);
                final List<DisabledDuration> accountBlockingDurations = createBlockingDurations(aggregateSubscriptionBlockingEvents);

                final SortedSet<BillingEvent> subscriptionBillingEvents = filter(billingEvents, subscription);

                final SortedSet<BillingEvent> newEvents = createNewEvents(accountBlockingDurations, subscriptionBillingEvents, catalog, context);
                billingEventsToAdd.addAll(newEvents);

                final SortedSet<BillingEvent> removedEvents = eventsToRemove(accountBlockingDurations, subscriptionBillingEvents);
                billingEventsToRemove.addAll(removedEvents);
            }
        }

        for (final BillingEvent eventToAdd : billingEventsToAdd) {
            billingEvents.add(eventToAdd);
        }

        for (final BillingEvent eventToRemove : billingEventsToRemove) {
            billingEvents.remove(eventToRemove);
        }

        return !(billingEventsToAdd.isEmpty() && billingEventsToRemove.isEmpty());
    }

    final List<BlockingState> getAggregateBlockingEventsPerSubscription(@Nullable final DateTime subscriptionEndDate, final Iterable<BlockingState> subscriptionBlockingEvents, final Iterable<BlockingState> bundleBlockingEvents, final Iterable<BlockingState> accountBlockingEvents) {
        final Iterable<BlockingState> tmp = Iterables.concat(subscriptionBlockingEvents, bundleBlockingEvents, accountBlockingEvents);
        final Iterable<BlockingState> allEventsPriorToCancelDate = Iterables.filter(tmp,
                                                                                    new Predicate<BlockingState>() {
                                                                                        @Override
                                                                                        public boolean apply(final BlockingState input) {
                                                                                            return subscriptionEndDate == null || input.getEffectiveDate().compareTo(subscriptionEndDate) <= 0;
                                                                                        }
                                                                                    });
        final List<BlockingState> result = Lists.newArrayList(allEventsPriorToCancelDate);
        Collections.sort(result);
        return result;
    }

    final Map<UUID, List<BlockingState>> getPerTypeBlockingEvents(final BlockingStateType type, final List<BlockingState> blockingEvents) {
        final Iterable<BlockingState> bundleBlockingEvents = Iterables.filter(blockingEvents, new Predicate<BlockingState>() {
            @Override
            public boolean apply(final BlockingState input) {
                return type == input.getType();
            }
        });

        final Map<UUID, List<BlockingState>> perTypeBlockingEvents = new HashMap<UUID, List<BlockingState>>();
        for (final BlockingState cur : bundleBlockingEvents) {
            if (!perTypeBlockingEvents.containsKey(cur.getBlockedId())) {
                perTypeBlockingEvents.put(cur.getBlockedId(), new ArrayList<BlockingState>());
            }
            perTypeBlockingEvents.get(cur.getBlockedId()).add(cur);
        }
        return perTypeBlockingEvents;
    }

    protected SortedSet<BillingEvent> eventsToRemove(final List<DisabledDuration> disabledDuration,
                                                     final SortedSet<BillingEvent> subscriptionBillingEvents) {
        final SortedSet<BillingEvent> result = new TreeSet<BillingEvent>();

        for (final DisabledDuration duration : disabledDuration) {
            for (final BillingEvent event : subscriptionBillingEvents) {
                if (duration.getEnd() == null || event.getEffectiveDate().isBefore(duration.getEnd())) {
                    if (!event.getEffectiveDate().isBefore(duration.getStart())) {
                        result.add(event);
                    }
                } else { //after the last event of the pair no need to keep checking
                    break;
                }
            }
        }
        return result;
    }

    protected SortedSet<BillingEvent> createNewEvents(final List<DisabledDuration> disabledDuration, final SortedSet<BillingEvent> subscriptionBillingEvents, final Catalog catalog, final InternalTenantContext context) throws CatalogApiException {

        Preconditions.checkState(context.getAccountRecordId() != null);

        final SortedSet<BillingEvent> result = new TreeSet<BillingEvent>();

        for (final DisabledDuration duration : disabledDuration) {
            // The first one before the blocked duration
            final BillingEvent precedingInitialEvent = precedingBillingEventForSubscription(duration.getStart(), subscriptionBillingEvents);
            // The last one during of before the duration
            final BillingEvent precedingFinalEvent = precedingBillingEventForSubscription(duration.getEnd(), subscriptionBillingEvents);

            if (precedingInitialEvent != null) { // there is a preceding billing event
                result.add(createNewDisableEvent(duration.getStart(), precedingInitialEvent, catalog));
                if (duration.getEnd() != null) { // no second event in the pair means they are still disabled (no re-enable)
                    result.add(createNewReenableEvent(duration.getEnd(), precedingFinalEvent, catalog, context));
                }
            } else if (precedingFinalEvent != null) { // can happen - e.g. phase event
                result.add(createNewReenableEvent(duration.getEnd(), precedingFinalEvent, catalog, context));
            }
            // N.B. if there's no precedingInitial and no precedingFinal then there's nothing to do
        }
        return result;
    }


    protected BillingEvent precedingBillingEventForSubscription(final DateTime disabledDurationStart, final SortedSet<BillingEvent> subscriptionBillingEvents) {
        if (disabledDurationStart == null) {
            return null;
        }

        // We look for the first billingEvent strictly prior our disabledDurationStart or null if none
        BillingEvent prev = null;
        for (final BillingEvent event : subscriptionBillingEvents) {
            if (!event.getEffectiveDate().isBefore(disabledDurationStart)) {
                return prev;
            } else {
                prev = event;
            }
        }
        return prev;
    }


    protected SortedSet<BillingEvent> filter(final SortedSet<BillingEvent> billingEvents, final SubscriptionBase subscription) {
        final SortedSet<BillingEvent> result = new TreeSet<BillingEvent>();
        for (final BillingEvent event : billingEvents) {
            if (event.getSubscription().getId().equals(subscription.getId())) {
                result.add(event);
            }
        }
        return result;
    }

    protected BillingEvent createNewDisableEvent(final DateTime disabledDurationStart, final BillingEvent previousEvent, final Catalog catalog) throws CatalogApiException {

        final int billCycleDay = previousEvent.getBillCycleDayLocal();
        final SubscriptionBase subscription = previousEvent.getSubscription();
        final DateTime effectiveDate = disabledDurationStart;
        final PlanPhase planPhase = previousEvent.getPlanPhase();
        final Plan plan = previousEvent.getPlan();

        // Make sure to set the fixed price to null and the billing period to NO_BILLING_PERIOD,
        // which makes invoice disregard this event
        final BigDecimal fixedPrice = null;
        final BillingPeriod billingPeriod = BillingPeriod.NO_BILLING_PERIOD;

        final Currency currency = previousEvent.getCurrency();
        final String description = "";
        final SubscriptionBaseTransitionType type = SubscriptionBaseTransitionType.START_BILLING_DISABLED;
        final Long totalOrdering = globaltotalOrder.getAndIncrement();

        return new DefaultBillingEvent(subscription, effectiveDate, true, plan, planPhase, fixedPrice,
                                       currency,
                                       billingPeriod, billCycleDay,
                                       description, totalOrdering, type, catalog, true);
    }

    protected BillingEvent createNewReenableEvent(final DateTime odEventTime, final BillingEvent previousEvent, final Catalog catalog, final InternalTenantContext context) throws CatalogApiException {
        // All fields are populated with the event state from before the blocking period, for invoice to resume invoicing
        final int billCycleDay = previousEvent.getBillCycleDayLocal();
        final SubscriptionBase subscription = previousEvent.getSubscription();
        final DateTime effectiveDate = odEventTime;
        final PlanPhase planPhase = previousEvent.getPlanPhase();
        final BigDecimal fixedPrice = previousEvent.getFixedPrice();
        final Plan plan = previousEvent.getPlan();
        final Currency currency = previousEvent.getCurrency();
        final String description = "";
        final BillingPeriod billingPeriod = previousEvent.getBillingPeriod();
        final SubscriptionBaseTransitionType type = SubscriptionBaseTransitionType.END_BILLING_DISABLED;
        final Long totalOrdering = globaltotalOrder.getAndIncrement();

        return new DefaultBillingEvent(subscription, effectiveDate, true, plan, planPhase, fixedPrice,
                                       currency,
                                       billingPeriod, billCycleDay,
                                       description, totalOrdering, type, catalog, false);
    }

    protected Hashtable<UUID, List<SubscriptionBase>> createBundleSubscriptionMap(final SortedSet<BillingEvent> billingEvents) {
        final Hashtable<UUID, List<SubscriptionBase>> result = new Hashtable<UUID, List<SubscriptionBase>>();
        for (final BillingEvent event : billingEvents) {
            final UUID bundleId = event.getSubscription().getBundleId();
            List<SubscriptionBase> subs = result.get(bundleId);
            if (subs == null) {
                subs = new ArrayList<SubscriptionBase>();
                result.put(bundleId, subs);
            }
            if (!result.get(bundleId).contains(event.getSubscription())) {
                subs.add(event.getSubscription());
            }
        }
        return result;
    }

    // In ascending order
    protected List<DisabledDuration> createBlockingDurations(final Iterable<BlockingState> inputBundleEvents) {

        final List<DisabledDuration> result = new ArrayList<DisabledDuration>();

        final Set<String> services = ImmutableSet.copyOf(Iterables.transform(inputBundleEvents, new Function<BlockingState, String>() {
            @Override
            public String apply(final BlockingState input) {
                return input.getService();
            }
        }));

        final Map<String, BlockingStateService> svcBlockedMap = new HashMap<String, BlockingStateService>();
        for (String svc : services) {
            svcBlockedMap.put(svc, new BlockingStateService());
        }

        for (final BlockingState e : inputBundleEvents) {
            svcBlockedMap.get(e.getService()).addBlockingState(e);
        }

        final Iterable<DisabledDuration> unorderedDisabledDuration = Iterables.concat(Iterables.transform(svcBlockedMap.values(), new Function<BlockingStateService, List<DisabledDuration>>() {
            @Override
            public List<DisabledDuration> apply(final BlockingStateService input) {
                return input.build();
            }
        }));

        final List<DisabledDuration> sortedDisabledDuration = Ordering.natural().sortedCopy(unorderedDisabledDuration);

        DisabledDuration prevDuration = null;
        for (DisabledDuration d : sortedDisabledDuration) {
            // isDisjoint
            if (prevDuration == null) {
                prevDuration = d;
            } else {
                if (prevDuration.isDisjoint(d)) {
                    result.add(prevDuration);
                    prevDuration = d;
                } else {
                    prevDuration = DisabledDuration.mergeDuration(prevDuration, d);
                }
            }
        }
        if (prevDuration != null) {
            result.add(prevDuration);
        }

        return result;
    }

    @VisibleForTesting
    static AtomicLong getGlobalTotalOrder() {
        return globaltotalOrder;
    }
}
