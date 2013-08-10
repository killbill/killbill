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

package com.ning.billing.subscription.api.user;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.ErrorCode;
import com.ning.billing.ObjectType;
import com.ning.billing.catalog.api.BillingActionPolicy;
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
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.subscription.alignment.PlanAligner;
import com.ning.billing.subscription.alignment.TimedPhase;
import com.ning.billing.subscription.api.SubscriptionBaseApiService;
import com.ning.billing.subscription.api.SubscriptionBase;
import com.ning.billing.subscription.engine.addon.AddonUtils;
import com.ning.billing.subscription.engine.dao.SubscriptionDao;
import com.ning.billing.subscription.events.SubscriptionEvent;
import com.ning.billing.subscription.events.phase.PhaseEvent;
import com.ning.billing.subscription.events.phase.PhaseEventData;
import com.ning.billing.subscription.events.user.ApiEvent;
import com.ning.billing.subscription.events.user.ApiEventBuilder;
import com.ning.billing.subscription.events.user.ApiEventCancel;
import com.ning.billing.subscription.events.user.ApiEventChange;
import com.ning.billing.subscription.events.user.ApiEventCreate;
import com.ning.billing.subscription.events.user.ApiEventReCreate;
import com.ning.billing.subscription.events.user.ApiEventUncancel;
import com.ning.billing.subscription.exceptions.SubscriptionBaseError;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.clock.Clock;
import com.ning.billing.clock.DefaultClock;

import com.google.inject.Inject;

public class DefaultSubscriptionBaseApiService implements SubscriptionBaseApiService {

    private final Clock clock;
    private final SubscriptionDao dao;
    private final CatalogService catalogService;
    private final PlanAligner planAligner;
    private final AddonUtils addonUtils;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public DefaultSubscriptionBaseApiService(final Clock clock, final SubscriptionDao dao, final CatalogService catalogService,
                                             final PlanAligner planAligner, final AddonUtils addonUtils,
                                             final InternalCallContextFactory internalCallContextFactory) {
        this.clock = clock;
        this.catalogService = catalogService;
        this.planAligner = planAligner;
        this.dao = dao;
        this.addonUtils = addonUtils;
        this.internalCallContextFactory = internalCallContextFactory;
    }


    @Override
    public DefaultSubscriptionBase createPlan(final SubscriptionBuilder builder, final Plan plan, final PhaseType initialPhase,
                                       final String realPriceList, final DateTime requestedDate, final DateTime effectiveDate, final DateTime processedDate,
                                       final CallContext context) throws SubscriptionBaseApiException {
        final DefaultSubscriptionBase subscription = new DefaultSubscriptionBase(builder, this, clock);

        createFromSubscription(subscription, plan, initialPhase, realPriceList, requestedDate, effectiveDate, processedDate, false, context);
        return subscription;
    }

    @Override
    public boolean recreatePlan(final DefaultSubscriptionBase subscription, final PlanPhaseSpecifier spec, final DateTime requestedDateWithMs, final CallContext context)
            throws SubscriptionBaseApiException {
        final SubscriptionState currentState = subscription.getState();
        if (currentState != null && currentState != SubscriptionState.CANCELLED) {
            throw new SubscriptionBaseApiException(ErrorCode.SUB_RECREATE_BAD_STATE, subscription.getId(), currentState);
        }

        final DateTime now = clock.getUTCNow();
        final DateTime requestedDate = (requestedDateWithMs != null) ? DefaultClock.truncateMs(requestedDateWithMs) : now;
        validateRequestedDate(subscription, now, requestedDate);

        try {
            final String realPriceList = (spec.getPriceListName() == null) ? PriceListSet.DEFAULT_PRICELIST_NAME : spec.getPriceListName();
            final Plan plan = catalogService.getFullCatalog().findPlan(spec.getProductName(), spec.getBillingPeriod(), realPriceList, requestedDate);
            final PlanPhase phase = plan.getAllPhases()[0];
            if (phase == null) {
                throw new SubscriptionBaseError(String.format("No initial PlanPhase for Product %s, term %s and set %s does not exist in the catalog",
                                                         spec.getProductName(), spec.getBillingPeriod().toString(), realPriceList));
            }

            final DateTime effectiveDate = requestedDate;
            final DateTime processedDate = now;

            createFromSubscription(subscription, plan, spec.getPhaseType(), realPriceList, requestedDate, effectiveDate, processedDate, true, context);
            return true;
        } catch (CatalogApiException e) {
            throw new SubscriptionBaseApiException(e);
        }
    }

