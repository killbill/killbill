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

package com.ning.billing.entitlement.alignment;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import javax.annotation.Nullable;

import org.joda.time.DateTime;

import com.ning.billing.ErrorCode;
import com.ning.billing.catalog.api.Catalog;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.catalog.api.Duration;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanAlignmentChange;
import com.ning.billing.catalog.api.PlanAlignmentCreate;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PlanSpecifier;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.api.user.SubscriptionTransitionData;
import com.ning.billing.entitlement.exceptions.EntitlementError;
import com.ning.billing.util.clock.DefaultClock;

import com.google.inject.Inject;

/**
 * PlanAligner offers specific APIs to return the correct {@code TimedPhase} when creating, changing Plan or to compute
 * next Phase on current Plan.
 */
public class PlanAligner {
    private final CatalogService catalogService;

    @Inject
    public PlanAligner(final CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    private enum WhichPhase {
        CURRENT,
        NEXT
    }

    /**
     * Returns the current and next phase for the subscription in creation
     *
     * @param subscription  the subscription in creation (only the start date and the bundle start date are looked at)
     * @param plan          the current Plan
     * @param initialPhase  the initialPhase on which we should create that subscription. can be null
     * @param priceList     the priceList
     * @param requestedDate the requested date (only used to load the catalog)
     * @param effectiveDate the effective creation date (driven by the catalog policy, i.e. when the creation occurs)
     * @return the current and next phases
     * @throws CatalogApiException         for catalog errors
     * @throws EntitlementUserApiException for entitlement errors
     */
    public TimedPhase[] getCurrentAndNextTimedPhaseOnCreate(final SubscriptionData subscription,
                                                            final Plan plan,
                                                            final PhaseType initialPhase,
                                                            final String priceList,
                                                            final DateTime requestedDate,
                                                            final DateTime effectiveDate) throws CatalogApiException, EntitlementUserApiException {
        final List<TimedPhase> timedPhases = getTimedPhaseOnCreate(subscription.getAlignStartDate(),
                                                                   subscription.getBundleStartDate(),
                                                                   plan,
                                                                   initialPhase,
                                                                   priceList,
                                                                   requestedDate);
        final TimedPhase[] result = new TimedPhase[2];
        result[0] = getTimedPhase(timedPhases, effectiveDate, WhichPhase.CURRENT);
        result[1] = getTimedPhase(timedPhases, effectiveDate, WhichPhase.NEXT);
        return result;
    }

    /**
     * Returns current Phase for that Plan change
     *
     * @param subscription  the subscription in change (only start date, bundle start date, current phase, plan and pricelist
     *                      are looked at)
     * @param plan          the current Plan
     * @param priceList     the priceList on which we should change that subscription.
     * @param requestedDate the requested date
     * @param effectiveDate the effective change date (driven by the catalog policy, i.e. when the change occurs)
     * @return the current phase
     * @throws CatalogApiException         for catalog errors
     * @throws EntitlementUserApiException for entitlement errors
     */
    public TimedPhase getCurrentTimedPhaseOnChange(final SubscriptionData subscription,
                                                   final Plan plan,
                                                   final String priceList,
                                                   final DateTime requestedDate,
                                                   final DateTime effectiveDate) throws CatalogApiException, EntitlementUserApiException {
        return getTimedPhaseOnChange(subscription, plan, priceList, requestedDate, effectiveDate, WhichPhase.CURRENT);
    }

    /**
     * Returns next Phase for that Plan change
     *
     * @param subscription  the subscription in change (only start date, bundle start date, current phase, plan and pricelist
     *                      are looked at)
     * @param plan          the current Plan
     * @param priceList     the priceList on which we should change that subscription.
     * @param requestedDate the requested date
     * @param effectiveDate the effective change date (driven by the catalog policy, i.e. when the change occurs)
     * @return the next phase
     * @throws CatalogApiException         for catalog errors
     * @throws EntitlementUserApiException for entitlement errors
     */
    public TimedPhase getNextTimedPhaseOnChange(final SubscriptionData subscription,
                                                final Plan plan,
                                                final String priceList,
                                                final DateTime requestedDate,
                                                final DateTime effectiveDate) throws CatalogApiException, EntitlementUserApiException {
        return getTimedPhaseOnChange(subscription, plan, priceList, requestedDate, effectiveDate, WhichPhase.NEXT);
    }

    /**
     * Returns next Phase for that Subscription at a point in time
     *
     * @param subscription  the subscription for which we need to compute the next Phase event
     * @param requestedDate the requested date
     * @param effectiveDate the date at which we look to compute that event. effective needs to be after last Plan change or initial Plan
     * @return the next phase
     */
    public TimedPhase getNextTimedPhase(final SubscriptionData subscription, final DateTime requestedDate, final DateTime effectiveDate) {
        try {
            final SubscriptionTransitionData lastPlanTransition = subscription.getInitialTransitionForCurrentPlan();
            if (effectiveDate.isBefore(lastPlanTransition.getEffectiveTransitionTime())) {
                throw new EntitlementError(String.format("Cannot specify an effectiveDate prior to last Plan Change, subscription = %s, effectiveDate = %s",
                                                         subscription.getId(), effectiveDate));
            }

            switch (lastPlanTransition.getTransitionType()) {
                // If we never had any Plan change, borrow the logic for createPlan alignment
                case MIGRATE_ENTITLEMENT:
                case CREATE:
                case RE_CREATE:
                case TRANSFER:
                    final List<TimedPhase> timedPhases = getTimedPhaseOnCreate(subscription.getAlignStartDate(),
                                                                               subscription.getBundleStartDate(),
                                                                               lastPlanTransition.getNextPlan(),
                                                                               lastPlanTransition.getNextPhase().getPhaseType(),
                                                                               lastPlanTransition.getNextPriceList().getName(),
                                                                               requestedDate);
                    return getTimedPhase(timedPhases, effectiveDate, WhichPhase.NEXT);
                // If we went through Plan changes, borrow the logic for changePlan alignment
                case CHANGE:
                    return getTimedPhaseOnChange(subscription.getAlignStartDate(),
                                                 subscription.getBundleStartDate(),
                                                 lastPlanTransition.getPreviousPhase(),
                                                 lastPlanTransition.getPreviousPlan(),
                                                 lastPlanTransition.getPreviousPriceList().getName(),
                                                 lastPlanTransition.getNextPlan(),
                                                 lastPlanTransition.getNextPriceList().getName(),
                                                 requestedDate,
                                                 effectiveDate,
                                                 WhichPhase.NEXT);
                default:
                    throw new EntitlementError(String.format("Unexpected initial transition %s for current plan %s on subscription %s",
                                                             lastPlanTransition.getTransitionType(), subscription.getCurrentPlan(), subscription.getId()));
            }
        } catch (Exception /* EntitlementUserApiException, CatalogApiException */ e) {
            throw new EntitlementError(String.format("Could not compute next phase change for subscription %s", subscription.getId()), e);
        }
    }

    private List<TimedPhase> getTimedPhaseOnCreate(final DateTime subscriptionStartDate,
                                                   final DateTime bundleStartDate,
                                                   final Plan plan,
                                                   final PhaseType initialPhase,
                                                   final String priceList,
                                                   final DateTime requestedDate)
            throws CatalogApiException, EntitlementUserApiException {
        final Catalog catalog = catalogService.getFullCatalog();

        final PlanSpecifier planSpecifier = new PlanSpecifier(plan.getProduct().getName(),
                                                              plan.getProduct().getCategory(),
                                                              plan.getBillingPeriod(),
                                                              priceList);

        final DateTime planStartDate;
        final PlanAlignmentCreate alignment = catalog.planCreateAlignment(planSpecifier, requestedDate);
        switch (alignment) {
            case START_OF_SUBSCRIPTION:
                planStartDate = subscriptionStartDate;
                break;
            case START_OF_BUNDLE:
                planStartDate = bundleStartDate;
                break;
            default:
                throw new EntitlementError(String.format("Unknown PlanAlignmentCreate %s", alignment));
        }

        return getPhaseAlignments(plan, initialPhase, planStartDate);
    }

    private TimedPhase getTimedPhaseOnChange(final SubscriptionData subscription,
                                             final Plan nextPlan,
                                             final String nextPriceList,
                                             final DateTime requestedDate,
                                             final DateTime effectiveDate,
                                             final WhichPhase which) throws CatalogApiException, EntitlementUserApiException {
        return getTimedPhaseOnChange(subscription.getAlignStartDate(),
                                     subscription.getBundleStartDate(),
                                     subscription.getCurrentPhase(),
                                     subscription.getCurrentPlan(),
                                     subscription.getCurrentPriceList().getName(),
                                     nextPlan,
                                     nextPriceList,
                                     requestedDate,
                                     effectiveDate,
                                     which);
    }

    private TimedPhase getTimedPhaseOnChange(final DateTime subscriptionStartDate,
                                             final DateTime bundleStartDate,
                                             final PlanPhase currentPhase,
                                             final Plan currentPlan,
                                             final String currentPriceList,
                                             final Plan nextPlan,
                                             final String priceList,
                                             final DateTime requestedDate,
                                             final DateTime effectiveDate,
                                             final WhichPhase which) throws CatalogApiException, EntitlementUserApiException {
        final Catalog catalog = catalogService.getFullCatalog();
        final ProductCategory currentCategory = currentPlan.getProduct().getCategory();
        final PlanPhaseSpecifier fromPlanPhaseSpecifier = new PlanPhaseSpecifier(currentPlan.getProduct().getName(),
                                                                                 currentCategory,
                                                                                 currentPlan.getBillingPeriod(),
                                                                                 currentPriceList,
                                                                                 currentPhase.getPhaseType());

        final PlanSpecifier toPlanSpecifier = new PlanSpecifier(nextPlan.getProduct().getName(),
                                                                nextPlan.getProduct().getCategory(),
                                                                nextPlan.getBillingPeriod(),
                                                                priceList);

        final DateTime planStartDate;
        final PlanAlignmentChange alignment = catalog.planChangeAlignment(fromPlanPhaseSpecifier, toPlanSpecifier, requestedDate);
        switch (alignment) {
            case START_OF_SUBSCRIPTION:
                planStartDate = subscriptionStartDate;
                break;
            case START_OF_BUNDLE:
                planStartDate = bundleStartDate;
                break;
            case CHANGE_OF_PLAN:
                planStartDate = effectiveDate;
                break;
            case CHANGE_OF_PRICELIST:
                throw new EntitlementError(String.format("Not implemented yet %s", alignment));
            default:
                throw new EntitlementError(String.format("Unknown PlanAlignmentChange %s", alignment));
        }

        final List<TimedPhase> timedPhases = getPhaseAlignments(nextPlan, null, planStartDate);
        return getTimedPhase(timedPhases, effectiveDate, which);
    }

    private List<TimedPhase> getPhaseAlignments(final Plan plan, @Nullable final PhaseType initialPhase, final DateTime initialPhaseStartDate) throws EntitlementUserApiException {
        if (plan == null) {
            return Collections.emptyList();
        }

        final List<TimedPhase> result = new LinkedList<TimedPhase>();
        DateTime curPhaseStart = (initialPhase == null) ? initialPhaseStartDate : null;
        DateTime nextPhaseStart;
        for (final PlanPhase cur : plan.getAllPhases()) {
            // For create we can specify the phase so skip any phase until we reach initialPhase
            if (curPhaseStart == null) {
                if (initialPhase != cur.getPhaseType()) {
                    continue;
                }
                curPhaseStart = initialPhaseStartDate;
            }

            result.add(new TimedPhase(cur, curPhaseStart));

            // STEPH check for duration null instead TimeUnit UNLIMITED
            if (cur.getPhaseType() != PhaseType.EVERGREEN) {
                final Duration curPhaseDuration = cur.getDuration();
                nextPhaseStart = DefaultClock.addDuration(curPhaseStart, curPhaseDuration);
                if (nextPhaseStart == null) {
                    throw new EntitlementError(String.format("Unexpected non ending UNLIMITED phase for plan %s",
                                                             plan.getName()));
                }
                curPhaseStart = nextPhaseStart;
            }
        }

        if (initialPhase != null && curPhaseStart == null) {
            throw new EntitlementUserApiException(ErrorCode.ENT_CREATE_BAD_PHASE, initialPhase);
        }

        return result;
    }

    // STEPH check for non evergreen Plans and what happens
    private TimedPhase getTimedPhase(final List<TimedPhase> timedPhases, final DateTime effectiveDate, final WhichPhase which) {
        TimedPhase cur = null;
        TimedPhase next = null;
        for (final TimedPhase phase : timedPhases) {
            if (phase.getStartPhase().isAfter(effectiveDate)) {
                next = phase;
                break;
            }
            cur = phase;
        }

        switch (which) {
            case CURRENT:
                return cur;
            case NEXT:
                return next;
            default:
                throw new EntitlementError(String.format("Unexpected %s TimedPhase", which));
        }
    }
}
