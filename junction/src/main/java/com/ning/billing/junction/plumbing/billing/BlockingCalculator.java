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

package com.ning.billing.junction.plumbing.billing;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import com.ning.billing.account.api.Account;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.subscription.api.SubscriptionBaseTransitionType;
import com.ning.billing.subscription.api.SubscriptionBase;
import com.ning.billing.entitlement.api.BlockingState;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.svcapi.junction.BillingEvent;
import com.ning.billing.util.svcapi.junction.BillingModeType;
import com.ning.billing.util.svcapi.junction.BlockingInternalApi;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;

public class BlockingCalculator {

    private static final AtomicLong globaltotalOrder = new AtomicLong();

    private final BlockingInternalApi blockingApi;

    protected static class DisabledDuration {
        private final DateTime start;
        private final DateTime end;

        public DisabledDuration(final DateTime start, final DateTime end) {
            this.start = start;
            this.end = end;
        }

        public DateTime getStart() {
            return start;
        }

        public DateTime getEnd() {
            return end;
        }

    }

    @Inject
    public BlockingCalculator(final BlockingInternalApi blockingApi) {
        this.blockingApi = blockingApi;
    }

    /**
     * Given a set of billing events, add corresponding blocking (overdue) billing events.
     *
     * @param billingEvents the original list of billing events to update (without overdue events)
     */
    public void insertBlockingEvents(final SortedSet<BillingEvent> billingEvents, final InternalTenantContext context) {
        if (billingEvents.size() <= 0) {
            return;
        }

        final Account account = billingEvents.first().getAccount();

        final Hashtable<UUID, List<SubscriptionBase>> bundleMap = createBundleSubscriptionMap(billingEvents);

        final SortedSet<BillingEvent> billingEventsToAdd = new TreeSet<BillingEvent>();
        final SortedSet<BillingEvent> billingEventsToRemove = new TreeSet<BillingEvent>();

        for (final UUID bundleId : bundleMap.keySet()) {
            final List<BlockingState> blockingEvents = blockingApi.getBlockingHistory(bundleId, context);
            blockingEvents.addAll(blockingApi.getBlockingHistory(account.getId(), context));
            final List<DisabledDuration> blockingDurations = createBlockingDurations(blockingEvents);

            for (final SubscriptionBase subscription : bundleMap.get(bundleId)) {
                billingEventsToAdd.addAll(createNewEvents(blockingDurations, billingEvents, account, subscription));
                billingEventsToRemove.addAll(eventsToRemove(blockingDurations, billingEvents, subscription));
            }
        }

        for (final BillingEvent eventToAdd : billingEventsToAdd) {
            billingEvents.add(eventToAdd);
        }

        for (final BillingEvent eventToRemove : billingEventsToRemove) {
            billingEvents.remove(eventToRemove);
        }
    }

    protected SortedSet<BillingEvent> eventsToRemove(final List<DisabledDuration> disabledDuration,
                                                     final SortedSet<BillingEvent> billingEvents, final SubscriptionBase subscription) {
        final SortedSet<BillingEvent> result = new TreeSet<BillingEvent>();

        final SortedSet<BillingEvent> filteredBillingEvents = filter(billingEvents, subscription);
        for (final DisabledDuration duration : disabledDuration) {
            for (final BillingEvent event : filteredBillingEvents) {
                if (duration.getEnd() == null || event.getEffectiveDate().isBefore(duration.getEnd())) {
                    if (event.getEffectiveDate().isAfter(duration.getStart())) { //between the pair
                        result.add(event);
                    }
                } else { //after the last event of the pair no need to keep checking
                    break;
                }
            }
        }
        return result;
    }

