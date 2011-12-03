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

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.joda.time.DateTime;

import com.google.inject.Inject;
import com.ning.billing.ErrorCode;
import com.ning.billing.catalog.api.ActionPolicy;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.ICatalogService;
import com.ning.billing.catalog.api.IPlan;
import com.ning.billing.catalog.api.IPlanPhase;
import com.ning.billing.catalog.api.IPriceList;
import com.ning.billing.catalog.api.IProduct;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.PlanChangeResult;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PlanSpecifier;
import com.ning.billing.entitlement.alignment.PlanAligner;
import com.ning.billing.entitlement.alignment.TimedPhase;
import com.ning.billing.entitlement.api.user.Subscription.SubscriptionState;
import com.ning.billing.entitlement.api.user.SubscriptionFactory.SubscriptionBuilder;
import com.ning.billing.entitlement.engine.dao.EntitlementDao;
import com.ning.billing.entitlement.events.EntitlementEvent;
import com.ning.billing.entitlement.events.phase.PhaseEvent;
import com.ning.billing.entitlement.events.phase.PhaseEventData;
import com.ning.billing.entitlement.events.user.ApiEventBuilder;
import com.ning.billing.entitlement.events.user.ApiEventCancel;
import com.ning.billing.entitlement.events.user.ApiEventChange;
import com.ning.billing.entitlement.events.user.ApiEventCreate;
import com.ning.billing.entitlement.events.user.ApiEventUncancel;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.DefaultClock;

public class SubscriptionApiService {

    private final Clock clock;
    private final EntitlementDao dao;
    private final ICatalogService catalogService;
    private final PlanAligner planAligner;

    @Inject
    public SubscriptionApiService(Clock clock, EntitlementDao dao, ICatalogService catalogService, PlanAligner planAligner) {
        this.clock = clock;
        this.catalogService = catalogService;
        this.planAligner = planAligner;
        this.dao = dao;
    }



    public SubscriptionData createBasePlan(SubscriptionBuilder builder, IPlan plan, PhaseType initialPhase,
            String realPriceList, DateTime requestedDate, DateTime effectiveDate, DateTime processedDate)
        throws EntitlementUserApiException {

        try {
            SubscriptionData subscription = new SubscriptionData(builder, this, clock);


            TimedPhase [] curAndNextPhases = planAligner.getCurrentAndNextTimedPhaseOnCreate(subscription, plan, initialPhase, realPriceList, effectiveDate);
            ApiEventCreate creationEvent = new ApiEventCreate(new ApiEventBuilder()
            .setSubscriptionId(subscription.getId())
            .setEventPlan(plan.getName())
            .setEventPlanPhase(curAndNextPhases[0].getPhase().getName())
            .setEventPriceList(realPriceList)
            .setActiveVersion(subscription.getActiveVersion())
            .setProcessedDate(processedDate)
            .setEffectiveDate(effectiveDate)
            .setRequestedDate(requestedDate));

            PhaseEvent nextPhaseEvent = PhaseEventData.getNextPhaseEvent(curAndNextPhases[1], subscription, processedDate);
            List<EntitlementEvent> events = new ArrayList<EntitlementEvent>();
            events.add(creationEvent);
            if (nextPhaseEvent != null) {
                events.add(nextPhaseEvent);
            }
            dao.createSubscription(subscription, events);
            subscription.rebuildTransitions(events, catalogService.getCatalog());
            return subscription;
        } catch (CatalogApiException e) {
            throw new EntitlementUserApiException(e);
        }
    }

    public void cancel(SubscriptionData subscription, DateTime requestedDate, boolean eot)
        throws EntitlementUserApiException {

        try {
            SubscriptionState currentState = subscription.getState();
            if (currentState != SubscriptionState.ACTIVE) {
                throw new EntitlementUserApiException(ErrorCode.ENT_CANCEL_BAD_STATE, subscription.getId(), currentState);
            }

            DateTime now = clock.getUTCNow();
            requestedDate = (requestedDate != null) ? DefaultClock.truncateMs(requestedDate) : null;
            if (requestedDate != null && requestedDate.isAfter(now)) {
                throw new EntitlementUserApiException(ErrorCode.ENT_INVALID_REQUESTED_DATE, requestedDate.toString());
            }

            IPlan currentPlan = subscription.getCurrentPlan();
            PlanPhaseSpecifier planPhase = new PlanPhaseSpecifier(currentPlan.getProduct().getName(),
                    currentPlan.getProduct().getCategory(),
                    subscription.getCurrentPlan().getBillingPeriod(),
                    subscription.getCurrentPriceList(),
                    subscription.getCurrentPhase().getPhaseType());

            ActionPolicy policy = null;
            policy = catalogService.getCatalog().planCancelPolicy(planPhase);
            DateTime effectiveDate = subscription.getPlanChangeEffectiveDate(policy, now);

            EntitlementEvent cancelEvent = new ApiEventCancel(new ApiEventBuilder()
            .setSubscriptionId(subscription.getId())
            .setActiveVersion(subscription.getActiveVersion())
            .setProcessedDate(now)
            .setEffectiveDate(effectiveDate)
            .setRequestedDate(now));

            dao.cancelSubscription(subscription.getId(), cancelEvent);
            subscription.rebuildTransitions(dao.getEventsForSubscription(subscription.getId()), catalogService.getCatalog());
        } catch (CatalogApiException e) {
            throw new EntitlementUserApiException(e);
        }
    }


