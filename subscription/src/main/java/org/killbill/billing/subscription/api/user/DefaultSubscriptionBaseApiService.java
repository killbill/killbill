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
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.ObjectType;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.CatalogService;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanChangeResult;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.PlanSpecifier;
import org.killbill.billing.catalog.api.PriceList;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.clock.Clock;
import org.killbill.clock.DefaultClock;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.subscription.alignment.PlanAligner;
import org.killbill.billing.subscription.alignment.TimedPhase;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseApiService;
import org.killbill.billing.subscription.engine.addon.AddonUtils;
import org.killbill.billing.subscription.engine.dao.SubscriptionDao;
import org.killbill.billing.subscription.events.SubscriptionBaseEvent;
import org.killbill.billing.subscription.events.phase.PhaseEvent;
import org.killbill.billing.subscription.events.phase.PhaseEventData;
import org.killbill.billing.subscription.events.user.ApiEvent;
import org.killbill.billing.subscription.events.user.ApiEventBuilder;
import org.killbill.billing.subscription.events.user.ApiEventCancel;
import org.killbill.billing.subscription.events.user.ApiEventChange;
import org.killbill.billing.subscription.events.user.ApiEventCreate;
import org.killbill.billing.subscription.events.user.ApiEventReCreate;
import org.killbill.billing.subscription.events.user.ApiEventUncancel;
import org.killbill.billing.subscription.exceptions.SubscriptionBaseError;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;

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

    @Deprecated
    @Override
    public boolean recreatePlan(final DefaultSubscriptionBase subscription, final PlanPhaseSpecifier spec, final DateTime requestedDateWithMs, final CallContext context)
            throws SubscriptionBaseApiException {
        final EntitlementState currentState = subscription.getState();
        if (currentState != null && currentState != EntitlementState.CANCELLED) {
            throw new SubscriptionBaseApiException(ErrorCode.SUB_RECREATE_BAD_STATE, subscription.getId(), currentState);
        }

        final DateTime now = clock.getUTCNow();
        final DateTime effectiveDate = (requestedDateWithMs != null) ? DefaultClock.truncateMs(requestedDateWithMs) : now;
        validateEffectiveDate(subscription, effectiveDate);

        try {
            final String realPriceList = (spec.getPriceListName() == null) ? PriceListSet.DEFAULT_PRICELIST_NAME : spec.getPriceListName();
            final Plan plan = catalogService.getFullCatalog().findPlan(spec.getProductName(), spec.getBillingPeriod(), realPriceList, effectiveDate);
            final PlanPhase phase = plan.getAllPhases()[0];
            if (phase == null) {
                throw new SubscriptionBaseError(String.format("No initial PlanPhase for Product %s, term %s and set %s does not exist in the catalog",
                                                              spec.getProductName(), spec.getBillingPeriod().toString(), realPriceList));
            }

            final DateTime processedDate = now;

            createFromSubscription(subscription, plan, spec.getPhaseType(), realPriceList, now, effectiveDate, processedDate, true, context);
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
            final List<SubscriptionBaseEvent> events = new ArrayList<SubscriptionBaseEvent>();
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
    public boolean cancel(final DefaultSubscriptionBase subscription, final CallContext context) throws SubscriptionBaseApiException {

        final EntitlementState currentState = subscription.getState();
        if (currentState != EntitlementState.ACTIVE) {
            throw new SubscriptionBaseApiException(ErrorCode.SUB_CANCEL_BAD_STATE, subscription.getId(), currentState);
        }
        final DateTime now = clock.getUTCNow();

        final Plan currentPlan = subscription.getCurrentPlan();
        final PlanPhaseSpecifier planPhase = new PlanPhaseSpecifier(currentPlan.getProduct().getName(),
                                                                    currentPlan.getProduct().getCategory(),
                                                                    subscription.getCurrentPlan().getBillingPeriod(),
                                                                    subscription.getCurrentPriceList().getName(),
                                                                    subscription.getCurrentPhase().getPhaseType());

        try {
            final BillingActionPolicy policy = catalogService.getFullCatalog().planCancelPolicy(planPhase, now);
            final DateTime effectiveDate = subscription.getPlanChangeEffectiveDate(policy);

            return doCancelPlan(subscription, now, effectiveDate, context);
        } catch (CatalogApiException e) {
            throw new SubscriptionBaseApiException(e);
        }
    }

    @Override
    public boolean cancelWithRequestedDate(final DefaultSubscriptionBase subscription, final DateTime requestedDateWithMs, final CallContext context) throws SubscriptionBaseApiException {
        final EntitlementState currentState = subscription.getState();
        if (currentState != EntitlementState.ACTIVE) {
            throw new SubscriptionBaseApiException(ErrorCode.SUB_CANCEL_BAD_STATE, subscription.getId(), currentState);
        }
        final DateTime now = clock.getUTCNow();
        final DateTime effectiveDate = (requestedDateWithMs != null) ? DefaultClock.truncateMs(requestedDateWithMs) : now;
        return doCancelPlan(subscription, now, effectiveDate, context);
    }

    @Override
    public boolean cancelWithPolicy(final DefaultSubscriptionBase subscription, final BillingActionPolicy policy, final CallContext context) throws SubscriptionBaseApiException {
        final EntitlementState currentState = subscription.getState();
        if (currentState != EntitlementState.ACTIVE) {
            throw new SubscriptionBaseApiException(ErrorCode.SUB_CANCEL_BAD_STATE, subscription.getId(), currentState);
        }
        final DateTime now = clock.getUTCNow();
        final DateTime effectiveDate = subscription.getPlanChangeEffectiveDate(policy);

        return doCancelPlan(subscription, now, effectiveDate, context);
    }

    private boolean doCancelPlan(final DefaultSubscriptionBase subscription, final DateTime now, final DateTime effectiveDate, final CallContext context) throws SubscriptionBaseApiException {
        validateEffectiveDate(subscription, effectiveDate);

        final SubscriptionBaseEvent cancelEvent = new ApiEventCancel(new ApiEventBuilder()
                                                                             .setSubscriptionId(subscription.getId())
                                                                             .setActiveVersion(subscription.getActiveVersion())
                                                                             .setProcessedDate(now)
                                                                             .setEffectiveDate(effectiveDate)
                                                                             .setRequestedDate(now)
                                                                             .setFromDisk(true));

        final InternalCallContext internalCallContext = createCallContextFromBundleId(subscription.getBundleId(), context);
        dao.cancelSubscription(subscription, cancelEvent, internalCallContext, 0);
        subscription.rebuildTransitions(dao.getEventsForSubscription(subscription.getId(), internalCallContext), catalogService.getFullCatalog());

        if (subscription.getCategory() == ProductCategory.BASE) {
            cancelAddOnsIfRequired(subscription, effectiveDate, internalCallContext);
        }

        final boolean isImmediate = subscription.getState() == EntitlementState.CANCELLED;
        return isImmediate;
    }

    @Override
    public boolean uncancel(final DefaultSubscriptionBase subscription, final CallContext context) throws SubscriptionBaseApiException {
        if (!subscription.isSubscriptionFutureCancelled()) {
            throw new SubscriptionBaseApiException(ErrorCode.SUB_UNCANCEL_BAD_STATE, subscription.getId().toString());
        }

        final DateTime now = clock.getUTCNow();
        final SubscriptionBaseEvent uncancelEvent = new ApiEventUncancel(new ApiEventBuilder()
                                                                                 .setSubscriptionId(subscription.getId())
                                                                                 .setActiveVersion(subscription.getActiveVersion())
                                                                                 .setProcessedDate(now)
                                                                                 .setRequestedDate(now)
                                                                                 .setEffectiveDate(now)
                                                                                 .setFromDisk(true));

        final List<SubscriptionBaseEvent> uncancelEvents = new ArrayList<SubscriptionBaseEvent>();
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
    public DateTime changePlan(final DefaultSubscriptionBase subscription, final String productName, final BillingPeriod term,
                               final String priceList, final CallContext context) throws SubscriptionBaseApiException {
        final DateTime now = clock.getUTCNow();

        validateEntitlementState(subscription);

        final PlanChangeResult planChangeResult = getPlanChangeResult(subscription, productName, term, priceList, now);
        final DateTime effectiveDate = subscription.getPlanChangeEffectiveDate(planChangeResult.getPolicy());
        validateEffectiveDate(subscription, effectiveDate);

        try {
            return doChangePlan(subscription, productName, term, planChangeResult.getNewPriceList().getName(), now, effectiveDate, context);
        } catch (CatalogApiException e) {
            throw new SubscriptionBaseApiException(e);
        }
    }

    @Override
    public DateTime changePlanWithRequestedDate(final DefaultSubscriptionBase subscription, final String productName, final BillingPeriod term,
                                                final String priceList, final DateTime requestedDateWithMs, final CallContext context) throws SubscriptionBaseApiException {
        final DateTime now = clock.getUTCNow();
        final DateTime effectiveDate = (requestedDateWithMs != null) ? DefaultClock.truncateMs(requestedDateWithMs) : now;

        validateEffectiveDate(subscription, effectiveDate);
        validateEntitlementState(subscription);

        try {
            return doChangePlan(subscription, productName, term, priceList, now, effectiveDate, context);
        } catch (CatalogApiException e) {
            throw new SubscriptionBaseApiException(e);
        }
    }

    @Override
    public DateTime changePlanWithPolicy(final DefaultSubscriptionBase subscription, final String productName, final BillingPeriod term,
                                         final String priceList, final BillingActionPolicy policy, final CallContext context)
            throws SubscriptionBaseApiException {
        final DateTime now = clock.getUTCNow();

        validateEntitlementState(subscription);

        final DateTime effectiveDate = subscription.getPlanChangeEffectiveDate(policy);
        try {
            return doChangePlan(subscription, productName, term, priceList, now, effectiveDate, context);
        } catch (CatalogApiException e) {
            throw new SubscriptionBaseApiException(e);
        }
    }

    private PlanChangeResult getPlanChangeResult(final DefaultSubscriptionBase subscription, final String productName,
                                                 final BillingPeriod term, final String priceList, final DateTime effectiveDate) throws SubscriptionBaseApiException {
        final PlanChangeResult planChangeResult;
        try {
            final Product destProduct = catalogService.getFullCatalog().findProduct(productName, effectiveDate);
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

            planChangeResult = catalogService.getFullCatalog().planChange(fromPlanPhase, toPlanPhase, effectiveDate);
        } catch (CatalogApiException e) {
            throw new SubscriptionBaseApiException(e);
        }

        return planChangeResult;
    }

    private DateTime doChangePlan(final DefaultSubscriptionBase subscription,
                                  final String newProductName,
                                  final BillingPeriod newBillingPeriod,
                                  final String newPriceList,
                                  final DateTime now,
                                  final DateTime effectiveDate,
                                  final CallContext context) throws SubscriptionBaseApiException, CatalogApiException {

        final Plan newPlan = catalogService.getFullCatalog().findPlan(newProductName, newBillingPeriod, newPriceList, effectiveDate, subscription.getStartDate());

        final TimedPhase currentTimedPhase = planAligner.getCurrentTimedPhaseOnChange(subscription, newPlan, newPriceList, now, effectiveDate);

        final SubscriptionBaseEvent changeEvent = new ApiEventChange(new ApiEventBuilder()
                                                                             .setSubscriptionId(subscription.getId())
                                                                             .setEventPlan(newPlan.getName())
                                                                             .setEventPlanPhase(currentTimedPhase.getPhase().getName())
                                                                             .setEventPriceList(newPriceList)
                                                                             .setActiveVersion(subscription.getActiveVersion())
                                                                             .setProcessedDate(now)
                                                                             .setEffectiveDate(effectiveDate)
                                                                             .setRequestedDate(now)
                                                                             .setFromDisk(true));

        final TimedPhase nextTimedPhase = planAligner.getNextTimedPhaseOnChange(subscription, newPlan, newPriceList, now, effectiveDate);
        final PhaseEvent nextPhaseEvent = (nextTimedPhase != null) ?
                                          PhaseEventData.createNextPhaseEvent(nextTimedPhase.getPhase().getName(), subscription, now, nextTimedPhase.getStartPhase()) :
                                          null;

        final List<SubscriptionBaseEvent> changeEvents = new ArrayList<SubscriptionBaseEvent>();
        // Only add the PHASE if it does not coincide with the CHANGE, if not this is 'just' a CHANGE.
        if (nextPhaseEvent != null && !nextPhaseEvent.getEffectiveDate().equals(changeEvent.getEffectiveDate())) {
            changeEvents.add(nextPhaseEvent);
        }
        changeEvents.add(changeEvent);

        final InternalCallContext internalCallContext = createCallContextFromBundleId(subscription.getBundleId(), context);
        dao.changePlan(subscription, changeEvents, internalCallContext);
        subscription.rebuildTransitions(dao.getEventsForSubscription(subscription.getId(), internalCallContext), catalogService.getFullCatalog());

        if (subscription.getCategory() == ProductCategory.BASE) {
            cancelAddOnsIfRequired(subscription, effectiveDate, internalCallContext);
        }

        final boolean isChangeImmediate = subscription.getCurrentPlan().getProduct().getName().equals(newProductName) &&
                                          subscription.getCurrentPlan().getBillingPeriod() == newBillingPeriod;
        return effectiveDate;
    }

    public int cancelAddOnsIfRequired(final DefaultSubscriptionBase baseSubscription, final DateTime effectiveDate, final InternalCallContext context) {

        // If cancellation/change occur in the future, there is nothing to do
        final DateTime now = clock.getUTCNow();
        if (effectiveDate.compareTo(now) > 0) {
            return 0;
        }

        final Product baseProduct = (baseSubscription.getState() == EntitlementState.CANCELLED) ? null : baseSubscription.getCurrentPlan().getProduct();

        final List<SubscriptionBase> subscriptions = dao.getSubscriptions(baseSubscription.getBundleId(), context);

        final List<DefaultSubscriptionBase> subscriptionsToBeCancelled = new LinkedList<DefaultSubscriptionBase>();
        final List<SubscriptionBaseEvent> cancelEvents = new LinkedList<SubscriptionBaseEvent>();

        for (final SubscriptionBase subscription : subscriptions) {
            final DefaultSubscriptionBase cur = (DefaultSubscriptionBase) subscription;
            if (cur.getState() == EntitlementState.CANCELLED ||
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
                final SubscriptionBaseEvent cancelEvent = new ApiEventCancel(new ApiEventBuilder()
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

    private void validateEffectiveDate(final DefaultSubscriptionBase subscription, final DateTime effectiveDate) throws SubscriptionBaseApiException {
        final SubscriptionBaseTransition previousTransition = subscription.getPreviousTransition();
        if (previousTransition != null && previousTransition.getEffectiveTransitionTime().isAfter(effectiveDate)) {
            throw new SubscriptionBaseApiException(ErrorCode.SUB_INVALID_REQUESTED_DATE,
                                                   effectiveDate.toString(), previousTransition.getEffectiveTransitionTime());
        }
    }

    private void validateEntitlementState(final DefaultSubscriptionBase subscription) throws SubscriptionBaseApiException {
        final EntitlementState currentState = subscription.getState();
        if (currentState != EntitlementState.ACTIVE) {
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
