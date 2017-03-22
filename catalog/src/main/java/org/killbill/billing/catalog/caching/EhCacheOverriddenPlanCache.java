/*
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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

package org.killbill.billing.catalog.caching;

import java.util.List;
import java.util.regex.Matcher;

import javax.inject.Inject;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.ObjectType;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.DefaultPlan;
import org.killbill.billing.catalog.DefaultPlanPhasePriceOverride;
import org.killbill.billing.catalog.StandaloneCatalog;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.PlanPhasePriceOverride;
import org.killbill.billing.catalog.api.StaticCatalog;
import org.killbill.billing.catalog.dao.CatalogOverrideDao;
import org.killbill.billing.catalog.dao.CatalogOverridePhaseDefinitionModelDao;
import org.killbill.billing.catalog.override.DefaultPriceOverride;
import org.killbill.billing.util.cache.Cachable.CacheType;
import org.killbill.billing.util.cache.CacheController;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.cache.CacheLoaderArgument;
import org.killbill.billing.util.cache.OverriddenPlanCacheLoader.LoaderCallback;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

public class EhCacheOverriddenPlanCache implements OverriddenPlanCache {

    private final CacheController<String, Plan> cacheController;
    private final LoaderCallback loaderCallback;
    private final CatalogOverrideDao overrideDao;

    @Inject
    public EhCacheOverriddenPlanCache(final CatalogOverrideDao overrideDao, final CacheControllerDispatcher cacheControllerDispatcher) {
        this.overrideDao = overrideDao;
        this.cacheController = cacheControllerDispatcher.getCacheController(CacheType.OVERRIDDEN_PLAN);
        this.loaderCallback = new LoaderCallback() {
            @Override
            public Plan loadPlan(final String planName, final StaticCatalog catalog, final InternalTenantContext context) throws CatalogApiException {
                return loadOverriddenPlan(planName, catalog, context);
            }
        };
    }

    @Override
    public DefaultPlan getOverriddenPlan(final String planName, final StaticCatalog catalog, final InternalTenantContext context) {

        final ObjectType irrelevant = null;
        final Object[] args = new Object[2];
        args[0] = loaderCallback;
        args[1] = catalog;

        final CacheLoaderArgument argument = new CacheLoaderArgument(irrelevant, args, context);
        return (DefaultPlan) cacheController.get(planName, argument);
    }

    @Override
    public void addDryRunPlan(final String planName, final Plan plan) {
        cacheController.putIfAbsent(planName, plan);
    }

    private DefaultPlan loadOverriddenPlan(final String planName, final StaticCatalog catalog, final InternalTenantContext context) throws CatalogApiException {

        final Matcher m = DefaultPriceOverride.CUSTOM_PLAN_NAME_PATTERN.matcher(planName);
        if (!m.matches()) {
            throw new CatalogApiException(ErrorCode.CAT_NO_SUCH_PLAN, planName);
        }
        final String parentPlanName = m.group(1);
        final Long planDefRecordId = Long.parseLong(m.group(2));

        final List<CatalogOverridePhaseDefinitionModelDao> phaseDefs = overrideDao.getOverriddenPlanPhases(planDefRecordId, context);
        final DefaultPlan defaultPlan = (DefaultPlan) catalog.findCurrentPlan(parentPlanName);

        final PlanPhasePriceOverride[] overrides = createOverrides(defaultPlan, phaseDefs);
        final DefaultPlan result =  new DefaultPlan(planName, defaultPlan, overrides);
        result.initialize((StandaloneCatalog) catalog, ((StandaloneCatalog) catalog).getCatalogURI());
        return result;
    }

    private PlanPhasePriceOverride[] createOverrides(final Plan defaultPlan, final List<CatalogOverridePhaseDefinitionModelDao> phaseDefs) {

        final PlanPhasePriceOverride[] result = new PlanPhasePriceOverride[defaultPlan.getAllPhases().length];

        for (int i = 0; i < defaultPlan.getAllPhases().length; i++) {

            final PlanPhase curPhase = defaultPlan.getAllPhases()[i];
            final CatalogOverridePhaseDefinitionModelDao overriddenPhase = Iterables.tryFind(phaseDefs, new Predicate<CatalogOverridePhaseDefinitionModelDao>() {
                @Override
                public boolean apply(final CatalogOverridePhaseDefinitionModelDao input) {
                    return input.getParentPhaseName().equals(curPhase.getName());
                }
            }).orNull();
            result[i] = (overriddenPhase != null) ?
                        new DefaultPlanPhasePriceOverride(curPhase.getName(), Currency.valueOf(overriddenPhase.getCurrency()), overriddenPhase.getFixedPrice(), overriddenPhase.getRecurringPrice()) :
                        null;
        }
        return result;
    }
}
