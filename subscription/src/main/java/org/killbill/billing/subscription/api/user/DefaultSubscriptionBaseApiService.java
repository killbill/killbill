/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.ObjectType;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Catalog;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.CatalogService;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanChangeResult;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.PlanPhasePriceOverride;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.PlanSpecifier;
import org.killbill.billing.catalog.api.PriceList;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.api.ProductCategory;
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
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.clock.Clock;
import org.killbill.clock.DefaultClock;

import com.google.common.collect.ImmutableList;
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
    public boolean recreatePlan(final DefaultSubscriptionBase subscription, final PlanPhaseSpecifier spec, final List<PlanPhasePriceOverride> overrides, final DateTime requestedDateWithMs, final CallContext context)
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
            final InternalTenantContext internalCallContext = createTenantContextFromBundleId(subscription.getBundleId(), context);
            final Plan plan = catalogService.getFullCatalog(internalCallContext).findPlan(spec.getProductName(), spec.getBillingPeriod(), realPriceList, overrides, effectiveDate);
            final PlanPhase phase = plan.getAllPhases()[0];
            if (phase == null) {
                throw new SubscriptionBaseError(String.format("No initial PlanPhase for Product %s, term %s and set %s does not exist in the catalog",
                                                              spec.getProductName(), spec.getBillingPeriod().toString(), realPriceList));
            }

            final DateTime processedDate = now;

            createFromSubscription(subscription, plan, spec.getPhaseType(), realPriceList, now, effectiveDate, processedDate, true, context);
            return true;
        } catch (final CatalogApiException e) {
            throw new SubscriptionBaseApiException(e);
        }
    }

    private void createFromSubscription(final DefaultSubscriptionBase subscription, final Plan plan, final PhaseType initialPhase,
                                        final String realPriceList, final DateTime requestedDate, final DateTime effectiveDate, final DateTime processedDate,
                                        final boolean reCreate, final CallContext context) throws SubscriptionBaseApiException {
        final InternalCallContext internalCallContext = createCallContextFromBundleId(subscription.getBundleId(), context);

        try {
            final List<SubscriptionBaseEvent> events = getEventsOnCreation(subscription.getBundleId(), subscription.getId(), subscription.getAlignStartDate(), subscription.getBundleStartDate(), subscription.getActiveVersion(),
                                                                           plan, initialPhase, realPriceList, requestedDate, effectiveDate, processedDate, reCreate, internalCallContext);
            if (reCreate) {
                dao.recreateSubscription(subscription, events, internalCallContext);
            } else {
                dao.createSubscription(subscription, events, internalCallContext);
            }
            subscription.rebuildTransitions(dao.getEventsForSubscription(subscription.getId(), internalCallContext), catalogService.getFullCatalog(internalCallContext));
        } catch (final CatalogApiException e) {
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
                                                                    subscription.getCurrentPlan().getRecurringBillingPeriod(),
                                                                    subscription.getCurrentPriceList().getName(),
                                                                    subscription.getCurrentPhase().getPhaseType());

        try {
            final InternalTenantContext internalCallContext = createTenantContextFromBundleId(subscription.getBundleId(), context);
            final BillingActionPolicy policy = catalogService.getFullCatalog(internalCallContext).planCancelPolicy(planPhase, now);
            final DateTime effectiveDate = subscription.getPlanChangeEffectiveDate(policy);

            return doCancelPlan(subscription, now, effectiveDate, context);
        } catch (final CatalogApiException e) {
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
        try {
            validateEffectiveDate(subscription, effectiveDate);

            final InternalCallContext internalCallContext = createCallContextFromBundleId(subscription.getBundleId(), context);
            final List<SubscriptionBaseEvent> cancelEvents = getEventsOnCancelPlan(subscription, now, effectiveDate, now, false, internalCallContext);
            // cancelEvents will contain only one item
            dao.cancelSubscription(subscription, cancelEvents.get(0), internalCallContext, 0);
            final Catalog fullCatalog = catalogService.getFullCatalog(internalCallContext);
            subscription.rebuildTransitions(dao.getEventsForSubscription(subscription.getId(), internalCallContext), fullCatalog);

            if (subscription.getCategory() == ProductCategory.BASE) {
                final Product baseProduct = (subscription.getState() == EntitlementState.CANCELLED) ? null : subscription.getCurrentPlan().getProduct();
                cancelAddOnsIfRequired(baseProduct, subscription.getBundleId(), effectiveDate, context);
            }

            final boolean isImmediate = subscription.getState() == EntitlementState.CANCELLED;
            return isImmediate;
        } catch (final CatalogApiException e) {
            throw new SubscriptionBaseApiException(e);
        }
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

        final InternalCallContext internalCallContext = createCallContextFromBundleId(subscription.getBundleId(), context);
        final TimedPhase nextTimedPhase = planAligner.getNextTimedPhase(subscription, now, now, internalCallContext);
        final PhaseEvent nextPhaseEvent = (nextTimedPhase != null) ?
                                          PhaseEventData.createNextPhaseEvent(subscription.getId(), subscription.getActiveVersion(), nextTimedPhase.getPhase().getName(), now, nextTimedPhase.getStartPhase()) :
                                          null;
        if (nextPhaseEvent != null) {
            uncancelEvents.add(nextPhaseEvent);
        }

        dao.uncancelSubscription(subscription, uncancelEvents, internalCallContext);
        try {
            final Catalog fullCatalog = catalogService.getFullCatalog(internalCallContext);
            subscription.rebuildTransitions(dao.getEventsForSubscription(subscription.getId(), internalCallContext), fullCatalog);
            return true;
        } catch (final CatalogApiException e) {
            throw new SubscriptionBaseApiException(e);
        }
    }

    @Override
    public DateTime changePlan(final DefaultSubscriptionBase subscription, final String productName, final BillingPeriod term,
                               final String priceList, final List<PlanPhasePriceOverride> overrides, final CallContext context) throws SubscriptionBaseApiException {
        final DateTime now = clock.getUTCNow();

        validateEntitlementState(subscription);

        final PlanChangeResult planChangeResult = getPlanChangeResult(subscription, productName, term, priceList, now, context);
        final DateTime effectiveDate = subscription.getPlanChangeEffectiveDate(planChangeResult.getPolicy());
        validateEffectiveDate(subscription, effectiveDate);

        try {
            return doChangePlan(subscription, productName, term, planChangeResult.getNewPriceList().getName(), overrides, now, effectiveDate, context);
        } catch (final CatalogApiException e) {
            throw new SubscriptionBaseApiException(e);
        }
    }

    @Override
    public DateTime changePlanWithRequestedDate(final DefaultSubscriptionBase subscription, final String productName, final BillingPeriod term,
                                                final String priceList, final List<PlanPhasePriceOverride> overrides,
                                                final DateTime requestedDateWithMs, final CallContext context) throws SubscriptionBaseApiException {
        final DateTime now = clock.getUTCNow();
        final DateTime effectiveDate = (requestedDateWithMs != null) ? DefaultClock.truncateMs(requestedDateWithMs) : now;

        validateEffectiveDate(subscription, effectiveDate);
        validateEntitlementState(subscription);

        try {
            return doChangePlan(subscription, productName, term, priceList, overrides, now, effectiveDate, context);
        } catch (final CatalogApiException e) {
            throw new SubscriptionBaseApiException(e);
        }
    }

    @Override
    public DateTime changePlanWithPolicy(final DefaultSubscriptionBase subscription, final String productName, final BillingPeriod term,
                                         final String priceList, final List<PlanPhasePriceOverride> overrides, final BillingActionPolicy policy, final CallContext context)
            throws SubscriptionBaseApiException {
        final DateTime now = clock.getUTCNow();

        validateEntitlementState(subscription);

        final DateTime effectiveDate = subscription.getPlanChangeEffectiveDate(policy);
        try {
            return doChangePlan(subscription, productName, term, priceList, overrides, now, effectiveDate, context);
        } catch (final CatalogApiException e) {
            throw new SubscriptionBaseApiException(e);
        }
    }

    @Override
    public PlanChangeResult getPlanChangeResult(final DefaultSubscriptionBase subscription, final String productName,
                                                final BillingPeriod term, final String priceList, final DateTime effectiveDate, final TenantContext context) throws SubscriptionBaseApiException {
        final PlanChangeResult planChangeResult;
        try {
            final InternalTenantContext internalCallContext = createTenantContextFromBundleId(subscription.getBundleId(), context);
            final Product destProduct = catalogService.getFullCatalog(internalCallContext).findProduct(productName, effectiveDate);
            final Plan currentPlan = subscription.getCurrentPlan();
            final PriceList currentPriceList = subscription.getCurrentPriceList();
            final PlanPhaseSpecifier fromPlanPhase = new PlanPhaseSpecifier(currentPlan.getProduct().getName(),
                                                                            currentPlan.getProduct().getCategory(),
                                                                            currentPlan.getRecurringBillingPeriod(),
                                                                            currentPriceList.getName(),
                                                                            subscription.getCurrentPhase().getPhaseType());
            final PlanSpecifier toPlanPhase = new PlanSpecifier(productName,
                                                                destProduct.getCategory(),
                                                                term,
                                                                priceList);

            planChangeResult = catalogService.getFullCatalog(internalCallContext).planChange(fromPlanPhase, toPlanPhase, effectiveDate);
        } catch (final CatalogApiException e) {
            throw new SubscriptionBaseApiException(e);
        }

        return planChangeResult;
    }

    private DateTime doChangePlan(final DefaultSubscriptionBase subscription,
                                  final String newProductName,
                                  final BillingPeriod newBillingPeriod,
                                  final String newPriceList,
                                  final List<PlanPhasePriceOverride> overrides,
                                  final DateTime now,
                                  final DateTime effectiveDate,
                                  final CallContext context) throws SubscriptionBaseApiException, CatalogApiException {
        final InternalCallContext internalCallContext = createCallContextFromBundleId(subscription.getBundleId(), context);
        final Plan newPlan = catalogService.getFullCatalog(internalCallContext).findPlan(newProductName, newBillingPeriod, newPriceList, overrides, effectiveDate, subscription.getStartDate());

        final List<SubscriptionBaseEvent> changeEvents = getEventsOnChangePlan(subscription, newPlan, newPriceList, now, effectiveDate, now, false, internalCallContext);
        dao.changePlan(subscription, changeEvents, internalCallContext);
        subscription.rebuildTransitions(dao.getEventsForSubscription(subscription.getId(), internalCallContext), catalogService.getFullCatalog(internalCallContext));

        if (subscription.getCategory() == ProductCategory.BASE) {
            final Product baseProduct = (subscription.getState() == EntitlementState.CANCELLED) ? null : subscription.getCurrentPlan().getProduct();
            cancelAddOnsIfRequired(baseProduct, subscription.getBundleId(), effectiveDate, context);
        }
        return effectiveDate;
    }

    @Override
    public List<SubscriptionBaseEvent> getEventsOnCreation(final UUID bundleId, final UUID subscriptionId, final DateTime alignStartDate, final DateTime bundleStartDate, final long activeVersion,
                                                           final Plan plan, final PhaseType initialPhase,
                                                           final String realPriceList, final DateTime requestedDate, final DateTime effectiveDate, final DateTime processedDate,
                                                           final boolean reCreate, final InternalTenantContext internalTenantContext) throws CatalogApiException, SubscriptionBaseApiException {
        final TimedPhase[] curAndNextPhases = planAligner.getCurrentAndNextTimedPhaseOnCreate(alignStartDate, bundleStartDate, plan, initialPhase,
                                                                                              realPriceList, requestedDate, effectiveDate, internalTenantContext);

        final ApiEventBuilder createBuilder = new ApiEventBuilder()
                .setSubscriptionId(subscriptionId)
                .setEventPlan(plan.getName())
                .setEventPlanPhase(curAndNextPhases[0].getPhase().getName())
                .setEventPriceList(realPriceList)
                .setActiveVersion(activeVersion)
                .setProcessedDate(processedDate)
                .setEffectiveDate(effectiveDate)
                .setRequestedDate(requestedDate)
                .setFromDisk(true);
        final ApiEvent creationEvent = (reCreate) ? new ApiEventReCreate(createBuilder) : new ApiEventCreate(createBuilder);

        final TimedPhase nextTimedPhase = curAndNextPhases[1];
        final PhaseEvent nextPhaseEvent = (nextTimedPhase != null) ?
                                          PhaseEventData.createNextPhaseEvent(subscriptionId, activeVersion, nextTimedPhase.getPhase().getName(), processedDate, nextTimedPhase.getStartPhase()) :
                                          null;
        final List<SubscriptionBaseEvent> events = new ArrayList<SubscriptionBaseEvent>();
        events.add(creationEvent);
        if (nextPhaseEvent != null) {
            events.add(nextPhaseEvent);
        }
        return events;
    }

    @Override
    public List<SubscriptionBaseEvent> getEventsOnChangePlan(final DefaultSubscriptionBase subscription, final Plan newPlan,
                                                             final String newPriceList, final DateTime requestedDate, final DateTime effectiveDate, final DateTime processedDate,
                                                             final boolean addCancellationAddOnForEventsIfRequired, final InternalTenantContext internalTenantContext) throws CatalogApiException, SubscriptionBaseApiException {
        final TimedPhase currentTimedPhase = planAligner.getCurrentTimedPhaseOnChange(subscription, newPlan, newPriceList, requestedDate, effectiveDate, internalTenantContext);

        final SubscriptionBaseEvent changeEvent = new ApiEventChange(new ApiEventBuilder()
                                                                             .setSubscriptionId(subscription.getId())
                                                                             .setEventPlan(newPlan.getName())
                                                                             .setEventPlanPhase(currentTimedPhase.getPhase().getName())
                                                                             .setEventPriceList(newPriceList)
                                                                             .setActiveVersion(subscription.getActiveVersion())
                                                                             .setProcessedDate(processedDate)
                                                                             .setEffectiveDate(effectiveDate)
                                                                             .setRequestedDate(requestedDate)
                                                                             .setFromDisk(true));

        final TimedPhase nextTimedPhase = planAligner.getNextTimedPhaseOnChange(subscription, newPlan, newPriceList, processedDate, effectiveDate, internalTenantContext);
        final PhaseEvent nextPhaseEvent = (nextTimedPhase != null) ?
                                          PhaseEventData.createNextPhaseEvent(subscription.getId(), subscription.getActiveVersion(),
                                                                              nextTimedPhase.getPhase().getName(), processedDate, nextTimedPhase.getStartPhase()) :
                                          null;

        final List<SubscriptionBaseEvent> changeEvents = new ArrayList<SubscriptionBaseEvent>();
        // Only add the PHASE if it does not coincide with the CHANGE, if not this is 'just' a CHANGE.
        changeEvents.add(changeEvent);
        if (nextPhaseEvent != null && !nextPhaseEvent.getEffectiveDate().equals(changeEvent.getEffectiveDate())) {
            changeEvents.add(nextPhaseEvent);
        }

        if (subscription.getCategory() == ProductCategory.BASE && addCancellationAddOnForEventsIfRequired) {
            final Product currentBaseProduct = changeEvent.getEffectiveDate().compareTo(clock.getUTCNow()) <= 0 ? newPlan.getProduct() : subscription.getCurrentPlan().getProduct();
            addCancellationAddOnForEventsIfRequired(changeEvents, currentBaseProduct, subscription.getBundleId(), requestedDate, effectiveDate, processedDate, internalTenantContext);
        }
        return changeEvents;
    }

    @Override
    public List<SubscriptionBaseEvent> getEventsOnCancelPlan(final DefaultSubscriptionBase subscription,
                                                             final DateTime requestedDate, final DateTime effectiveDate, final DateTime processedDate,
                                                             final boolean addCancellationAddOnForEventsIfRequired, final InternalTenantContext internalTenantContext) throws CatalogApiException {
        final List<SubscriptionBaseEvent> cancelEvents = new ArrayList<SubscriptionBaseEvent>();
        final SubscriptionBaseEvent cancelEvent = new ApiEventCancel(new ApiEventBuilder()
                                                                             .setSubscriptionId(subscription.getId())
                                                                             .setActiveVersion(subscription.getActiveVersion())
                                                                             .setProcessedDate(processedDate)
                                                                             .setEffectiveDate(effectiveDate)
                                                                             .setRequestedDate(requestedDate)
                                                                             .setFromDisk(true));
        cancelEvents.add(cancelEvent);
        if (subscription.getCategory() == ProductCategory.BASE && addCancellationAddOnForEventsIfRequired) {
            final Product currentBaseProduct = cancelEvent.getEffectiveDate().compareTo(clock.getUTCNow()) <= 0 ? null : subscription.getCurrentPlan().getProduct();
            addCancellationAddOnForEventsIfRequired(cancelEvents, currentBaseProduct, subscription.getBundleId(), requestedDate, effectiveDate, processedDate, internalTenantContext);
        }
        return cancelEvents;
    }

    public int cancelAddOnsIfRequired(final Product baseProduct, final UUID bundleId, final DateTime effectiveDate, final CallContext context) throws CatalogApiException {
        // If cancellation/change occur in the future, there is nothing to do
        final DateTime now = clock.getUTCNow();
        if (effectiveDate.compareTo(now) > 0) {
            return 0;
        }

        final List<SubscriptionBaseEvent> cancelEvents = new LinkedList<SubscriptionBaseEvent>();
        final InternalCallContext internalCallContext = createCallContextFromBundleId(bundleId, context);
        final List<DefaultSubscriptionBase> subscriptionsToBeCancelled = addCancellationAddOnForEventsIfRequired(cancelEvents, baseProduct, bundleId, now, effectiveDate, now, internalCallContext);
        if (!subscriptionsToBeCancelled.isEmpty()) {
            dao.cancelSubscriptions(subscriptionsToBeCancelled, cancelEvents, internalCallContext);
        }
        return subscriptionsToBeCancelled.size();
    }

    private List<DefaultSubscriptionBase> addCancellationAddOnForEventsIfRequired(final List<SubscriptionBaseEvent> events, final Product baseProduct, final UUID bundleId,
                                                                                  final DateTime requestedDate, final DateTime effectiveDate, final DateTime processedDate, final InternalTenantContext internalTenantContext) throws CatalogApiException {

        final List<DefaultSubscriptionBase> subscriptionsToBeCancelled = new ArrayList<DefaultSubscriptionBase>();

        final List<SubscriptionBase> subscriptions = dao.getSubscriptions(bundleId, ImmutableList.<SubscriptionBaseEvent>of(), internalTenantContext);

        for (final SubscriptionBase subscription : subscriptions) {
            final DefaultSubscriptionBase cur = (DefaultSubscriptionBase) subscription;
            if (cur.getState() == EntitlementState.CANCELLED ||
                cur.getCategory() != ProductCategory.ADD_ON) {
                continue;
            }

            final Plan addonCurrentPlan = cur.getCurrentPlan();
            if (baseProduct == null ||
                addonUtils.isAddonIncludedFromProdName(baseProduct.getName(), addonCurrentPlan, requestedDate, internalTenantContext) ||
                !addonUtils.isAddonAvailableFromProdName(baseProduct.getName(), addonCurrentPlan, requestedDate, internalTenantContext)) {
                //
                // Perform AO cancellation using the effectiveDate of the BP
                //
                final SubscriptionBaseEvent cancelEvent = new ApiEventCancel(new ApiEventBuilder()
                                                                                     .setSubscriptionId(cur.getId())
                                                                                     .setActiveVersion(cur.getActiveVersion())
                                                                                     .setProcessedDate(processedDate)
                                                                                     .setEffectiveDate(effectiveDate)
                                                                                     .setRequestedDate(requestedDate)
                                                                                     .setFromDisk(true));
                subscriptionsToBeCancelled.add(cur);
                events.add(cancelEvent);
            }
        }
        return subscriptionsToBeCancelled;
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

    private InternalTenantContext createTenantContextFromBundleId(final UUID bundleId, final TenantContext context) {
        return internalCallContextFactory.createInternalTenantContext(bundleId, ObjectType.BUNDLE, context);
    }
}
