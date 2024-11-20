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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.joda.time.DateTime;
import org.killbill.billing.catalog.CatalogTestSuiteWithEmbeddedDB;
import org.killbill.billing.catalog.DefaultPlanPhasePriceOverride;
import org.killbill.billing.catalog.DefaultTierPriceOverride;
import org.killbill.billing.catalog.DefaultTieredBlockPriceOverride;
import org.killbill.billing.catalog.DefaultUsagePriceOverride;
import org.killbill.billing.catalog.StandaloneCatalog;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhasePriceOverride;
import org.killbill.billing.catalog.api.TierPriceOverride;
import org.killbill.billing.catalog.api.TieredBlockPriceOverride;
import org.killbill.billing.catalog.api.UsagePriceOverride;
import org.killbill.billing.catalog.api.UsageType;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

public class TestCatalogOverrideDao extends CatalogTestSuiteWithEmbeddedDB {

    @Test(groups = "slow")
    public void testOverrideLastPhase() throws Exception {

        final StandaloneCatalog catalog = getCatalog("SpyCarBasic.xml");
        final Plan plan = catalog.findPlan("standard-monthly");

        final PlanPhasePriceOverride[] resolvedOverrides = new PlanPhasePriceOverride[plan.getAllPhases().length];
        resolvedOverrides[0] = null;
        resolvedOverrides[1] = new DefaultPlanPhasePriceOverride(plan.getFinalPhase().getName(), Currency.USD, null, new BigDecimal("128.76"), null);
        final CatalogOverridePlanDefinitionModelDao newPlan = catalogOverrideDao.getOrCreateOverridePlanDefinition(plan, new DateTime(catalog.getEffectiveDate()), resolvedOverrides, internalCallContext);
        assertEquals(newPlan.getParentPlanName(), "standard-monthly");
        assertTrue(newPlan.getIsActive());
    }

    @Test(groups = "slow")
    public void testOverrideTwoOutOfThreePhases() throws Exception {

        final StandaloneCatalog catalog = getCatalog("SpyCarAdvanced.xml");
        final Plan plan = catalog.findPlan("discount-standard-monthly");

        final PlanPhasePriceOverride[] resolvedOverrides = new PlanPhasePriceOverride[plan.getAllPhases().length];
        resolvedOverrides[0] = new DefaultPlanPhasePriceOverride(plan.getAllPhases()[0].getName(), Currency.USD, BigDecimal.TEN, null, null);
        resolvedOverrides[1] = null;
        resolvedOverrides[2] = new DefaultPlanPhasePriceOverride(plan.getFinalPhase().getName(), Currency.USD, null, new BigDecimal("348.64"), null);
        final CatalogOverridePlanDefinitionModelDao newPlan = catalogOverrideDao.getOrCreateOverridePlanDefinition(plan, new DateTime(catalog.getEffectiveDate()), resolvedOverrides, internalCallContext);

        assertEquals(newPlan.getParentPlanName(), "discount-standard-monthly");
        assertTrue(newPlan.getIsActive());

        final List<CatalogOverridePhaseDefinitionModelDao> phases = catalogOverrideDao.getOverriddenPlanPhases(newPlan.getRecordId(), internalCallContext);
        assertEquals(phases.size(), 2);
    }

