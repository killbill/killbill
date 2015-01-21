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

package org.killbill.billing.subscription.api.user;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Catalog;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.PriceList;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.clock.Clock;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementSourceType;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseApiService;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;
import org.killbill.billing.subscription.api.user.SubscriptionBaseTransitionDataIterator.Kind;
import org.killbill.billing.subscription.api.user.SubscriptionBaseTransitionDataIterator.Order;
import org.killbill.billing.subscription.api.user.SubscriptionBaseTransitionDataIterator.TimeLimit;
import org.killbill.billing.subscription.api.user.SubscriptionBaseTransitionDataIterator.Visibility;
import org.killbill.billing.subscription.events.SubscriptionBaseEvent;
import org.killbill.billing.subscription.events.SubscriptionBaseEvent.EventType;
import org.killbill.billing.subscription.events.phase.PhaseEvent;
import org.killbill.billing.subscription.events.user.ApiEvent;
import org.killbill.billing.subscription.events.user.ApiEventType;
import org.killbill.billing.subscription.exceptions.SubscriptionBaseError;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.entity.EntityBase;

public class DefaultSubscriptionBase extends EntityBase implements SubscriptionBase {

    private static final Logger log = LoggerFactory.getLogger(DefaultSubscriptionBase.class);

    private final Clock clock;
    private final SubscriptionBaseApiService apiService;

    //
    // Final subscription fields
    //
    private final UUID bundleId;
    private final DateTime alignStartDate;
    private final DateTime bundleStartDate;
    private final ProductCategory category;

