/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2020 Groupon, Inc
 * Copyright 2020-2021 Equinix, Inc
 * Copyright 2014-2021 The Billing Project, LLC
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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.Usage;
import org.killbill.billing.catalog.api.VersionedCatalog;
import org.killbill.billing.entitlement.api.BlockingState;
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.killbill.billing.junction.BillingEvent;
import org.killbill.billing.junction.BlockingInternalApi;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;
import com.google.inject.Inject;

public class BlockingCalculator {

    private static final AtomicLong globaltotalOrder = new AtomicLong();

    private final BlockingInternalApi blockingApi;

    @Inject
    public BlockingCalculator(final BlockingInternalApi blockingApi) {
        this.blockingApi = blockingApi;
    }

    @VisibleForTesting
    static AtomicLong getGlobalTotalOrder() {
        return globaltotalOrder;
    }

    /**
     * Given a set of billing events, add corresponding blocking (overdue) billing events.
     *
     * @param billingEvents the original list of billing events to update (without overdue events)
     * @param cutoffDt an optional cutoffDt to filter out billing events
     */
    public boolean insertBlockingEvents(final SortedSet<BillingEvent> billingEvents,
                                        final Set<UUID> skippedSubscriptions,
                                        final Map<UUID, List<SubscriptionBase>> subscriptionsForAccount,
                                        final VersionedCatalog catalog,
                                        @Nullable final LocalDate cutoffDt,
                                        final InternalTenantContext context) throws CatalogApiException {
        if (billingEvents.size() <= 0) {
            return false;
        }

        final Collection<BillingEvent> billingEventsToAdd = new TreeSet<BillingEvent>();
        final Collection<BillingEvent> billingEventsToRemove = new TreeSet<BillingEvent>();

        final List<BlockingState> blockingEvents = blockingApi.getBlockingActiveForAccount(catalog, cutoffDt, context);

        // Group blocking states per type
        final Collection<BlockingState> accountBlockingEvents = new LinkedList<BlockingState>();
        final Map<UUID, List<BlockingState>> perBundleBlockingEvents = new HashMap<UUID, List<BlockingState>>();
        final Map<UUID, List<BlockingState>> perSubscriptionBlockingEvents = new HashMap<UUID, List<BlockingState>>();
        for (final BlockingState blockingEvent : blockingEvents) {
            if (blockingEvent.getType() == BlockingStateType.ACCOUNT) {
                accountBlockingEvents.add(blockingEvent);
            } else if (blockingEvent.getType() == BlockingStateType.SUBSCRIPTION_BUNDLE) {
                perBundleBlockingEvents.putIfAbsent(blockingEvent.getBlockedId(), new LinkedList<BlockingState>());
                perBundleBlockingEvents.get(blockingEvent.getBlockedId()).add(blockingEvent);
            } else if (blockingEvent.getType() == BlockingStateType.SUBSCRIPTION) {
                perSubscriptionBlockingEvents.putIfAbsent(blockingEvent.getBlockedId(), new LinkedList<BlockingState>());
                perSubscriptionBlockingEvents.get(blockingEvent.getBlockedId()).add(blockingEvent);
            }
        }

        // Group billing events per subscriptionId
        final Map<UUID, SortedSet<BillingEvent>> perSubscriptionBillingEvents = new HashMap<UUID, SortedSet<BillingEvent>>();
        for (final BillingEvent event : billingEvents) {
            if (!perSubscriptionBillingEvents.containsKey(event.getSubscriptionId())) {
                perSubscriptionBillingEvents.put(event.getSubscriptionId(), new TreeSet<BillingEvent>());
            }
            perSubscriptionBillingEvents.get(event.getSubscriptionId()).add(event);
        }

        for (final Entry<UUID, List<SubscriptionBase>> entry : subscriptionsForAccount.entrySet()) {
            final UUID bundleId = entry.getKey();

            final List<BlockingState> bundleBlockingEvents = perBundleBlockingEvents.get(bundleId) != null ? perBundleBlockingEvents.get(bundleId) : ImmutableList.<BlockingState>of();

            for (final SubscriptionBase subscription : entry.getValue()) {
                // Avoid inserting additional events for subscriptions that don't even have a START event
                if (skippedSubscriptions.contains(subscription.getId())) {
                    continue;
                }

                final List<BlockingState> subscriptionBlockingEvents = perSubscriptionBlockingEvents.get(subscription.getId()) != null ? perSubscriptionBlockingEvents.get(subscription.getId()) : ImmutableList.<BlockingState>of();
                final List<BlockingState> aggregateSubscriptionBlockingEvents = getAggregateBlockingEventsPerSubscription(subscription.getEndDate(), subscriptionBlockingEvents, bundleBlockingEvents, accountBlockingEvents);
                final List<DisabledDuration> accountBlockingDurations = createBlockingDurations(aggregateSubscriptionBlockingEvents);

                final SortedSet<BillingEvent> subscriptionBillingEvents = perSubscriptionBillingEvents.getOrDefault(subscription.getId(), ImmutableSortedSet.<BillingEvent>of());

                final SortedSet<BillingEvent> newEvents = createNewEvents(accountBlockingDurations, subscriptionBillingEvents, context);
                billingEventsToAdd.addAll(newEvents);

                final SortedSet<BillingEvent> removedEvents = eventsToRemove(accountBlockingDurations, subscriptionBillingEvents);
                billingEventsToRemove.addAll(removedEvents);
            }
        }

        billingEvents.addAll(billingEventsToAdd);

        for (final BillingEvent eventToRemove : billingEventsToRemove) {
            billingEvents.remove(eventToRemove);
        }

        return !(billingEventsToAdd.isEmpty() && billingEventsToRemove.isEmpty());
    }

