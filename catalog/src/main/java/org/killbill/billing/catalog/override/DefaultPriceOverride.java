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
import java.util.regex.Pattern;

import org.joda.time.DateTime;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.*;
import org.killbill.billing.catalog.api.*;
import org.killbill.billing.catalog.caching.OverriddenPlanCache;
import org.killbill.billing.catalog.dao.CatalogOverrideDao;
import org.killbill.billing.catalog.dao.CatalogOverridePlanDefinitionModelDao;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;

public class DefaultPriceOverride implements PriceOverride {

    public static final Pattern CUSTOM_PLAN_NAME_PATTERN = Pattern.compile("(.*)-(\\d+)$");

    private final CatalogOverrideDao overrideDao;
    private final OverriddenPlanCache overriddenPlanCache;

    @Inject
    public DefaultPriceOverride(final CatalogOverrideDao overrideDao, final OverriddenPlanCache overriddenPlanCache) {
        this.overrideDao = overrideDao;
        this.overriddenPlanCache = overriddenPlanCache;
    }

    @Override
    public DefaultPlan getOrCreateOverriddenPlan(final Plan parentPlan, final DateTime catalogEffectiveDate, final List<PlanPhasePriceOverride> overrides, final InternalCallContext context) throws CatalogApiException {

        final PlanPhasePriceOverride[] resolvedOverride = new PlanPhasePriceOverride[parentPlan.getAllPhases().length];
        int index = 0;
        for (final PlanPhase curPhase : parentPlan.getAllPhases()) {
            final PlanPhasePriceOverride curOverride = Iterables.tryFind(overrides, new Predicate<PlanPhasePriceOverride>() {
                @Override
                public boolean apply(final PlanPhasePriceOverride input) {
                    if (input.getPhaseName() != null) {
                        return input.getPhaseName().equals(curPhase.getName());
                    }
                    // If the phaseName was not passed, we infer by matching the phaseType. This obviously would not work in a case where
                    // a plan is defined with multiple phases of the same type.
                    final PlanPhaseSpecifier curPlanPhaseSpecifier = input.getPlanPhaseSpecifier();
                    if (curPlanPhaseSpecifier.getPhaseType().equals(curPhase.getPhaseType())) {
                        return true;
                    }
                    return false;
                }
            }).orNull();

            resolvedOverride[index++] = curOverride != null ?
                                        new DefaultPlanPhasePriceOverride(curPhase.getName(), curOverride.getCurrency(), curOverride.getFixedPrice(), curOverride.getRecurringPrice(), getResolvedUsageOverrides(curPhase.getUsages(), curOverride.getUsagePriceOverrides())) :
                                        null;
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

        final CatalogOverridePlanDefinitionModelDao overriddenPlan = overrideDao.getOrCreateOverridePlanDefinition(parentPlan, catalogEffectiveDate, resolvedOverride, context);
        final String planName = new StringBuffer(parentPlan.getName()).append("-").append(overriddenPlan.getRecordId()).toString();
        final DefaultPlan result = new DefaultPlan(planName, (DefaultPlan) parentPlan, resolvedOverride);
        return result;
    }


    public List<UsagePriceOverride> getResolvedUsageOverrides(Usage[] usages, List<UsagePriceOverride> usagePriceOverrides){
        List<UsagePriceOverride> resolvedUsageOverrides = new ArrayList<UsagePriceOverride>();
        int index = 0;
        for (final Usage curUsage : usages) {
            final UsagePriceOverride curOverride = Iterables.tryFind(usagePriceOverrides, new Predicate<UsagePriceOverride>() {
                @Override
                public boolean apply(final UsagePriceOverride input) {
                    if (input.getName() != null) {
                        return input.getName().equals(curUsage.getName());
                    }
                    return false;
                }
            }).orNull();
              if(curOverride != null)
                   resolvedUsageOverrides.add(new DefaultUsagePriceOverride(curUsage.getName(), curUsage.getUsageType(), getResolvedTierOverrides(curUsage.getTiers(), curOverride.getTierPriceOverrides())));
              else
                 resolvedUsageOverrides.add(null);
        }
        return resolvedUsageOverrides;
    }

    public List<TierPriceOverride> getResolvedTierOverrides(Tier[] tiers, List<TierPriceOverride> tierPriceOverrides){
        List<TierPriceOverride> resolvedTierOverrides = new ArrayList<TierPriceOverride>();
        int index = 0;
        for (final Tier curTier : tiers) {
            final TierPriceOverride curOverride = Iterables.tryFind(tierPriceOverrides, new Predicate<TierPriceOverride>() {
                @Override
                public boolean apply(final TierPriceOverride input) {

                    if (input.getTieredBlockPriceOverrides() != null)
                        for (TieredBlockPriceOverride blockPriceOverride : input.getTieredBlockPriceOverrides()) {
                            String unitName = blockPriceOverride.getUnitName();
                            Double max = blockPriceOverride.getMax();
                            Double size = blockPriceOverride.getSize();

                            for (int i = 0; i < curTier.getTieredBlocks().length; i++) {
                                TieredBlock curTieredBlock = curTier.getTieredBlocks()[i];
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

            if(curOverride != null)
            resolvedTierOverrides.add(new DefaultTierPriceOverride(getResolvedTieredBlockPriceOverrides(curTier.getTieredBlocks(),curOverride.getTieredBlockPriceOverrides())));
            else
                resolvedTierOverrides.add(null);
        }

        return resolvedTierOverrides;
    }

    public List<TieredBlockPriceOverride> getResolvedTieredBlockPriceOverrides(TieredBlock[] tieredBlocks, List<TieredBlockPriceOverride> tieredBlockPriceOverrides){
        List<TieredBlockPriceOverride> resolvedTieredBlockPriceOverrides = new ArrayList<TieredBlockPriceOverride>();
        int index = 0;
        for (final TieredBlock curTieredBlock : tieredBlocks) {

            final TieredBlockPriceOverride curOverride = Iterables.tryFind(tieredBlockPriceOverrides, new Predicate<TieredBlockPriceOverride>() {
                @Override
                public boolean apply(final TieredBlockPriceOverride input) {
                    if (input.getUnitName() != null  && input.getSize()!=null && input.getMax()!=null) {

                        return (input.getUnitName().equals(curTieredBlock.getUnit().getName()) && Double.compare(input.getSize(),curTieredBlock.getSize())==0 && Double.compare(input.getMax(),curTieredBlock.getMax())==0);
                    }
                    return false;
                }
            }).orNull();

            if(curOverride != null) {
                resolvedTieredBlockPriceOverrides.add(new DefaultTieredBlockPriceOverride(curTieredBlock.getUnit().getName(), curOverride.getSize(), curOverride.getPrice(), curOverride.getMax())) ;
            }
            else
                resolvedTieredBlockPriceOverrides.add(null);
        }

      return resolvedTieredBlockPriceOverrides;
    }


    @Override
    public DefaultPlan getOverriddenPlan(final String planName, final StaticCatalog catalog, final InternalTenantContext context) throws CatalogApiException {
        return overriddenPlanCache.getOverriddenPlan(planName, catalog, context);
    }
}
