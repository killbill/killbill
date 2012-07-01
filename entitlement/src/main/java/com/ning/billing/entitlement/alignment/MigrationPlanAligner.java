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


import org.joda.time.DateTime;

import com.google.inject.Inject;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.catalog.api.Duration;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.entitlement.api.migration.EntitlementMigrationApi.EntitlementSubscriptionMigrationCase;
import com.ning.billing.entitlement.api.migration.EntitlementMigrationApiException;
import com.ning.billing.entitlement.events.EntitlementEvent.EventType;
import com.ning.billing.entitlement.events.user.ApiEventType;
import com.ning.billing.util.clock.DefaultClock;

public class MigrationPlanAligner {

    private final CatalogService catalogService;

    @Inject
    public MigrationPlanAligner(final CatalogService catalogService) {
        this.catalogService = catalogService;
    }


    public TimedMigration[] getEventsMigration(final EntitlementSubscriptionMigrationCase[] input, final DateTime now)
            throws EntitlementMigrationApiException {

        try {
            TimedMigration[] events = null;
            final Plan plan0 = catalogService.getFullCatalog().findPlan(input[0].getPlanPhaseSpecifier().getProductName(),
                                                                  input[0].getPlanPhaseSpecifier().getBillingPeriod(), input[0].getPlanPhaseSpecifier().getPriceListName(), now);

            final Plan plan1 = (input.length > 1) ? catalogService.getFullCatalog().findPlan(input[1].getPlanPhaseSpecifier().getProductName(),
                                                                                       input[1].getPlanPhaseSpecifier().getBillingPeriod(), input[1].getPlanPhaseSpecifier().getPriceListName(), now) :
                    null;

            DateTime migrationStartDate = now;

            if (isRegularMigratedSubscription(input)) {

                events = getEventsOnRegularMigration(plan0,
                                                     getPlanPhase(plan0, input[0].getPlanPhaseSpecifier().getPhaseType()),
                                                     input[0].getPlanPhaseSpecifier().getPriceListName(),
                                                     now);

            } else if (isRegularFutureCancelledMigratedSubscription(input)) {

                events = getEventsOnFuturePlanCancelMigration(plan0,
                                                              getPlanPhase(plan0, input[0].getPlanPhaseSpecifier().getPhaseType()),
                                                              input[0].getPlanPhaseSpecifier().getPriceListName(),
                                                              now,
                                                              input[0].getCancelledDate());

            } else if (isPhaseChangeMigratedSubscription(input)) {

                final PhaseType curPhaseType = input[0].getPlanPhaseSpecifier().getPhaseType();
                Duration curPhaseDuration = null;
                for (final PlanPhase cur : plan0.getAllPhases()) {
                    if (cur.getPhaseType() == curPhaseType) {
                        curPhaseDuration = cur.getDuration();
                        break;
                    }
                }
                if (curPhaseDuration == null) {
                    throw new EntitlementMigrationApiException(String.format("Failed to compute current phase duration for plan %s and phase %s",
                                                                             plan0.getName(), curPhaseType));
                }

                migrationStartDate = DefaultClock.removeDuration(input[1].getEffectiveDate(), curPhaseDuration);
                events = getEventsOnFuturePhaseChangeMigration(plan0,
                                                               getPlanPhase(plan0, input[0].getPlanPhaseSpecifier().getPhaseType()),
                                                               input[0].getPlanPhaseSpecifier().getPriceListName(),
                                                               migrationStartDate,
                                                               input[1].getEffectiveDate());

            } else if (isPlanChangeMigratedSubscription(input)) {

                events = getEventsOnFuturePlanChangeMigration(plan0,
                                                              getPlanPhase(plan0, input[0].getPlanPhaseSpecifier().getPhaseType()),
                                                              plan1,
                                                              getPlanPhase(plan1, input[1].getPlanPhaseSpecifier().getPhaseType()),
                                                              input[0].getPlanPhaseSpecifier().getPriceListName(),
                                                              now,
                                                              input[1].getEffectiveDate());

            } else {
                throw new EntitlementMigrationApiException("Unknown migration type");
            }

            return events;
        } catch (CatalogApiException e) {
            throw new EntitlementMigrationApiException(e);
        }
    }

    private TimedMigration[] getEventsOnRegularMigration(final Plan plan, final PlanPhase initialPhase, final String priceList, final DateTime effectiveDate) {
        final TimedMigration[] result = new TimedMigration[1];
        result[0] = new TimedMigration(effectiveDate, EventType.API_USER, ApiEventType.MIGRATE_ENTITLEMENT, plan, initialPhase, priceList);
        return result;
    }