    final List<BlockingState> getAggregateBlockingEventsPerSubscription(@Nullable final DateTime subscriptionEndDate,
                                                                        final Iterable<BlockingState> subscriptionBlockingEvents,
                                                                        final Iterable<BlockingState> bundleBlockingEvents,
                                                                        final Iterable<BlockingState> accountBlockingEvents) {
        final List<BlockingState> result = new LinkedList<BlockingState>();
        for (final BlockingState bs : Iterables.concat(subscriptionBlockingEvents, bundleBlockingEvents, accountBlockingEvents)) {
            if (subscriptionEndDate == null || bs.getEffectiveDate().compareTo(subscriptionEndDate) <= 0) {
                // Event is prior to cancel date
                result.add(bs);
            }
        }
        Collections.sort(result);
        return result;
    }

    protected SortedSet<BillingEvent> eventsToRemove(final Iterable<DisabledDuration> disabledDuration,
                                                     final Iterable<BillingEvent> subscriptionBillingEvents) {
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

    protected SortedSet<BillingEvent> createNewEvents(final Iterable<DisabledDuration> disabledDuration,
                                                      final Iterable<BillingEvent> subscriptionBillingEvents,
                                                      final InternalTenantContext context) throws CatalogApiException {
        Preconditions.checkState(context.getAccountRecordId() != null);

        final SortedSet<BillingEvent> result = new TreeSet<BillingEvent>();

        for (final DisabledDuration duration : disabledDuration) {
            // The first one before the blocked duration
            final BillingEvent precedingInitialEvent = precedingBillingEventForSubscription(duration.getStart(), subscriptionBillingEvents);
            // The last one during of before the duration
            final BillingEvent precedingFinalEvent = precedingBillingEventForSubscription(duration.getEnd(), subscriptionBillingEvents);

            if (precedingInitialEvent != null) { // there is a preceding billing event
                result.add(createNewDisableEvent(duration.getStart(), precedingInitialEvent));
                if (duration.getEnd() != null) { // no second event in the pair means they are still disabled (no re-enable)
                    result.add(createNewReenableEvent(duration.getEnd(), precedingFinalEvent));
                }
            } else if (precedingFinalEvent != null) { // can happen - e.g. phase event
                result.add(createNewReenableEvent(duration.getEnd(), precedingFinalEvent));
            }
            // N.B. if there's no precedingInitial and no precedingFinal then there's nothing to do
        }
        return result;
    }

    protected BillingEvent precedingBillingEventForSubscription(final DateTime disabledDurationStart,
                                                                final Iterable<BillingEvent> subscriptionBillingEvents) {
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

    protected BillingEvent createNewDisableEvent(final DateTime disabledDurationStart,
                                                 final BillingEvent previousEvent) {
        final int billCycleDay = previousEvent.getBillCycleDayLocal();
        final DateTime effectiveDate = disabledDurationStart;
        final PlanPhase planPhase = previousEvent.getPlanPhase();
        final Plan plan = previousEvent.getPlan();

        // Make sure to set the fixed price to null and the billing period to NO_BILLING_PERIOD,
        // which makes invoice disregard this event
        final BigDecimal fixedPrice = null;
        final BigDecimal recurringPrice = null;
        final BillingPeriod billingPeriod = BillingPeriod.NO_BILLING_PERIOD;

        final Currency currency = previousEvent.getCurrency();
        final String description = "";
        final SubscriptionBaseTransitionType type = SubscriptionBaseTransitionType.START_BILLING_DISABLED;
        final Long totalOrdering = globaltotalOrder.getAndIncrement();

        return new DefaultBillingEvent(previousEvent.getSubscriptionId(),
                                       previousEvent.getBundleId(),
                                       effectiveDate,
                                       plan,
                                       planPhase,
                                       fixedPrice,
                                       recurringPrice,
                                       ImmutableList.of(),
                                       currency,
                                       billingPeriod,
                                       billCycleDay,
                                       description,
                                       totalOrdering,
                                       type,
                                       true
        );
    }

    protected BillingEvent createNewReenableEvent(final DateTime odEventTime,
                                                  final BillingEvent previousEvent) throws CatalogApiException {
        // All fields are populated with the event state from before the blocking period, for invoice to resume invoicing
        final int billCycleDay = previousEvent.getBillCycleDayLocal();
        final DateTime effectiveDate = odEventTime;
        final PlanPhase planPhase = previousEvent.getPlanPhase();
        final BigDecimal fixedPrice = previousEvent.getFixedPrice();
        final BigDecimal recurringPrice = previousEvent.getRecurringPrice();
        final List<Usage> usages = previousEvent.getUsages();
        final Plan plan = previousEvent.getPlan();
        final Currency currency = previousEvent.getCurrency();
        final String description = "";
        final BillingPeriod billingPeriod = previousEvent.getBillingPeriod();
        final SubscriptionBaseTransitionType type = SubscriptionBaseTransitionType.END_BILLING_DISABLED;
        final Long totalOrdering = globaltotalOrder.getAndIncrement();

        return new DefaultBillingEvent(previousEvent.getSubscriptionId(),
                                       previousEvent.getBundleId(),
                                       effectiveDate,
                                       plan,
                                       planPhase,
                                       fixedPrice,
                                       recurringPrice,
                                       usages,
                                       currency,
                                       billingPeriod,
                                       billCycleDay,
                                       description,
                                       totalOrdering,
                                       type,
                                       false
        );
    }

    // In ascending order
    protected List<DisabledDuration> createBlockingDurations(final Iterable<BlockingState> inputBundleEvents) {
        final List<DisabledDuration> result = new LinkedList<DisabledDuration>();

        final Map<String, BlockingStateService> svcBlockedMap = new HashMap<String, BlockingStateService>();
        for (final BlockingState bs : inputBundleEvents) {
            final String service = bs.getService();
            svcBlockedMap.putIfAbsent(service, new BlockingStateService());
            svcBlockedMap.get(service).addBlockingState(bs);
        }

        final Collection<DisabledDuration> unorderedDisabledDuration = new LinkedList<DisabledDuration>();
        for (final Entry<String, BlockingStateService> entry : svcBlockedMap.entrySet()) {
            unorderedDisabledDuration.addAll(entry.getValue().build());
        }
        final List<DisabledDuration> sortedDisabledDuration = Ordering.natural().sortedCopy(unorderedDisabledDuration);

        DisabledDuration prevDuration = null;
        for (final DisabledDuration d : sortedDisabledDuration) {
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
}