    private void createFromSubscription(final DefaultSubscriptionBase subscription, final Plan plan, final PhaseType initialPhase,
                                        final String realPriceList, final DateTime requestedDate, final DateTime effectiveDate, final DateTime processedDate,
                                        final boolean reCreate, final CallContext context) throws SubscriptionBaseApiException {
        final InternalCallContext internalCallContext = createCallContextFromBundleId(subscription.getBundleId(), context);

        try {
            final TimedPhase[] curAndNextPhases = planAligner.getCurrentAndNextTimedPhaseOnCreate(subscription, plan, initialPhase, realPriceList, requestedDate, effectiveDate);

            final ApiEventBuilder createBuilder = new ApiEventBuilder()
                    .setSubscriptionId(subscription.getId())
                    .setEventPlan(plan.getName())
                    .setEventPlanPhase(curAndNextPhases[0].getPhase().getName())
                    .setEventPriceList(realPriceList)
                    .setActiveVersion(subscription.getActiveVersion())
                    .setProcessedDate(processedDate)
                    .setEffectiveDate(effectiveDate)
                    .setRequestedDate(requestedDate)
                    .setFromDisk(true);
            final ApiEvent creationEvent = (reCreate) ? new ApiEventReCreate(createBuilder) : new ApiEventCreate(createBuilder);

            final TimedPhase nextTimedPhase = curAndNextPhases[1];
            final PhaseEvent nextPhaseEvent = (nextTimedPhase != null) ?
                                              PhaseEventData.createNextPhaseEvent(nextTimedPhase.getPhase().getName(), subscription, processedDate, nextTimedPhase.getStartPhase()) :
                                              null;
            final List<SubscriptionEvent> events = new ArrayList<SubscriptionEvent>();
            events.add(creationEvent);
            if (nextPhaseEvent != null) {
                events.add(nextPhaseEvent);
            }
            if (reCreate) {
                dao.recreateSubscription(subscription, events, internalCallContext);
            } else {
                dao.createSubscription(subscription, events, internalCallContext);
            }
            subscription.rebuildTransitions(dao.getEventsForSubscription(subscription.getId(), internalCallContext), catalogService.getFullCatalog());
        } catch (CatalogApiException e) {
            throw new SubscriptionBaseApiException(e);
        }
    }

    @Override
    public boolean cancel(final DefaultSubscriptionBase subscription, final DateTime requestedDateWithMs, final CallContext context) throws SubscriptionBaseApiException {
        try {
            final SubscriptionState currentState = subscription.getState();
            if (currentState != SubscriptionState.ACTIVE) {
                throw new SubscriptionBaseApiException(ErrorCode.SUB_CANCEL_BAD_STATE, subscription.getId(), currentState);
            }
            final DateTime now = clock.getUTCNow();
            final DateTime requestedDate = (requestedDateWithMs != null) ? DefaultClock.truncateMs(requestedDateWithMs) : now;

            final Plan currentPlan = subscription.getCurrentPlan();
            final PlanPhaseSpecifier planPhase = new PlanPhaseSpecifier(currentPlan.getProduct().getName(),
                                                                        currentPlan.getProduct().getCategory(),
                                                                        subscription.getCurrentPlan().getBillingPeriod(),
                                                                        subscription.getCurrentPriceList().getName(),
                                                                        subscription.getCurrentPhase().getPhaseType());

            final BillingActionPolicy policy = catalogService.getFullCatalog().planCancelPolicy(planPhase, requestedDate);

            return doCancelPlan(subscription, requestedDateWithMs, now, policy, context);
        } catch (CatalogApiException e) {
            throw new SubscriptionBaseApiException(e);
        }
    }

    @Override
    public boolean cancelWithPolicy(final DefaultSubscriptionBase subscription, final DateTime requestedDateWithMs, final BillingActionPolicy policy, final CallContext context) throws SubscriptionBaseApiException {
        final SubscriptionState currentState = subscription.getState();
        if (currentState != SubscriptionState.ACTIVE) {
            throw new SubscriptionBaseApiException(ErrorCode.SUB_CANCEL_BAD_STATE, subscription.getId(), currentState);
        }
        final DateTime now = clock.getUTCNow();
        return doCancelPlan(subscription, requestedDateWithMs, now, policy, context);
    }