    @Test(groups = "slow")
    public void testGetOverriddenPlanPhases() throws Exception {

        final StandaloneCatalog catalog = getCatalog("SpyCarAdvanced.xml");
        final Plan plan = catalog.findPlan("discount-standard-monthly");

        final PlanPhasePriceOverride[] resolvedOverrides = new PlanPhasePriceOverride[plan.getAllPhases().length];
        resolvedOverrides[0] = new DefaultPlanPhasePriceOverride(plan.getAllPhases()[0].getName(), Currency.USD, BigDecimal.TEN, BigDecimal.ONE, null);
        resolvedOverrides[1] = new DefaultPlanPhasePriceOverride(plan.getAllPhases()[1].getName(), Currency.USD, BigDecimal.ONE, BigDecimal.TEN, null);
        resolvedOverrides[2] = new DefaultPlanPhasePriceOverride(plan.getFinalPhase().getName(), Currency.USD, BigDecimal.ZERO, new BigDecimal("348.64"), null);

        final CatalogOverridePlanDefinitionModelDao newPlan = catalogOverrideDao.getOrCreateOverridePlanDefinition(plan, new DateTime(catalog.getEffectiveDate()), resolvedOverrides, internalCallContext);

        final List<CatalogOverridePhaseDefinitionModelDao> phases = catalogOverrideDao.getOverriddenPlanPhases(newPlan.getRecordId(), internalCallContext);
        assertEquals(phases.size(), 3);
        for (int i = 0; i < 3; i++) {
            final CatalogOverridePhaseDefinitionModelDao curPhase = phases.get(i);
            assertEquals(curPhase.getCurrency(), resolvedOverrides[i].getCurrency().name());
            assertEquals(curPhase.getFixedPrice().compareTo(resolvedOverrides[i].getFixedPrice()), 0);
            assertEquals(curPhase.getRecurringPrice().compareTo(resolvedOverrides[i].getRecurringPrice()), 0);
            assertEquals(curPhase.getParentPhaseName(), resolvedOverrides[i].getPhaseName());
        }
    }

