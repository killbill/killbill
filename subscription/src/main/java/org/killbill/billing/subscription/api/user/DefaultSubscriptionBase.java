/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

package org.killbill.billing.subscription.api.user;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingAlignment;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Catalog;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.PlanPhasePriceOverride;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.PriceList;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementSourceType;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.entity.EntityBase;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseApiService;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;
import org.killbill.billing.subscription.api.user.SubscriptionBaseTransitionDataIterator.Order;
import org.killbill.billing.subscription.api.user.SubscriptionBaseTransitionDataIterator.TimeLimit;
import org.killbill.billing.subscription.api.user.SubscriptionBaseTransitionDataIterator.Visibility;
import org.killbill.billing.subscription.events.SubscriptionBaseEvent;
import org.killbill.billing.subscription.events.SubscriptionBaseEvent.EventType;
import org.killbill.billing.subscription.events.bcd.BCDEvent;
import org.killbill.billing.subscription.events.phase.PhaseEvent;
import org.killbill.billing.subscription.events.user.ApiEvent;
import org.killbill.billing.subscription.events.user.ApiEventType;
import org.killbill.billing.subscription.exceptions.SubscriptionBaseError;
import org.killbill.billing.util.bcd.BillCycleDayCalculator;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.clock.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

public class DefaultSubscriptionBase extends EntityBase implements SubscriptionBase {

    private static final Logger log = LoggerFactory.getLogger(DefaultSubscriptionBase.class);

    private final Clock clock;
    private final SubscriptionBaseApiService apiService;

    //
    // Final subscription fields
    //
    private final UUID bundleId;
    private final String bundleExternalKey;
    private final DateTime alignStartDate;
    private final DateTime bundleStartDate;
    private final ProductCategory category;
    private final boolean migrated;

    //
    // Those can be modified through non User APIs, and a new SubscriptionBase
    // object would be created
    //
    private final DateTime chargedThroughDate;

    //
    // User APIs (create, change, cancelWithRequestedDate,...) will recompute those each time,
    // so the user holding that subscription object get the correct state when
    // the call completes
    //
    private LinkedList<SubscriptionBaseTransition> transitions;

    // Low level events are ONLY used for Repair APIs
    protected List<SubscriptionBaseEvent> events;

    public List<SubscriptionBaseEvent> getEvents() {
        return events;
    }

    // Transient object never returned at the API
    public DefaultSubscriptionBase(final SubscriptionBuilder builder) {
        this(builder, null, null);
    }

    public DefaultSubscriptionBase(final SubscriptionBuilder builder, @Nullable final SubscriptionBaseApiService apiService, @Nullable final Clock clock) {
        super(builder.getId(), builder.getCreatedDate(), builder.getUpdatedDate());
        this.apiService = apiService;
        this.clock = clock;
        this.bundleId = builder.getBundleId();
        this.bundleExternalKey = builder.getBundleExternalKey();
        this.alignStartDate = builder.getAlignStartDate();
        this.bundleStartDate = builder.getBundleStartDate();
        this.category = builder.getCategory();
        this.chargedThroughDate = builder.getChargedThroughDate();
        this.migrated = builder.isMigrated();
    }

    // Used for API to make sure we have a clock and an apiService set before we return the object
    public DefaultSubscriptionBase(final DefaultSubscriptionBase internalSubscription, final SubscriptionBaseApiService apiService, final Clock clock) {
        super(internalSubscription.getId(), internalSubscription.getCreatedDate(), internalSubscription.getUpdatedDate());
        this.apiService = apiService;
        this.clock = clock;
        this.bundleId = internalSubscription.getBundleId();
        this.bundleExternalKey = internalSubscription.getBundleExternalKey();
        this.alignStartDate = internalSubscription.getAlignStartDate();
        this.bundleStartDate = internalSubscription.getBundleStartDate();
        this.category = internalSubscription.getCategory();
        this.chargedThroughDate = internalSubscription.getChargedThroughDate();
        this.migrated = internalSubscription.isMigrated();
        this.transitions = new LinkedList<SubscriptionBaseTransition>(internalSubscription.getAllTransitions());
        this.events = internalSubscription.getEvents();
    }

    @Override
    public UUID getBundleId() {
        return bundleId;
    }

    public String getBundleExternalKey() {
        return bundleExternalKey;
    }

    @Override
    public DateTime getStartDate() {
        return transitions.get(0).getEffectiveTransitionTime();
    }

