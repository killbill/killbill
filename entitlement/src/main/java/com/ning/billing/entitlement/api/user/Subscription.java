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

package com.ning.billing.entitlement.api.user;

import com.ning.billing.ErrorCode;
import com.ning.billing.catalog.api.*;
import com.ning.billing.entitlement.alignment.IPlanAligner;
import com.ning.billing.entitlement.alignment.IPlanAligner.TimedPhase;
import com.ning.billing.entitlement.engine.dao.IEntitlementDao;
import com.ning.billing.entitlement.events.IEvent;
import com.ning.billing.entitlement.events.IEvent.EventType;
import com.ning.billing.entitlement.events.phase.IPhaseEvent;
import com.ning.billing.entitlement.events.phase.PhaseEvent;
import com.ning.billing.entitlement.events.user.*;
import com.ning.billing.entitlement.exceptions.EntitlementError;
import com.ning.billing.entitlement.glue.InjectorMagic;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.IClock;
import org.joda.time.DateTime;

import java.util.*;

public class Subscription extends PrivateFields  implements ISubscription {

    //
    // Singletons used to perform API changes
    private final IClock clock;
    private final IEntitlementDao dao;
    private final ICatalog catalog;
    private final IPlanAligner planAligner;

    //
    // Final subscription fields
    //
    private final UUID id;
    private final UUID bundleId;
    private final DateTime startDate;
    private final DateTime bundleStartDate;
    private final ProductCategory category;

    //
    // Those can be modified through non User APIs, and a new Subscription object would be created
    //
    private final long activeVersion;
    private final DateTime chargedThroughDate;
    private final DateTime paidThroughDate;

    //
    // User APIs (createm chnage, cancel,...) will recompute those each time,
    // so the user holding that subscription object get the correct state when
    // the call completes
    //
    private List<SubscriptionTransition> transitions;

