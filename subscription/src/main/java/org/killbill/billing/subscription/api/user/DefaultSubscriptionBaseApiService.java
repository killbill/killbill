/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

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
import org.killbill.billing.catalog.api.PlanPhasePriceOverride;
import org.killbill.billing.catalog.api.PlanPhasePriceOverridesWithCallContext;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.PlanSpecifier;
import org.killbill.billing.catalog.api.PriceList;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.subscription.alignment.PlanAligner;
import org.killbill.billing.subscription.alignment.TimedPhase;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseApiService;
import org.killbill.billing.subscription.api.svcs.DefaultPlanPhasePriceOverridesWithCallContext;
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
import org.killbill.billing.subscription.events.user.ApiEventUncancel;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.clock.Clock;
import org.killbill.clock.DefaultClock;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
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
                                              final String realPriceList, final DateTime effectiveDate, final DateTime processedDate,
                                              final CallContext context) throws SubscriptionBaseApiException {
        final DefaultSubscriptionBase subscription = new DefaultSubscriptionBase(builder, this, clock);

        final InternalCallContext internalCallContext = createCallContextFromBundleId(subscription.getBundleId(), context);

        try {
            final List<SubscriptionBaseEvent> events = getEventsOnCreation(subscription.getBundleId(), subscription.getId(), subscription.getAlignStartDate(), subscription.getBundleStartDate(),
                                                                           plan, initialPhase, realPriceList, effectiveDate, processedDate, internalCallContext);
            dao.createSubscription(subscription, events, internalCallContext);
            subscription.rebuildTransitions(dao.getEventsForSubscription(subscription.getId(), internalCallContext), catalogService.getFullCatalog(internalCallContext));
            return subscription;
        } catch (final CatalogApiException e) {
            throw new SubscriptionBaseApiException(e);
        }
    }

    @Override
    public List<DefaultSubscriptionBase> createPlans(final Iterable<SubscriptionSpecifier> subscriptions, final CallContext context) throws SubscriptionBaseApiException {

        Map<UUID, List<SubscriptionBaseEvent>> eventsMap = new HashMap<UUID, List<SubscriptionBaseEvent>>();
        List<DefaultSubscriptionBase> subscriptionBaseList = new ArrayList<DefaultSubscriptionBase>();
        for (SubscriptionSpecifier subscription : subscriptions) {

            try {
                final DefaultSubscriptionBase subscriptionBase = new DefaultSubscriptionBase(subscription.getBuilder(), this, clock);
                final InternalCallContext internalCallContext = createCallContextFromBundleId(subscriptionBase.getBundleId(), context);
                final List<SubscriptionBaseEvent> events = getEventsOnCreation(subscriptionBase.getBundleId(), subscriptionBase.getId(), subscriptionBase.getAlignStartDate(),
                                                                               subscriptionBase.getBundleStartDate(), subscription.getPlan(),
                                                                               subscription.getInitialPhase(), subscription.getRealPriceList(),
                                                                               subscription.getEffectiveDate(), subscription.getProcessedDate(), internalCallContext);
                eventsMap.put(subscriptionBase.getId(), events);
                subscriptionBaseList.add(subscriptionBase);

            } catch (final CatalogApiException e) {
                throw new SubscriptionBaseApiException(e);
            }
        }

        final InternalCallContext internalCallContext = createCallContextFromBundleId(subscriptionBaseList.get(0).getBundleId(), context);
        dao.createSubscriptionWithAddOns(subscriptionBaseList, eventsMap, internalCallContext);

        final DefaultSubscriptionBase baseSubscription = findBaseSubscription(subscriptionBaseList);
        try {
            baseSubscription.rebuildTransitions(dao.getEventsForSubscription(baseSubscription.getId(), internalCallContext),
                                                catalogService.getFullCatalog(internalCallContext));

            for (final DefaultSubscriptionBase input : subscriptionBaseList) {
                if (input.getId().equals(baseSubscription.getId())) {
                    continue;
                }

                input.rebuildTransitions(dao.getEventsForSubscription(input.getId(), internalCallContext),
                                         catalogService.getFullCatalog(internalCallContext));
            }
        } catch (CatalogApiException e) {
            throw new SubscriptionBaseApiException(e);
        }
        return subscriptionBaseList;
    }

    private DefaultSubscriptionBase findBaseSubscription(final List<DefaultSubscriptionBase> subscriptionBaseList) {
        return Iterables.tryFind(subscriptionBaseList, new Predicate<DefaultSubscriptionBase>() {
            @Override
            public boolean apply(final DefaultSubscriptionBase subscription) {
                return ProductCategory.BASE.equals(subscription.getCategory());
            }
        }).orNull();
    }

    @Override
    public boolean cancel(final DefaultSubscriptionBase subscription, final CallContext context) throws SubscriptionBaseApiException {
        final EntitlementState currentState = subscription.getState();
        if (currentState == EntitlementState.CANCELLED) {
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
            final InternalCallContext internalCallContext = createCallContextFromBundleId(subscription.getBundleId(), context);
            final BillingActionPolicy policy = catalogService.getFullCatalog(internalCallContext).planCancelPolicy(planPhase, now);
            final DateTime effectiveDate = subscription.getPlanChangeEffectiveDate(policy);

            return doCancelPlan(ImmutableMap.<DefaultSubscriptionBase, DateTime>of(subscription, effectiveDate), now, internalCallContext);
        } catch (final CatalogApiException e) {
            throw new SubscriptionBaseApiException(e);
        }
    }

    @Override
    public boolean cancelWithRequestedDate(final DefaultSubscriptionBase subscription, final DateTime requestedDateWithMs, final CallContext context) throws SubscriptionBaseApiException {
        final EntitlementState currentState = subscription.getState();
        if (currentState == EntitlementState.CANCELLED) {
            throw new SubscriptionBaseApiException(ErrorCode.SUB_CANCEL_BAD_STATE, subscription.getId(), currentState);
        }
        final DateTime now = clock.getUTCNow();
        final DateTime effectiveDate = (requestedDateWithMs != null) ? DefaultClock.truncateMs(requestedDateWithMs) : now;

        final InternalCallContext internalCallContext = createCallContextFromBundleId(subscription.getBundleId(), context);
        return doCancelPlan(ImmutableMap.<DefaultSubscriptionBase, DateTime>of(subscription, effectiveDate), now, internalCallContext);
    }

    @Override
    public boolean cancelWithPolicy(final DefaultSubscriptionBase subscription, final BillingActionPolicy policy, final CallContext context) throws SubscriptionBaseApiException {
        final EntitlementState currentState = subscription.getState();
        if (currentState == EntitlementState.CANCELLED) {
            throw new SubscriptionBaseApiException(ErrorCode.SUB_CANCEL_BAD_STATE, subscription.getId(), currentState);
        }

        final InternalCallContext internalCallContext = createCallContextFromBundleId(subscription.getBundleId(), context);
        return cancelWithPolicyNoValidation(ImmutableList.<DefaultSubscriptionBase>of(subscription), policy, internalCallContext);
    }

    @Override
    public boolean cancelWithPolicyNoValidation(final Iterable<DefaultSubscriptionBase> subscriptions, final BillingActionPolicy policy, final InternalCallContext context) throws SubscriptionBaseApiException {
        final Map<DefaultSubscriptionBase, DateTime> subscriptionsWithEffectiveDate = new HashMap<DefaultSubscriptionBase, DateTime>();
        final DateTime now = clock.getUTCNow();

        for (final DefaultSubscriptionBase subscription : subscriptions) {
            final DateTime effectiveDate = subscription.getPlanChangeEffectiveDate(policy);
            subscriptionsWithEffectiveDate.put(subscription, effectiveDate);
        }

        return doCancelPlan(subscriptionsWithEffectiveDate, now, context);
    }

    private boolean doCancelPlan(final Map<DefaultSubscriptionBase, DateTime> subscriptions, final DateTime now, final InternalCallContext internalCallContext) throws SubscriptionBaseApiException {
        final List<DefaultSubscriptionBase> subscriptionsToBeCancelled = new LinkedList<DefaultSubscriptionBase>();
        final List<SubscriptionBaseEvent> cancelEvents = new LinkedList<SubscriptionBaseEvent>();

        try {
            for (final DefaultSubscriptionBase subscription : subscriptions.keySet()) {
                final DateTime effectiveDate = subscriptions.get(subscription);
                validateEffectiveDate(subscription, effectiveDate);

                subscriptionsToBeCancelled.add(subscription);
                cancelEvents.addAll(getEventsOnCancelPlan(subscription, effectiveDate, now, false, internalCallContext));

                if (subscription.getCategory() == ProductCategory.BASE) {
                    subscriptionsToBeCancelled.addAll(computeAddOnsToCancel(cancelEvents, null, subscription.getBundleId(), effectiveDate, internalCallContext));
                }
            }

            dao.cancelSubscriptions(subscriptionsToBeCancelled, cancelEvents, internalCallContext);

            boolean allSubscriptionsCancelled = true;
            for (final DefaultSubscriptionBase subscription : subscriptions.keySet()) {
                final Catalog fullCatalog = catalogService.getFullCatalog(internalCallContext);
                subscription.rebuildTransitions(dao.getEventsForSubscription(subscription.getId(), internalCallContext), fullCatalog);
                allSubscriptionsCancelled = allSubscriptionsCancelled && (subscription.getState() == EntitlementState.CANCELLED);
            }

            return allSubscriptionsCancelled;
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
                                                                                 .setEffectiveDate(now)
                                                                                 .setFromDisk(true));

        final List<SubscriptionBaseEvent> uncancelEvents = new ArrayList<SubscriptionBaseEvent>();
        uncancelEvents.add(uncancelEvent);

        final InternalCallContext internalCallContext = createCallContextFromBundleId(subscription.getBundleId(), context);
        final TimedPhase nextTimedPhase = planAligner.getNextTimedPhase(subscription, now, internalCallContext);
        final PhaseEvent nextPhaseEvent = (nextTimedPhase != null) ?
                                          PhaseEventData.createNextPhaseEvent(subscription.getId(), nextTimedPhase.getPhase().getName(), nextTimedPhase.getStartPhase()) :
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
    public DateTime dryRunChangePlan(final DefaultSubscriptionBase subscription,
                                     final String productName,
                                     final BillingPeriod term,
                                     final String priceList,
                                     @Nullable final DateTime requestedDateWithMs,
                                     @Nullable final BillingActionPolicy requestedPolicy,
                                     final TenantContext context) throws SubscriptionBaseApiException {
        final DateTime now = clock.getUTCNow();

        BillingActionPolicy policyMaybeNull = requestedPolicy;
        if (requestedDateWithMs == null && requestedPolicy == null) {
            final PlanChangeResult planChangeResult = getPlanChangeResult(subscription, productName, term, priceList, now, context);
            policyMaybeNull = planChangeResult.getPolicy();
        }

        if (policyMaybeNull != null) {
            return subscription.getPlanChangeEffectiveDate(policyMaybeNull);
        } else if (requestedDateWithMs != null) {
            return DefaultClock.truncateMs(requestedDateWithMs);
        } else {
            return now;
        }
    }

    @Override
    public DateTime changePlan(final DefaultSubscriptionBase subscription, final String productName, final BillingPeriod term,
                               final String priceList, final List<PlanPhasePriceOverride> overrides, final CallContext context) throws SubscriptionBaseApiException {
        final DateTime now = clock.getUTCNow();

        validateEntitlementState(subscription);

        final PlanChangeResult planChangeResult = getPlanChangeResult(subscription, productName, term, priceList, now, context);
        final DateTime effectiveDate = dryRunChangePlan(subscription, productName, term, priceList, null, planChangeResult.getPolicy(), context);
        validateEffectiveDate(subscription, effectiveDate);

        try {
            doChangePlan(subscription, productName, term, planChangeResult.getNewPriceList().getName(), overrides, effectiveDate, context);
        } catch (final CatalogApiException e) {
            throw new SubscriptionBaseApiException(e);
        }

        return effectiveDate;
    }

    @Override
    public DateTime changePlanWithRequestedDate(final DefaultSubscriptionBase subscription, final String productName, final BillingPeriod term,
                                                final String priceList, final List<PlanPhasePriceOverride> overrides,
                                                final DateTime requestedDateWithMs, final CallContext context) throws SubscriptionBaseApiException {
        final DateTime effectiveDate = dryRunChangePlan(subscription, productName, term, priceList, requestedDateWithMs, null, context);
        validateEffectiveDate(subscription, effectiveDate);
        validateEntitlementState(subscription);

        try {
            doChangePlan(subscription, productName, term, priceList, overrides, effectiveDate, context);
        } catch (final CatalogApiException e) {
            throw new SubscriptionBaseApiException(e);
        }

        return effectiveDate;
    }

    @Override
    public DateTime changePlanWithPolicy(final DefaultSubscriptionBase subscription, final String productName, final BillingPeriod term,
                                         final String priceList, final List<PlanPhasePriceOverride> overrides, final BillingActionPolicy policy, final CallContext context) throws SubscriptionBaseApiException {
        validateEntitlementState(subscription);

        final DateTime effectiveDate = dryRunChangePlan(subscription, productName, term, priceList, null, policy, context);
        try {
            doChangePlan(subscription, productName, term, priceList, overrides, effectiveDate, context);
        } catch (final CatalogApiException e) {
            throw new SubscriptionBaseApiException(e);
        }

        return effectiveDate;
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

    private void doChangePlan(final DefaultSubscriptionBase subscription,
                              final String newProductName,
                              final BillingPeriod newBillingPeriod,
                              final String newPriceList,
                              final List<PlanPhasePriceOverride> overrides,
                              final DateTime effectiveDate,
                              final CallContext context) throws SubscriptionBaseApiException, CatalogApiException {
        final InternalCallContext internalCallContext = createCallContextFromBundleId(subscription.getBundleId(), context);
        final PlanPhasePriceOverridesWithCallContext overridesWithContext = new DefaultPlanPhasePriceOverridesWithCallContext(overrides, context);
        final Plan newPlan = catalogService.getFullCatalog(internalCallContext).createOrFindPlan(newProductName, newBillingPeriod, newPriceList, overridesWithContext, effectiveDate, subscription.getStartDate());

        if (newPlan.getProduct().getCategory() != subscription.getCategory()) {
            throw new SubscriptionBaseApiException(ErrorCode.SUB_CHANGE_INVALID, subscription.getId());
        }

        final List<DefaultSubscriptionBase> addOnSubscriptionsToBeCancelled = new ArrayList<DefaultSubscriptionBase>();
        final List<SubscriptionBaseEvent> addOnCancelEvents = new ArrayList<SubscriptionBaseEvent>();
        final List<SubscriptionBaseEvent> changeEvents = getEventsOnChangePlan(subscription, newPlan, newPriceList, effectiveDate, true, addOnSubscriptionsToBeCancelled, addOnCancelEvents, internalCallContext);

        dao.changePlan(subscription, changeEvents, addOnSubscriptionsToBeCancelled, addOnCancelEvents, internalCallContext);

        final Catalog fullCatalog = catalogService.getFullCatalog(internalCallContext);
        subscription.rebuildTransitions(dao.getEventsForSubscription(subscription.getId(), internalCallContext), fullCatalog);
    }

    @Override
    public List<SubscriptionBaseEvent> getEventsOnCreation(final UUID bundleId, final UUID subscriptionId, final DateTime alignStartDate, final DateTime bundleStartDate,
                                                           final Plan plan, final PhaseType initialPhase,
                                                           final String realPriceList, final DateTime effectiveDate, final DateTime processedDate,
                                                           final InternalTenantContext internalTenantContext) throws CatalogApiException, SubscriptionBaseApiException {
        final TimedPhase[] curAndNextPhases = planAligner.getCurrentAndNextTimedPhaseOnCreate(alignStartDate, bundleStartDate, plan, initialPhase,
                                                                                              realPriceList, effectiveDate, internalTenantContext);

        final ApiEventBuilder createBuilder = new ApiEventBuilder()
                .setSubscriptionId(subscriptionId)
                .setEventPlan(plan.getName())
                .setEventPlanPhase(curAndNextPhases[0].getPhase().getName())
                .setEventPriceList(realPriceList)
                .setEffectiveDate(effectiveDate)
                .setFromDisk(true);
        final ApiEvent creationEvent = new ApiEventCreate(createBuilder);

        final TimedPhase nextTimedPhase = curAndNextPhases[1];
        final PhaseEvent nextPhaseEvent = (nextTimedPhase != null) ?
                                          PhaseEventData.createNextPhaseEvent(subscriptionId, nextTimedPhase.getPhase().getName(), nextTimedPhase.getStartPhase()) :
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
                                                             final String newPriceList, final DateTime effectiveDate, final DateTime processedDate,
                                                             final boolean addCancellationAddOnForEventsIfRequired, final InternalTenantContext internalTenantContext) throws CatalogApiException, SubscriptionBaseApiException {
        final Collection<DefaultSubscriptionBase> addOnSubscriptionsToBeCancelled = new ArrayList<DefaultSubscriptionBase>();
        final List<SubscriptionBaseEvent> addOnCancelEvents = new ArrayList<SubscriptionBaseEvent>();

        final List<SubscriptionBaseEvent> changeEvents = getEventsOnChangePlan(subscription, newPlan, newPriceList, effectiveDate, addCancellationAddOnForEventsIfRequired, addOnSubscriptionsToBeCancelled, addOnCancelEvents, internalTenantContext);
        changeEvents.addAll(addOnCancelEvents);
        return changeEvents;
    }

    private List<SubscriptionBaseEvent> getEventsOnChangePlan(final DefaultSubscriptionBase subscription, final Plan newPlan,
                                                              final String newPriceList, final DateTime effectiveDate,
                                                              final boolean addCancellationAddOnForEventsIfRequired,
                                                              final Collection<DefaultSubscriptionBase> addOnSubscriptionsToBeCancelled,
                                                              final List<SubscriptionBaseEvent> addOnCancelEvents,
                                                              final InternalTenantContext internalTenantContext) throws CatalogApiException, SubscriptionBaseApiException {
        final TimedPhase currentTimedPhase = planAligner.getCurrentTimedPhaseOnChange(subscription, newPlan, newPriceList, effectiveDate, internalTenantContext);

        final SubscriptionBaseEvent changeEvent = new ApiEventChange(new ApiEventBuilder()
                                                                             .setSubscriptionId(subscription.getId())
                                                                             .setEventPlan(newPlan.getName())
                                                                             .setEventPlanPhase(currentTimedPhase.getPhase().getName())
                                                                             .setEventPriceList(newPriceList)
                                                                             .setEffectiveDate(effectiveDate)
                                                                             .setFromDisk(true));

        final TimedPhase nextTimedPhase = planAligner.getNextTimedPhaseOnChange(subscription, newPlan, newPriceList, effectiveDate, internalTenantContext);
        final PhaseEvent nextPhaseEvent = (nextTimedPhase != null) ?
                                          PhaseEventData.createNextPhaseEvent(subscription.getId(),
                                                                              nextTimedPhase.getPhase().getName(), nextTimedPhase.getStartPhase()) :
                                          null;

        final List<SubscriptionBaseEvent> changeEvents = new ArrayList<SubscriptionBaseEvent>();
        // Only add the PHASE if it does not coincide with the CHANGE, if not this is 'just' a CHANGE.
        changeEvents.add(changeEvent);
        if (nextPhaseEvent != null && !nextPhaseEvent.getEffectiveDate().equals(changeEvent.getEffectiveDate())) {
            changeEvents.add(nextPhaseEvent);
        }

        if (subscription.getCategory() == ProductCategory.BASE && addCancellationAddOnForEventsIfRequired) {
            final Product currentBaseProduct = changeEvent.getEffectiveDate().compareTo(clock.getUTCNow()) <= 0 ? newPlan.getProduct() : subscription.getCurrentPlan().getProduct();
            addOnSubscriptionsToBeCancelled.addAll(addCancellationAddOnForEventsIfRequired(addOnCancelEvents, currentBaseProduct, subscription.getBundleId(), effectiveDate, internalTenantContext));
        }
        return changeEvents;
    }

    @Override
    public List<SubscriptionBaseEvent> getEventsOnCancelPlan(final DefaultSubscriptionBase subscription,
                                                             final DateTime effectiveDate, final DateTime processedDate,
                                                             final boolean addCancellationAddOnForEventsIfRequired, final InternalTenantContext internalTenantContext) throws CatalogApiException {
        final List<SubscriptionBaseEvent> cancelEvents = new ArrayList<SubscriptionBaseEvent>();
        final SubscriptionBaseEvent cancelEvent = new ApiEventCancel(new ApiEventBuilder()
                                                                             .setSubscriptionId(subscription.getId())
                                                                             .setEffectiveDate(effectiveDate)
                                                                             .setFromDisk(true));
        cancelEvents.add(cancelEvent);
        if (subscription.getCategory() == ProductCategory.BASE && addCancellationAddOnForEventsIfRequired) {
            final Product currentBaseProduct = cancelEvent.getEffectiveDate().compareTo(clock.getUTCNow()) <= 0 ? null : subscription.getCurrentPlan().getProduct();
            addCancellationAddOnForEventsIfRequired(cancelEvents, currentBaseProduct, subscription.getBundleId(), effectiveDate, internalTenantContext);
        }
        return cancelEvents;
    }

    @Override
    public int cancelAddOnsIfRequiredOnBasePlanEvent(final DefaultSubscriptionBase subscription, final SubscriptionBaseEvent event, final CallContext context) throws CatalogApiException {
        final Product baseProduct = (subscription.getState() == EntitlementState.CANCELLED) ? null : subscription.getCurrentPlan().getProduct();

        final List<SubscriptionBaseEvent> cancelEvents = new LinkedList<SubscriptionBaseEvent>();
        final InternalCallContext internalCallContext = createCallContextFromBundleId(subscription.getBundleId(), context);
        final List<DefaultSubscriptionBase> subscriptionsToBeCancelled = computeAddOnsToCancel(cancelEvents, baseProduct, subscription.getBundleId(), event.getEffectiveDate(), internalCallContext);
        dao.cancelSubscriptionsOnBasePlanEvent(subscription, event, subscriptionsToBeCancelled, cancelEvents, internalCallContext);

        return subscriptionsToBeCancelled.size();
    }

    private List<DefaultSubscriptionBase> computeAddOnsToCancel(final List<SubscriptionBaseEvent> cancelEvents, final Product baseProduct, final UUID bundleId, final DateTime effectiveDate, final InternalCallContext internalCallContext) throws CatalogApiException {
        // If cancellation/change occur in the future, there is nothing to do
        final DateTime now = clock.getUTCNow();
        if (effectiveDate.compareTo(now) > 0) {
            return ImmutableList.<DefaultSubscriptionBase>of();
        } else {
            return addCancellationAddOnForEventsIfRequired(cancelEvents, baseProduct, bundleId, effectiveDate, internalCallContext);
        }
    }

    private List<DefaultSubscriptionBase> addCancellationAddOnForEventsIfRequired(final List<SubscriptionBaseEvent> events, final Product baseProduct, final UUID bundleId,
                                                                                  final DateTime effectiveDate, final InternalTenantContext internalTenantContext) throws CatalogApiException {

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
                addonUtils.isAddonIncludedFromProdName(baseProduct.getName(), addonCurrentPlan, effectiveDate, internalTenantContext) ||
                !addonUtils.isAddonAvailableFromProdName(baseProduct.getName(), addonCurrentPlan, effectiveDate, internalTenantContext)) {
                //
                // Perform AO cancellation using the effectiveDate of the BP
                //
                final SubscriptionBaseEvent cancelEvent = new ApiEventCancel(new ApiEventBuilder()
                                                                                     .setSubscriptionId(cur.getId())
                                                                                     .setEffectiveDate(effectiveDate)
                                                                                     .setFromDisk(true));
                subscriptionsToBeCancelled.add(cur);
                events.add(cancelEvent);
            }
        }
        return subscriptionsToBeCancelled;
    }

    private void validateEffectiveDate(final DefaultSubscriptionBase subscription, final DateTime effectiveDate) throws SubscriptionBaseApiException {

        final SubscriptionBaseTransition previousTransition = subscription.getPreviousTransition();

        // Our effectiveDate must be after or equal the last transition that already occured (START, PHASE1, PHASE2,...) or the startDate for future started subscription
        final DateTime earliestValidDate = previousTransition != null ? previousTransition.getEffectiveTransitionTime() : subscription.getStartDate();
        if (effectiveDate.isBefore(earliestValidDate)) {
            throw new SubscriptionBaseApiException(ErrorCode.SUB_INVALID_REQUESTED_DATE,
                                                   effectiveDate.toString(), previousTransition != null ? previousTransition.getEffectiveTransitionTime() : "null");
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
