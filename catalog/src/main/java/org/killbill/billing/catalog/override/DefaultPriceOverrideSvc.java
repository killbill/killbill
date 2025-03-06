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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.joda.time.DateTime;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.DefaultPlan;
import org.killbill.billing.catalog.DefaultPlanPhase;
import org.killbill.billing.catalog.DefaultPlanPhasePriceOverride;
import org.killbill.billing.catalog.DefaultTierPriceOverride;
import org.killbill.billing.catalog.DefaultTieredBlockPriceOverride;
import org.killbill.billing.catalog.DefaultUsagePriceOverride;
import org.killbill.billing.catalog.StandaloneCatalog;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.PlanPhasePriceOverride;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.Tier;
import org.killbill.billing.catalog.api.TierPriceOverride;
import org.killbill.billing.catalog.api.TieredBlock;
import org.killbill.billing.catalog.api.TieredBlockPriceOverride;
import org.killbill.billing.catalog.api.Usage;
import org.killbill.billing.catalog.api.UsagePriceOverride;
import org.killbill.billing.catalog.caching.OverriddenPlanCache;
import org.killbill.billing.catalog.caching.PriceOverridePattern;
import org.killbill.billing.catalog.dao.CatalogOverrideDao;
import org.killbill.billing.catalog.dao.CatalogOverridePlanDefinitionModelDao;

public class DefaultPriceOverrideSvc implements PriceOverrideSvc {

    private static final AtomicLong DRY_RUN_PLAN_IDX = new AtomicLong(0);

    private final CatalogOverrideDao overrideDao;
    private final OverriddenPlanCache overriddenPlanCache;

    private final PriceOverridePattern priceOverridePattern;

    @Inject
    public DefaultPriceOverrideSvc(final CatalogOverrideDao overrideDao, final OverriddenPlanCache overriddenPlanCache, final PriceOverridePattern priceOverridePattern) {
        this.overrideDao = overrideDao;
        this.overriddenPlanCache = overriddenPlanCache;
        this.priceOverridePattern = priceOverridePattern;
    }

    @Override
    public boolean isOverriddenPlan(final String planName) {
        return priceOverridePattern.isOverriddenPlan(planName);
    }

    @Override
    public DefaultPlan getOrCreateOverriddenPlan(final StandaloneCatalog standaloneCatalog, final Plan parentPlan, final DateTime catalogEffectiveDate, final List<PlanPhasePriceOverride> overrides, @Nullable final InternalCallContext context) throws CatalogApiException {
        final PlanPhasePriceOverride[] resolvedOverride = new PlanPhasePriceOverride[parentPlan.getAllPhases().length];
        int index = 0;
        for (final PlanPhase curPhase : parentPlan.getAllPhases()) {
            final PlanPhasePriceOverride curOverride = overrides.stream()
                    .filter(input -> {
                        if (input.getPhaseName() != null) {
                            return input.getPhaseName().equals(curPhase.getName());
                        }
                        // If the phaseName was not passed, we infer by matching the phaseType. This obviously would not work in a case where
                        // a plan is defined with multiple phases of the same type.
                        final PlanPhaseSpecifier curPlanPhaseSpecifier = input.getPlanPhaseSpecifier();
                        return curPlanPhaseSpecifier.getPhaseType().equals(curPhase.getPhaseType());
                    })
                    .findFirst().orElse(null);

            if (curOverride != null) {
                final List<UsagePriceOverride> resolvedUsageOverrides = getResolvedUsageOverrides(curPhase.getUsages(), curOverride.getUsagePriceOverrides());
                resolvedOverride[index++] = new DefaultPlanPhasePriceOverride(curPhase.getName(),
                                                                              curOverride.getCurrency(),
                                                                              curOverride.getFixedPrice(),
                                                                              curOverride.getRecurringPrice(),
                                                                              resolvedUsageOverrides);
            } else {
                resolvedOverride[index++] = null;
            }
        }

        for (int i = 0; i < resolvedOverride.length; i++) {
            final PlanPhasePriceOverride curOverride = resolvedOverride[i];
            if (curOverride != null) {
                final DefaultPlanPhase curPhase = (DefaultPlanPhase) parentPlan.getAllPhases()[i];

                if (curPhase.getFixed() == null && curOverride.getFixedPrice() != null) {
                    final String error = String.format("There is no existing fixed price for the phase %s", curPhase.getName());
                    throw new CatalogApiException(ErrorCode.CAT_INVALID_INVALID_PRICE_OVERRIDE, parentPlan.getName(), error);
                }

                if (curPhase.getRecurring() == null && curOverride.getRecurringPrice() != null) {
                    final String error = String.format("There is no existing recurring price for the phase %s", curPhase.getName());
                    throw new CatalogApiException(ErrorCode.CAT_INVALID_INVALID_PRICE_OVERRIDE, parentPlan.getName(), error);
                }
            }
        }

        final String planName;
        if (context != null) {
            final CatalogOverridePlanDefinitionModelDao overriddenPlan = overrideDao.getOrCreateOverridePlanDefinition(parentPlan, catalogEffectiveDate, resolvedOverride, context);
            planName = new StringBuffer(parentPlan.getName()).append("-").append(overriddenPlan.getRecordId()).toString();
        } else {
            planName = new StringBuffer(parentPlan.getName()).append("-dryrun-").append(DRY_RUN_PLAN_IDX.incrementAndGet()).toString();
        }

        final DefaultPlan result = new DefaultPlan(planName, (DefaultPlan) parentPlan, resolvedOverride);
        result.initialize(standaloneCatalog);
        if (context == null) {
            overriddenPlanCache.addDryRunPlan(planName, result);
        }
        return result;
    }

