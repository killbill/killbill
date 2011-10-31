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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import com.ning.billing.ErrorCode;
import com.ning.billing.account.api.IAccount;
import org.joda.time.DateTime;

import com.ning.billing.catalog.api.ActionPolicy;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.ICatalog;
import com.ning.billing.catalog.api.IPlan;
import com.ning.billing.catalog.api.IPlanPhase;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PlanSpecifier;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.alignment.IPlanAligner;
import com.ning.billing.entitlement.alignment.IPlanAligner.TimedPhase;
import com.ning.billing.entitlement.engine.core.Engine;
import com.ning.billing.entitlement.engine.dao.IEntitlementDao;
import com.ning.billing.entitlement.events.IEvent;
import com.ning.billing.entitlement.events.IEvent.EventType;
import com.ning.billing.entitlement.events.phase.IPhaseEvent;
import com.ning.billing.entitlement.events.phase.PhaseEvent;
import com.ning.billing.entitlement.events.user.ApiEventCancel;
import com.ning.billing.entitlement.events.user.ApiEventChange;
import com.ning.billing.entitlement.events.user.ApiEventType;
import com.ning.billing.entitlement.events.user.ApiEventUncancel;
import com.ning.billing.entitlement.events.user.IUserEvent;
import com.ning.billing.entitlement.exceptions.EntitlementError;
import com.ning.billing.util.clock.IClock;

public class Subscription extends PrivateFields  implements ISubscription {

    private final UUID id;
    private final UUID bundleId;
    private final DateTime startDate;
    private final DateTime bundleStartDate;
    private final long activeVersion;
    private final ProductCategory category;

    private final IClock clock;
    private final IEntitlementDao dao;
    private final ICatalog catalog;
    private final IPlanAligner planAligner;

    // STEPH interaction with billing /payment system
    private final DateTime chargedThroughDate;
    private final DateTime paidThroughDate;

    // STEPH non final because of change/ cancel API at the object level
    private List<SubscriptionTransition> transitions;


    public static class SubscriptionBuilder {
        private  UUID id;
        private  UUID bundleId;
        private  DateTime startDate;
        private  DateTime bundleStartDate;
        private  Long activeVersion;
        private  ProductCategory category;
        private  DateTime chargedThroughDate;
        private  DateTime paidThroughDate;

        public SubscriptionBuilder setId(UUID id) {
            this.id = id;
            return this;
        }
        public SubscriptionBuilder setBundleId(UUID bundleId) {
            this.bundleId = bundleId;
            return this;
        }
        public SubscriptionBuilder setStartDate(DateTime startDate) {
            this.startDate = startDate;
            return this;
        }
        public SubscriptionBuilder setBundleStartDate(DateTime bundleStartDate) {
            this.bundleStartDate = bundleStartDate;
            return this;
            }
        public SubscriptionBuilder setActiveVersion(long activeVersion) {
            this.activeVersion = activeVersion;
            return this;
        }
        public SubscriptionBuilder setChargedThroughDate(DateTime chargedThroughDate) {
            this.chargedThroughDate = chargedThroughDate;
            return this;
        }
        public SubscriptionBuilder setPaidThroughDate(DateTime paidThroughDate) {
            this.paidThroughDate = paidThroughDate;
            return this;
        }
        public SubscriptionBuilder setCategory(ProductCategory category) {
            this.category = category;
            return this;
        }

        private void checkAllFieldsSet() {
            for (Field cur : SubscriptionBuilder.class.getDeclaredFields()) {
                try {
                    Object value = cur.get(this);
                    if (value == null) {
                        throw new EntitlementError(String.format("Field %s has not been set for Subscription",
                                cur.getName()));
                    }
                } catch (IllegalAccessException e) {
                    throw new EntitlementError(String.format("Failed to access value for field %s for Subscription",
                            cur.getName()), e);
                }
            }
        }

        public Subscription build() {
            //checkAllFieldsSet();
            return new Subscription(id, bundleId, category, bundleStartDate, startDate, chargedThroughDate, paidThroughDate, activeVersion);
        }

    }

    public Subscription(UUID bundleId, ProductCategory category, DateTime bundleStartDate, DateTime startDate) {
        this(UUID.randomUUID(), bundleId, category, bundleStartDate, startDate, null, null, SubscriptionEvents.INITIAL_VERSION);
    }