    @Override
    public EntitlementState getState() {

        final SubscriptionBaseTransition previousTransition = getPreviousTransition();
        if (previousTransition != null) {
            return previousTransition.getNextState();
        }

        final SubscriptionBaseTransition pendingTransition = getPendingTransition();
        if (pendingTransition != null) {
            return EntitlementState.PENDING;
        }
        throw new IllegalStateException("Should return a valid EntitlementState");
    }

    @Override
    public EntitlementSourceType getSourceType() {
        if (transitions == null) {
            return null;
        }
        if (isMigrated()) {
            return EntitlementSourceType.MIGRATED;
        } else {
            final SubscriptionBaseTransitionData initialTransition = (SubscriptionBaseTransitionData) transitions.get(0);
            switch (initialTransition.getApiEventType()) {
                case TRANSFER:
                    return EntitlementSourceType.TRANSFERRED;
                default:
                    return EntitlementSourceType.NATIVE;
            }
        }
    }

    @Override
    public PlanPhase getCurrentPhase() {
        return (getPreviousTransition() == null) ? null
                                                 : getPreviousTransition().getNextPhase();
    }

    public PlanPhase getCurrentOrPendingPhase() {
        if (getState() == EntitlementState.PENDING) {
            return getPendingTransition().getNextPhase();
        } else {
            return getCurrentPhase();
        }
    }


    @Override
    public Plan getCurrentPlan() {
        return (getPreviousTransition() == null) ? null
                                                 : getPreviousTransition().getNextPlan();
    }

    @Override
    public Plan getCurrentOrPendingPlan() {
        if (getState() == EntitlementState.PENDING) {
            return getPendingTransition().getNextPlan();
        } else {
            return getCurrentPlan();
        }
    }

    @Override
    public PriceList getCurrentPriceList() {
        return (getPreviousTransition() == null) ? null :
               getPreviousTransition().getNextPriceList();

    }

    @Override
    public DateTime getEndDate() {
        final SubscriptionBaseTransition latestTransition = getPreviousTransition();
        if (latestTransition != null && latestTransition.getNextState() == EntitlementState.CANCELLED) {
            return latestTransition.getEffectiveTransitionTime();
        }
        return null;
    }

    @Override
    public DateTime getFutureEndDate() {
        if (transitions == null) {
            return null;
        }

        final SubscriptionBaseTransitionDataIterator it = new SubscriptionBaseTransitionDataIterator(
                clock, transitions, Order.ASC_FROM_PAST,
                Visibility.ALL, TimeLimit.FUTURE_ONLY);
        while (it.hasNext()) {
            final SubscriptionBaseTransition cur = it.next();
            if (cur.getTransitionType() == SubscriptionBaseTransitionType.CANCEL) {
                return cur.getEffectiveTransitionTime();
            }
        }
        return null;
    }

    @Override
    public boolean cancel(final CallContext context) throws SubscriptionBaseApiException {
        return apiService.cancel(this, context);
    }

    @Override
    public boolean cancelWithDate(final DateTime requestedDate, final CallContext context) throws SubscriptionBaseApiException {
        return apiService.cancelWithRequestedDate(this, requestedDate, context);
    }

    @Override
    public boolean cancelWithPolicy(final BillingActionPolicy policy, int accountBillCycleDayLocal, final CallContext context) throws SubscriptionBaseApiException {
        return apiService.cancelWithPolicy(this, policy, accountBillCycleDayLocal, context);
    }

    @Override
    public boolean uncancel(final CallContext context)
            throws SubscriptionBaseApiException {
        return apiService.uncancel(this, context);
    }

    @Override
    public DateTime changePlan(final PlanPhaseSpecifier spec,
                               final List<PlanPhasePriceOverride> overrides, final CallContext context) throws SubscriptionBaseApiException {
        return apiService.changePlan(this, spec, overrides, context);
    }

    @Override
    public boolean undoChangePlan(final CallContext context) throws SubscriptionBaseApiException {
        return apiService.undoChangePlan(this, context);
    }

    @Override
    public DateTime changePlanWithDate(final PlanPhaseSpecifier spec, final List<PlanPhasePriceOverride> overrides,
                                       final DateTime requestedDate, final CallContext context) throws SubscriptionBaseApiException {
        return apiService.changePlanWithRequestedDate(this, spec, overrides, requestedDate, context);
    }

