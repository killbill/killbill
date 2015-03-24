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

import org.joda.time.DateTime;

import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.CatalogService;
import org.killbill.billing.catalog.api.Duration;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.subscription.api.migration.SubscriptionBaseMigrationApi.SubscriptionMigrationCase;
import org.killbill.billing.subscription.api.migration.SubscriptionBaseMigrationApiException;
import org.killbill.billing.subscription.events.SubscriptionBaseEvent.EventType;
import org.killbill.billing.subscription.events.user.ApiEventType;

import com.google.inject.Inject;

public class MigrationPlanAligner extends BaseAligner {

    private final CatalogService catalogService;

    @Inject
    public MigrationPlanAligner(final CatalogService catalogService) {
        this.catalogService = catalogService;
    }


    public TimedMigration[] getEventsMigration(final SubscriptionMigrationCase[] input, final DateTime now, final InternalTenantContext context)
            throws SubscriptionBaseMigrationApiException {

        try {
            TimedMigration[] events;
            final Plan plan0 = catalogService.getFullCatalog(context).findPlan(input[0].getPlanPhaseSpecifier().getProductName(),
                                                                        input[0].getPlanPhaseSpecifier().getBillingPeriod(), input[0].getPlanPhaseSpecifier().getPriceListName(), null, now);

            final Plan plan1 = (input.length > 1) ? catalogService.getFullCatalog(context).findPlan(input[1].getPlanPhaseSpecifier().getProductName(),
                                                                                             input[1].getPlanPhaseSpecifier().getBillingPeriod(), input[1].getPlanPhaseSpecifier().getPriceListName(), null, now) :
                               null;

            DateTime migrationStartDate = input[0].getEffectiveDate();

            if (isRegularMigratedSubscription(input)) {

                events = getEventsOnRegularMigration(plan0,
                                                     getPlanPhase(plan0, input[0].getPlanPhaseSpecifier().getPhaseType()),
                                                     input[0].getPlanPhaseSpecifier().getPriceListName(),
                                                     migrationStartDate);

            } else if (isRegularFutureCancelledMigratedSubscription(input)) {

                events = getEventsOnFuturePlanCancelMigration(plan0,
                                                              getPlanPhase(plan0, input[0].getPlanPhaseSpecifier().getPhaseType()),
                                                              input[0].getPlanPhaseSpecifier().getPriceListName(),
                                                              migrationStartDate,
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
                    throw new SubscriptionBaseMigrationApiException(String.format("Failed to compute current phase duration for plan %s and phase %s",
                                                                             plan0.getName(), curPhaseType));
                }

                migrationStartDate = removeDuration(input[1].getEffectiveDate(), curPhaseDuration);
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
                                                              migrationStartDate,
                                                              input[1].getEffectiveDate());

            } else {
                throw new SubscriptionBaseMigrationApiException("Unknown migration type");
            }

            return events;
        } catch (CatalogApiException e) {
            throw new SubscriptionBaseMigrationApiException(e);
        }
    }

    private TimedMigration[] getEventsOnRegularMigration(final Plan plan, final PlanPhase initialPhase, final String priceList, final DateTime effectiveDate) {
        final TimedMigration[] result = new TimedMigration[1];
        result[0] = new TimedMigration(effectiveDate, EventType.API_USER, ApiEventType.MIGRATE_ENTITLEMENT, plan, initialPhase, priceList);
        return result;
    }

    private TimedMigration[] getEventsOnFuturePhaseChangeMigration(final Plan plan, final PlanPhase initialPhase, final String priceList, final DateTime effectiveDate, final DateTime effectiveDateForNextPhase)
            throws SubscriptionBaseMigrationApiException {

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
            throw new SubscriptionBaseMigrationApiException(String.format("Cannot find next phase for Plan %s and current Phase %s",
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
    private PlanPhase getPlanPhase(final Plan plan, final PhaseType phaseType) throws SubscriptionBaseMigrationApiException {
        for (final PlanPhase cur : plan.getAllPhases()) {
            if (cur.getPhaseType() == phaseType) {
                return cur;
            }
        }
        throw new SubscriptionBaseMigrationApiException(String.format("Cannot find PlanPhase from Plan %s and type %s", plan.getName(), phaseType));
    }

    private boolean isRegularMigratedSubscription(final SubscriptionMigrationCase[] input) {
        return (input.length == 1 && input[0].getCancelledDate() == null);
    }

    private boolean isRegularFutureCancelledMigratedSubscription(final SubscriptionMigrationCase[] input) {
        return (input.length == 1 && input[0].getCancelledDate() != null);
    }

    private boolean isPhaseChangeMigratedSubscription(final SubscriptionMigrationCase[] input) {
        if (input.length != 2) {
            return false;
        }
        return (isSamePlan(input[0].getPlanPhaseSpecifier(), input[1].getPlanPhaseSpecifier()) &&
                !isSamePhase(input[0].getPlanPhaseSpecifier(), input[1].getPlanPhaseSpecifier()));
    }

    private boolean isPlanChangeMigratedSubscription(final SubscriptionMigrationCase[] input) {
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