    @Test(groups = "slow")
    public void testGetOverriddenPlanPhasesWithUsageOverrides() throws Exception {

        final StandaloneCatalog catalog = getCatalog("SpyCarAdvanced.xml");
        final Plan plan = catalog.findPlan("gas-monthly-2-tiers");

        final PlanPhasePriceOverride[] resolvedOverrides = new PlanPhasePriceOverride[plan.getAllPhases().length];

        List<TieredBlockPriceOverride> tieredBlockPriceOverrides1 = new ArrayList<TieredBlockPriceOverride>();
        DefaultTieredBlockPriceOverride tieredBlockPriceOverride1 = new DefaultTieredBlockPriceOverride("gallons", new BigDecimal("1"), new BigDecimal(4), Currency.USD, new BigDecimal("100"));
        tieredBlockPriceOverrides1.add(tieredBlockPriceOverride1);

        List<TieredBlockPriceOverride> tieredBlockPriceOverrides2 = new ArrayList<TieredBlockPriceOverride>();
        DefaultTieredBlockPriceOverride tieredBlockPriceOverride2 = new DefaultTieredBlockPriceOverride("gallons", new BigDecimal("1"), new BigDecimal(8), Currency.USD, new BigDecimal("500"));
        tieredBlockPriceOverrides2.add(tieredBlockPriceOverride2);

        List<TierPriceOverride> tierPriceOverrides = new ArrayList<TierPriceOverride>();
        DefaultTierPriceOverride tierPriceOverride1 = new DefaultTierPriceOverride(tieredBlockPriceOverrides1);
        tierPriceOverrides.add(tierPriceOverride1);

        DefaultTierPriceOverride tierPriceOverride2 = new DefaultTierPriceOverride(tieredBlockPriceOverrides2);
        tierPriceOverrides.add(tierPriceOverride2);

        List<UsagePriceOverride> usagePriceOverrides = new ArrayList<UsagePriceOverride>();
        DefaultUsagePriceOverride usagePriceOverride = new DefaultUsagePriceOverride("gas-monthly-2-tiers-in-arrear", UsageType.CONSUMABLE, tierPriceOverrides);
        usagePriceOverrides.add(usagePriceOverride);

       //Override the gallons price from $3.95 to $4 and also the recurring price from  $0 to $348.64
        resolvedOverrides[0]  = new DefaultPlanPhasePriceOverride(plan.getFinalPhase().getName(), Currency.USD, BigDecimal.ZERO, new BigDecimal("348.64"), usagePriceOverrides);

        final CatalogOverridePlanDefinitionModelDao newPlan = catalogOverrideDao.getOrCreateOverridePlanDefinition(plan, new DateTime(catalog.getEffectiveDate()), resolvedOverrides, internalCallContext);

        final CatalogOverrideUsageDefinitionModelDao usageDef1 = checkUsageDefinition(newPlan, usagePriceOverride);
        final List<CatalogOverrideTierDefinitionModelDao> tierDefs1 = checkTierDefs(usageDef1.getRecordId(), tieredBlockPriceOverride1, tieredBlockPriceOverride2);
        assertEquals(tierDefs1.size(), 2);

        // Exercise the idempotency of the getOrCreateOverridePlanDefinition method. We verify that we do not recreate any entries given the same input.
        final CatalogOverridePlanDefinitionModelDao newPlan2 = catalogOverrideDao.getOrCreateOverridePlanDefinition(plan, new DateTime(catalog.getEffectiveDate()), resolvedOverrides, internalCallContext);
        final CatalogOverrideUsageDefinitionModelDao usageDef2 = checkUsageDefinition(newPlan2, usagePriceOverride);
        // Check we have not recreated the same usage definition record
        assertEquals(usageDef1.getRecordId(), usageDef2.getRecordId());

        final List<CatalogOverrideTierDefinitionModelDao> tierDefs2 = checkTierDefs(usageDef2.getRecordId(), tieredBlockPriceOverride1, tieredBlockPriceOverride2);
        assertEquals(tierDefs2.size(), 2);
        assertEquals(tierDefs1.get(0).getRecordId(), tierDefs2.get(0).getRecordId());
        assertEquals(tierDefs1.get(1).getRecordId(), tierDefs2.get(1).getRecordId());

        // Exercise the recreation of all the entries given a different override input
        tieredBlockPriceOverrides1.clear();
        tieredBlockPriceOverride1 = new DefaultTieredBlockPriceOverride("gallons", new BigDecimal("1"), new BigDecimal(3), Currency.USD, new BigDecimal("100"));
        tieredBlockPriceOverrides1.add(tieredBlockPriceOverride1);

        tieredBlockPriceOverrides2.clear();
        tieredBlockPriceOverrides2 = new ArrayList<TieredBlockPriceOverride>();
        tieredBlockPriceOverride2 = new DefaultTieredBlockPriceOverride("gallons", new BigDecimal("1"), new BigDecimal(5), Currency.USD, new BigDecimal("500"));
        tieredBlockPriceOverrides2.add(tieredBlockPriceOverride2);

        tierPriceOverrides.clear();
        tierPriceOverride1 = new DefaultTierPriceOverride(tieredBlockPriceOverrides1);
        tierPriceOverrides.add(tierPriceOverride1);

        tierPriceOverride2 = new DefaultTierPriceOverride(tieredBlockPriceOverrides2);
        tierPriceOverrides.add(tierPriceOverride2);

        usagePriceOverrides.clear();
        usagePriceOverride = new DefaultUsagePriceOverride("gas-monthly-2-tiers-in-arrear", UsageType.CONSUMABLE, tierPriceOverrides);
        usagePriceOverrides.add(usagePriceOverride);

        final CatalogOverridePlanDefinitionModelDao otherPlan = catalogOverrideDao.getOrCreateOverridePlanDefinition(plan, new DateTime(catalog.getEffectiveDate()), resolvedOverrides, internalCallContext);

        final CatalogOverrideUsageDefinitionModelDao otherUsageDef = checkUsageDefinition(otherPlan, usagePriceOverride);
        final List<CatalogOverrideTierDefinitionModelDao> otherTierDefs = checkTierDefs(otherUsageDef.getRecordId(), tieredBlockPriceOverride1, tieredBlockPriceOverride2);
        assertEquals(otherTierDefs.size(), 2);
        assertNotEquals(otherTierDefs.get(0).getRecordId(), tierDefs2.get(0).getRecordId());
        assertNotEquals(otherTierDefs.get(1).getRecordId(), tierDefs2.get(1).getRecordId());
    }