    @Override
    public DateTime changePlanWithPolicy(final PlanPhaseSpecifier spec,
                                         final List<PlanPhasePriceOverride> overrides, final BillingActionPolicy policy, final CallContext context) throws SubscriptionBaseApiException {
        return apiService.changePlanWithPolicy(this, spec, overrides, policy, context);
    }

    @Override
    public SubscriptionBaseTransition getPendingTransition() {
        if (transitions == null) {
            return null;
        }
        final SubscriptionBaseTransitionDataIterator it = new SubscriptionBaseTransitionDataIterator(
                clock, transitions, Order.ASC_FROM_PAST,
                Visibility.ALL, TimeLimit.FUTURE_ONLY);

        return it.hasNext() ? it.next() : null;
    }

    @Override
    public Product getLastActiveProduct() {
        if (getState() == EntitlementState.CANCELLED) {
            final SubscriptionBaseTransition data = getPreviousTransition();
            return data.getPreviousPlan().getProduct();
        } else if (getState() == EntitlementState.PENDING) {
            final SubscriptionBaseTransition data = getPendingTransition();
            return data.getNextPlan().getProduct();
        } else {
            final Plan currentPlan = getCurrentPlan();
            // currentPlan can be null when playing with the clock (subscription created in the future)
            return currentPlan == null ? null : currentPlan.getProduct();
        }
    }

    @Override
    public PriceList getLastActivePriceList() {
        if (getState() == EntitlementState.CANCELLED) {
            final SubscriptionBaseTransition data = getPreviousTransition();
            return data.getPreviousPriceList();
        } else if (getState() == EntitlementState.PENDING) {
            final SubscriptionBaseTransition data = getPendingTransition();
            return data.getNextPriceList();
        } else {
            return getCurrentPriceList();
        }
    }

    @Override
    public ProductCategory getLastActiveCategory() {
        if (getState() == EntitlementState.CANCELLED) {
            final SubscriptionBaseTransition data = getPreviousTransition();
            return data.getPreviousPlan().getProduct().getCategory();
        } else if (getState() == EntitlementState.PENDING) {
            final SubscriptionBaseTransition data = getPendingTransition();
            return data.getNextPlan().getProduct().getCategory();
        } else {
            final Plan currentPlan = getCurrentPlan();
            // currentPlan can be null when playing with the clock (subscription created in the future)
            return currentPlan == null ? null : currentPlan.getProduct().getCategory();
        }
    }

    @Override
    public Plan getLastActivePlan() {
        if (getState() == EntitlementState.CANCELLED) {
            final SubscriptionBaseTransition data = getPreviousTransition();
            return data.getPreviousPlan();
        } else if (getState() == EntitlementState.PENDING) {
            final SubscriptionBaseTransition data = getPendingTransition();
            return data.getNextPlan();
        } else {
            return getCurrentPlan();
        }
    }

    @Override
    public PlanPhase getLastActivePhase() {
        if (getState() == EntitlementState.CANCELLED) {
            final SubscriptionBaseTransition data = getPreviousTransition();
            return data.getPreviousPhase();
        } else if (getState() == EntitlementState.PENDING) {
            final SubscriptionBaseTransition data = getPendingTransition();
            return data.getNextPhase();
        } else {
            return getCurrentPhase();
        }
    }

    @Override
    public BillingPeriod getLastActiveBillingPeriod() {
        if (getState() == EntitlementState.CANCELLED) {
            final SubscriptionBaseTransition data = getPreviousTransition();
            return data.getPreviousPlan().getRecurringBillingPeriod();
        } else if (getState() == EntitlementState.PENDING) {
            final SubscriptionBaseTransition data = getPendingTransition();
            return data.getNextPlan().getRecurringBillingPeriod();
        } else {
            final Plan currentPlan = getCurrentPlan();
            // currentPlan can be null when playing with the clock (subscription created in the future)
            return currentPlan.getRecurringBillingPeriod();
        }
    }

    @Override
    public SubscriptionBaseTransition getPreviousTransition() {
        if (transitions == null) {
            return null;
        }
        final SubscriptionBaseTransitionDataIterator it = new SubscriptionBaseTransitionDataIterator(
                clock, transitions, Order.DESC_FROM_FUTURE,
                Visibility.FROM_DISK_ONLY, TimeLimit.PAST_OR_PRESENT_ONLY);

        return it.hasNext() ? it.next() : null;
    }

    @Override
    public ProductCategory getCategory() {
        return category;
    }

