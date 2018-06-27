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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

import javax.inject.Inject;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.ObjectType;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.DefaultPlan;
import org.killbill.billing.catalog.DefaultPlanPhasePriceOverride;
import org.killbill.billing.catalog.DefaultTierPriceOverride;
import org.killbill.billing.catalog.DefaultTieredBlockPriceOverride;
import org.killbill.billing.catalog.DefaultUsagePriceOverride;
import org.killbill.billing.catalog.StandaloneCatalog;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.PlanPhasePriceOverride;
import org.killbill.billing.catalog.api.StaticCatalog;
import org.killbill.billing.catalog.api.Tier;
import org.killbill.billing.catalog.api.TierPriceOverride;
import org.killbill.billing.catalog.api.TieredBlock;
import org.killbill.billing.catalog.api.TieredBlockPriceOverride;
import org.killbill.billing.catalog.api.Usage;
import org.killbill.billing.catalog.api.UsagePriceOverride;
import org.killbill.billing.catalog.dao.CatalogOverrideBlockDefinitionModelDao;
import org.killbill.billing.catalog.dao.CatalogOverrideDao;
import org.killbill.billing.catalog.dao.CatalogOverridePhaseDefinitionModelDao;
import org.killbill.billing.catalog.dao.CatalogOverrideTierDefinitionModelDao;
import org.killbill.billing.catalog.dao.CatalogOverrideUsageDefinitionModelDao;
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
                return loadOverriddenPlan(planName, (StandaloneCatalog) catalog, context);
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

    private DefaultPlan loadOverriddenPlan(final String planName, final StandaloneCatalog catalog, final InternalTenantContext context) throws CatalogApiException {
        final Matcher m = DefaultPriceOverride.CUSTOM_PLAN_NAME_PATTERN.matcher(planName);
        if (!m.matches()) {
            throw new CatalogApiException(ErrorCode.CAT_NO_SUCH_PLAN, planName);
        }
        final String parentPlanName = m.group(1);
        final Long planDefRecordId = Long.parseLong(m.group(2));

        final List<CatalogOverridePhaseDefinitionModelDao> phaseDefs = overrideDao.getOverriddenPlanPhases(planDefRecordId, context);
        final DefaultPlan defaultPlan = catalog.findCurrentPlan(parentPlanName);
        final PlanPhasePriceOverride[] overrides = createOverrides(defaultPlan, phaseDefs, context);
        final DefaultPlan result = new DefaultPlan(catalog, planName, defaultPlan, overrides);
        result.initialize(catalog, catalog.getCatalogURI());
        return result;
    }

    private PlanPhasePriceOverride[] createOverrides(final Plan defaultPlan, final List<CatalogOverridePhaseDefinitionModelDao> phaseDefs, final InternalTenantContext context) {

        final PlanPhasePriceOverride[] result = new PlanPhasePriceOverride[defaultPlan.getAllPhases().length];

        for (int i = 0; i < defaultPlan.getAllPhases().length; i++) {
            final PlanPhase curPhase = defaultPlan.getAllPhases()[i];
            final CatalogOverridePhaseDefinitionModelDao overriddenPhase = Iterables.tryFind(phaseDefs, new Predicate<CatalogOverridePhaseDefinitionModelDao>() {
                @Override
                public boolean apply(final CatalogOverridePhaseDefinitionModelDao input) {
                    return input.getParentPhaseName().equals(curPhase.getName());
                }
            }).orNull();

            if(overriddenPhase != null) {
              List<UsagePriceOverride> usagePriceOverrides =  getUsagePriceOverrides(curPhase, overriddenPhase, context);
              result[i] = new DefaultPlanPhasePriceOverride(curPhase.getName(), Currency.valueOf(overriddenPhase.getCurrency()), overriddenPhase.getFixedPrice(), overriddenPhase.getRecurringPrice(), usagePriceOverrides);
            }
            else {
                result[i] = null;
            }
        }
        return result;
    }

    List<UsagePriceOverride> getUsagePriceOverrides(PlanPhase curPhase, CatalogOverridePhaseDefinitionModelDao overriddenPhase, final InternalTenantContext context) {

        final List<UsagePriceOverride> usagePriceOverrides = new ArrayList<UsagePriceOverride>();
        final List<CatalogOverrideUsageDefinitionModelDao> usageDefs = overrideDao.getOverriddenPhaseUsages(overriddenPhase.getRecordId(), context);

        for(int i = 0; i < curPhase.getUsages().length; i++){
            final Usage curUsage = curPhase.getUsages()[i];
            final CatalogOverrideUsageDefinitionModelDao overriddenUsage = Iterables.tryFind(usageDefs, new Predicate<CatalogOverrideUsageDefinitionModelDao>() {
                @Override
                public boolean apply(final CatalogOverrideUsageDefinitionModelDao input) {
                    return input.getParentUsageName().equals(curUsage.getName());
                }
            }).orNull();

            if(overriddenUsage != null) {
                List<TierPriceOverride> tierPriceOverrides = getTierPriceOverrides(curUsage, overriddenUsage, context);
                usagePriceOverrides.add(new DefaultUsagePriceOverride(overriddenUsage.getParentUsageName(), curUsage.getUsageType(),tierPriceOverrides));
            }
        }
        return usagePriceOverrides;
    }

    List<TierPriceOverride> getTierPriceOverrides(Usage curUsage, CatalogOverrideUsageDefinitionModelDao overriddenUsage, final InternalTenantContext context) {

        final List<TierPriceOverride> tierPriceOverrides = new ArrayList<TierPriceOverride>();

        final List<CatalogOverrideTierDefinitionModelDao> tierDefs = overrideDao.getOverriddenUsageTiers(overriddenUsage.getRecordId(), context);
        for(int i = 0; i < curUsage.getTiers().length; i++){
            final Tier curTier = curUsage.getTiers()[i];
            final TieredBlock[] curTieredBlocks = curTier.getTieredBlocks();

            final CatalogOverrideTierDefinitionModelDao overriddenTier = Iterables.tryFind(tierDefs, new Predicate<CatalogOverrideTierDefinitionModelDao>() {
                @Override
                public boolean apply(final CatalogOverrideTierDefinitionModelDao input) {
                    final List<CatalogOverrideBlockDefinitionModelDao> blockDefs = overrideDao.getOverriddenTierBlocks(input.getRecordId(), context);
                     for(CatalogOverrideBlockDefinitionModelDao blockDef : blockDefs) {
                         String unitName = blockDef.getParentUnitName();
                         Double max = blockDef.getMax();
                         Double size = blockDef.getSize();

                         for(TieredBlock curTieredBlock : curTieredBlocks) {
                             if (unitName.equals(curTieredBlock.getUnit().getName()) &&
                                     Double.compare(size, curTieredBlock.getSize()) == 0 &&
                                     Double.compare(max, curTieredBlock.getMax()) == 0) {
                                 return true;
                             }
                         }
                     }
                    return false;
                }

            }).orNull();

            if(overriddenTier != null) {
                List<TieredBlockPriceOverride> tieredBlockPriceOverrides = getTieredBlockPriceOverrides(curTier, overriddenTier, context);
                tierPriceOverrides.add(new DefaultTierPriceOverride(tieredBlockPriceOverrides));
            }
        }
        return tierPriceOverrides;
    }

    List<TieredBlockPriceOverride> getTieredBlockPriceOverrides(Tier curTier, CatalogOverrideTierDefinitionModelDao overriddenTier, final InternalTenantContext context) {

        final List<TieredBlockPriceOverride> blockPriceOverrides = new ArrayList<TieredBlockPriceOverride>();
        final List<CatalogOverrideBlockDefinitionModelDao> blockDefs = overrideDao.getOverriddenTierBlocks(overriddenTier.getRecordId(), context);

        for(int i = 0; i < curTier.getTieredBlocks().length; i++){
            final TieredBlock curTieredBlock = curTier.getTieredBlocks()[i];
            final CatalogOverrideBlockDefinitionModelDao overriddenTierBlock = Iterables.tryFind(blockDefs, new Predicate<CatalogOverrideBlockDefinitionModelDao>() {
                @Override
                public boolean apply(final CatalogOverrideBlockDefinitionModelDao input) {
                    return (input.getParentUnitName().equals(curTieredBlock.getUnit().getName()) &&
                            Double.compare(input.getSize(), curTieredBlock.getSize()) == 0 &&
                            Double.compare(input.getMax(), curTieredBlock.getMax()) == 0);

                }
            }).orNull();

            if(overriddenTierBlock != null) {
                blockPriceOverrides.add(new DefaultTieredBlockPriceOverride(overriddenTierBlock.getParentUnitName(), overriddenTierBlock.getSize(), overriddenTierBlock.getPrice(), Currency.valueOf(overriddenTierBlock.getCurrency()), overriddenTierBlock.getMax()));
            }
        }
        return blockPriceOverrides;
    }
}