    private boolean doCancelPlan(final DefaultSubscriptionBase subscription, final DateTime requestedDateWithMs, final DateTime now, final BillingActionPolicy policy, final CallContext context) throws SubscriptionBaseApiException {

        final DateTime requestedDate = (requestedDateWithMs != null) ? DefaultClock.truncateMs(requestedDateWithMs) : now;
        validateRequestedDate(subscription, now, requestedDate);
        final DateTime effectiveDate = subscription.getPlanChangeEffectiveDate(policy, requestedDate);

        final SubscriptionEvent cancelEvent = new ApiEventCancel(new ApiEventBuilder()
                                                                        .setSubscriptionId(subscription.getId())
                                                                        .setActiveVersion(subscription.getActiveVersion())
                                                                        .setProcessedDate(now)
                                                                        .setEffectiveDate(effectiveDate)
                                                                        .setRequestedDate(requestedDate)
                                                                        .setFromDisk(true));

        final InternalCallContext internalCallContext = createCallContextFromBundleId(subscription.getBundleId(), context);
        dao.cancelSubscription(subscription, cancelEvent, internalCallContext, 0);
        subscription.rebuildTransitions(dao.getEventsForSubscription(subscription.getId(), internalCallContext), catalogService.getFullCatalog());

        cancelAddOnsIfRequired(subscription, effectiveDate, internalCallContext);

        return (policy == BillingActionPolicy.IMMEDIATE);
    }

    @Override
    public boolean uncancel(final DefaultSubscriptionBase subscription, final CallContext context) throws SubscriptionBaseApiException {
        if (!subscription.isSubscriptionFutureCancelled()) {
            throw new SubscriptionBaseApiException(ErrorCode.SUB_UNCANCEL_BAD_STATE, subscription.getId().toString());
        }

        final DateTime now = clock.getUTCNow();
        final SubscriptionEvent uncancelEvent = new ApiEventUncancel(new ApiEventBuilder()
                                                                            .setSubscriptionId(subscription.getId())
                                                                            .setActiveVersion(subscription.getActiveVersion())
                                                                            .setProcessedDate(now)
                                                                            .setRequestedDate(now)
                                                                            .setEffectiveDate(now)
                                                                            .setFromDisk(true));

        final List<SubscriptionEvent> uncancelEvents = new ArrayList<SubscriptionEvent>();
        uncancelEvents.add(uncancelEvent);

        final TimedPhase nextTimedPhase = planAligner.getNextTimedPhase(subscription, now, now);
        final PhaseEvent nextPhaseEvent = (nextTimedPhase != null) ?
                                          PhaseEventData.createNextPhaseEvent(nextTimedPhase.getPhase().getName(), subscription, now, nextTimedPhase.getStartPhase()) :
                                          null;
        if (nextPhaseEvent != null) {
            uncancelEvents.add(nextPhaseEvent);
        }

        final InternalCallContext internalCallContext = createCallContextFromBundleId(subscription.getBundleId(), context);
        dao.uncancelSubscription(subscription, uncancelEvents, internalCallContext);
        subscription.rebuildTransitions(dao.getEventsForSubscription(subscription.getId(), internalCallContext), catalogService.getFullCatalog());

        return true;
    }

    @Override
    public boolean changePlan(final DefaultSubscriptionBase subscription, final String productName, final BillingPeriod term,
                              final String priceList, final DateTime requestedDateWithMs, final CallContext context)
            throws SubscriptionBaseApiException {
        final DateTime now = clock.getUTCNow();
        final DateTime requestedDate = (requestedDateWithMs != null) ? DefaultClock.truncateMs(requestedDateWithMs) : now;

        validateRequestedDate(subscription, now, requestedDate);
        validateSubscriptionState(subscription);

        final PlanChangeResult planChangeResult = getPlanChangeResult(subscription, productName, term, priceList, requestedDate);
        final BillingActionPolicy policy = planChangeResult.getPolicy();

        try {
            return doChangePlan(subscription, planChangeResult, now, requestedDate, productName, term, policy, context);
        } catch (CatalogApiException e) {
            throw new SubscriptionBaseApiException(e);
        }
    }