    public Subscription(SubscriptionBuilder builder, boolean rebuildTransition) {
        super();
        
        /**
         * Why are these found via static lookup rather than passed in via DI? 
         * See http://martinfowler.com/articles/injection.html for explanation of
         * why DI is your friend. -brianm
         */
        this.clock = InjectorMagic.getClock();
        this.dao = InjectorMagic.getEntitlementDao();
        this.catalog = InjectorMagic.getCatlog();
        this.planAligner = InjectorMagic.getPlanAligner();
        
        this.id = builder.getId();
        this.bundleId = builder.getBundleId();
        this.startDate = builder.getStartDate();
        this.bundleStartDate = builder.getBundleStartDate();
        this.category = builder.getCategory();
        this.activeVersion = builder.getActiveVersion();
        this.chargedThroughDate = builder.getChargedThroughDate();
        this.paidThroughDate = builder.getPaidThroughDate();
        if (rebuildTransition) {
            rebuildTransitions();
        }
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public UUID getBundleId() {
        return bundleId;
    }


    @Override
    public DateTime getStartDate() {
        return startDate;
    }


    @Override
    public SubscriptionState getState() {
        return (transitions == null) ? null : getLatestTranstion().getNextState();
    }

    @Override
    public IPlanPhase getCurrentPhase() {
        return (transitions == null) ? null : getLatestTranstion().getNextPhase();
    }


    @Override
    public IPlan getCurrentPlan() {
        return (transitions == null) ? null : getLatestTranstion().getNextPlan();
    }

    @Override
    public String getCurrentPriceList() {
        return (transitions == null) ? null : getLatestTranstion().getNextPriceList();
    }


    @Override
    public DateTime getEndDate() {
        ISubscriptionTransition latestTransition = getLatestTranstion();
        if (latestTransition.getNextState() == SubscriptionState.CANCELLED) {
            return latestTransition.getEffectiveTransitionTime();
        }
        return null;
    }


    @Override
    public void cancel(DateTime requestedDate, boolean eot) throws EntitlementUserApiException  {

        SubscriptionState currentState = getState();
        if (currentState != SubscriptionState.ACTIVE) {
            throw new EntitlementUserApiException(ErrorCode.ENT_CANCEL_BAD_STATE, id, currentState);
        }

        DateTime now = clock.getUTCNow();
        requestedDate = (requestedDate != null) ? Clock.truncateMs(requestedDate) : null;
        if (requestedDate != null && requestedDate.isAfter(now)) {
            throw new EntitlementUserApiException(ErrorCode.ENT_INVALID_REQUESTED_DATE, requestedDate.toString());
        }

        IPlan currentPlan = getCurrentPlan();
        PlanPhaseSpecifier planPhase = new PlanPhaseSpecifier(currentPlan.getProduct().getName(),
                currentPlan.getProduct().getCategory(),
        		getCurrentPlan().getBillingPeriod(),
        		getCurrentPriceList(),
        		getCurrentPhase().getPhaseType());

        ActionPolicy policy = catalog.getPlanCancelPolicy(planPhase);
        DateTime effectiveDate = getPlanChangeEffectiveDate(policy, now);

        IEvent cancelEvent = new ApiEventCancel(new ApiEventBuilder()
        .setSubscriptionId(id)
        .setActiveVersion(activeVersion)
        .setProcessedDate(now)
        .setEffectiveDate(effectiveDate)
        .setRequestedDate(now));

        dao.cancelSubscription(id, cancelEvent);
        rebuildTransitions();
    }

    @Override
    public void uncancel() throws EntitlementUserApiException {
        if (!isSubscriptionFutureCancelled()) {
            throw new EntitlementUserApiException(ErrorCode.ENT_UNCANCEL_BAD_STATE, id.toString());
        }
        DateTime now = clock.getUTCNow();
        IEvent uncancelEvent = new ApiEventUncancel(new ApiEventBuilder()
            .setSubscriptionId(id)
            .setActiveVersion(activeVersion)
            .setProcessedDate(now)
            .setRequestedDate(now)
            .setEffectiveDate(now));

        List<IEvent> uncancelEvents = new ArrayList<IEvent>();
        uncancelEvents.add(uncancelEvent);

        DateTime planStartDate = getCurrentPlanStart();
        TimedPhase nextTimedPhase = planAligner.getNextTimedPhase(this, getCurrentPlan(), now, planStartDate);
        IPhaseEvent nextPhaseEvent = PhaseEvent.getNextPhaseEvent(nextTimedPhase, this, now);
        if (nextPhaseEvent != null) {
            uncancelEvents.add(nextPhaseEvent);
        }
        
        /**
         * I think you might be better of disentangling state storage from business logic. 
         * This happens in a number of places as well as here (such as the subsequent call to
         * rebuildTransitions() so assume this comment applies at all such places :-) -brianm
         */
        dao.uncancelSubscription(id, uncancelEvents);
        rebuildTransitions();
    }

    @Override
    public void changePlan(String productName, BillingPeriod term,
            String priceList, DateTime requestedDate) throws EntitlementUserApiException {

        requestedDate = (requestedDate != null) ? Clock.truncateMs(requestedDate) : null;
        String currentPriceList = getCurrentPriceList();

        SubscriptionState currentState = getState();
        if (currentState != SubscriptionState.ACTIVE) {
            throw new EntitlementUserApiException(ErrorCode.ENT_CHANGE_NON_ACTIVE, id, currentState);
        }

        if (isSubscriptionFutureCancelled()) {
            throw new EntitlementUserApiException(ErrorCode.ENT_CHANGE_FUTURE_CANCELLED, id);
        }

        DateTime now = clock.getUTCNow();
        PlanChangeResult planChangeResult = null;
        try {

            IProduct destProduct = catalog.getProductFromName(productName);
            // STEPH really catalog exception
            if (destProduct == null) {
                throw new EntitlementUserApiException(ErrorCode.ENT_CREATE_BAD_CATALOG,
                        productName, term.toString(), "");
            }

            IPlan currentPlan = getCurrentPlan();
            PlanPhaseSpecifier fromPlanPhase = new PlanPhaseSpecifier(currentPlan.getProduct().getName(),
                    currentPlan.getProduct().getCategory(),
                    currentPlan.getBillingPeriod(),
                    currentPriceList, getCurrentPhase().getPhaseType());
            PlanSpecifier toPlanPhase = new PlanSpecifier(productName,
                    destProduct.getCategory(),
                    term,
                    priceList);

            planChangeResult = catalog.planChange(fromPlanPhase, toPlanPhase);
        } catch (CatalogApiException e) {
            throw new EntitlementUserApiException(e);
        }

        ActionPolicy policy = planChangeResult.getPolicy();
        IPriceList newPriceList = planChangeResult.getNewPriceList();

        IPlan newPlan = catalog.getPlan(productName, term, newPriceList.getName());
        if (newPlan == null) {
            throw new EntitlementUserApiException(ErrorCode.ENT_CREATE_BAD_CATALOG,
                    productName, term.toString(), newPriceList.getName());
        }

        DateTime effectiveDate = getPlanChangeEffectiveDate(policy, now);

        TimedPhase currentTimedPhase = planAligner.getCurrentTimedPhaseOnChange(this, newPlan, newPriceList.getName(), effectiveDate);

        IEvent changeEvent = new ApiEventChange(new ApiEventBuilder()
        .setSubscriptionId(id)
        .setEventPlan(newPlan.getName())
        .setEventPlanPhase(currentTimedPhase.getPhase().getName())
        .setEventPriceList(newPriceList.getName())
        .setActiveVersion(activeVersion)
        .setProcessedDate(now)
        .setEffectiveDate(effectiveDate)
        .setRequestedDate(now));

        TimedPhase nextTimedPhase = planAligner.getNextTimedPhaseOnChange(this, newPlan, newPriceList.getName(), effectiveDate);
        IPhaseEvent nextPhaseEvent = PhaseEvent.getNextPhaseEvent(nextTimedPhase, this, now);
        List<IEvent> changeEvents = new ArrayList<IEvent>();
        // Only add the PHASE if it does not coincide with the CHANGE, if not this is 'just' a CHANGE.
        if (nextPhaseEvent != null && ! nextPhaseEvent.getEffectiveDate().equals(changeEvent.getEffectiveDate())) {
            changeEvents.add(nextPhaseEvent);
        }
        changeEvents.add(changeEvent);
        dao.changePlan(id, changeEvents);
        rebuildTransitions();
    }

    @Override
    public void pause() throws EntitlementUserApiException {
        throw new EntitlementUserApiException(ErrorCode.NOT_IMPLEMENTED);
    }

    @Override
    public void resume() throws EntitlementUserApiException  {
        throw new EntitlementUserApiException(ErrorCode.NOT_IMPLEMENTED);
    }


    public ISubscriptionTransition getLatestTranstion() {

        if (transitions == null) {
            return null;
        }
        ISubscriptionTransition latestSubscription = null;
        for (ISubscriptionTransition cur : transitions) {
            if (cur.getEffectiveTransitionTime().isAfter(clock.getUTCNow())) {
                break;
            }
            latestSubscription = cur;
        }
        return latestSubscription;
    }

    public ISubscriptionTransition getTransitionFromEvent(IEvent event) {
        if (transitions == null || event == null) {
            return null;
        }

        for (ISubscriptionTransition cur : transitions) {
            if (cur.getId().equals(event.getId())) {
                return cur;
            }
        }
        return null;
    }

    public long getActiveVersion() {
        return activeVersion;
    }

    public ProductCategory getCategory() {
        return category;
    }

    public DateTime getBundleStartDate() {
        return bundleStartDate;
    }

    public DateTime getChargedThroughDate() {
        return chargedThroughDate;
    }

    public DateTime getPaidThroughDate() {
        return paidThroughDate;
    }

    public DateTime getCurrentPlanStart() {

        if (transitions == null) {
            throw new EntitlementError(String.format("No transitions for subscription %s", getId()));
        }

        Iterator<SubscriptionTransition> it = ((LinkedList<SubscriptionTransition>) transitions).descendingIterator();
        while (it.hasNext()) {
            SubscriptionTransition cur = it.next();
            if (cur.getEffectiveTransitionTime().isAfter(clock.getUTCNow())) {
                // Skip future events
                continue;
            }
            if (cur.getEventType() == EventType.API_USER &&
                    cur.getApiEventType() == ApiEventType.CHANGE) {
                return cur.getEffectiveTransitionTime();
            }
        }
        // CREATE event
        return transitions.get(0).getEffectiveTransitionTime();
    }

    public List<ISubscriptionTransition> getActiveTransitions() {
        if (transitions == null) {
            return Collections.emptyList();
        }

        List<ISubscriptionTransition> activeTransitions = new ArrayList<ISubscriptionTransition>();
        for (ISubscriptionTransition cur : transitions) {
            if (cur.getEffectiveTransitionTime().isAfter(clock.getUTCNow())) {
                activeTransitions.add(cur);
            }
        }
        return activeTransitions;
    }

    private boolean isSubscriptionFutureCancelled() {
        if (transitions == null) {
            return false;
        }

        for (SubscriptionTransition cur : transitions) {
            if (cur.getEffectiveTransitionTime().isBefore(clock.getUTCNow()) ||
                    cur.getEventType() == EventType.PHASE ||
                        cur.getApiEventType() != ApiEventType.CANCEL) {
                continue;
            }
            return true;
        }
        return false;
    }


    private DateTime getPlanChangeEffectiveDate(ActionPolicy policy, DateTime now) {

        if (policy == ActionPolicy.IMMEDIATE) {
            return now;
        }
        if (policy != ActionPolicy.END_OF_TERM) {
            throw new EntitlementError(String.format("Unexpected policy type %s", policy.toString()));
        }

        //
        // If CTD is null or CTD in the past, we default to the start date of the current phase
        //
        DateTime effectiveDate = chargedThroughDate;
        if (chargedThroughDate == null || chargedThroughDate.isBefore(clock.getUTCNow())) {
            effectiveDate = getCurrentPhaseStart();
        }
        return effectiveDate;
    }


    private DateTime getCurrentPhaseStart() {

        if (transitions == null) {
            throw new EntitlementError(String.format("No transitions for subscription %s", getId()));
        }

        Iterator<SubscriptionTransition> it = ((LinkedList<SubscriptionTransition>) transitions).descendingIterator();
        while (it.hasNext()) {
            SubscriptionTransition cur = it.next();
            if (cur.getEffectiveTransitionTime().isAfter(clock.getUTCNow())) {
                // Skip future events
                continue;
            }
            if (cur.getEventType() == EventType.PHASE) {
                return cur.getEffectiveTransitionTime();
            }
        }
        // CREATE event
        return transitions.get(0).getEffectiveTransitionTime();
    }

    private void rebuildTransitions() {
        List<IEvent> events = dao.getEventsForSubscription(id);
        if (events == null) {
            return;
        }

        SubscriptionState nextState = null;
        String nextPlanName = null;
        String nextPhaseName = null;
        String nextPriceList = null;

        SubscriptionState previousState = null;
        String previousPlanName = null;
        String previousPhaseName = null;
        String previousPriceList = null;

        this.transitions = new LinkedList<SubscriptionTransition>();

        for (final IEvent cur : events) {

            if (!cur.isActive() || cur.getActiveVersion() < activeVersion) {
                continue;
            }

            ApiEventType apiEventType = null;

            switch (cur.getType()) {

            case PHASE:
                IPhaseEvent phaseEV = (IPhaseEvent) cur;
                nextPhaseName = phaseEV.getPhase();
                break;

            case API_USER:
                IApiEvent userEV = (IApiEvent) cur;
                apiEventType = userEV.getEventType();
                switch(apiEventType) {
                case CREATE:
                    nextState = SubscriptionState.ACTIVE;
                    nextPlanName = userEV.getEventPlan();
                    nextPhaseName = userEV.getEventPlanPhase();
                    nextPriceList = userEV.getPriceList();
                    break;
                case CHANGE:
                    nextPlanName = userEV.getEventPlan();
                    nextPhaseName = userEV.getEventPlanPhase();
                    nextPriceList = userEV.getPriceList();
                    break;
                case PAUSE:
                    nextState = SubscriptionState.PAUSED;
                    break;
                case RESUME:
                    nextState = SubscriptionState.ACTIVE;
                    break;
                case CANCEL:
                    nextState = SubscriptionState.CANCELLED;
                    nextPlanName = null;
                    nextPhaseName = null;
                    break;
                case UNCANCEL:
                    break;
                default:
                    throw new EntitlementError(String.format("Unexpected UserEvent type = %s",
                            userEV.getEventType().toString()));
                }
                break;

            default:
                throw new EntitlementError(String.format("Unexpected Event type = %s",
                        cur.getType()));
            }

            IPlan previousPlan = catalog.getPlanFromName(previousPlanName);
            IPlanPhase previousPhase = catalog.getPhaseFromName(previousPhaseName);
            IPlan nextPlan = catalog.getPlanFromName(nextPlanName);
            IPlanPhase nextPhase = catalog.getPhaseFromName(nextPhaseName);

            SubscriptionTransition transition =
                new SubscriptionTransition(cur.getId(), id, bundleId, cur.getType(), apiEventType,
                        cur.getRequestedDate(), cur.getEffectiveDate(),
                        previousState, previousPlan, previousPhase, previousPriceList,
                        nextState, nextPlan, nextPhase, nextPriceList);
            transitions.add(transition);

            previousState = nextState;
            previousPlanName = nextPlanName;
            previousPhaseName = nextPhaseName;
            previousPriceList = nextPriceList;
        }
    }
}