    @Override
    public Integer getBillCycleDayLocal() {

        final SubscriptionBaseTransitionDataIterator it = new SubscriptionBaseTransitionDataIterator(
                clock, transitions, Order.DESC_FROM_FUTURE,
                Visibility.FROM_DISK_ONLY, TimeLimit.PAST_OR_PRESENT_ONLY);
        while (it.hasNext()) {
            final SubscriptionBaseTransition cur = it.next();
            if (cur.getTransitionType() == SubscriptionBaseTransitionType.BCD_CHANGE) {
                return cur.getNextBillingCycleDayLocal();
            }
        }
        return null;
    }

    public DateTime getBundleStartDate() {
        return bundleStartDate;
    }

    @Override
    public DateTime getChargedThroughDate() {
        return chargedThroughDate;
    }

    @Override
    public boolean isMigrated() {
        return migrated;
    }

    @Override
    public List<SubscriptionBaseTransition> getAllTransitions() {
        if (transitions == null) {
            return Collections.emptyList();
        }
        final List<SubscriptionBaseTransition> result = new ArrayList<SubscriptionBaseTransition>();
        final SubscriptionBaseTransitionDataIterator it = new SubscriptionBaseTransitionDataIterator(clock, transitions, Order.ASC_FROM_PAST, Visibility.ALL, TimeLimit.ALL);
        while (it.hasNext()) {
            result.add(it.next());
        }
        return result;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                 + ((id == null) ? 0 : id.hashCode());
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final DefaultSubscriptionBase other = (DefaultSubscriptionBase) obj;
        if (id == null) {
            if (other.id != null) {
                return false;
            }
        } else if (!id.equals(other.id)) {
            return false;
        }
        return true;
    }

    @Override
    public DateTime getDateOfFirstRecurringNonZeroCharge() {
        final Plan initialPlan = !transitions.isEmpty() ? transitions.get(0).getNextPlan() : null;
        final PlanPhase initialPhase = !transitions.isEmpty() ? transitions.get(0).getNextPhase() : null;
        final PhaseType initialPhaseType = initialPhase != null ? initialPhase.getPhaseType() : null;
        return initialPlan.dateOfFirstRecurringNonZeroCharge(getStartDate(), initialPhaseType);
    }

    public SubscriptionBaseTransitionData getTransitionFromEvent(final SubscriptionBaseEvent event, final int seqId) {
        if (transitions == null || event == null) {
            return null;
        }
        SubscriptionBaseTransitionData prev = null;
        for (final SubscriptionBaseTransition cur : transitions) {
            final SubscriptionBaseTransitionData curData = (SubscriptionBaseTransitionData) cur;
            if (curData.getId().equals(event.getId())) {

                final SubscriptionBaseTransitionData withSeq = new SubscriptionBaseTransitionData(curData, seqId);
                return withSeq;
            }
            if (curData.getTotalOrdering() < event.getTotalOrdering()) {
                prev = curData;
            }
        }
        // Since UNCANCEL/UNDO_CHANGE are not part of the transitions, we compute a new transition based on the event right before
        // This is used to be able to send a bus event for uncancellation/undo_change
        if (prev != null && event.getType() == EventType.API_USER && (((ApiEvent) event).getApiEventType() == ApiEventType.UNCANCEL || ((ApiEvent) event).getApiEventType() == ApiEventType.UNDO_CHANGE)) {
            final SubscriptionBaseTransitionData withSeq = new SubscriptionBaseTransitionData(prev, EventType.API_USER, ((ApiEvent) event).getApiEventType(), seqId);
            return withSeq;
        }
        return null;
    }

    public DateTime getAlignStartDate() {
        return alignStartDate;
    }

    public long getLastEventOrderedId() {
        final SubscriptionBaseTransitionDataIterator it = new SubscriptionBaseTransitionDataIterator(
                clock, transitions, Order.DESC_FROM_FUTURE,
                Visibility.FROM_DISK_ONLY, TimeLimit.ALL);
        return it.hasNext() ? ((SubscriptionBaseTransitionData) it.next()).getTotalOrdering() : -1L;
    }