    protected SortedSet<BillingEvent> createNewEvents(final List<DisabledDuration> disabledDuration, final SortedSet<BillingEvent> billingEvents, final Account account, final SubscriptionBase subscription) {
        final SortedSet<BillingEvent> result = new TreeSet<BillingEvent>();
        for (final DisabledDuration duration : disabledDuration) {
            // The first one before the blocked duration
            final BillingEvent precedingInitialEvent = precedingBillingEventForSubscription(duration.getStart(), billingEvents, subscription);
            // The last one during of before the duration
            final BillingEvent precedingFinalEvent = precedingBillingEventForSubscription(duration.getEnd(), billingEvents, subscription);

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

    protected BillingEvent precedingBillingEventForSubscription(final DateTime datetime, final SortedSet<BillingEvent> billingEvents, final SubscriptionBase subscription) {
        if (datetime == null) { //second of a pair can be null if there's no re-enabling
            return null;
        }

        final SortedSet<BillingEvent> filteredBillingEvents = filter(billingEvents, subscription);
        BillingEvent result = filteredBillingEvents.first();

        if (datetime.isBefore(result.getEffectiveDate())) {
            //This case can happen, for example, if we have an add on and the bundle goes into disabled before the add on is created
            return null;
        }

        for (final BillingEvent event : filteredBillingEvents) {
            if (!event.getEffectiveDate().isBefore(datetime)) { // found it its the previous event
                return result;
            } else { // still looking
                result = event;
            }
        }
        return result;
    }

    protected SortedSet<BillingEvent> filter(final SortedSet<BillingEvent> billingEvents, final SubscriptionBase subscription) {
        final SortedSet<BillingEvent> result = new TreeSet<BillingEvent>();
        for (final BillingEvent event : billingEvents) {
            if (event.getSubscription() == subscription) {
                result.add(event);
            }
        }
        return result;
    }

    protected BillingEvent createNewDisableEvent(final DateTime odEventTime, final BillingEvent previousEvent) {
        final Account account = previousEvent.getAccount();
        final int billCycleDay = previousEvent.getBillCycleDayLocal();
        final SubscriptionBase subscription = previousEvent.getSubscription();
        final DateTime effectiveDate = odEventTime;
        final PlanPhase planPhase = previousEvent.getPlanPhase();
        final Plan plan = previousEvent.getPlan();

        // Make sure to set the fixed price to null and the billing period to NO_BILLING_PERIOD,
        // which makes invoice disregard this event
        final BigDecimal fixedPrice = null;
        final BigDecimal recurringPrice = null;
        final BillingPeriod billingPeriod = BillingPeriod.NO_BILLING_PERIOD;

        final Currency currency = previousEvent.getCurrency();
        final String description = "";
        final BillingModeType billingModeType = previousEvent.getBillingMode();
        final SubscriptionBaseTransitionType type = SubscriptionBaseTransitionType.START_BILLING_DISABLED;
        final Long totalOrdering = globaltotalOrder.getAndIncrement();
        final DateTimeZone tz = previousEvent.getTimeZone();

        return new DefaultBillingEvent(account, subscription, effectiveDate, plan, planPhase,
                                       fixedPrice, recurringPrice, currency,
                                       billingPeriod, billCycleDay, billingModeType,
                                       description, totalOrdering, type, tz);
    }

    protected BillingEvent createNewReenableEvent(final DateTime odEventTime, final BillingEvent previousEvent) {
        // All fields are populated with the event state from before the blocking period, for invoice to resume invoicing
        final Account account = previousEvent.getAccount();
        final int billCycleDay = previousEvent.getBillCycleDayLocal();
        final SubscriptionBase subscription = previousEvent.getSubscription();
        final DateTime effectiveDate = odEventTime;
        final PlanPhase planPhase = previousEvent.getPlanPhase();
        final Plan plan = previousEvent.getPlan();
        final BigDecimal fixedPrice = previousEvent.getFixedPrice();
        final BigDecimal recurringPrice = previousEvent.getRecurringPrice();
        final Currency currency = previousEvent.getCurrency();
        final String description = "";
        final BillingModeType billingModeType = previousEvent.getBillingMode();
        final BillingPeriod billingPeriod = previousEvent.getBillingPeriod();
        final SubscriptionBaseTransitionType type = SubscriptionBaseTransitionType.END_BILLING_DISABLED;
        final Long totalOrdering = globaltotalOrder.getAndIncrement();
        final DateTimeZone tz = previousEvent.getTimeZone();

        return new DefaultBillingEvent(account, subscription, effectiveDate, plan, planPhase,
                                       fixedPrice, recurringPrice, currency,
                                       billingPeriod, billCycleDay, billingModeType,
                                       description, totalOrdering, type, tz);
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
    protected List<DisabledDuration> createBlockingDurations(final List<BlockingState> overdueBundleEvents) {
        final List<DisabledDuration> result = new ArrayList<BlockingCalculator.DisabledDuration>();
        // Earliest blocking event
        BlockingState first = null;

        for (final BlockingState e : overdueBundleEvents) {
            if (e.isBlockBilling() && first == null) {
                // First blocking event of contiguous series of blocking events
                first = e;
            } else if (first != null && !e.isBlockBilling()) {
                // End of the interval
                result.add(new DisabledDuration(first.getTimestamp(), e.getTimestamp()));
                first = null;
            }
        }

        if (first != null) { // found a transition to disabled with no terminating event
            result.add(new DisabledDuration(first.getTimestamp(), null));
        }

        return result;
    }

    @VisibleForTesting
    static AtomicLong getGlobalTotalOrder() {
        return globaltotalOrder;
    }
}