    private CatalogOverrideUsageDefinitionModelDao checkUsageDefinition(final CatalogOverridePlanDefinitionModelDao newPlan, final UsagePriceOverride usagePriceOverride) {
        final List<CatalogOverridePhaseDefinitionModelDao> phases = catalogOverrideDao.getOverriddenPlanPhases(newPlan.getRecordId(), internalCallContext);
        assertEquals(phases.size(), 1);
        final CatalogOverridePhaseDefinitionModelDao curPhase = phases.get(0);

        final List<CatalogOverrideUsageDefinitionModelDao> usages = catalogOverrideDao.getOverriddenPhaseUsages(curPhase.getRecordId(), internalCallContext);
        assertEquals(usages.size(), 1);
        final CatalogOverrideUsageDefinitionModelDao curUsage = usages.get(0);
        assertEquals(curUsage.getParentUsageName(), usagePriceOverride.getName());
        assertEquals(curUsage.getType(), usagePriceOverride.getUsageType().toString());
        return usages.get(0);
    }
        private List<CatalogOverrideTierDefinitionModelDao> checkTierDefs(final Long usageDefRecordId, final TieredBlockPriceOverride tieredBlockPriceOverride1, final TieredBlockPriceOverride tieredBlockPriceOverride2) {

        final List<CatalogOverrideTierDefinitionModelDao> tiers = catalogOverrideDao.getOverriddenUsageTiers(usageDefRecordId, internalCallContext);
        assertEquals(tiers.size(), 2);

        final CatalogOverrideTierDefinitionModelDao firstTier = tiers.get(0);
        final List<CatalogOverrideBlockDefinitionModelDao> firstTierBlocks =  catalogOverrideDao.getOverriddenTierBlocks(firstTier.getRecordId(), internalCallContext);
        assertEquals(firstTierBlocks.size(), 1);
        final CatalogOverrideBlockDefinitionModelDao firstTierBlock =  firstTierBlocks.get(0);
        assertEquals(firstTierBlock.getParentUnitName(),tieredBlockPriceOverride1.getUnitName());
        assertEquals(firstTierBlock.getPrice().compareTo(tieredBlockPriceOverride1.getPrice()), 0);
        assertEquals(firstTierBlock.getSize().compareTo(tieredBlockPriceOverride1.getSize()), 0);
        assertEquals(firstTierBlock.getMax().compareTo(tieredBlockPriceOverride1.getMax()), 0);

        final CatalogOverrideTierDefinitionModelDao secondTier = tiers.get(1);
        final List<CatalogOverrideBlockDefinitionModelDao> secondTierBlocks =  catalogOverrideDao.getOverriddenTierBlocks(secondTier.getRecordId(), internalCallContext);
        assertEquals(secondTierBlocks.size(), 1);
        final CatalogOverrideBlockDefinitionModelDao secondTierBlock =  secondTierBlocks.get(0);
        assertEquals(secondTierBlock.getParentUnitName(),tieredBlockPriceOverride2.getUnitName());
        assertEquals(secondTierBlock.getPrice().compareTo(tieredBlockPriceOverride2.getPrice()), 0);
        assertEquals(secondTierBlock.getSize().compareTo(tieredBlockPriceOverride2.getSize()), 0);
        assertEquals(secondTierBlock.getMax().compareTo(tieredBlockPriceOverride2.getMax()), 0);
        return tiers;
    }

