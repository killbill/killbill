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

package org.killbill.billing.subscription.alignment;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.joda.time.DateTime;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.Catalog;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.CatalogService;
import org.killbill.billing.catalog.api.Duration;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanAlignmentChange;
import org.killbill.billing.catalog.api.PlanAlignmentCreate;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.PlanSpecifier;
import org.killbill.billing.subscription.api.user.SubscriptionBaseApiException;
import org.killbill.billing.subscription.api.user.SubscriptionBaseTransitionData;
import org.killbill.billing.subscription.api.user.DefaultSubscriptionBase;
import org.killbill.billing.subscription.exceptions.SubscriptionBaseError;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

/**
 * PlanAligner offers specific APIs to return the correct {@code TimedPhase} when creating, changing Plan or to compute
 * next Phase on current Plan.
 */
public class PlanAligner extends BaseAligner {

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
     * @param alignStartDate  the subscription (align) startDate for the subscription
     * @param bundleStartDate the bundle startDate used alignment
     * @param plan            the current Plan
     * @param initialPhase    the initialPhase on which we should create that subscription. can be null
     * @param priceList       the priceList
     * @param effectiveDate   the effective creation date (driven by the catalog policy, i.e. when the creation occurs)
     * @return the current and next phases
     * @throws CatalogApiException         for catalog errors
     * @throws org.killbill.billing.subscription.api.user.SubscriptionBaseApiException for subscription errors
     */
    public TimedPhase[] getCurrentAndNextTimedPhaseOnCreate(final DateTime alignStartDate,
                                                            final DateTime bundleStartDate,
                                                            final Plan plan,
                                                            @Nullable final PhaseType initialPhase,
                                                            final String priceList,
                                                            final DateTime effectiveDate,
                                                            final InternalTenantContext context) throws CatalogApiException, SubscriptionBaseApiException {
        final List<TimedPhase> timedPhases = getTimedPhaseOnCreate(alignStartDate,
                                                                   bundleStartDate,
                                                                   plan,
                                                                   initialPhase,
                                                                   priceList,
                                                                   effectiveDate,
                                                                   context);
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
     * @param effectiveDate the effective change date (driven by the catalog policy, i.e. when the change occurs)
     * @return the current phase
     * @throws CatalogApiException         for catalog errors
     * @throws org.killbill.billing.subscription.api.user.SubscriptionBaseApiException for subscription errors
     */
    public TimedPhase getCurrentTimedPhaseOnChange(final DefaultSubscriptionBase subscription,
                                                   final Plan plan,
                                                   final String priceList,
                                                   final DateTime effectiveDate,
                                                   final InternalTenantContext context) throws CatalogApiException, SubscriptionBaseApiException {
        return getTimedPhaseOnChange(subscription, plan, effectiveDate, WhichPhase.CURRENT, context);
    }

    /**
     * Returns next Phase for that Plan change
     *
     * @param subscription  the subscription in change (only start date, bundle start date, current phase, plan and pricelist
     *                      are looked at)
     * @param plan          the current Plan
     * @param priceList     the priceList on which we should change that subscription.
     * @param effectiveDate the effective change date (driven by the catalog policy, i.e. when the change occurs)
     * @return the next phase
     * @throws CatalogApiException         for catalog errors
     * @throws org.killbill.billing.subscription.api.user.SubscriptionBaseApiException for subscription errors
     */
    public TimedPhase getNextTimedPhaseOnChange(final DefaultSubscriptionBase subscription,
                                                final Plan plan,
                                                final String priceList,
                                                final DateTime effectiveDate,
                                                final InternalTenantContext context) throws CatalogApiException, SubscriptionBaseApiException {
        return getTimedPhaseOnChange(subscription, plan, effectiveDate, WhichPhase.NEXT, context);
    }

    /**
     * Returns next Phase for that SubscriptionBase at a point in time
     *
     * @param subscription  the subscription for which we need to compute the next Phase event
     * @return the next phase
     */
    public TimedPhase getNextTimedPhase(final DefaultSubscriptionBase subscription, final DateTime effectiveDate, final InternalTenantContext context) {
        try {
            final SubscriptionBaseTransitionData lastPlanTransition = subscription.getLastTransitionForCurrentPlan();
            if (effectiveDate.isBefore(lastPlanTransition.getEffectiveTransitionTime())) {
                throw new SubscriptionBaseError(String.format("Cannot specify an effectiveDate prior to last Plan Change, subscription = %s, effectiveDate = %s",
                                                         subscription.getId(), effectiveDate));
            }

            switch (lastPlanTransition.getTransitionType()) {
                // If we never had any Plan change, borrow the logic for createPlan alignment
                case CREATE:
                case TRANSFER:
                    final List<TimedPhase> timedPhases = getTimedPhaseOnCreate(subscription.getAlignStartDate(),
                                                                               subscription.getBundleStartDate(),
                                                                               lastPlanTransition.getNextPlan(),
                                                                               lastPlanTransition.getNextPhase().getPhaseType(),
                                                                               lastPlanTransition.getNextPriceList().getName(),
                                                                               effectiveDate,
                                                                               context);
                    return getTimedPhase(timedPhases, effectiveDate, WhichPhase.NEXT);
                case CHANGE:
                    return getTimedPhaseOnChange(subscription.getAlignStartDate(),
                                                 subscription.getBundleStartDate(),
                                                 lastPlanTransition.getPreviousPhase(),
                                                 lastPlanTransition.getPreviousPlan(),
                                                 lastPlanTransition.getNextPlan(),
                                                 effectiveDate,
                                                 lastPlanTransition.getEffectiveTransitionTime(),
                                                 subscription.getAllTransitions().get(0).getNextPhase().getPhaseType(),
                                                 WhichPhase.NEXT,
                                                 context);
                default:
                    throw new SubscriptionBaseError(String.format("Unexpected initial transition %s for current plan %s on subscription %s",
                                                             lastPlanTransition.getTransitionType(), subscription.getCurrentPlan(), subscription.getId()));
            }
        } catch (Exception /* SubscriptionBaseApiException, CatalogApiException */ e) {
            throw new SubscriptionBaseError(String.format("Could not compute next phase change for subscription %s", subscription.getId()), e);
        }
    }

    private List<TimedPhase> getTimedPhaseOnCreate(final DateTime subscriptionStartDate,
                                                   final DateTime bundleStartDate,
                                                   final Plan plan,
                                                   @Nullable final PhaseType initialPhase,
                                                   final String priceList,
                                                   final DateTime effectiveDate,
                                                   final InternalTenantContext context)
            throws CatalogApiException, SubscriptionBaseApiException {
        final Catalog catalog = catalogService.getFullCatalog(true, true, context);

        final PlanSpecifier planSpecifier = new PlanSpecifier(plan.getName());

        final DateTime planStartDate;
        final PlanAlignmentCreate alignment = catalog.planCreateAlignment(planSpecifier, effectiveDate);
        switch (alignment) {
            case START_OF_SUBSCRIPTION:
                planStartDate = subscriptionStartDate;
                break;
            case START_OF_BUNDLE:
                planStartDate = bundleStartDate;
                break;
            default:
                throw new SubscriptionBaseError(String.format("Unknown PlanAlignmentCreate %s", alignment));
        }

        return getPhaseAlignments(plan, initialPhase, planStartDate);
    }

    private TimedPhase getTimedPhaseOnChange(final DefaultSubscriptionBase subscription,
                                             final Plan nextPlan,
                                             final DateTime effectiveDate,
                                             final WhichPhase which,
                                             final InternalTenantContext context) throws CatalogApiException, SubscriptionBaseApiException {
        return getTimedPhaseOnChange(subscription.getAlignStartDate(),
                                     subscription.getBundleStartDate(),
                                     subscription.getCurrentPhase(),
                                     subscription.getCurrentPlan(),
                                     nextPlan,
                                     effectiveDate,
                                     // This method is only called while doing the change, hence we want to pass the change effective date
                                     effectiveDate,
                                     subscription.getAllTransitions().get(0).getNextPhase().getPhaseType(),
                                     which,
                                     context);
    }

    private TimedPhase getTimedPhaseOnChange(final DateTime subscriptionStartDate,
                                             final DateTime bundleStartDate,
                                             final PlanPhase currentPhase,
                                             final Plan currentPlan,
                                             final Plan nextPlan,
                                             final DateTime effectiveDate,
                                             final DateTime lastOrCurrentChangeEffectiveDate,
                                             final PhaseType originalInitialPhase,
                                             final WhichPhase which,
                                             final InternalTenantContext context) throws CatalogApiException, SubscriptionBaseApiException {
        final Catalog catalog = catalogService.getFullCatalog(true, true, context);
        final PlanPhaseSpecifier fromPlanPhaseSpecifier = new PlanPhaseSpecifier(currentPlan.getName(),
                                                                                 currentPhase.getPhaseType());

        final PlanSpecifier toPlanSpecifier = new PlanSpecifier(nextPlan.getName());
        final PhaseType initialPhase;
        final DateTime planStartDate;
        final PlanAlignmentChange alignment = catalog.planChangeAlignment(fromPlanPhaseSpecifier, toPlanSpecifier, effectiveDate);
        switch (alignment) {
            case START_OF_SUBSCRIPTION:
                planStartDate = subscriptionStartDate;
                initialPhase = isPlanContainPhaseType(nextPlan, originalInitialPhase) ? originalInitialPhase : null;
                break;
            case START_OF_BUNDLE:
                planStartDate = bundleStartDate;
                initialPhase = isPlanContainPhaseType(nextPlan, originalInitialPhase) ? originalInitialPhase : null;
                break;
            case CHANGE_OF_PLAN:
                planStartDate = lastOrCurrentChangeEffectiveDate;
                initialPhase = null;
                break;
            case CHANGE_OF_PRICELIST:
                throw new SubscriptionBaseError(String.format("Not implemented yet %s", alignment));
            default:
                throw new SubscriptionBaseError(String.format("Unknown PlanAlignmentChange %s", alignment));
        }

        final List<TimedPhase> timedPhases = getPhaseAlignments(nextPlan, initialPhase, planStartDate);
        return getTimedPhase(timedPhases, effectiveDate, which);
    }


    private List<TimedPhase> getPhaseAlignments(final Plan plan, @Nullable final PhaseType initialPhase, final DateTime initialPhaseStartDate) throws SubscriptionBaseApiException {
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
                nextPhaseStart = addDuration(curPhaseStart, curPhaseDuration);
                if (nextPhaseStart == null) {
                    throw new SubscriptionBaseError(String.format("Unexpected non ending UNLIMITED phase for plan %s",
                                                             plan.getName()));
                }
                curPhaseStart = nextPhaseStart;
            }
        }

        if (initialPhase != null && curPhaseStart == null) {
            throw new SubscriptionBaseApiException(ErrorCode.SUB_CREATE_BAD_PHASE, initialPhase);
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
                throw new SubscriptionBaseError(String.format("Unexpected %s TimedPhase", which));
        }
    }

    private boolean isPlanContainPhaseType(final Plan plan, @Nullable final PhaseType phaseType) {
        return Iterables.any(ImmutableList.copyOf(plan.getAllPhases()), new Predicate<PlanPhase>() {
            @Override
            public boolean apply(final PlanPhase input) {
                return input.getPhaseType() == phaseType;
            }
        });
    }
}
