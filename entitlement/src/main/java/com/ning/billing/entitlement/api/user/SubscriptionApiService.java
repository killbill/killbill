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
import java.util.List;

import org.joda.time.DateTime;

import com.google.inject.Inject;
import com.ning.billing.ErrorCode;
import com.ning.billing.catalog.api.ActionPolicy;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanChangeResult;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PlanSpecifier;
import com.ning.billing.catalog.api.PriceList;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.catalog.api.Product;
import com.ning.billing.entitlement.alignment.PlanAligner;
import com.ning.billing.entitlement.alignment.TimedPhase;
import com.ning.billing.entitlement.api.user.Subscription.SubscriptionState;
import com.ning.billing.entitlement.api.user.SubscriptionFactory.SubscriptionBuilder;
import com.ning.billing.entitlement.engine.dao.EntitlementDao;
import com.ning.billing.entitlement.events.EntitlementEvent;
import com.ning.billing.entitlement.events.phase.PhaseEvent;
import com.ning.billing.entitlement.events.phase.PhaseEventData;
import com.ning.billing.entitlement.events.user.ApiEvent;
import com.ning.billing.entitlement.events.user.ApiEventBuilder;
import com.ning.billing.entitlement.events.user.ApiEventCancel;
import com.ning.billing.entitlement.events.user.ApiEventChange;
import com.ning.billing.entitlement.events.user.ApiEventCreate;
import com.ning.billing.entitlement.events.user.ApiEventReCreate;
import com.ning.billing.entitlement.events.user.ApiEventUncancel;
import com.ning.billing.entitlement.exceptions.EntitlementError;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.DefaultClock;

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

    public SubscriptionData createPlan(SubscriptionBuilder builder, Plan plan, PhaseType initialPhase,
            String realPriceList, DateTime requestedDate, DateTime effectiveDate, DateTime processedDate,
            CallContext context)
        throws EntitlementUserApiException {
        SubscriptionData subscription = new SubscriptionData(builder, this, clock);


        
        createFromSubscription(subscription, plan, initialPhase, realPriceList, requestedDate, effectiveDate, processedDate, false, context);
        return subscription;
    }


    public boolean recreatePlan(SubscriptionData subscription, PlanPhaseSpecifier spec, DateTime requestedDate, CallContext context)
    throws EntitlementUserApiException {

        SubscriptionState currentState = subscription.getState();
        if (currentState != SubscriptionState.CANCELLED) {
            throw new EntitlementUserApiException(ErrorCode.ENT_RECREATE_BAD_STATE, subscription.getId(), currentState);
        }
        DateTime now = clock.getUTCNow();
        requestedDate = (requestedDate != null) ? DefaultClock.truncateMs(requestedDate) : now;
        validateRequestedDate(subscription, now, requestedDate);

        try {
            String realPriceList = (spec.getPriceListName() == null) ? PriceListSet.DEFAULT_PRICELIST_NAME : spec.getPriceListName();
            Plan plan = catalogService.getFullCatalog().findPlan(spec.getProductName(), spec.getBillingPeriod(), realPriceList, requestedDate);
            PlanPhase phase = plan.getAllPhases()[0];
            if (phase == null) {
                throw new EntitlementError(String.format("No initial PlanPhase for Product %s, term %s and set %s does not exist in the catalog",
                        spec.getProductName(), spec.getBillingPeriod().toString(), realPriceList));
            }

            DateTime effectiveDate = requestedDate;
            DateTime processedDate = now;

            createFromSubscription(subscription, plan, spec.getPhaseType(), realPriceList, requestedDate, effectiveDate, processedDate, true, context);
            return true;
        } catch (CatalogApiException e) {
            throw new EntitlementUserApiException(e);
        }
    }

    private void createFromSubscription(SubscriptionData subscription, Plan plan, PhaseType initialPhase,
            String realPriceList, DateTime requestedDate, DateTime effectiveDate, DateTime processedDate,
            boolean reCreate, CallContext context)
    throws EntitlementUserApiException {


        try {
            TimedPhase [] curAndNextPhases = planAligner.getCurrentAndNextTimedPhaseOnCreate(subscription, plan, initialPhase, realPriceList, requestedDate, effectiveDate);

            ApiEventBuilder createBuilder = new ApiEventBuilder()
            .setSubscriptionId(subscription.getId())
            .setEventPlan(plan.getName())
            .setEventPlanPhase(curAndNextPhases[0].getPhase().getName())
            .setEventPriceList(realPriceList)
            .setActiveVersion(subscription.getActiveVersion())
            .setProcessedDate(processedDate)
            .setEffectiveDate(effectiveDate)
            .setRequestedDate(requestedDate)
            .setUserToken(context.getUserToken())            
            .setFromDisk(true);
            ApiEvent creationEvent = (reCreate) ? new ApiEventReCreate(createBuilder) : new ApiEventCreate(createBuilder);

            TimedPhase nextTimedPhase = curAndNextPhases[1];
            PhaseEvent nextPhaseEvent = (nextTimedPhase != null) ?
                    PhaseEventData.createNextPhaseEvent(nextTimedPhase.getPhase().getName(), subscription, processedDate, nextTimedPhase.getStartPhase()) :
                        null;
                    List<EntitlementEvent> events = new ArrayList<EntitlementEvent>();
                    events.add(creationEvent);
                    if (nextPhaseEvent != null) {
                        events.add(nextPhaseEvent);
                    }
                    if (reCreate) {
                        dao.recreateSubscription(subscription.getId(), events, context);
                    } else {
                        dao.createSubscription(subscription, events, context);
                    }
                    subscription.rebuildTransitions(dao.getEventsForSubscription(subscription.getId()), catalogService.getFullCatalog());
        } catch (CatalogApiException e) {
            throw new EntitlementUserApiException(e);
        }
    }

    public boolean cancel(SubscriptionData subscription, DateTime requestedDate, boolean eot, CallContext context)
    throws EntitlementUserApiException {

        try {
            SubscriptionState currentState = subscription.getState();
            if (currentState != SubscriptionState.ACTIVE) {
                throw new EntitlementUserApiException(ErrorCode.ENT_CANCEL_BAD_STATE, subscription.getId(), currentState);
            }

            DateTime now = clock.getUTCNow();
            requestedDate = (requestedDate != null) ? DefaultClock.truncateMs(requestedDate) : now;
            validateRequestedDate(subscription, now, requestedDate);

            Plan currentPlan = subscription.getCurrentPlan();
            PlanPhaseSpecifier planPhase = new PlanPhaseSpecifier(currentPlan.getProduct().getName(),
                    currentPlan.getProduct().getCategory(),
                    subscription.getCurrentPlan().getBillingPeriod(),
                    subscription.getCurrentPriceList().getName(),
                    subscription.getCurrentPhase().getPhaseType());

            ActionPolicy policy = catalogService.getFullCatalog().planCancelPolicy(planPhase, requestedDate);
            DateTime effectiveDate = subscription.getPlanChangeEffectiveDate(policy, requestedDate);

            EntitlementEvent cancelEvent = new ApiEventCancel(new ApiEventBuilder()
            .setSubscriptionId(subscription.getId())
            .setActiveVersion(subscription.getActiveVersion())
            .setProcessedDate(now)
            .setEffectiveDate(effectiveDate)
            .setRequestedDate(requestedDate)
            .setUserToken(context.getUserToken())
            .setFromDisk(true));

            dao.cancelSubscription(subscription.getId(), cancelEvent, context, 0);
            subscription.rebuildTransitions(dao.getEventsForSubscription(subscription.getId()), catalogService.getFullCatalog());
            return (policy == ActionPolicy.IMMEDIATE);
        } catch (CatalogApiException e) {
            throw new EntitlementUserApiException(e);
        }
    }


    public boolean uncancel(SubscriptionData subscription, CallContext context)
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
        .setEffectiveDate(now)
        .setUserToken(context.getUserToken())
        .setFromDisk(true));

        List<EntitlementEvent> uncancelEvents = new ArrayList<EntitlementEvent>();
        uncancelEvents.add(uncancelEvent);

        TimedPhase nextTimedPhase = planAligner.getNextTimedPhase(subscription, now, now);
        PhaseEvent nextPhaseEvent = (nextTimedPhase != null) ?
                PhaseEventData.createNextPhaseEvent(nextTimedPhase.getPhase().getName(), subscription, now, nextTimedPhase.getStartPhase()) :
                    null;
                if (nextPhaseEvent != null) {
                    uncancelEvents.add(nextPhaseEvent);
                }
                dao.uncancelSubscription(subscription.getId(), uncancelEvents, context);
                subscription.rebuildTransitions(dao.getEventsForSubscription(subscription.getId()), catalogService.getFullCatalog());
                return true;
    }

    public boolean changePlan(SubscriptionData subscription, String productName, BillingPeriod term,
            String priceList, DateTime requestedDate, CallContext context)

    throws EntitlementUserApiException {

        try {

            DateTime now = clock.getUTCNow();
            requestedDate = (requestedDate != null) ? DefaultClock.truncateMs(requestedDate) : now;
            validateRequestedDate(subscription, now, requestedDate);

            PriceList currentPriceList = subscription.getCurrentPriceList();

            SubscriptionState currentState = subscription.getState();
            if (currentState != SubscriptionState.ACTIVE) {
                throw new EntitlementUserApiException(ErrorCode.ENT_CHANGE_NON_ACTIVE, subscription.getId(), currentState);
            }

            if (subscription.isSubscriptionFutureCancelled()) {
                throw new EntitlementUserApiException(ErrorCode.ENT_CHANGE_FUTURE_CANCELLED, subscription.getId());
            }
            PlanChangeResult planChangeResult = null;
            try {

                Product destProduct = catalogService.getFullCatalog().findProduct(productName, requestedDate);
                Plan currentPlan = subscription.getCurrentPlan();
                PlanPhaseSpecifier fromPlanPhase = new PlanPhaseSpecifier(currentPlan.getProduct().getName(),
                        currentPlan.getProduct().getCategory(),
                        currentPlan.getBillingPeriod(),
                        currentPriceList.getName(), subscription.getCurrentPhase().getPhaseType());
                PlanSpecifier toPlanPhase = new PlanSpecifier(productName,
                        destProduct.getCategory(),
                        term,
                        priceList);

                planChangeResult = catalogService.getFullCatalog().planChange(fromPlanPhase, toPlanPhase, requestedDate);
            } catch (CatalogApiException e) {
                throw new EntitlementUserApiException(e);
            }

            ActionPolicy policy = planChangeResult.getPolicy();
            PriceList newPriceList = planChangeResult.getNewPriceList();

            Plan newPlan = catalogService.getFullCatalog().findPlan(productName, term, newPriceList.getName(), requestedDate, subscription.getStartDate());
            DateTime effectiveDate = subscription.getPlanChangeEffectiveDate(policy, requestedDate);

            TimedPhase currentTimedPhase = planAligner.getCurrentTimedPhaseOnChange(subscription, newPlan, newPriceList.getName(), requestedDate, effectiveDate);

            EntitlementEvent changeEvent = new ApiEventChange(new ApiEventBuilder()
            .setSubscriptionId(subscription.getId())
            .setEventPlan(newPlan.getName())
            .setEventPlanPhase(currentTimedPhase.getPhase().getName())
            .setEventPriceList(newPriceList.getName())
            .setActiveVersion(subscription.getActiveVersion())
            .setProcessedDate(now)
            .setEffectiveDate(effectiveDate)
            .setRequestedDate(requestedDate)
            .setUserToken(context.getUserToken())            
            .setFromDisk(true));

            TimedPhase nextTimedPhase = planAligner.getNextTimedPhaseOnChange(subscription, newPlan, newPriceList.getName(), requestedDate, effectiveDate);
            PhaseEvent nextPhaseEvent = (nextTimedPhase != null) ?
                    PhaseEventData.createNextPhaseEvent(nextTimedPhase.getPhase().getName(), subscription, now, nextTimedPhase.getStartPhase()) :
                        null;
            List<EntitlementEvent> changeEvents = new ArrayList<EntitlementEvent>();
            // Only add the PHASE if it does not coincide with the CHANGE, if not this is 'just' a CHANGE.
            if (nextPhaseEvent != null && ! nextPhaseEvent.getEffectiveDate().equals(changeEvent.getEffectiveDate())) {
                changeEvents.add(nextPhaseEvent);
            }
            changeEvents.add(changeEvent);
            dao.changePlan(subscription.getId(), changeEvents, context);
            subscription.rebuildTransitions(dao.getEventsForSubscription(subscription.getId()), catalogService.getFullCatalog());
            return (policy == ActionPolicy.IMMEDIATE);
        } catch (CatalogApiException e) {
            throw new EntitlementUserApiException(e);
        }
    }


    public void commitCustomFields(SubscriptionData subscription, CallContext context) {
        dao.saveCustomFields(subscription, context);
    }

    private void validateRequestedDate(SubscriptionData subscription, DateTime now, DateTime requestedDate)
        throws EntitlementUserApiException {

        if (requestedDate.isAfter(now) ) {
            throw new EntitlementUserApiException(ErrorCode.ENT_INVALID_REQUESTED_FUTURE_DATE, requestedDate.toString());
        }

        SubscriptionEventTransition previousTransition = subscription.getPreviousTransition();
        if (previousTransition.getEffectiveTransitionTime().isAfter(requestedDate)) {
            throw new EntitlementUserApiException(ErrorCode.ENT_INVALID_REQUESTED_DATE,
                    requestedDate.toString(), previousTransition.getEffectiveTransitionTime());
        }
    }
    

}
