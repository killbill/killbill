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
import com.ning.billing.entitlement.api.migration.EntitlementMigrationApiException;
import com.ning.billing.entitlement.api.migration.EntitlementMigrationApi.EntitlementSubscriptionMigrationCase;
import com.ning.billing.entitlement.events.EntitlementEvent.EventType;
import com.ning.billing.entitlement.events.user.ApiEventType;
import com.ning.billing.util.clock.DefaultClock;

public class MigrationPlanAligner {

    private final CatalogService catalogService;

    @Inject
    public MigrationPlanAligner(CatalogService catalogService) {
        this.catalogService = catalogService;
    }


    public TimedMigration [] getEventsMigration(EntitlementSubscriptionMigrationCase [] input, DateTime now)
        throws EntitlementMigrationApiException {

        try {
            TimedMigration [] events = null;
            Plan plan0 = catalogService.getFullCatalog().findPlan(input[0].getPlanPhaseSpecifer().getProductName(),
                    input[0].getPlanPhaseSpecifer().getBillingPeriod(), input[0].getPlanPhaseSpecifer().getPriceListName(), now);

            Plan plan1 = (input.length > 1) ? catalogService.getFullCatalog().findPlan(input[1].getPlanPhaseSpecifer().getProductName(),
                    input[1].getPlanPhaseSpecifer().getBillingPeriod(), input[1].getPlanPhaseSpecifer().getPriceListName(), now) :
                        null;

            DateTime migrationStartDate = now;

            if (isRegularMigratedSubscription(input)) {

                events = getEventsOnRegularMigration(plan0,
                        getPlanPhase(plan0, input[0].getPlanPhaseSpecifer().getPhaseType()),
                        input[0].getPlanPhaseSpecifer().getPriceListName(),
                        now);

            } else if (isRegularFutureCancelledMigratedSubscription(input)) {

                events = getEventsOnFuturePlanCancelMigration(plan0,
                        getPlanPhase(plan0, input[0].getPlanPhaseSpecifer().getPhaseType()),
                        input[0].getPlanPhaseSpecifer().getPriceListName(),
                        now,
                        input[0].getCancelledDate());

            } else if (isPhaseChangeMigratedSubscription(input)) {

                PhaseType curPhaseType = input[0].getPlanPhaseSpecifer().getPhaseType();
                Duration curPhaseDuration = null;
                for (PlanPhase cur : plan0.getAllPhases()) {
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
                        getPlanPhase(plan0, input[0].getPlanPhaseSpecifer().getPhaseType()),
                        input[0].getPlanPhaseSpecifer().getPriceListName(),
                        migrationStartDate,
                        input[1].getEffectiveDate());

            } else if (isPlanChangeMigratedSubscription(input)) {

                events = getEventsOnFuturePlanChangeMigration(plan0,
                        getPlanPhase(plan0, input[0].getPlanPhaseSpecifer().getPhaseType()),
                        plan1,
                        getPlanPhase(plan1, input[1].getPlanPhaseSpecifer().getPhaseType()),
                        input[0].getPlanPhaseSpecifer().getPriceListName(),
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

    private TimedMigration [] getEventsOnRegularMigration(Plan plan, PlanPhase initialPhase, String priceList, DateTime effectiveDate) {
        TimedMigration [] result = new TimedMigration[1];
        result[0] = new TimedMigration(effectiveDate, EventType.API_USER, ApiEventType.MIGRATE_ENTITLEMENT, plan, initialPhase, priceList);
        return result;
    }

    private TimedMigration [] getEventsOnFuturePhaseChangeMigration(Plan plan, PlanPhase initialPhase, String priceList, DateTime effectiveDate, DateTime effectiveDateForNextPhase)
        throws EntitlementMigrationApiException {

        TimedMigration [] result = new TimedMigration[2];
        result[0] = new TimedMigration(effectiveDate, EventType.API_USER, ApiEventType.MIGRATE_ENTITLEMENT, plan, initialPhase, priceList);
        boolean foundCurrent = false;
        PlanPhase nextPhase = null;
        for (PlanPhase cur : plan.getAllPhases()) {
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

    private TimedMigration [] getEventsOnFuturePlanChangeMigration(Plan currentPlan, PlanPhase currentPhase, Plan newPlan, PlanPhase newPhase, String priceList, DateTime effectiveDate, DateTime effectiveDateForChangePlan) {
        TimedMigration [] result = new TimedMigration[2];
        result[0] = new TimedMigration(effectiveDate, EventType.API_USER, ApiEventType.MIGRATE_ENTITLEMENT, currentPlan, currentPhase, priceList);
        result[1] = new TimedMigration(effectiveDateForChangePlan, EventType.API_USER, ApiEventType.CHANGE, newPlan, newPhase, priceList);
        return result;
    }

    private TimedMigration [] getEventsOnFuturePlanCancelMigration(Plan plan, PlanPhase initialPhase, String priceList, DateTime effectiveDate, DateTime effectiveDateForCancellation) {
        TimedMigration [] result = new TimedMigration[2];
        result[0] = new TimedMigration(effectiveDate, EventType.API_USER, ApiEventType.MIGRATE_ENTITLEMENT, plan, initialPhase, priceList);
        result[1] = new TimedMigration(effectiveDateForCancellation, EventType.API_USER, ApiEventType.CANCEL, null, null, null);
        return result;
    }


    // STEPH should be in catalog
    private PlanPhase getPlanPhase(Plan plan, PhaseType phaseType) throws EntitlementMigrationApiException {
        for (PlanPhase cur: plan.getAllPhases()) {
            if (cur.getPhaseType() == phaseType) {
                return cur;
            }
        }
        throw new EntitlementMigrationApiException(String.format("Cannot find PlanPhase from Plan %s and type %s", plan.getName(), phaseType));
    }

    private boolean isRegularMigratedSubscription(EntitlementSubscriptionMigrationCase [] input) {
        return (input.length == 1 && input[0].getCancelledDate() == null);
    }

    private boolean isRegularFutureCancelledMigratedSubscription(EntitlementSubscriptionMigrationCase [] input) {
        return (input.length == 1 && input[0].getCancelledDate() != null);
    }

    private boolean isPhaseChangeMigratedSubscription(EntitlementSubscriptionMigrationCase [] input) {
        if (input.length != 2) {
            return false;
        }
        return (isSamePlan(input[0].getPlanPhaseSpecifer(), input[1].getPlanPhaseSpecifer()) &&
                !isSamePhase(input[0].getPlanPhaseSpecifer(), input[1].getPlanPhaseSpecifer()));
    }

    private boolean isPlanChangeMigratedSubscription(EntitlementSubscriptionMigrationCase [] input) {
        if (input.length != 2) {
            return false;
        }
        return ! isSamePlan(input[0].getPlanPhaseSpecifer(), input[1].getPlanPhaseSpecifer());
    }

    private boolean isSamePlan(PlanPhaseSpecifier plan0, PlanPhaseSpecifier plan1) {
        if (plan0.getPriceListName().equals(plan1.getPriceListName()) &&
                plan0.getProductName().equals(plan1.getProductName()) &&
                plan0.getBillingPeriod() == plan1.getBillingPeriod()) {
            return true;
        }
        return false;
    }

    private boolean isSamePhase(PlanPhaseSpecifier plan0, PlanPhaseSpecifier plan1) {
        if (plan0.getPhaseType() == plan1.getPhaseType()) {
            return true;
        }
        return false;
    }
}
