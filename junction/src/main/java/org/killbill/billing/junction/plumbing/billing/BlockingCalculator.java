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

package org.killbill.billing.junction.plumbing.billing;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import org.killbill.billing.account.api.Account;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.BillingMode;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.entitlement.api.BlockingState;
import org.killbill.billing.junction.BillingEvent;
import org.killbill.billing.junction.BlockingInternalApi;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;

public class BlockingCalculator {

    private static final AtomicLong globaltotalOrder = new AtomicLong();

    private final BlockingInternalApi blockingApi;

    protected static class DisabledDuration {

        private final DateTime start;
        private DateTime end;

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

        public void setEnd(final DateTime end) {
            this.end = end;
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

        final List<BlockingState> blockingEvents = blockingApi.getBlockingAllForAccount(context);
        final List<DisabledDuration> blockingDurations = createBlockingDurations(blockingEvents);
        for (final UUID bundleId : bundleMap.keySet()) {
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
        final BillingMode billingMode = previousEvent.getBillingMode();
        final SubscriptionBaseTransitionType type = SubscriptionBaseTransitionType.START_BILLING_DISABLED;
        final Long totalOrdering = globaltotalOrder.getAndIncrement();
        final DateTimeZone tz = previousEvent.getTimeZone();

        return new DefaultBillingEvent(account, subscription, effectiveDate, plan, planPhase,
                                       fixedPrice, recurringPrice, currency,
                                       billingPeriod, billCycleDay, billingMode,
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
        final BillingMode billingMode  = previousEvent.getBillingMode();
        final BillingPeriod billingPeriod = previousEvent.getBillingPeriod();
        final SubscriptionBaseTransitionType type = SubscriptionBaseTransitionType.END_BILLING_DISABLED;
        final Long totalOrdering = globaltotalOrder.getAndIncrement();
        final DateTimeZone tz = previousEvent.getTimeZone();

        return new DefaultBillingEvent(account, subscription, effectiveDate, plan, planPhase,
                                       fixedPrice, recurringPrice, currency,
                                       billingPeriod, billCycleDay, billingMode,
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
    protected List<DisabledDuration> createBlockingDurations(final Iterable<BlockingState> overdueBundleEvents) {
        final List<DisabledDuration> result = new ArrayList<BlockingCalculator.DisabledDuration>();
        // Earliest blocking event
        BlockingState first = null;

        int blockedNesting = 0;
        BlockingState lastOne = null;
        for (final BlockingState e : overdueBundleEvents) {
            lastOne = e;
            if (e.isBlockBilling() && blockedNesting == 0) {
                // First blocking event of contiguous series of blocking events
                first = e;
                blockedNesting++;
            } else if (e.isBlockBilling() && blockedNesting > 0) {
                // Nest blocking states
                blockedNesting++;
            } else if (!e.isBlockBilling() && blockedNesting > 0) {
                blockedNesting--;
                if (blockedNesting == 0) {
                    // End of the interval
                    addDisabledDuration(result, first, e);
                    first = null;
                }
            }
        }

        if (first != null) { // found a transition to disabled with no terminating event
            addDisabledDuration(result, first, lastOne.isBlockBilling() ? null : lastOne);
        }

        return result;
    }

    private void addDisabledDuration(final List<DisabledDuration> result, final BlockingState firstBlocking, @Nullable final BlockingState firstNonBlocking) {
        final DisabledDuration lastOne;
        if (!result.isEmpty()) {
            lastOne = result.get(result.size() - 1);
        } else {
            lastOne = null;
        }

        final DateTime endDate = firstNonBlocking == null ? null : firstNonBlocking.getEffectiveDate();
        if (lastOne != null && lastOne.getEnd().compareTo(firstBlocking.getEffectiveDate()) == 0) {
            lastOne.setEnd(endDate);
        } else {
            result.add(new DisabledDuration(firstBlocking.getEffectiveDate(), endDate));
        }
    }

    @VisibleForTesting
    static AtomicLong getGlobalTotalOrder() {
        return globaltotalOrder;
    }
}