    //
    // Those can be modified through non User APIs, and a new SubscriptionBase
    // object would be created
    //
    private final long activeVersion;
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
        this.alignStartDate = builder.getAlignStartDate();
        this.bundleStartDate = builder.getBundleStartDate();
        this.category = builder.getCategory();
        this.activeVersion = builder.getActiveVersion();
        this.chargedThroughDate = builder.getChargedThroughDate();
    }

    // Used for API to make sure we have a clock and an apiService set before we return the object
    public DefaultSubscriptionBase(final DefaultSubscriptionBase internalSubscription, final SubscriptionBaseApiService apiService, final Clock clock) {
        super(internalSubscription.getId(), internalSubscription.getCreatedDate(), internalSubscription.getUpdatedDate());
        this.apiService = apiService;
        this.clock = clock;
        this.bundleId = internalSubscription.getBundleId();
        this.alignStartDate = internalSubscription.getAlignStartDate();
        this.bundleStartDate = internalSubscription.getBundleStartDate();
        this.category = internalSubscription.getCategory();
        this.activeVersion = internalSubscription.getActiveVersion();
        this.chargedThroughDate = internalSubscription.getChargedThroughDate();
        this.transitions = new LinkedList<SubscriptionBaseTransition>(internalSubscription.getAllTransitions());
        this.events = internalSubscription.getEvents();
    }

    @Override
    public UUID getBundleId() {
        return bundleId;
    }

    @Override
    public DateTime getStartDate() {
        return transitions.get(0).getEffectiveTransitionTime();
    }

    @Override
    public EntitlementState getState() {
        return (getPreviousTransition() == null) ? null
                                                 : getPreviousTransition().getNextState();
    }

    @Override
    public EntitlementSourceType getSourceType() {
        if (transitions == null) {
            return null;
        }
        final SubscriptionBaseTransitionData initialTransition = (SubscriptionBaseTransitionData) transitions.get(0);
        switch (initialTransition.getApiEventType()) {
            case MIGRATE_BILLING:
            case MIGRATE_ENTITLEMENT:
                return EntitlementSourceType.MIGRATED;
            case TRANSFER:
                return EntitlementSourceType.TRANSFERRED;
            default:
                return EntitlementSourceType.NATIVE;
        }
    }

    @Override
    public PlanPhase getCurrentPhase() {
        return (getPreviousTransition() == null) ? null
                                                 : getPreviousTransition().getNextPhase();
    }

    @Override
    public Plan getCurrentPlan() {
        return (getPreviousTransition() == null) ? null
                                                 : getPreviousTransition().getNextPlan();
    }

    @Override
    public PriceList getCurrentPriceList() {
        return (getPreviousTransition() == null) ? null :
               getPreviousTransition().getNextPriceList();

    }

    @Override
    public DateTime getEndDate() {
        final SubscriptionBaseTransition latestTransition = getPreviousTransition();
        if (latestTransition.getNextState() == EntitlementState.CANCELLED) {
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
                clock, transitions, Order.ASC_FROM_PAST, Kind.SUBSCRIPTION,
                Visibility.ALL, TimeLimit.FUTURE_ONLY);
        while (it.hasNext()) {
            final SubscriptionBaseTransition cur = it.next();
            if (cur.getTransitionType() == SubscriptionBaseTransitionType.CANCEL) {
                return cur.getEffectiveTransitionTime();
            }
        }
        return null;
    }

    public boolean recreate(final PlanPhaseSpecifier spec, final DateTime requestedDate,
                            final CallContext context) throws SubscriptionBaseApiException {
        return apiService.recreatePlan(this, spec, requestedDate, context);
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
    public DateTime changePlan(final String productName, final BillingPeriod term, final String priceList,
                               final CallContext context) throws SubscriptionBaseApiException {
        return apiService.changePlan(this, productName, term, priceList, context);
    }

    @Override
    public DateTime changePlanWithDate(final String productName, final BillingPeriod term, final String priceList,
                                       final DateTime requestedDate, final CallContext context) throws SubscriptionBaseApiException {
        return apiService.changePlanWithRequestedDate(this, productName, term, priceList, requestedDate, context);
    }

    @Override
    public DateTime changePlanWithPolicy(final String productName, final BillingPeriod term, final String priceList,
                                         final BillingActionPolicy policy, final CallContext context) throws SubscriptionBaseApiException {
        return apiService.changePlanWithPolicy(this, productName, term, priceList, policy, context);
    }

    @Override
    public SubscriptionBaseTransition getPendingTransition() {
        if (transitions == null) {
            return null;
        }
        final SubscriptionBaseTransitionDataIterator it = new SubscriptionBaseTransitionDataIterator(
                clock, transitions, Order.ASC_FROM_PAST, Kind.SUBSCRIPTION,
                Visibility.ALL, TimeLimit.FUTURE_ONLY);
        return it.hasNext() ? it.next() : null;
    }

    @Override
    public Product getLastActiveProduct() {
        if (getState() == EntitlementState.CANCELLED) {
            final SubscriptionBaseTransition data = getPreviousTransition();
            return data.getPreviousPlan().getProduct();
        } else {
            return getCurrentPlan().getProduct();
        }
    }

    @Override
    public PriceList getLastActivePriceList() {
        if (getState() == EntitlementState.CANCELLED) {
            final SubscriptionBaseTransition data = getPreviousTransition();
            return data.getPreviousPriceList();
        } else {
            return getCurrentPriceList();
        }
    }

    @Override
    public ProductCategory getLastActiveCategory() {
        if (getState() == EntitlementState.CANCELLED) {
            final SubscriptionBaseTransition data = getPreviousTransition();
            return data.getPreviousPlan().getProduct().getCategory();
        } else {
            return getCurrentPlan().getProduct().getCategory();
        }
    }

    @Override
    public Plan getLastActivePlan() {
        if (getState() == EntitlementState.CANCELLED) {
            final SubscriptionBaseTransition data = getPreviousTransition();
            return data.getPreviousPlan();
        } else {
            return getCurrentPlan();
        }
    }

    @Override
    public PlanPhase getLastActivePhase() {
        if (getState() == EntitlementState.CANCELLED) {
            final SubscriptionBaseTransition data = getPreviousTransition();
            return data.getPreviousPhase();
        } else {
            return getCurrentPhase();
        }
    }

    @Override
    public BillingPeriod getLastActiveBillingPeriod() {
        if (getState() == EntitlementState.CANCELLED) {
            final SubscriptionBaseTransition data = getPreviousTransition();
            return data.getPreviousPlan().getRecurringBillingPeriod();
        } else {
            return getCurrentPlan().getRecurringBillingPeriod();
        }
    }

    @Override
    public SubscriptionBaseTransition getPreviousTransition() {
        if (transitions == null) {
            return null;
        }
        final SubscriptionBaseTransitionDataIterator it = new SubscriptionBaseTransitionDataIterator(
                clock, transitions, Order.DESC_FROM_FUTURE, Kind.SUBSCRIPTION,
                Visibility.FROM_DISK_ONLY, TimeLimit.PAST_OR_PRESENT_ONLY);
        return it.hasNext() ? it.next() : null;
    }

    @Override
    public ProductCategory getCategory() {
        return category;
    }

    public DateTime getBundleStartDate() {
        return bundleStartDate;
    }

    @Override
    public DateTime getChargedThroughDate() {
        return chargedThroughDate;
    }

    @Override
    public List<SubscriptionBaseTransition> getAllTransitions() {
        if (transitions == null) {
            return Collections.emptyList();
        }
        final List<SubscriptionBaseTransition> result = new ArrayList<SubscriptionBaseTransition>();
        final SubscriptionBaseTransitionDataIterator it = new SubscriptionBaseTransitionDataIterator(clock, transitions, Order.ASC_FROM_PAST, Kind.ALL, Visibility.ALL, TimeLimit.ALL);
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
        // Since UNCANCEL are not part of the transitions, we compute a new 'UNCANCEL' transition based on the event right before that UNCANCEL
        // This is used to be able to send a bus event for uncancellation
        if (prev != null && event.getType() == EventType.API_USER && ((ApiEvent) event).getEventType() == ApiEventType.UNCANCEL) {
            final SubscriptionBaseTransitionData withSeq = new SubscriptionBaseTransitionData((SubscriptionBaseTransitionData) prev, EventType.API_USER, ApiEventType.UNCANCEL, seqId);
            return withSeq;
        }
        return null;
    }

    public DateTime getAlignStartDate() {
        return alignStartDate;
    }

    public long getLastEventOrderedId() {
        final SubscriptionBaseTransitionDataIterator it = new SubscriptionBaseTransitionDataIterator(
                clock, transitions, Order.DESC_FROM_FUTURE, Kind.SUBSCRIPTION,
                Visibility.FROM_DISK_ONLY, TimeLimit.ALL);
        return it.hasNext() ? ((SubscriptionBaseTransitionData) it.next()).getTotalOrdering() : -1L;
    }

    public long getActiveVersion() {
        return activeVersion;
    }

    public List<SubscriptionBaseTransition> getBillingTransitions() {

        if (transitions == null) {
            return Collections.emptyList();
        }
        final List<SubscriptionBaseTransition> result = new ArrayList<SubscriptionBaseTransition>();
        final SubscriptionBaseTransitionDataIterator it = new SubscriptionBaseTransitionDataIterator(
                clock, transitions, Order.ASC_FROM_PAST, Kind.BILLING,
                Visibility.ALL, TimeLimit.ALL);
        // Remove anything prior to first CREATE or MIGRATE_BILLING
        boolean foundInitialEvent = false;
        while (it.hasNext()) {
            final SubscriptionBaseTransitionData curTransition = (SubscriptionBaseTransitionData) it.next();
            if (!foundInitialEvent) {
                foundInitialEvent = curTransition.getEventType() == EventType.API_USER &&
                                    (curTransition.getApiEventType() == ApiEventType.CREATE ||
                                     curTransition.getApiEventType() == ApiEventType.MIGRATE_BILLING ||
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
                                                                                                     Kind.SUBSCRIPTION,
                                                                                                     Visibility.ALL,
                                                                                                     TimeLimit.PAST_OR_PRESENT_ONLY);

        while (it.hasNext()) {
            final SubscriptionBaseTransitionData cur = (SubscriptionBaseTransitionData) it.next();
            if (cur.getTransitionType() == SubscriptionBaseTransitionType.CREATE
                || cur.getTransitionType() == SubscriptionBaseTransitionType.RE_CREATE
                || cur.getTransitionType() == SubscriptionBaseTransitionType.TRANSFER
                || cur.getTransitionType() == SubscriptionBaseTransitionType.CHANGE
                || cur.getTransitionType() == SubscriptionBaseTransitionType.MIGRATE_ENTITLEMENT) {
                return cur;
            }
        }

        throw new SubscriptionBaseError(String.format("Failed to find InitialTransitionForCurrentPlan id = %s", getId()));
    }

    public boolean isSubscriptionFutureCancelled() {
        return getFutureEndDate() != null;
    }

    public DateTime getPlanChangeEffectiveDate(final BillingActionPolicy policy) {
        switch (policy) {
            case IMMEDIATE:
                return clock.getUTCNow();
            case END_OF_TERM:
                //
                // If we have a chargedThroughDate that is 'up to date' we use it, if not default to now
                // chargedThroughDate could exist and be less than now if:
                // 1. account is not being invoiced, for e.g AUTO_INVOICING_OFF nis set
                // 2. In the case if FIXED item CTD is set using startDate of the service period
                //
                return (chargedThroughDate != null && chargedThroughDate.isAfter(clock.getUTCNow())) ? chargedThroughDate : clock.getUTCNow();
            default:
                throw new SubscriptionBaseError(String.format(
                        "Unexpected policy type %s", policy.toString()));
        }
    }

    public DateTime getCurrentPhaseStart() {

        if (transitions == null) {
            throw new SubscriptionBaseError(String.format(
                    "No transitions for subscription %s", getId()));
        }
        final SubscriptionBaseTransitionDataIterator it = new SubscriptionBaseTransitionDataIterator(
                clock, transitions, Order.DESC_FROM_FUTURE, Kind.SUBSCRIPTION,
                Visibility.ALL, TimeLimit.PAST_OR_PRESENT_ONLY);
        while (it.hasNext()) {
            final SubscriptionBaseTransitionData cur = (SubscriptionBaseTransitionData) it.next();

            if (cur.getTransitionType() == SubscriptionBaseTransitionType.PHASE
                || cur.getTransitionType() == SubscriptionBaseTransitionType.TRANSFER
                || cur.getTransitionType() == SubscriptionBaseTransitionType.CREATE
                || cur.getTransitionType() == SubscriptionBaseTransitionType.RE_CREATE
                || cur.getTransitionType() == SubscriptionBaseTransitionType.CHANGE
                || cur.getTransitionType() == SubscriptionBaseTransitionType.MIGRATE_ENTITLEMENT) {
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

        UUID nextUserToken = null;

        UUID nextEventId = null;
        DateTime nextCreatedDate = null;
        EntitlementState nextState = null;
        String nextPlanName = null;
        String nextPhaseName = null;
        String nextPriceListName = null;

        UUID prevEventId = null;
        DateTime prevCreatedDate = null;
        EntitlementState previousState = null;
        PriceList previousPriceList = null;
        Plan previousPlan = null;
        PlanPhase previousPhase = null;

        transitions = new LinkedList<SubscriptionBaseTransition>();

        for (final SubscriptionBaseEvent cur : inputEvents) {

            if (!cur.isActive() || cur.getActiveVersion() < activeVersion) {
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

                case API_USER:
                    final ApiEvent userEV = (ApiEvent) cur;
                    apiEventType = userEV.getEventType();
                    isFromDisk = userEV.isFromDisk();

                    switch (apiEventType) {
                        case TRANSFER:
                        case MIGRATE_BILLING:
                        case MIGRATE_ENTITLEMENT:
                        case CREATE:
                        case RE_CREATE:
                            prevEventId = null;
                            prevCreatedDate = null;
                            previousState = null;
                            previousPlan = null;
                            previousPhase = null;
                            previousPriceList = null;
                            nextState = EntitlementState.ACTIVE;
                            nextPlanName = userEV.getEventPlan();
                            nextPhaseName = userEV.getEventPlanPhase();
                            nextPriceListName = userEV.getPriceList();
                            break;
                        case CHANGE:
                            nextPlanName = userEV.getEventPlan();
                            nextPhaseName = userEV.getEventPlanPhase();
                            nextPriceListName = userEV.getPriceList();
                            break;
                        case CANCEL:
                            nextState = EntitlementState.CANCELLED;
                            nextPlanName = null;
                            nextPhaseName = null;
                            break;
                        case UNCANCEL:
                        default:
                            throw new SubscriptionBaseError(String.format(
                                    "Unexpected UserEvent type = %s", userEV
                                    .getEventType().toString()));
                    }
                    break;
                default:
                    throw new SubscriptionBaseError(String.format(
                            "Unexpected Event type = %s", cur.getType()));
            }

            Plan nextPlan = null;
            PlanPhase nextPhase = null;
            PriceList nextPriceList = null;

            nextPlan = (nextPlanName != null) ? catalog.findPlan(nextPlanName, cur.getRequestedDate(), getAlignStartDate()) : null;
            nextPhase = (nextPhaseName != null) ? catalog.findPhase(nextPhaseName, cur.getRequestedDate(), getAlignStartDate()) : null;
            nextPriceList = (nextPriceListName != null) ? catalog.findPriceList(nextPriceListName, cur.getRequestedDate()) : null;

            final SubscriptionBaseTransitionData transition = new SubscriptionBaseTransitionData(
                    cur.getId(), id, bundleId, cur.getType(), apiEventType,
                    cur.getRequestedDate(), cur.getEffectiveDate(),
                    prevEventId, prevCreatedDate,
                    previousState, previousPlan, previousPhase,
                    previousPriceList,
                    nextEventId, nextCreatedDate,
                    nextState, nextPlan, nextPhase,
                    nextPriceList, cur.getTotalOrdering(),
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

        }
    }
}
