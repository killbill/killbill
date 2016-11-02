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

package org.killbill.billing.catalog.dao;

import java.util.List;

import org.joda.time.DateTime;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhasePriceOverride;

public interface CatalogOverrideDao {

    public CatalogOverridePlanDefinitionModelDao getOrCreateOverridePlanDefinition(Plan parentPlan, DateTime catalogEffectiveDate, PlanPhasePriceOverride[] resolvedOverride, InternalCallContext context);

    public List<CatalogOverridePhaseDefinitionModelDao> getOverriddenPlanPhases(final Long planDefRecordId, final InternalTenantContext context);

    public List<CatalogOverrideUsageDefinitionModelDao> getOverriddenPhaseUsages(final Long phaseDefRecordId, final InternalTenantContext context);

    public List<CatalogOverrideTierDefinitionModelDao> getOverriddenUsageTiers(final Long usageDefRecordId, final InternalTenantContext context);

    public List<CatalogOverrideBlockDefinitionModelDao> getOverriddenTierBlocks(final Long tierDefRecordId, final InternalTenantContext context);

}