    public void uncancel(SubscriptionData subscription)
    throws EntitlementUserApiException {

        if (!subscription.isSubscriptionFutureCancelled()) {
            throw new EntitlementUserApiException(ErrorCode.ENT_UNCANCEL_BAD_STATE, subscription.getId().toString());
        }

        DateTime now = clock.getUTCNow();
        EntitlementEvent uncancelEvent = new ApiEventUncancel(new ApiEventBuilder()
        .setSubscriptionId(subscription.getId())
        .setActiveVersion(subscription.getActiveVersion())
        .setProcessedDate(now)
        .setRequestedDate(now)
        .setEffectiveDate(now));

        List<EntitlementEvent> uncancelEvents = new ArrayList<EntitlementEvent>();
        uncancelEvents.add(uncancelEvent);

        DateTime planStartDate = subscription.getCurrentPlanStart();
        TimedPhase nextTimedPhase = planAligner.getNextTimedPhase(subscription.getCurrentPlan(), subscription.getInitialPhaseOnCurrentPlan().getPhaseType(), now, planStartDate);
        PhaseEvent nextPhaseEvent = PhaseEventData.getNextPhaseEvent(nextTimedPhase, subscription, now);
        if (nextPhaseEvent != null) {
            uncancelEvents.add(nextPhaseEvent);
        }
        dao.uncancelSubscription(subscription.getId(), uncancelEvents);
        subscription.rebuildTransitions(dao.getEventsForSubscription(subscription.getId()), catalogService.getCatalog());
    }

    public void changePlan(SubscriptionData subscription, String productName, BillingPeriod term,
            String priceList, DateTime requestedDate)
        throws EntitlementUserApiException {

        try {
        requestedDate = (requestedDate != null) ? DefaultClock.truncateMs(requestedDate) : null;
        String currentPriceList = subscription.getCurrentPriceList();

        SubscriptionState currentState = subscription.getState();
        if (currentState != SubscriptionState.ACTIVE) {
            throw new EntitlementUserApiException(ErrorCode.ENT_CHANGE_NON_ACTIVE, subscription.getId(), currentState);
        }

        if (subscription.isSubscriptionFutureCancelled()) {
            throw new EntitlementUserApiException(ErrorCode.ENT_CHANGE_FUTURE_CANCELLED, subscription.getId());
        }

        DateTime now = clock.getUTCNow();
        PlanChangeResult planChangeResult = null;
        try {

            IProduct destProduct = catalogService.getCatalog().findProduct(productName);
            IPlan currentPlan = subscription.getCurrentPlan();
            PlanPhaseSpecifier fromPlanPhase = new PlanPhaseSpecifier(currentPlan.getProduct().getName(),
                    currentPlan.getProduct().getCategory(),
                    currentPlan.getBillingPeriod(),
                    currentPriceList, subscription.getCurrentPhase().getPhaseType());
            PlanSpecifier toPlanPhase = new PlanSpecifier(productName,
                    destProduct.getCategory(),
                    term,
                    priceList);

            planChangeResult = catalogService.getCatalog().planChange(fromPlanPhase, toPlanPhase);
        } catch (CatalogApiException e) {
            throw new EntitlementUserApiException(e);
        }

        ActionPolicy policy = planChangeResult.getPolicy();
        IPriceList newPriceList = planChangeResult.getNewPriceList();

        IPlan newPlan = catalogService.getCatalog().findPlan(productName, term, newPriceList.getName());
        DateTime effectiveDate = subscription.getPlanChangeEffectiveDate(policy, now);

        TimedPhase currentTimedPhase = planAligner.getCurrentTimedPhaseOnChange(subscription, newPlan, newPriceList.getName(), effectiveDate);

        EntitlementEvent changeEvent = new ApiEventChange(new ApiEventBuilder()
        .setSubscriptionId(subscription.getId())
        .setEventPlan(newPlan.getName())
        .setEventPlanPhase(currentTimedPhase.getPhase().getName())
        .setEventPriceList(newPriceList.getName())
        .setActiveVersion(subscription.getActiveVersion())
        .setProcessedDate(now)
        .setEffectiveDate(effectiveDate)
        .setRequestedDate(now));

        TimedPhase nextTimedPhase = planAligner.getNextTimedPhaseOnChange(subscription, newPlan, newPriceList.getName(), effectiveDate);
        PhaseEvent nextPhaseEvent = PhaseEventData.getNextPhaseEvent(nextTimedPhase, subscription, now);
        List<EntitlementEvent> changeEvents = new ArrayList<EntitlementEvent>();
        // Only add the PHASE if it does not coincide with the CHANGE, if not this is 'just' a CHANGE.
        if (nextPhaseEvent != null && ! nextPhaseEvent.getEffectiveDate().equals(changeEvent.getEffectiveDate())) {
            changeEvents.add(nextPhaseEvent);
        }
        changeEvents.add(changeEvent);
        dao.changePlan(subscription.getId(), changeEvents);
        subscription.rebuildTransitions(dao.getEventsForSubscription(subscription.getId()), catalogService.getCatalog());
        } catch (CatalogApiException e) {
            throw new EntitlementUserApiException(e);
        }
    }
}