    public Subscription(UUID id, UUID bundleId, ProductCategory category, DateTime bundleStartDate, DateTime startDate, DateTime ctd, DateTime ptd, long activeVersion) {

        super();

        Engine engine = Engine.getInstance();
        this.clock = engine.getClock();
        this.dao = engine.getDao();
        this.catalog = engine.getCatalog();
        this.planAligner = engine.getPlanAligner();
        this.id = id;
        this.bundleId = bundleId;
        this.startDate = startDate;
        this.bundleStartDate = bundleStartDate;
        this.category = category;

        this.activeVersion = activeVersion;

        this.chargedThroughDate = ctd;
        this.paidThroughDate = ptd;

        rebuildTransitions();
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
    public void cancel() throws EntitlementUserApiException  {

        SubscriptionState currentState = getState();
        if (currentState != SubscriptionState.ACTIVE) {
            throw new EntitlementUserApiException(ErrorCode.ENT_CANCEL_BAD_STATE, id, currentState);
        }

        DateTime now = clock.getUTCNow();

        IPlan currentPlan = getCurrentPlan();
        PlanPhaseSpecifier planPhase = new PlanPhaseSpecifier(currentPlan.getProduct().getName(),
                currentPlan.getProduct().getCategory(),
        		getCurrentPlan().getBillingPeriod(),
        		getCurrentPriceList(),
        		getCurrentPhase().getPhaseType());

        ActionPolicy policy = catalog.getPlanCancelPolicy(planPhase);
        DateTime effectiveDate = getPlanChangeEffectiveDate(policy, now);
        IEvent cancelEvent = new ApiEventCancel(id, bundleStartDate, now, now, effectiveDate, activeVersion);
        dao.cancelSubscription(id, cancelEvent);
        rebuildTransitions();
    }

    @Override
    public void uncancel() throws EntitlementUserApiException {
        if (!isSubscriptionFutureCancelled()) {
            throw new EntitlementUserApiException(ErrorCode.ENT_UNCANCEL_BAD_STATE, id.toString());
        }
        DateTime now = clock.getUTCNow();
        IEvent uncancelEvent = new ApiEventUncancel(id, bundleStartDate, now, now, now, activeVersion);
        List<IEvent> uncancelEvents = new ArrayList<IEvent>();
        uncancelEvents.add(uncancelEvent);

        DateTime planStartDate = getCurrentPlanStart();
        TimedPhase nextTimedPhase = planAligner.getNextTimedPhase(this, getCurrentPlan(), now, planStartDate);
        IPhaseEvent nextPhaseEvent = PhaseEvent.getNextPhaseEvent(nextTimedPhase, this, now);
        if (nextPhaseEvent != null) {
            uncancelEvents.add(nextPhaseEvent);
        }
        dao.uncancelSubscription(id, uncancelEvents);
        rebuildTransitions();
    }

    @Override
    public void changePlan(String productName, BillingPeriod term,
            String priceList) throws EntitlementUserApiException {

        String currentPriceList = getCurrentPriceList();
        String realPriceList = (priceList == null) ? currentPriceList : priceList;

        SubscriptionState currentState = getState();
        if (currentState != SubscriptionState.ACTIVE) {
            throw new EntitlementUserApiException(ErrorCode.ENT_CHANGE_NON_ACTIVE, id, currentState);
        }

        if (isSubscriptionFutureCancelled()) {
            throw new EntitlementUserApiException(ErrorCode.ENT_CHANGE_FUTURE_CANCELLED, id);
        }

        DateTime now = clock.getUTCNow();
        IPlan newPlan = catalog.getPlan(productName, term, realPriceList);
        if (newPlan == null) {
            throw new EntitlementUserApiException(ErrorCode.ENT_CREATE_BAD_CATALOG,
                    productName, term.toString(), realPriceList);
        }

        IPlan currentPlan = getCurrentPlan();
        PlanPhaseSpecifier fromPlanPhase = new PlanPhaseSpecifier(currentPlan.getProduct().getName(),
        		currentPlan.getProduct().getCategory(),
                currentPlan.getBillingPeriod(),
        		currentPriceList, getCurrentPhase().getPhaseType());
        PlanSpecifier toPlanPhase = new PlanSpecifier(newPlan.getProduct().getName(),
                newPlan.getProduct().getCategory(),
        		newPlan.getBillingPeriod(),
        		realPriceList);

        ActionPolicy policy = catalog.getPlanChangePolicy(fromPlanPhase, toPlanPhase);
        DateTime effectiveDate = getPlanChangeEffectiveDate(policy, now);

        TimedPhase currentTimedPhase = planAligner.getCurrentTimedPhaseOnChange(this, newPlan, realPriceList, effectiveDate);
        IEvent changeEvent = new ApiEventChange(id, bundleStartDate, now, newPlan.getName(), currentTimedPhase.getPhase().getName(),
                realPriceList, now, effectiveDate, activeVersion);

        TimedPhase nextTimedPhase = planAligner.getNextTimedPhaseOnChange(this, newPlan, realPriceList, effectiveDate);
        IPhaseEvent nextPhaseEvent = PhaseEvent.getNextPhaseEvent(nextTimedPhase, this, now);
        List<IEvent> changeEvents = new ArrayList<IEvent>();
        // Add phase event first so we expect to see PHASE event first-- mostly for test expectation
        if (nextPhaseEvent != null) {
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
            return null;
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

    // STEPH do we need that? forgot?
    private boolean isSubscriptionFutureChanged() {
        if (transitions == null) {
            return false;
        }

        for (SubscriptionTransition cur : transitions) {
            if (cur.getEffectiveTransitionTime().isBefore(clock.getUTCNow()) ||
                    cur.getEventType() == EventType.PHASE ||
                    cur.getApiEventType() != ApiEventType.CHANGE) {
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
                IUserEvent userEV = (IUserEvent) cur;
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
                new SubscriptionTransition(id, bundleId, cur.getType(), apiEventType,
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
