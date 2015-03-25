/*
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

package org.killbill.billing.catalog.override;

import java.util.List;

import org.joda.time.DateTime;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.DefaultPlan;
import org.killbill.billing.catalog.DefaultPlanPhasePriceOverride;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.PlanPhasePriceOverride;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.dao.CatalogOverrideDao;
import org.killbill.billing.catalog.dao.CatalogOverridePlanDefinitionModelDao;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;

public class DefaultPriceOverride implements PriceOverride {

    private final CatalogOverrideDao overrideDao;

    @Inject
    public DefaultPriceOverride(final CatalogOverrideDao overrideDao) {
        this.overrideDao = overrideDao;
    }

    @Override
    public DefaultPlan getOrCreateOverriddenPlan(final Plan parentPlan, final DateTime catalogEffectiveDate, final List<PlanPhasePriceOverride> overrides, final InternalCallContext context) throws CatalogApiException {

        final PlanPhasePriceOverride[] resolvedOverride = new PlanPhasePriceOverride[parentPlan.getAllPhases().length];
        int index  = 0;
        for (final PlanPhase curPhase : parentPlan.getAllPhases()) {
            final PlanPhasePriceOverride curOverride = Iterables.tryFind(overrides, new Predicate<PlanPhasePriceOverride>() {
                @Override
                public boolean apply(final PlanPhasePriceOverride input) {
                    if (input.getPhaseName() != null && input.getPhaseName().equals(curPhase.getName())) {
                        return true;
                    }
                    // If the phaseName was not passed, we infer by matching the phaseType. This obvously would not work in a case where
                    // a plan is defined with multiple phases of the same type.
                    final PlanPhaseSpecifier curPlanPhaseSpecifier = input.getPlanPhaseSpecifier();
                    if (curPlanPhaseSpecifier.getPhaseType().equals(curPhase.getPhaseType())) {
                        return true;
                    }
                    return false;
                }
            }).orNull();
            resolvedOverride[index++] = curOverride != null ?
                                        new DefaultPlanPhasePriceOverride(curPhase.getName(), curOverride.getCurrency(), curOverride.getFixedPrice(), curOverride.getRecurringPrice()) :
                                        null;
        }

        final CatalogOverridePlanDefinitionModelDao overriddenPlan = overrideDao.getOrCreateOverridePlanDefinition(parentPlan.getName(), catalogEffectiveDate, resolvedOverride, context);
        final String planName = new StringBuffer(parentPlan.getName()).append("-").append(overriddenPlan.getRecordId()).toString();
        final DefaultPlan result  = new DefaultPlan(planName, (DefaultPlan) parentPlan, resolvedOverride);
        return result;
    }
}