    @Override
    public boolean changePlanWithPolicy(final DefaultSubscriptionBase subscription, final String productName, final BillingPeriod term,
                                        final String priceList, final DateTime requestedDateWithMs, final BillingActionPolicy policy, final CallContext context)
            throws SubscriptionBaseApiException {
        final DateTime now = clock.getUTCNow();
        final DateTime requestedDate = (requestedDateWithMs != null) ? DefaultClock.truncateMs(requestedDateWithMs) : now;

        validateRequestedDate(subscription, now, requestedDate);
        validateSubscriptionState(subscription);

        final PlanChangeResult planChangeResult = getPlanChangeResult(subscription, productName, term, priceList, requestedDate);

        try {
            return doChangePlan(subscription, planChangeResult, now, requestedDate, productName, term, policy, context);
        } catch (CatalogApiException e) {
            throw new SubscriptionBaseApiException(e);
        }
    }

    private PlanChangeResult getPlanChangeResult(final DefaultSubscriptionBase subscription, final String productName,
                                                 final BillingPeriod term, final String priceList, final DateTime requestedDate) throws SubscriptionBaseApiException {
        final PlanChangeResult planChangeResult;
        try {
            final Product destProduct = catalogService.getFullCatalog().findProduct(productName, requestedDate);
            final Plan currentPlan = subscription.getCurrentPlan();
            final PriceList currentPriceList = subscription.getCurrentPriceList();
            final PlanPhaseSpecifier fromPlanPhase = new PlanPhaseSpecifier(currentPlan.getProduct().getName(),
                                                                            currentPlan.getProduct().getCategory(),
                                                                            currentPlan.getBillingPeriod(),
                                                                            currentPriceList.getName(),
                                                                            subscription.getCurrentPhase().getPhaseType());
            final PlanSpecifier toPlanPhase = new PlanSpecifier(productName,
                                                                destProduct.getCategory(),
                                                                term,
                                                                priceList);

            planChangeResult = catalogService.getFullCatalog().planChange(fromPlanPhase, toPlanPhase, requestedDate);
        } catch (CatalogApiException e) {
            throw new SubscriptionBaseApiException(e);
        }

        return planChangeResult;
    }

    private boolean doChangePlan(final DefaultSubscriptionBase subscription, final PlanChangeResult planChangeResult,
                                 final DateTime now, final DateTime requestedDate, final String productName,
                                 final BillingPeriod term, final BillingActionPolicy policy, final CallContext context) throws SubscriptionBaseApiException, CatalogApiException {
        final PriceList newPriceList = planChangeResult.getNewPriceList();

        final Plan newPlan = catalogService.getFullCatalog().findPlan(productName, term, newPriceList.getName(), requestedDate, subscription.getStartDate());
        final DateTime effectiveDate = subscription.getPlanChangeEffectiveDate(policy, requestedDate);

        final TimedPhase currentTimedPhase = planAligner.getCurrentTimedPhaseOnChange(subscription, newPlan, newPriceList.getName(), requestedDate, effectiveDate);

        final SubscriptionEvent changeEvent = new ApiEventChange(new ApiEventBuilder()
                                                                        .setSubscriptionId(subscription.getId())
                                                                        .setEventPlan(newPlan.getName())
                                                                        .setEventPlanPhase(currentTimedPhase.getPhase().getName())
                                                                        .setEventPriceList(newPriceList.getName())
                                                                        .setActiveVersion(subscription.getActiveVersion())
                                                                        .setProcessedDate(now)
                                                                        .setEffectiveDate(effectiveDate)
                                                                        .setRequestedDate(requestedDate)
                                                                        .setFromDisk(true));

        final TimedPhase nextTimedPhase = planAligner.getNextTimedPhaseOnChange(subscription, newPlan, newPriceList.getName(), requestedDate, effectiveDate);
        final PhaseEvent nextPhaseEvent = (nextTimedPhase != null) ?
                                          PhaseEventData.createNextPhaseEvent(nextTimedPhase.getPhase().getName(), subscription, now, nextTimedPhase.getStartPhase()) :
                                          null;

        final List<SubscriptionEvent> changeEvents = new ArrayList<SubscriptionEvent>();
        // Only add the PHASE if it does not coincide with the CHANGE, if not this is 'just' a CHANGE.
        if (nextPhaseEvent != null && !nextPhaseEvent.getEffectiveDate().equals(changeEvent.getEffectiveDate())) {
            changeEvents.add(nextPhaseEvent);
        }
        changeEvents.add(changeEvent);

        final InternalCallContext internalCallContext = createCallContextFromBundleId(subscription.getBundleId(), context);
        dao.changePlan(subscription, changeEvents, internalCallContext);
        subscription.rebuildTransitions(dao.getEventsForSubscription(subscription.getId(), internalCallContext), catalogService.getFullCatalog());

        cancelAddOnsIfRequired(subscription, effectiveDate, internalCallContext);

        return (policy == BillingActionPolicy.IMMEDIATE);
    }


