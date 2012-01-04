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

import com.google.inject.Inject;
import com.ning.billing.ErrorCode;
import com.ning.billing.catalog.api.*;
import com.ning.billing.entitlement.alignment.PlanAligner;
import com.ning.billing.entitlement.alignment.TimedPhase;
import com.ning.billing.entitlement.api.user.Subscription.SubscriptionState;
import com.ning.billing.entitlement.api.user.SubscriptionFactory.SubscriptionBuilder;
import com.ning.billing.entitlement.engine.dao.EntitlementDao;
import com.ning.billing.entitlement.events.EntitlementEvent;
import com.ning.billing.entitlement.events.phase.PhaseEvent;
import com.ning.billing.entitlement.events.phase.PhaseEventData;
import com.ning.billing.entitlement.events.user.*;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.DefaultClock;
import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.List;

public class SubscriptionApiService {

    private final Clock clock;
    private final EntitlementDao dao;
    private final CatalogService catalogService;
    private final PlanAligner planAligner;

    @Inject
    public SubscriptionApiService(Clock clock, EntitlementDao dao, CatalogService catalogService, PlanAligner planAligner) {
        this.clock = clock;
        this.catalogService = catalogService;
        this.planAligner = planAligner;
        this.dao = dao;
    }



    public SubscriptionData createBasePlan(SubscriptionBuilder builder, Plan plan, PhaseType initialPhase,
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

            TimedPhase nextTimedPhase = curAndNextPhases[1];
            PhaseEvent nextPhaseEvent = (nextTimedPhase != null) ?
                    PhaseEventData.getNextPhaseEvent(nextTimedPhase.getPhase().getName(), subscription, processedDate, nextTimedPhase.getStartPhase()) :
                        null;
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
            requestedDate = (requestedDate != null) ? DefaultClock.truncateMs(requestedDate) : now;
            // STEPH needs to check if requestedDate is before last 'erasable event'?
            if (requestedDate != null && requestedDate.isAfter(now)) {
                throw new EntitlementUserApiException(ErrorCode.ENT_INVALID_REQUESTED_DATE, requestedDate.toString());
            }

            Plan currentPlan = subscription.getCurrentPlan();
            PlanPhaseSpecifier planPhase = new PlanPhaseSpecifier(currentPlan.getProduct().getName(),
                    currentPlan.getProduct().getCategory(),
                    subscription.getCurrentPlan().getBillingPeriod(),
                    subscription.getCurrentPriceList(),
                    subscription.getCurrentPhase().getPhaseType());

            ActionPolicy policy = null;
            policy = catalogService.getCatalog().planCancelPolicy(planPhase);
            DateTime effectiveDate = subscription.getPlanChangeEffectiveDate(policy, requestedDate);

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
        PhaseEvent nextPhaseEvent = (nextTimedPhase != null) ?
                PhaseEventData.getNextPhaseEvent(nextTimedPhase.getPhase().getName(), subscription, now, nextTimedPhase.getStartPhase()) :
                    null;
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


            DateTime now = clock.getUTCNow();
            requestedDate = (requestedDate != null) ? DefaultClock.truncateMs(requestedDate) : now;
            // STEPH needs to check if requestedDate is before last 'erasable event'?
            if (requestedDate != null && requestedDate.isAfter(now)) {
                throw new EntitlementUserApiException(ErrorCode.ENT_INVALID_REQUESTED_DATE, requestedDate.toString());
            }

            String currentPriceList = subscription.getCurrentPriceList();

            SubscriptionState currentState = subscription.getState();
            if (currentState != SubscriptionState.ACTIVE) {
                throw new EntitlementUserApiException(ErrorCode.ENT_CHANGE_NON_ACTIVE, subscription.getId(), currentState);
            }

            if (subscription.isSubscriptionFutureCancelled()) {
                throw new EntitlementUserApiException(ErrorCode.ENT_CHANGE_FUTURE_CANCELLED, subscription.getId());
            }
            PlanChangeResult planChangeResult = null;
            try {

                Product destProduct = catalogService.getCatalog().findProduct(productName);
                Plan currentPlan = subscription.getCurrentPlan();
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
            PriceList newPriceList = planChangeResult.getNewPriceList();

            Plan newPlan = catalogService.getCatalog().findPlan(productName, term, newPriceList.getName());
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
            PhaseEvent nextPhaseEvent = (nextTimedPhase != null) ?
                    PhaseEventData.getNextPhaseEvent(nextTimedPhase.getPhase().getName(), subscription, now, nextTimedPhase.getStartPhase()) :
                        null;
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