    @Test(groups = "slow")
    public void testGetOverrideOneOutOfTwoTieredBlocks() throws Exception {

        final StandaloneCatalog catalog = getCatalog("UsageExperimental.xml");
        final Plan plan = catalog.findPlan("chocolate-monthly");

        final PlanPhasePriceOverride[] resolvedOverrides = new PlanPhasePriceOverride[plan.getAllPhases().length];

        List<TieredBlockPriceOverride> tieredBlockPriceOverrides = new ArrayList<TieredBlockPriceOverride>();
        DefaultTieredBlockPriceOverride tieredBlockPriceOverride = new DefaultTieredBlockPriceOverride("chocolate-videos", new BigDecimal("1"), new BigDecimal(0.75), Currency.USD, new BigDecimal("10000"));
        tieredBlockPriceOverrides.add(tieredBlockPriceOverride);

        List<TierPriceOverride> tierPriceOverrides = new ArrayList<TierPriceOverride>();
        DefaultTierPriceOverride tierPriceOverride = new DefaultTierPriceOverride(tieredBlockPriceOverrides);

        tierPriceOverrides.add(null);
        tierPriceOverrides.add(tierPriceOverride);

        List<UsagePriceOverride> usagePriceOverrides = new ArrayList<UsagePriceOverride>();
        DefaultUsagePriceOverride usagePriceOverride = new DefaultUsagePriceOverride("chocolate-monthly-videos", UsageType.CONSUMABLE, tierPriceOverrides);
        usagePriceOverrides.add(usagePriceOverride);

        //Override chocolate-videos unit price with size = 1 and max value = 10000 from $1 to $0.75
        resolvedOverrides[0]  = new DefaultPlanPhasePriceOverride(plan.getFinalPhase().getName(), Currency.USD, null, null, usagePriceOverrides);

        final CatalogOverridePlanDefinitionModelDao newPlan = catalogOverrideDao.getOrCreateOverridePlanDefinition(plan, new DateTime(catalog.getEffectiveDate()), resolvedOverrides, internalCallContext);

        final List<CatalogOverridePhaseDefinitionModelDao> phases = catalogOverrideDao.getOverriddenPlanPhases(newPlan.getRecordId(), internalCallContext);
        assertEquals(phases.size(), 1);
        final CatalogOverridePhaseDefinitionModelDao curPhase = phases.get(0);

        assertEquals(curPhase.getCurrency(), resolvedOverrides[0].getCurrency().name());
        Assert.assertNull(curPhase.getFixedPrice());
        Assert.assertNull(curPhase.getRecurringPrice());
        assertEquals(curPhase.getParentPhaseName(), resolvedOverrides[0].getPhaseName());

        final List<CatalogOverrideUsageDefinitionModelDao> usages = catalogOverrideDao.getOverriddenPhaseUsages(curPhase.getRecordId(), internalCallContext);
        assertEquals(usages.size(), 1);
        final CatalogOverrideUsageDefinitionModelDao curUsage = usages.get(0);
        assertEquals(curUsage.getParentUsageName(), usagePriceOverride.getName());
        assertEquals(curUsage.getType(), usagePriceOverride.getUsageType().toString());

        final List<CatalogOverrideTierDefinitionModelDao> tiers = catalogOverrideDao.getOverriddenUsageTiers(curUsage.getRecordId(), internalCallContext);
        assertEquals(tiers.size(), 1);
        final CatalogOverrideTierDefinitionModelDao curTier = tiers.get(0);

        final List<CatalogOverrideBlockDefinitionModelDao> tierBlocks =  catalogOverrideDao.getOverriddenTierBlocks(curTier.getRecordId(), internalCallContext);
        assertEquals(tierBlocks.size(), 1);
        final CatalogOverrideBlockDefinitionModelDao curTieredBlock =  tierBlocks.get(0);
        assertEquals(curTieredBlock.getParentUnitName(),tieredBlockPriceOverride.getUnitName());
        assertEquals(curTieredBlock.getPrice().compareTo(tieredBlockPriceOverride.getPrice()), 0);
        assertEquals(curTieredBlock.getSize().compareTo(tieredBlockPriceOverride.getSize()), 0);
        assertEquals(curTieredBlock.getMax().compareTo(tieredBlockPriceOverride.getMax()), 0);
    }

}