    public int cancelAddOnsIfRequired(final DefaultSubscriptionBase baseSubscription, final DateTime effectiveDate, final InternalCallContext context) {

        // If cancellation/change occur in the future, there is nothing to do
        final DateTime now = clock.getUTCNow();
        if (effectiveDate.compareTo(now) > 0) {
            return 0;
        }

        final Product baseProduct = (baseSubscription.getState() == SubscriptionState.CANCELLED) ? null : baseSubscription.getCurrentPlan().getProduct();

        final List<SubscriptionBase> subscriptions = dao.getSubscriptions(baseSubscription.getBundleId(), context);

        final List<DefaultSubscriptionBase> subscriptionsToBeCancelled = new LinkedList<DefaultSubscriptionBase>();
        final List<SubscriptionEvent> cancelEvents = new LinkedList<SubscriptionEvent>();

        for (final SubscriptionBase subscription : subscriptions) {
            final DefaultSubscriptionBase cur = (DefaultSubscriptionBase) subscription;
            if (cur.getState() == SubscriptionState.CANCELLED ||
                cur.getCategory() != ProductCategory.ADD_ON) {
                continue;
            }

            final Plan addonCurrentPlan = cur.getCurrentPlan();
            if (baseProduct == null ||
                addonUtils.isAddonIncluded(baseProduct, addonCurrentPlan) ||
                !addonUtils.isAddonAvailable(baseProduct, addonCurrentPlan)) {
                //
                // Perform AO cancellation using the effectiveDate of the BP
                //
                final SubscriptionEvent cancelEvent = new ApiEventCancel(new ApiEventBuilder()
                                                                                .setSubscriptionId(cur.getId())
                                                                                .setActiveVersion(cur.getActiveVersion())
                                                                                .setProcessedDate(now)
                                                                                .setEffectiveDate(effectiveDate)
                                                                                .setRequestedDate(now)
                                                                                .setFromDisk(true));
                subscriptionsToBeCancelled.add(cur);
                cancelEvents.add(cancelEvent);
            }
        }

        dao.cancelSubscriptions(subscriptionsToBeCancelled, cancelEvents, context);
        return subscriptionsToBeCancelled.size();
    }

    private void validateRequestedDate(final DefaultSubscriptionBase subscription, final DateTime now, final DateTime requestedDate)
            throws SubscriptionBaseApiException {

        if (requestedDate.isAfter(now)) {
            throw new SubscriptionBaseApiException(ErrorCode.SUB_INVALID_REQUESTED_FUTURE_DATE, requestedDate.toString());
        }

        final SubscriptionBaseTransition previousTransition = subscription.getPreviousTransition();
        if (previousTransition != null && previousTransition.getEffectiveTransitionTime().isAfter(requestedDate)) {
            throw new SubscriptionBaseApiException(ErrorCode.SUB_INVALID_REQUESTED_DATE,
                                                  requestedDate.toString(), previousTransition.getEffectiveTransitionTime());
        }
    }

    private void validateSubscriptionState(final DefaultSubscriptionBase subscription) throws SubscriptionBaseApiException {
        final SubscriptionState currentState = subscription.getState();
        if (currentState != SubscriptionState.ACTIVE) {
            throw new SubscriptionBaseApiException(ErrorCode.SUB_CHANGE_NON_ACTIVE, subscription.getId(), currentState);
        }
        if (subscription.isSubscriptionFutureCancelled()) {
            throw new SubscriptionBaseApiException(ErrorCode.SUB_CHANGE_FUTURE_CANCELLED, subscription.getId());
        }
    }

    private InternalCallContext createCallContextFromBundleId(final UUID bundleId, final CallContext context) {
        return internalCallContextFactory.createInternalCallContext(bundleId, ObjectType.BUNDLE, context);
    }
}