    public List<SubscriptionBaseTransition> getBillingTransitions() {

        if (transitions == null) {
            return Collections.emptyList();
        }
        final List<SubscriptionBaseTransition> result = new ArrayList<SubscriptionBaseTransition>();
        final SubscriptionBaseTransitionDataIterator it = new SubscriptionBaseTransitionDataIterator(
                clock, transitions, Order.ASC_FROM_PAST,
                Visibility.ALL, TimeLimit.ALL);
        // Remove anything prior to first CREATE
        boolean foundInitialEvent = false;
        while (it.hasNext()) {
            final SubscriptionBaseTransitionData curTransition = (SubscriptionBaseTransitionData) it.next();
            if (!foundInitialEvent) {
                foundInitialEvent = curTransition.getEventType() == EventType.API_USER &&
                                    (curTransition.getApiEventType() == ApiEventType.CREATE ||
                                     curTransition.getApiEventType() == ApiEventType.TRANSFER);
            }
            if (foundInitialEvent) {
                result.add(curTransition);
            }
        }
        return result;
    }

    public SubscriptionBaseTransitionData getLastTransitionForCurrentPlan() {
        if (transitions == null) {
            throw new SubscriptionBaseError(String.format("No transitions for subscription %s", getId()));
        }

        final SubscriptionBaseTransitionDataIterator it = new SubscriptionBaseTransitionDataIterator(clock,
                                                                                                     transitions,
                                                                                                     Order.DESC_FROM_FUTURE,
                                                                                                     Visibility.ALL,
                                                                                                     TimeLimit.PAST_OR_PRESENT_ONLY);

        while (it.hasNext()) {
            final SubscriptionBaseTransitionData cur = (SubscriptionBaseTransitionData) it.next();
            if (cur.getTransitionType() == SubscriptionBaseTransitionType.CREATE
                || cur.getTransitionType() == SubscriptionBaseTransitionType.TRANSFER
                || cur.getTransitionType() == SubscriptionBaseTransitionType.CHANGE) {
                return cur;
            }
        }

        throw new SubscriptionBaseError(String.format("Failed to find InitialTransitionForCurrentPlan id = %s", getId()));
    }

    public boolean isFutureCancelled() {
        return getFutureEndDate() != null;
    }


    public boolean isPendingChangePlan() {
        final SubscriptionBaseTransition pendingTransition = getPendingTransition();
        return pendingTransition != null && pendingTransition.getTransitionType() == SubscriptionBaseTransitionType.CHANGE;
    }



    public DateTime getPlanChangeEffectiveDate(final BillingActionPolicy policy, @Nullable final BillingAlignment alignment, @Nullable final Integer accountBillCycleDayLocal, final InternalTenantContext context) {

        final DateTime candidateResult;
        switch (policy) {
            case IMMEDIATE:
                candidateResult = clock.getUTCNow();
                break;
            case START_OF_TERM:
                if (chargedThroughDate == null) {
                    candidateResult = getStartDate();
                // Will take care of billing IN_ARREAR or subscriptions that are not invoiced up to date
                } else if (!chargedThroughDate.isAfter(clock.getUTCNow())) {
                    candidateResult = chargedThroughDate;
                } else {

                    // In certain path (dryRun, or default catalog START_OF_TERM policy), the info is not easily available and as a result, such policy is not implemented
                    Preconditions.checkState(alignment != null && context != null && accountBillCycleDayLocal != null, "START_OF_TERM not implemented in dryRun use case");

                    Preconditions.checkState(alignment != BillingAlignment.BUNDLE || category != ProductCategory.ADD_ON,  "START_OF_TERM not implemented for AO configured with a BUNDLE billing alignment");

                    // If BCD was overriden at the subscription level, we take its latest value (it should also be reflected in the chargedThroughDate) but still required for
                    // alignment purpose
                    Integer bcd = getBillCycleDayLocal();
                    if (bcd == null) {
                        bcd = BillCycleDayCalculator.calculateBcdForAlignment(null, this, this, alignment, context, accountBillCycleDayLocal);
                    }

                    final BillingPeriod billingPeriod = getLastActivePlan().getRecurringBillingPeriod();
                    DateTime proposedDate = chargedThroughDate;
                    while (proposedDate.isAfter(clock.getUTCNow())) {
                        proposedDate = proposedDate.minus(billingPeriod.getPeriod());
                    }

                    final LocalDate resultingLocalDate  = BillCycleDayCalculator.alignProposedBillCycleDate(proposedDate, bcd, billingPeriod, context);
                    candidateResult = context.toUTCDateTime(resultingLocalDate);
                }

                break;

            case END_OF_TERM:
                //
                // If we have a chargedThroughDate that is 'up to date' we use it, if not default to now
                // chargedThroughDate could exist and be less than now if:
                // 1. account is not being invoiced, for e.g AUTO_INVOICING_OFF nis set
                // 2. In the case if FIXED item CTD is set using startDate of the service period
                //
                candidateResult = (chargedThroughDate != null && chargedThroughDate.isAfter(clock.getUTCNow())) ? chargedThroughDate : clock.getUTCNow();
                break;
            default:
                throw new SubscriptionBaseError(String.format(
                        "Unexpected policy type %s", policy.toString()));
        }
        return (candidateResult.compareTo(getStartDate()) < 0) ? getStartDate() : candidateResult;
    }

