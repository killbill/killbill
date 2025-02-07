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
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.Period;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingAlignment;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.PriceList;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.catalog.api.StaticCatalog;
import org.killbill.billing.catalog.api.VersionedCatalog;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementSourceType;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.entitlement.api.EntitlementSpecifier;
import org.killbill.billing.entity.EntityBase;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseApiService;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;
import org.killbill.billing.subscription.api.user.SubscriptionBaseTransitionDataIterator.Order;
import org.killbill.billing.subscription.api.user.SubscriptionBaseTransitionDataIterator.TimeLimit;
import org.killbill.billing.subscription.api.user.SubscriptionBaseTransitionDataIterator.Visibility;
import org.killbill.billing.subscription.catalog.DefaultSubscriptionCatalogApi;
import org.killbill.billing.subscription.catalog.SubscriptionCatalog;
import org.killbill.billing.subscription.events.SubscriptionBaseEvent;
import org.killbill.billing.subscription.events.SubscriptionBaseEvent.EventType;
import org.killbill.billing.subscription.events.bcd.BCDEvent;
import org.killbill.billing.subscription.events.phase.PhaseEvent;
import org.killbill.billing.subscription.events.quantity.QuantityEvent;
import org.killbill.billing.subscription.events.user.ApiEvent;
import org.killbill.billing.subscription.events.user.ApiEventType;
import org.killbill.billing.subscription.exceptions.SubscriptionBaseError;
import org.killbill.billing.util.bcd.BillCycleDayCalculator;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.catalog.CatalogDateHelper;
import org.killbill.clock.Clock;
import org.killbill.commons.utils.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultSubscriptionBase extends EntityBase implements SubscriptionBase {

    private static final Logger log = LoggerFactory.getLogger(DefaultSubscriptionBase.class);

    private final Clock clock;
    private final SubscriptionBaseApiService apiService;

    //
    // Final subscription fields
    //
    private final UUID bundleId;
    private final String externalKey;
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

    private LinkedList<SubscriptionBaseTransition> transitionsWithDeletedEvents;

    private boolean includeDeletedEvents;

    // Low level events are ONLY used for Repair APIs
    protected List<SubscriptionBaseEvent> events;

    public List<SubscriptionBaseEvent> getEvents() {
        return events;
    }

    @Override
    public boolean getIncludeDeletedEvents() {
        return includeDeletedEvents;
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
        this.externalKey = builder.getExternalKey();
        this.bundleExternalKey = builder.getBundleExternalKey();
        this.alignStartDate = builder.getAlignStartDate();
        this.bundleStartDate = builder.getBundleStartDate();
        this.category = builder.getCategory();
        this.chargedThroughDate = builder.getChargedThroughDate();
        this.migrated = builder.isMigrated();
        this.includeDeletedEvents = builder.getIncludeDeletedEvents();
    }

    // Used for API to make sure we have a clock and an apiService set before we return the object
    public DefaultSubscriptionBase(final DefaultSubscriptionBase internalSubscription, final SubscriptionBaseApiService apiService, final Clock clock) {
        super(internalSubscription.getId(), internalSubscription.getCreatedDate(), internalSubscription.getUpdatedDate());
        this.apiService = apiService;
        this.clock = clock;
        this.bundleId = internalSubscription.getBundleId();
        this.externalKey = internalSubscription.getExternalKey();
        this.bundleExternalKey = internalSubscription.getBundleExternalKey();
        this.alignStartDate = internalSubscription.getAlignStartDate();
        this.bundleStartDate = internalSubscription.getBundleStartDate();
        this.category = internalSubscription.getCategory();
        this.chargedThroughDate = internalSubscription.getChargedThroughDate();
        this.migrated = internalSubscription.isMigrated();
        this.transitions = new LinkedList<SubscriptionBaseTransition>(internalSubscription.getAllTransitions(false));
        this.events = internalSubscription.getEvents();
        this.transitionsWithDeletedEvents = new LinkedList<SubscriptionBaseTransition>(internalSubscription.getAllTransitions(true));
        this.includeDeletedEvents = internalSubscription.getIncludeDeletedEvents();
    }

    // Used for API to make sure we have a clock and an apiService set before we return the object
    public DefaultSubscriptionBase(final DefaultSubscriptionBase internalSubscription, final boolean includeDeletedEvents) {
        this(internalSubscription, null, null);
        this.includeDeletedEvents = includeDeletedEvents;
    }

    @Override
    public UUID getBundleId() {
        return bundleId;
    }

    @Override
    public String getExternalKey() {
        return externalKey;
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
    public DateTime getFutureExpiryDate() {
        if (transitions == null) {
            return null;
        }

        final SubscriptionBaseTransitionDataIterator it = new SubscriptionBaseTransitionDataIterator(
                clock, transitions, Order.ASC_FROM_PAST,
                Visibility.ALL, TimeLimit.FUTURE_ONLY);
        while (it.hasNext()) {
            final SubscriptionBaseTransition cur = it.next();
            if (cur.getTransitionType() == SubscriptionBaseTransitionType.EXPIRED) {
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
    public boolean cancelWithPolicy(final BillingActionPolicy policy, final CallContext context) throws SubscriptionBaseApiException {
        return apiService.cancelWithPolicy(this, policy, context);
    }

    @Override
    public boolean uncancel(final CallContext context)
            throws SubscriptionBaseApiException {
        return apiService.uncancel(this, context);
    }

    @Override
    public DateTime changePlan(final EntitlementSpecifier spec,
                               final CallContext context) throws SubscriptionBaseApiException {
        return apiService.changePlan(this, spec, context);
    }

    @Override
    public boolean undoChangePlan(final CallContext context) throws SubscriptionBaseApiException {
        return apiService.undoChangePlan(this, context);
    }

    @Override
    public DateTime changePlanWithDate(final EntitlementSpecifier spec,
                                       final DateTime requestedDate, final CallContext context) throws SubscriptionBaseApiException {
        return apiService.changePlanWithRequestedDate(this, spec, requestedDate, context);
    }

    @Override
    public DateTime changePlanWithPolicy(final EntitlementSpecifier spec,
                                         final BillingActionPolicy policy, final CallContext context) throws SubscriptionBaseApiException {
        return apiService.changePlanWithPolicy(this, spec, policy, context);
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
        if (getState() == EntitlementState.CANCELLED || getState() == EntitlementState.EXPIRED) {
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
        if (getState() == EntitlementState.CANCELLED || getState() == EntitlementState.EXPIRED) {
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
        return getPrevValue(true);
    }

    @Override
    public Integer getQuantity() {
        return getPrevValue(false);
    }

    private Integer getPrevValue(final boolean bcd) {

        final SubscriptionBaseTransitionDataIterator it = new SubscriptionBaseTransitionDataIterator(
                clock, transitions, Order.DESC_FROM_FUTURE,
                Visibility.FROM_DISK_ONLY, TimeLimit.PAST_OR_PRESENT_ONLY);
        while (it.hasNext()) {
            final SubscriptionBaseTransition cur = it.next();
            if (bcd && cur.getTransitionType() == SubscriptionBaseTransitionType.BCD_CHANGE) {
                return cur.getNextBillingCycleDayLocal();
            } else if (!bcd && cur.getTransitionType() == SubscriptionBaseTransitionType.QUANTITY_CHANGE) {
                return cur.getNextQuantity();
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
    public List<SubscriptionBaseTransition> getAllTransitions(final boolean includeDeleted) {
        if (includeDeleted) {
            return transitionsWithDeletedEvents != null ? getSortedTransactions(transitionsWithDeletedEvents) : Collections.emptyList();
        } else {
            return transitions != null ? getSortedTransactions(transitions) : Collections.emptyList();
        }
    }

    private List<SubscriptionBaseTransition> getSortedTransactions(final LinkedList<SubscriptionBaseTransition> inputTransitions) {
        final List<SubscriptionBaseTransition> result = new ArrayList<SubscriptionBaseTransition>();
        final SubscriptionBaseTransitionDataIterator it = new SubscriptionBaseTransitionDataIterator(clock, inputTransitions, Order.ASC_FROM_PAST, Visibility.ALL, TimeLimit.ALL);
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

        // Fix spotbugs "NP_NULL_ON_SOME_PATH".
        // "initialPlan == null ? getStartDate()": using "getStartDate()" because this is what we have in
        // org.killbill.billing.catalog.DefaultPlan#dateOfFirstRecurringNonZeroCharge()
        return initialPlan == null ? getStartDate() : initialPlan.dateOfFirstRecurringNonZeroCharge(getStartDate(), initialPhaseType);
    }

    @Override
    public BillingAlignment getBillingAlignment(final PlanPhaseSpecifier spec, final DateTime transitionTime, final VersionedCatalog publicCatalog) throws SubscriptionBaseApiException {
        try {
            final SubscriptionCatalog catalog = DefaultSubscriptionCatalogApi.wrapCatalog(publicCatalog, clock);

            final SubscriptionBaseTransition transition = (getState() == EntitlementState.PENDING) ?
                                                          getPendingTransition() :
                                                          getLastTransitionForCurrentPlan();
            final BillingAlignment alignment = catalog.billingAlignment(spec, transitionTime, transition.getEffectiveTransitionTime());
            return alignment;
        } catch (final CatalogApiException e) {
            throw new SubscriptionBaseApiException(e);
        }
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

    @Override
    public List<SubscriptionBillingEvent> getSubscriptionBillingEvents(final VersionedCatalog publicCatalog, final InternalTenantContext context) throws SubscriptionBaseApiException {

        if (transitions == null) {
            return Collections.emptyList();
        }

        final SubscriptionCatalog catalog = DefaultSubscriptionCatalogApi.wrapCatalog(publicCatalog, clock);
        try {

            final List<SubscriptionBillingEvent> result = new ArrayList<SubscriptionBillingEvent>();
            final SubscriptionBaseTransitionDataIterator it = new SubscriptionBaseTransitionDataIterator(
                    clock, transitions, Order.ASC_FROM_PAST,
                    Visibility.ALL, TimeLimit.ALL);

            // Recomputed for each event from the active Plan -- if Plan is null (cancellation this is not set)
            StaticCatalog lastActiveCatalog = null;
            final PriorityQueue<SubscriptionBillingEvent> candidatesCatalogChangeEvents = new PriorityQueue<>();
            boolean foundInitialEvent = false;

            SubscriptionBaseTransitionData lastPlanTransition = null;
            while (it.hasNext()) {

                final SubscriptionBaseTransitionData cur = (SubscriptionBaseTransitionData) it.next();
                final boolean isCreateOrTransfer = cur.getTransitionType() == SubscriptionBaseTransitionType.CREATE ||
                                                   cur.getTransitionType() == SubscriptionBaseTransitionType.TRANSFER;
                final boolean isChangeEvent = cur.getTransitionType() == SubscriptionBaseTransitionType.CHANGE;
                final boolean isPhaseEvent = cur.getTransitionType() == SubscriptionBaseTransitionType.PHASE;
                final boolean isCancelEvent = cur.getTransitionType() == SubscriptionBaseTransitionType.CANCEL;

                if (!foundInitialEvent) {
                    foundInitialEvent = isCreateOrTransfer;
                }

                // Remove anything prior to first CREATE
                if (foundInitialEvent) {

                    // Track the last transition where we changed Plan
                    if (isCreateOrTransfer || isChangeEvent) {
                        lastPlanTransition = cur;
                    }

                    // Look for any catalog change transition whose date is less the cur event
                    SubscriptionBillingEvent prevCandidateForCatalogChangeEvents = candidatesCatalogChangeEvents.poll();
                    while (prevCandidateForCatalogChangeEvents != null &&
                           prevCandidateForCatalogChangeEvents.getEffectiveDate().compareTo(cur.getEffectiveTransitionTime()) < 0) {
                        result.add(prevCandidateForCatalogChangeEvents);
                        prevCandidateForCatalogChangeEvents = candidatesCatalogChangeEvents.poll();
                    }

                    if (isChangeEvent || isCancelEvent || isPhaseEvent) {
                        candidatesCatalogChangeEvents.clear();
                    } else if (prevCandidateForCatalogChangeEvents != null) {
                        candidatesCatalogChangeEvents.add(prevCandidateForCatalogChangeEvents);
                    }

                    final Plan plan = cur.getNextPlan();
                    final PlanPhase planPhase = cur.getNextPhase();

                    if (plan != null) {
                        lastActiveCatalog = plan.getCatalog();
                    }

                    // Computed from lastActiveCatalog
                    final DateTime catalogEffectiveDate = CatalogDateHelper.toUTCDateTime(lastActiveCatalog.getEffectiveDate());
                    final SubscriptionBillingEvent billingTransition = new DefaultSubscriptionBillingEvent(cur.getTransitionType(), plan, planPhase, cur.getEffectiveTransitionTime(),
                                                                                                           cur.getTotalOrdering(), cur.getNextBillingCycleDayLocal(), cur.getNextQuantity(),
                                                                                                           catalogEffectiveDate);
                    result.add(billingTransition);

                    if (isCreateOrTransfer || isChangeEvent || isPhaseEvent) {

                        // If we are moving to a new Plan, we use the latest active catalog version at the time this operation took place.
                        final DateTime billingTransitionEffectiveDate = isPhaseEvent ? lastPlanTransition.getEffectiveTransitionTime() : billingTransition.getEffectiveDate();
                        final StaticCatalog catalogVersion = catalog.versionForDate(billingTransitionEffectiveDate);

                        final Plan currentPlan = catalogVersion.findPlan(billingTransition.getPlan().getName());
                        final Integer bcdLocal = cur.getNextBillingCycleDayLocal();
                        // Iterate through all more recent version of the catalog to find possible effectiveDateForExistingSubscriptions transition for this Plan
                        Plan nextPlan = catalog.getNextPlanVersion(currentPlan);

                        while (nextPlan != null) {
                            if (nextPlan.getEffectiveDateForExistingSubscriptions() != null) {

                                DateTime nextEffectiveDate = new DateTime(nextPlan.getEffectiveDateForExistingSubscriptions()).toDateTime(DateTimeZone.UTC);

                                nextEffectiveDate = alignToNextBCDIfRequired(plan, planPhase, cur.getEffectiveTransitionTime(), nextEffectiveDate, catalog, bcdLocal, context);
                                // Add the catalog change transition if it is for a date past our current transition
                                if (nextEffectiveDate != null && !nextEffectiveDate.isBefore(cur.getEffectiveTransitionTime())) {
                                    // Computed from the nextPlan
                                    final PlanPhase nextPlanPhase = nextPlan.findPhase(planPhase.getName());
                                    final DateTime catalogEffectiveDateForNextPlan = CatalogDateHelper.toUTCDateTime(nextPlan.getCatalog().getEffectiveDate());
                                    final SubscriptionBillingEvent newBillingTransition = new DefaultSubscriptionBillingEvent(SubscriptionBaseTransitionType.CHANGE, nextPlan, nextPlanPhase, nextEffectiveDate,
                                                                                                                              cur.getTotalOrdering(), bcdLocal, cur.getNextQuantity(), catalogEffectiveDateForNextPlan);
                                    candidatesCatalogChangeEvents.add(newBillingTransition);
                                }

                            }
                            // TODO not so optimized as we keep parsing catalogs from the start...
                            nextPlan = catalog.getNextPlanVersion(nextPlan);
                        }
                    }
                }
            }
            SubscriptionBillingEvent prevCandidateForCatalogChangeEvents = candidatesCatalogChangeEvents.poll();
            while (prevCandidateForCatalogChangeEvents != null) {
                result.add(prevCandidateForCatalogChangeEvents);
                prevCandidateForCatalogChangeEvents = candidatesCatalogChangeEvents.poll();
            }

            return result;
        } catch (final CatalogApiException e) {
            throw new SubscriptionBaseApiException(e);
        }
    }

    // Align to next BCD based on recurring section for the phase
    private DateTime alignToNextBCDIfRequired(final Plan curPlan, final PlanPhase curPlanPhase, final DateTime prevTransitionDate, final DateTime curTransitionDate, final SubscriptionCatalog catalog, final Integer bcdLocal, final InternalTenantContext context) throws SubscriptionBaseApiException, CatalogApiException {

        if (!apiService.isEffectiveDateForExistingSubscriptionsAlignedToBCD(context)) {
            return curTransitionDate;
        }

        // If there is no recurring phase, we can't align to any BCD and we don't create a billing transition for such phase
        // TODO It does not take into consideration possible usage sections that could be present
        if (curPlanPhase.getRecurring() == null) {
            return null;
        }

        final BillingAlignment billingAlignment = catalog.billingAlignment(new PlanPhaseSpecifier(curPlan.getName(), curPlanPhase.getPhaseType()),
                                                                           curTransitionDate, prevTransitionDate);
        final int accountBillCycleDayLocal = apiService.getAccountBCD(context);
        Integer bcd = bcdLocal;
        if (bcd == null) {
            // TODO If we have an add-on subscription with a BUNDLE alignment, this is incorrect as we need access to the base subscription
            bcd = BillCycleDayCalculator.calculateBcdForAlignment(null, this, this, billingAlignment, context, accountBillCycleDayLocal);
        }

        final BillingPeriod billingPeriod = curPlanPhase.getRecurring() != null ? curPlanPhase.getRecurring().getBillingPeriod() : BillingPeriod.NO_BILLING_PERIOD;
        final LocalDate resultingLocalDate = BillCycleDayCalculator.alignToNextBillCycleDate(prevTransitionDate, curTransitionDate, bcd, billingPeriod, context);
        final DateTime candidateResult = context.toUTCDateTime(resultingLocalDate);
        return candidateResult;
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

        final SubscriptionBaseTransitionDataIterator it = new SubscriptionBaseTransitionDataIterator(
                clock, transitions, Order.ASC_FROM_PAST,
                Visibility.ALL, TimeLimit.FUTURE_ONLY);

        while (it.hasNext()) {
            final SubscriptionBaseTransition next = it.next();
            if (next.getTransitionType() == SubscriptionBaseTransitionType.CHANGE) {
                return true;
            }
        }
        return false;
    }

    public DateTime getEffectiveDateForPolicy(final BillingActionPolicy policy, @Nullable final BillingAlignment alignment, final InternalTenantContext context) throws SubscriptionBaseApiException {

        final Integer accountBillCycleDayLocal = apiService != null ? apiService.getAccountBCD(context) : null;
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

                    Preconditions.checkState(alignment != BillingAlignment.BUNDLE || category != ProductCategory.ADD_ON, "START_OF_TERM not implemented for AO configured with a BUNDLE billing alignment");

                    // If BCD was overriden at the subscription level, we take its latest value (it should also be reflected in the chargedThroughDate) but still required for
                    // alignment purpose
                    Integer bcd = getBillCycleDayLocal();
                    if (bcd == null) {
                        bcd = BillCycleDayCalculator.calculateBcdForAlignment(null, this, this, alignment, context, accountBillCycleDayLocal);
                    }

                    final BillingPeriod billingPeriod = getLastActivePlan().getRecurringBillingPeriod();
                    DateTime proposedDate = chargedThroughDate;
                    if (billingPeriod.getPeriod().equals(Period.ZERO)) {
                        proposedDate = clock.getUTCNow();
                    } else {
                        while (proposedDate.isAfter(clock.getUTCNow())) {
                            proposedDate = proposedDate.minus(billingPeriod.getPeriod());
                        }
                    }

                    final LocalDate resultingLocalDate = BillCycleDayCalculator.alignProposedBillCycleDate(proposedDate, bcd, billingPeriod, context);
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
        // Finally we verify we won't cancel prior the beginning of our current PHASE  -- mostly as a sanity or for test stability
        final DateTime lastTransitionTime = getCurrentPhaseStart();
        return (candidateResult.compareTo(lastTransitionTime) < 0) ? lastTransitionTime : candidateResult;
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
        // If the subscription is not yet started we return the startDate
        return getStartDate();
    }

    static class NextBillingCycleDayLocal {

        private final SortedMap<DateTime, Integer> bcdMap;
        private final SortedMap<DateTime, Integer> quantityMap;

        public NextBillingCycleDayLocal(final List<SubscriptionBaseEvent> inputEvents) {
            this.bcdMap = new TreeMap<DateTime, Integer>() {};
            this.quantityMap = new TreeMap<DateTime, Integer>() {};

            for (final SubscriptionBaseEvent cur : inputEvents) {
                if (cur.getType() == EventType.BCD_UPDATE && cur.isActive()) {
                    bcdMap.put(cur.getEffectiveDate(), ((BCDEvent) cur).getBillCycleDayLocal());
                } else if (cur.getType() == EventType.QUANTITY_UPDATE && cur.isActive()) {
                    quantityMap.put(cur.getEffectiveDate(), ((QuantityEvent) cur).getQuantity());
                }
            }
        }

        // Returns the BCD date applicable for a given input date
        public Integer getNextBillingCycleDayLocal(final DateTime inputDate) {
            return getNextBillingCycleDayLocal(inputDate, bcdMap);
        }

        public Integer getNextQuantity(final DateTime inputDate) {
            return getNextBillingCycleDayLocal(inputDate, quantityMap);
        }

        private static Integer getNextBillingCycleDayLocal(final DateTime inputDate, final SortedMap<DateTime, Integer> targetMap) {
            final SortedMap<DateTime, Integer> map = targetMap.headMap(inputDate.plusMillis(1));
            final DateTime targetKey = map.isEmpty() ? null : map.lastKey();
            if (targetKey == null) {
                return null;
            }
            return targetMap.get(targetKey);
        }
    }

    public void rebuildTransitions(final List<SubscriptionBaseEvent> inputEvents, final SubscriptionCatalog catalog) throws CatalogApiException {
        if (inputEvents == null) {
            return;
        }
        this.events = inputEvents;

        Collections.sort(inputEvents);

        removeEverythingPastCancelEvent(events);

        transitions = new LinkedList<SubscriptionBaseTransition>();
        transitionsWithDeletedEvents = new LinkedList<SubscriptionBaseTransition>();

        if (!includeDeletedEvents) {
            rebuildTransitionsInternal(inputEvents, catalog, transitions, id, bundleId, bundleExternalKey);
        } else {
            rebuildTransitionsInternal(inputEvents.stream().filter(event -> event.isActive()).collect(Collectors.toList()), catalog, transitions, id, bundleId, bundleExternalKey); //use only active events to build transitions
            rebuildTransitionsInternal(inputEvents, catalog, transitionsWithDeletedEvents, id, bundleId, bundleExternalKey); //use all events to build transitionsWithDeletedEvents
        }

    }

    private static void rebuildTransitionsInternal(final List<SubscriptionBaseEvent> inputEvents, final SubscriptionCatalog catalog, final LinkedList<SubscriptionBaseTransition> transitions, final UUID id, final UUID bundleId, final String bundleExternalKey) throws CatalogApiException {

        if (inputEvents == null || inputEvents.size() == 0) {
            return;
        }
        final UUID nextUserToken = null;

        UUID nextEventId;
        DateTime nextCreatedDate;
        EntitlementState nextState = null;
        String nextPlanName = null;
        String nextPhaseName = null;
        Integer nextBcdLocal = null;
        Integer nextQuantity = null;

        UUID prevEventId = null;
        DateTime prevCreatedDate = null;
        EntitlementState previousState = null;
        PriceList previousPriceList = null;
        Plan previousPlan = null;
        PlanPhase previousPhase = null;
        Integer prevBcdLocal = null;
        Integer prevQuantity = null;

        // Track each time we change Plan to fetch the Plan from the right catalog version
        DateTime lastPlanChangeTime = null;

        final NextBillingCycleDayLocal nextBillingCycleDayLocal = new NextBillingCycleDayLocal(inputEvents);

        for (final SubscriptionBaseEvent cur : inputEvents) {

            nextBcdLocal = nextBillingCycleDayLocal.getNextBillingCycleDayLocal(cur.getEffectiveDate());
            nextQuantity = nextBillingCycleDayLocal.getNextQuantity(cur.getEffectiveDate());

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
                case QUANTITY_UPDATE:
                    // Skip, taken into account from NextBillingCycleDayLocal
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
                            lastPlanChangeTime = cur.getEffectiveDate();
                            break;

                        case CHANGE:
                            nextPlanName = userEV.getEventPlan();
                            nextPhaseName = userEV.getEventPlanPhase();
                            lastPlanChangeTime = cur.getEffectiveDate();
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
                case EXPIRED:
                    nextState = EntitlementState.EXPIRED;
                    nextPlanName = null;
                    nextPhaseName = null;
                    break;
                default:
                    throw new SubscriptionBaseError(String.format(
                            "Unexpected Event type = %s", cur.getType()));
            }

            final Plan nextPlan = (nextPlanName != null && cur.isActive()) ? catalog.findPlan(nextPlanName, cur.getEffectiveDate(), lastPlanChangeTime) : null;
            final PlanPhase nextPhase = (nextPlan != null && nextPhaseName != null && cur.isActive()) ? nextPlan.findPhase(nextPhaseName) : null;
            final PriceList nextPriceList = (nextPlan != null && cur.isActive()) ? nextPlan.getPriceList() : null;

            final SubscriptionBaseTransitionData transition = new SubscriptionBaseTransitionData(
                    cur.getId(), id, bundleId, bundleExternalKey, cur.getType(), apiEventType,
                    cur.getEffectiveDate(),
                    prevEventId, prevCreatedDate,
                    previousState, previousPlan, previousPhase,
                    previousPriceList,
                    prevBcdLocal,
                    prevQuantity,
                    nextEventId, nextCreatedDate,
                    nextState, nextPlan, nextPhase,
                    nextPriceList,
                    nextBcdLocal,
                    nextQuantity,
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
            prevBcdLocal = nextBcdLocal;
            prevQuantity = nextQuantity;

        }

    }

    // Skip any event after a CANCEL event:
    //
    //  * DefaultSubscriptionDao#buildBundleSubscriptions may have added an out-of-order cancellation event (https://github.com/killbill/killbill/issues/897)
    //  * Hardening against data integrity issues where we have multiple active CANCEL (https://github.com/killbill/killbill/issues/619)
    //
    private void removeEverythingPastCancelEvent(final List<SubscriptionBaseEvent> inputEvents) {
        final SubscriptionBaseEvent cancellationEvent = inputEvents.stream()
                                                                   .filter(input -> input.getType() == EventType.API_USER && ((ApiEvent) input).getApiEventType() == ApiEventType.CANCEL)
                                                                   .findFirst().orElse(null);
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