    private TimedMigration[] getEventsOnFuturePhaseChangeMigration(final Plan plan, final PlanPhase initialPhase, final String priceList, final DateTime effectiveDate, final DateTime effectiveDateForNextPhase)
            throws EntitlementMigrationApiException {

        final TimedMigration[] result = new TimedMigration[2];
        result[0] = new TimedMigration(effectiveDate, EventType.API_USER, ApiEventType.MIGRATE_ENTITLEMENT, plan, initialPhase, priceList);
        boolean foundCurrent = false;
        PlanPhase nextPhase = null;
        for (final PlanPhase cur : plan.getAllPhases()) {
            if (cur == initialPhase) {
                foundCurrent = true;
                continue;
            }
            if (foundCurrent) {
                nextPhase = cur;
            }
        }
        if (nextPhase == null) {
            throw new EntitlementMigrationApiException(String.format("Cannot find next phase for Plan %s and current Phase %s",
                                                                     plan.getName(), initialPhase.getName()));
        }
        result[1] = new TimedMigration(effectiveDateForNextPhase, EventType.PHASE, null, plan, nextPhase, priceList);
        return result;
    }

    private TimedMigration[] getEventsOnFuturePlanChangeMigration(final Plan currentPlan, final PlanPhase currentPhase, final Plan newPlan, final PlanPhase newPhase, final String priceList, final DateTime effectiveDate, final DateTime effectiveDateForChangePlan) {
        final TimedMigration[] result = new TimedMigration[2];
        result[0] = new TimedMigration(effectiveDate, EventType.API_USER, ApiEventType.MIGRATE_ENTITLEMENT, currentPlan, currentPhase, priceList);
        result[1] = new TimedMigration(effectiveDateForChangePlan, EventType.API_USER, ApiEventType.CHANGE, newPlan, newPhase, priceList);
        return result;
    }

    private TimedMigration[] getEventsOnFuturePlanCancelMigration(final Plan plan, final PlanPhase initialPhase, final String priceList, final DateTime effectiveDate, final DateTime effectiveDateForCancellation) {
        final TimedMigration[] result = new TimedMigration[2];
        result[0] = new TimedMigration(effectiveDate, EventType.API_USER, ApiEventType.MIGRATE_ENTITLEMENT, plan, initialPhase, priceList);
        result[1] = new TimedMigration(effectiveDateForCancellation, EventType.API_USER, ApiEventType.CANCEL, null, null, null);
        return result;
    }


    // STEPH should be in catalog
    private PlanPhase getPlanPhase(final Plan plan, final PhaseType phaseType) throws EntitlementMigrationApiException {
        for (final PlanPhase cur : plan.getAllPhases()) {
            if (cur.getPhaseType() == phaseType) {
                return cur;
            }
        }
        throw new EntitlementMigrationApiException(String.format("Cannot find PlanPhase from Plan %s and type %s", plan.getName(), phaseType));
    }

    private boolean isRegularMigratedSubscription(final EntitlementSubscriptionMigrationCase[] input) {
        return (input.length == 1 && input[0].getCancelledDate() == null);
    }

    private boolean isRegularFutureCancelledMigratedSubscription(final EntitlementSubscriptionMigrationCase[] input) {
        return (input.length == 1 && input[0].getCancelledDate() != null);
    }

    private boolean isPhaseChangeMigratedSubscription(final EntitlementSubscriptionMigrationCase[] input) {
        if (input.length != 2) {
            return false;
        }
        return (isSamePlan(input[0].getPlanPhaseSpecifier(), input[1].getPlanPhaseSpecifier()) &&
                !isSamePhase(input[0].getPlanPhaseSpecifier(), input[1].getPlanPhaseSpecifier()));
    }

    private boolean isPlanChangeMigratedSubscription(final EntitlementSubscriptionMigrationCase[] input) {
        if (input.length != 2) {
            return false;
        }
        return !isSamePlan(input[0].getPlanPhaseSpecifier(), input[1].getPlanPhaseSpecifier());
    }

    private boolean isSamePlan(final PlanPhaseSpecifier plan0, final PlanPhaseSpecifier plan1) {
        if (plan0.getPriceListName().equals(plan1.getPriceListName()) &&
                plan0.getProductName().equals(plan1.getProductName()) &&
                plan0.getBillingPeriod() == plan1.getBillingPeriod()) {
            return true;
        }
        return false;
    }

    private boolean isSamePhase(final PlanPhaseSpecifier plan0, final PlanPhaseSpecifier plan1) {
        if (plan0.getPhaseType() == plan1.getPhaseType()) {
            return true;
        }
        return false;
    }
}
