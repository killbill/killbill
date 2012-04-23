/*
 * Copyright 2010-2011 Ning, Inc.
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

import org.joda.time.DateTime;

import com.google.inject.Inject;
import com.ning.billing.account.api.Account;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.entitlement.api.billing.BillingEvent;
import com.ning.billing.entitlement.api.billing.BillingModeType;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionEventTransition.SubscriptionTransitionType;
import com.ning.billing.junction.api.Blockable;
import com.ning.billing.junction.api.BlockingApi;
import com.ning.billing.junction.api.BlockingState;
import com.ning.billing.junction.api.blocking.DefaultBlockingState;

public class BlockingCalculator {
    private final BlockingApi blockingApi;

    protected static class DisabledDuration {
        private final DateTime start;
        private final DateTime end;

        public DisabledDuration(DateTime start,DateTime end) {
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

    protected static class MergeEvent extends DefaultBlockingState {

        public MergeEvent(DateTime timestamp) {
            super(null,null,null,null,false,false,false,timestamp);
        }

    }

    @Inject
    public BlockingCalculator(BlockingApi blockingApi) {
        this.blockingApi = blockingApi;
    }

    public void insertBlockingEvents(SortedSet<BillingEvent> billingEvents) {
        if(billingEvents.size() <= 0) { return; }

        Account account = billingEvents.first().getAccount();
 
        Hashtable<UUID,List<Subscription>> bundleMap = createBundleSubscriptionMap(billingEvents);

        SortedSet<BillingEvent> billingEventsToAdd = new TreeSet<BillingEvent>();
        SortedSet<BillingEvent> billingEventsToRemove = new TreeSet<BillingEvent>();

        for(UUID bundleId : bundleMap.keySet()) {
            SortedSet<BlockingState> blockingEvents = blockingApi.getBlockingHistory(bundleId, Blockable.Type.SUBSCRIPTION_BUNDLE);
            blockingEvents.addAll(blockingApi.getBlockingHistory(account.getId(), Blockable.Type.ACCOUNT));
            List<DisabledDuration>  blockingDurations  = createBlockingDurations(blockingEvents); 

            for (Subscription subscription: bundleMap.get(bundleId)) {
                billingEventsToAdd.addAll(createNewEvents( blockingDurations, billingEvents, account, subscription));
                billingEventsToRemove.addAll(eventsToRemove(blockingDurations, billingEvents, subscription));
            }
        }

        for(BillingEvent eventToAdd: billingEventsToAdd ) {
            billingEvents.add(eventToAdd);
        }

        for(BillingEvent eventToRemove : billingEventsToRemove) {
            billingEvents.remove(eventToRemove);
        }

    }

    protected SortedSet<BillingEvent> eventsToRemove(List<DisabledDuration> disabledDuration,
            SortedSet<BillingEvent> billingEvents, Subscription subscription) {
        SortedSet<BillingEvent> result = new TreeSet<BillingEvent>();

        SortedSet<BillingEvent> filteredBillingEvents = filter(billingEvents, subscription);
        for(DisabledDuration duration : disabledDuration) {
            for(BillingEvent event : filteredBillingEvents) {
                if(duration.getEnd() == null || event.getEffectiveDate().isBefore(duration.getEnd())) {
                    if( event.getEffectiveDate().isAfter(duration.getStart()) ) { //between the pair
                        result.add(event);
                    }
                } else { //after the last event of the pair no need to keep checking
                    break;
                }
            }
        }
        return result;
    }

     protected SortedSet<BillingEvent> createNewEvents( List<DisabledDuration> disabledDuration, SortedSet<BillingEvent> billingEvents, Account account, Subscription subscription) {
        SortedSet<BillingEvent> result = new TreeSet<BillingEvent>();
        for(DisabledDuration duration : disabledDuration) {
            BillingEvent precedingInitialEvent = precedingBillingEventForSubscription(duration.getStart(), billingEvents, subscription);
            BillingEvent precedingFinalEvent = precedingBillingEventForSubscription(duration.getEnd(), billingEvents, subscription);

            if(precedingInitialEvent != null) { // there is a preceding billing event
                result.add(createNewDisableEvent(duration.getStart(), precedingInitialEvent));
                if(duration.getEnd() != null) { // no second event in the pair means they are still disabled (no re-enable)
                    result.add(createNewReenableEvent(duration.getEnd(), precedingFinalEvent));
                }

            } else if(precedingFinalEvent != null) { // can happen - e.g. phase event
                //
                // TODO: check with Jeff that this is going to do something sensible
                //
                result.add(createNewReenableEvent(duration.getEnd(), precedingFinalEvent));

            } 

            // N.B. if there's no precedingInitial and no precedingFinal then there's nothing to do
        }
        return result;
    }

    protected BillingEvent precedingBillingEventForSubscription(DateTime datetime, SortedSet<BillingEvent> billingEvents, Subscription subscription) { 
        if(datetime == null) { //second of a pair can be null if there's no re-enabling
            return null;
        }

        SortedSet<BillingEvent> filteredBillingEvents = filter(billingEvents, subscription);
        BillingEvent result = filteredBillingEvents.first();

        if(datetime.isBefore(result.getEffectiveDate())) {
            //This case can happen, for example, if we have an add on and the bundle goes into disabled before the add on is created
            return null;
        }

        for(BillingEvent event : filteredBillingEvents) {
            if(event.getEffectiveDate().isAfter(datetime)) { // found it its the previous event
                return result;
            } else { // still looking
                result = event;
            }
        }
        return result;
    }

    protected SortedSet<BillingEvent> filter(SortedSet<BillingEvent> billingEvents, Subscription subscription) {
        SortedSet<BillingEvent> result = new TreeSet<BillingEvent>();
        for(BillingEvent event : billingEvents) {
            if(event.getSubscription() == subscription) {
                result.add(event);
            }
        }
        return result;
    }

    protected BillingEvent createNewDisableEvent(DateTime odEventTime, BillingEvent previousEvent) {
        final Account account = previousEvent.getAccount();
        final int billCycleDay = previousEvent.getBillCycleDay();
        final Subscription subscription = previousEvent.getSubscription();
        final DateTime effectiveDate = odEventTime;
        final PlanPhase planPhase = previousEvent.getPlanPhase();
        final Plan plan = previousEvent.getPlan();
        final BigDecimal fixedPrice = BigDecimal.ZERO;
        final BigDecimal recurringPrice = BigDecimal.ZERO;
        final Currency currency = previousEvent.getCurrency();
        final String description = "";
        final BillingModeType billingModeType = previousEvent.getBillingMode();
        final BillingPeriod billingPeriod = previousEvent.getBillingPeriod();
        final SubscriptionTransitionType type = SubscriptionTransitionType.CANCEL;
        final Long totalOrdering = 0L; //TODO

        return new DefaultBillingEvent(account, subscription, effectiveDate, plan, planPhase,
                fixedPrice, recurringPrice, currency,
                billingPeriod, billCycleDay, billingModeType,
                description, totalOrdering, type);
    }

    protected BillingEvent createNewReenableEvent(DateTime odEventTime, BillingEvent previousEvent) {
        final Account account = previousEvent.getAccount();
        final int billCycleDay = previousEvent.getBillCycleDay();
        final Subscription subscription = previousEvent.getSubscription();
        final DateTime effectiveDate = odEventTime;
        final PlanPhase planPhase = previousEvent.getPlanPhase();
        final Plan plan = previousEvent.getPlan();
        final BigDecimal fixedPrice = previousEvent.getFixedPrice();
        final BigDecimal recurringPrice = previousEvent.getRecurringPrice();
        final Currency currency = previousEvent.getCurrency();
        final String description = "";
        final BillingModeType billingModeType = previousEvent.getBillingMode();
        final BillingPeriod billingPeriod = previousEvent.getBillingPeriod();
        final SubscriptionTransitionType type = SubscriptionTransitionType.RE_CREATE;
        final Long totalOrdering = 0L; //TODO

        return new DefaultBillingEvent(account, subscription, effectiveDate, plan, planPhase,
                fixedPrice, recurringPrice, currency,
                billingPeriod, billCycleDay, billingModeType,
                description, totalOrdering, type);
    }

    protected Hashtable<UUID,List<Subscription>> createBundleSubscriptionMap(SortedSet<BillingEvent> billingEvents) {
        Hashtable<UUID,List<Subscription>> result = new Hashtable<UUID,List<Subscription>>();
        for(BillingEvent event : billingEvents) {
            UUID bundleId = event.getSubscription().getBundleId();
            List<Subscription> subs = result.get(bundleId);
            if(subs == null) {
                subs = new ArrayList<Subscription>();
                result.put(bundleId,subs);
            }
            if(!result.contains(event.getSubscription())) {
                subs.add(event.getSubscription());        
            }
        }
        return result;
    }



    protected List<DisabledDuration> createBlockingDurations(SortedSet<BlockingState> overdueBundleEvents) {
        List<DisabledDuration> result = new ArrayList<BlockingCalculator.DisabledDuration>();
        BlockingState first = null;

        for(BlockingState e : overdueBundleEvents) {
            if(e.isBlockBilling() && first == null) { // found a transition to disabled
                first = e;
            } else if(first != null && !e.isBlockBilling()) { // found a transition from disabled
                result.add(new DisabledDuration(first.getTimestamp(), e.getTimestamp()));
                first = null;
            }
        }

        if(first != null) { // found a transition to disabled with no terminating event
            result.add(new DisabledDuration(first.getTimestamp(), null));
        }

        return result;
    }

}