    public DateTime getCurrentPhaseStart() {

        if (transitions == null) {
            throw new SubscriptionBaseError(String.format(
                    "No transitions for subscription %s", getId()));
        }
        final SubscriptionBaseTransitionDataIterator it = new SubscriptionBaseTransitionDataIterator(
                clock, transitions, Order.DESC_FROM_FUTURE,
                Visibility.ALL, TimeLimit.PAST_OR_PRESENT_ONLY);
        while (it.hasNext()) {
            final SubscriptionBaseTransitionData cur = (SubscriptionBaseTransitionData) it.next();

            if (cur.getTransitionType() == SubscriptionBaseTransitionType.PHASE
                || cur.getTransitionType() == SubscriptionBaseTransitionType.TRANSFER
                || cur.getTransitionType() == SubscriptionBaseTransitionType.CREATE
                || cur.getTransitionType() == SubscriptionBaseTransitionType.CHANGE) {
                return cur.getEffectiveTransitionTime();
            }
        }
        throw new SubscriptionBaseError(String.format(
                "Failed to find CurrentPhaseStart id = %s", getId().toString()));
    }

    public void rebuildTransitions(final List<SubscriptionBaseEvent> inputEvents, final Catalog catalog) throws CatalogApiException {

        if (inputEvents == null) {
            return;
        }

        this.events = inputEvents;

        Collections.sort(inputEvents, new Comparator<SubscriptionBaseEvent>() {
            @Override
            public int compare(final SubscriptionBaseEvent o1, final SubscriptionBaseEvent o2) {
                final int res = o1.getEffectiveDate().compareTo(o2.getEffectiveDate());
                if (res != 0) {
                    return res;
                }

                // In-memory events have a total order of 0, make sure they are after on disk event
                if (o1.getTotalOrdering() == 0 && o2.getTotalOrdering() > 0) {
                    return 1;
                } else if (o1.getTotalOrdering() > 0 && o2.getTotalOrdering() == 0) {
                    return -1;
                } else if (o1.getTotalOrdering() == o2.getTotalOrdering()) {
                    return 0;
                } else {
                    return o1.getTotalOrdering() < (o2.getTotalOrdering()) ? -1 : 1;
                }
            }
        });

        removeEverythingPastCancelEvent(events);

        UUID nextUserToken = null;

        UUID nextEventId = null;
        DateTime nextCreatedDate = null;
        EntitlementState nextState = null;
        String nextPlanName = null;
        String nextPhaseName = null;
        Integer nextBillingCycleDayLocal = null;

        UUID prevEventId = null;
        DateTime prevCreatedDate = null;
        EntitlementState previousState = null;
        PriceList previousPriceList = null;
        Plan previousPlan = null;
        PlanPhase previousPhase = null;
        Integer previousBillingCycleDayLocal = null;

        transitions = new LinkedList<SubscriptionBaseTransition>();

        for (final SubscriptionBaseEvent cur : inputEvents) {
            if (!cur.isActive()) {
                continue;
            }

            ApiEventType apiEventType = null;
            boolean isFromDisk = true;

            nextEventId = cur.getId();
            nextCreatedDate = cur.getCreatedDate();

            switch (cur.getType()) {

                case PHASE:
                    final PhaseEvent phaseEV = (PhaseEvent) cur;
                    nextPhaseName = phaseEV.getPhase();
                    break;

                case BCD_UPDATE:
                    final BCDEvent bcdEvent = (BCDEvent) cur;
                    nextBillingCycleDayLocal = bcdEvent.getBillCycleDayLocal();
                    break;

                case API_USER:
                    final ApiEvent userEV = (ApiEvent) cur;
                    apiEventType = userEV.getApiEventType();
                    isFromDisk = userEV.isFromDisk();

                    switch (apiEventType) {
                        case TRANSFER:
                        case CREATE:
                            prevEventId = null;
                            prevCreatedDate = null;
                            previousState = null;
                            previousPlan = null;
                            previousPhase = null;
                            previousPriceList = null;
                            nextState = EntitlementState.ACTIVE;
                            nextPlanName = userEV.getEventPlan();
                            nextPhaseName = userEV.getEventPlanPhase();
                            break;
                        case CHANGE:
                            nextPlanName = userEV.getEventPlan();
                            nextPhaseName = userEV.getEventPlanPhase();
                            break;
                        case CANCEL:
                            nextState = EntitlementState.CANCELLED;
                            nextPlanName = null;
                            nextPhaseName = null;
                            break;
                        case UNCANCEL:
                        case UNDO_CHANGE:
                        default:
                            throw new SubscriptionBaseError(String.format(
                                    "Unexpected UserEvent type = %s", userEV
                                            .getApiEventType().toString()));
                    }
                    break;
                default:
                    throw new SubscriptionBaseError(String.format(
                            "Unexpected Event type = %s", cur.getType()));
            }

            Plan nextPlan = null;
            PlanPhase nextPhase = null;
            PriceList nextPriceList = null;

            nextPlan = (nextPlanName != null) ? catalog.findPlan(nextPlanName, cur.getEffectiveDate(), getAlignStartDate()) : null;
            nextPhase = (nextPhaseName != null) ? catalog.findPhase(nextPhaseName, cur.getEffectiveDate(), getAlignStartDate()) : null;
            nextPriceList = (nextPlan != null) ? catalog.findPriceListForPlan(nextPlanName, cur.getEffectiveDate(), getAlignStartDate()) : null;

            final SubscriptionBaseTransitionData transition = new SubscriptionBaseTransitionData(
                    cur.getId(), id, bundleId, bundleExternalKey, cur.getType(), apiEventType,
                    cur.getEffectiveDate(),
                    prevEventId, prevCreatedDate,
                    previousState, previousPlan, previousPhase,
                    previousPriceList,
                    previousBillingCycleDayLocal,
                    nextEventId, nextCreatedDate,
                    nextState, nextPlan, nextPhase,
                    nextPriceList,
                    nextBillingCycleDayLocal,
                    cur.getTotalOrdering(),
                    cur.getCreatedDate(),
                    nextUserToken,
                    isFromDisk);

            transitions.add(transition);

            previousState = nextState;
            previousPlan = nextPlan;
            previousPhase = nextPhase;
            previousPriceList = nextPriceList;
            prevEventId = nextEventId;
            prevCreatedDate = nextCreatedDate;
            previousBillingCycleDayLocal = nextBillingCycleDayLocal;

        }
    }

