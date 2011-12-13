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
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.entitlement.api.migration.EntitlementMigrationApiException;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.events.EntitlementEvent.EventType;
import com.ning.billing.entitlement.events.user.ApiEventType;

public class MigrationPlanAligner {

    private final CatalogService catalogService;

    @Inject
    public MigrationPlanAligner(CatalogService catalogService) {
        this.catalogService = catalogService;
    }


    public TimedMigration [] getEventsOnRegularMigration(SubscriptionData subscription,
            Plan plan, PlanPhase initialPhase, String priceList, DateTime effectiveDate) {
        TimedMigration [] result = new TimedMigration[1];
        result[0] = new TimedMigration(effectiveDate, EventType.API_USER, ApiEventType.MIGRATE_ENTITLEMENT, plan, initialPhase, priceList);
        return result;
    }

    public TimedMigration [] getEventsOnFuturePhaseChangeMigration(SubscriptionData subscription,
            Plan plan, PlanPhase initialPhase, String priceList, DateTime effectiveDate, DateTime effectiveDateForNextPhase)
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

    public TimedMigration [] getEventsOnFuturePlanChangeMigration(SubscriptionData subscription,
            Plan currentPlan, PlanPhase currentPhase, Plan newPlan, String priceList, DateTime effectiveDate, DateTime effectiveDateForChangePlan) {
        TimedMigration [] result = new TimedMigration[2];
        result[0] = new TimedMigration(effectiveDate, EventType.API_USER, ApiEventType.MIGRATE_ENTITLEMENT, currentPlan, currentPhase, priceList);
        PlanPhase newPlanPhase = newPlan.getAllPhases()[0];
        result[1] = new TimedMigration(effectiveDate, EventType.API_USER, ApiEventType.CHANGE, newPlan, newPlanPhase, priceList);
        return result;
    }

    public TimedMigration [] getEventsOnFuturePlanCancelMigration(SubscriptionData subscription,
            Plan plan, PlanPhase initialPhase, String priceList, DateTime effectiveDate, DateTime effectiveDateForCancellation) {
        TimedMigration [] result = new TimedMigration[2];
        result[0] = new TimedMigration(effectiveDate, EventType.API_USER, ApiEventType.MIGRATE_ENTITLEMENT, plan, initialPhase, priceList);
        result[1] = new TimedMigration(effectiveDate, EventType.API_USER, ApiEventType.CANCEL, null, null, null);
        return result;
    }
}