    public List<UsagePriceOverride> getResolvedUsageOverrides(final Usage[] usages, final List<UsagePriceOverride> usagePriceOverrides) throws CatalogApiException {
        List<UsagePriceOverride> resolvedUsageOverrides = new ArrayList<>();

        for (final Usage curUsage : usages) {
            final UsagePriceOverride curOverride = usagePriceOverrides.stream()
                    .filter(input -> input.getName() != null && input.getName().equals(curUsage.getName()))
                    .findFirst().orElse(null);
            if (curOverride != null) {
                List<TierPriceOverride> tierPriceOverrides = getResolvedTierOverrides(curUsage.getTiers(), curOverride.getTierPriceOverrides());
                resolvedUsageOverrides.add(new DefaultUsagePriceOverride(curUsage.getName(), curUsage.getUsageType(), tierPriceOverrides));
            } else {
                resolvedUsageOverrides.add(null);
            }
        }

        return resolvedUsageOverrides;
    }

    public List<TierPriceOverride> getResolvedTierOverrides(Tier[] tiers, List<TierPriceOverride> tierPriceOverrides) throws CatalogApiException {
        List<TierPriceOverride> resolvedTierOverrides = new ArrayList<>();

        for (final Tier curTier : tiers) {
            final TierPriceOverride curOverride = tierPriceOverrides.stream()
                    .filter(input -> {
                        if (input.getTieredBlockPriceOverrides() != null) {
                            for (TieredBlockPriceOverride blockPriceOverride : input.getTieredBlockPriceOverrides()) {
                                String unitName = blockPriceOverride.getUnitName();

                                for (int i = 0; i < curTier.getTieredBlocks().length; i++) {
                                    final TieredBlock curTieredBlock = curTier.getTieredBlocks()[i];
                                    if (unitName.equals(curTieredBlock.getUnit().getName()) &&
                                        blockPriceOverride.getSize().compareTo(curTieredBlock.getSize()) == 0 &&
                                        blockPriceOverride.getMax().compareTo(curTieredBlock.getMax()) == 0) {
                                        return true;
                                    }
                                }
                            }
                        }
                        return false;
                    }).findFirst().orElse(null);

            if (curOverride != null) {
                final List<TieredBlockPriceOverride> tieredBlockPriceOverrides = getResolvedTieredBlockPriceOverrides(curTier.getTieredBlocks(), curOverride.getTieredBlockPriceOverrides());
                resolvedTierOverrides.add(new DefaultTierPriceOverride(tieredBlockPriceOverrides));
            } else {
                resolvedTierOverrides.add(null);
            }
        }

        return resolvedTierOverrides;
    }

    public List<TieredBlockPriceOverride> getResolvedTieredBlockPriceOverrides(TieredBlock[] tieredBlocks, List<TieredBlockPriceOverride> tieredBlockPriceOverrides) throws CatalogApiException {
        List<TieredBlockPriceOverride> resolvedTieredBlockPriceOverrides = new ArrayList<>();

        for (final TieredBlock curTieredBlock : tieredBlocks) {
            final TieredBlockPriceOverride curOverride = tieredBlockPriceOverrides.stream()
                    .filter(input -> input.getUnitName() != null && input.getSize() != null && input.getMax() != null &&
                                     (input.getUnitName().equals(curTieredBlock.getUnit().getName()) &&
                                      input.getSize().compareTo(curTieredBlock.getSize()) == 0 &&
                                      input.getMax().compareTo(curTieredBlock.getMax()) == 0))
                    .findFirst().orElse(null);

            if (curOverride != null) {
                resolvedTieredBlockPriceOverrides.add(new DefaultTieredBlockPriceOverride(curTieredBlock.getUnit().getName(), curOverride.getSize(), curOverride.getPrice(), curOverride.getCurrency(), curOverride.getMax()));
            } else {
                resolvedTieredBlockPriceOverrides.add(null);
            }
        }
        return resolvedTieredBlockPriceOverrides;
    }

    @Override
    public DefaultPlan getOverriddenPlan(final String planName, final StandaloneCatalog catalog, final InternalTenantContext context) throws CatalogApiException {
        return overriddenPlanCache.getOverriddenPlan(planName, catalog, context);
    }
}