    // Skip any event after a CANCEL event:
    //
    //  * DefaultSubscriptionDao#buildBundleSubscriptions may have added an out-of-order cancellation event (https://github.com/killbill/killbill/issues/897)
    //  * Hardening against data integrity issues where we have multiple active CANCEL (https://github.com/killbill/killbill/issues/619)
    //
    private void removeEverythingPastCancelEvent(final List<SubscriptionBaseEvent> inputEvents) {
        final SubscriptionBaseEvent cancellationEvent = Iterables.tryFind(inputEvents,
                                                                          new Predicate<SubscriptionBaseEvent>() {
                                                                              @Override
                                                                              public boolean apply(final SubscriptionBaseEvent input) {
                                                                                  return input.getType() == EventType.API_USER && ((ApiEvent) input).getApiEventType() == ApiEventType.CANCEL;
                                                                              }
                                                                          }).orNull();
        if (cancellationEvent == null) {
            return;
        }

        final Iterator<SubscriptionBaseEvent> it = inputEvents.iterator();
        while (it.hasNext()) {
            final SubscriptionBaseEvent input = it.next();
            if (!input.isActive()) {
                continue;
            }

            if (input.getId().compareTo(cancellationEvent.getId()) == 0) {
                // Keep the cancellation event
            } else if (input.getType() == EventType.API_USER && (((ApiEvent) input).getApiEventType() == ApiEventType.TRANSFER || ((ApiEvent) input).getApiEventType() == ApiEventType.CREATE)) {
                // Keep the initial event (SOT use-case)
            } else if (input.getEffectiveDate().compareTo(cancellationEvent.getEffectiveDate()) >= 0) {
                // Event to ignore past cancellation date
                it.remove();
            }
        }
    }
}
