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
import org.killbill.xmlloader.XMLLoader;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.io.Resources;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class TestCatalogOverrideDao extends CatalogTestSuiteWithEmbeddedDB {

    @Test(groups = "slow")
    public void testOverrideLastPhase() throws Exception {

        final StandaloneCatalog catalog = XMLLoader.getObjectFromString(Resources.getResource("SpyCarBasic.xml").toExternalForm(), StandaloneCatalog.class);
        final Plan plan = catalog.findCurrentPlan("standard-monthly");

        final PlanPhasePriceOverride[] resolvedOverrides = new PlanPhasePriceOverride[plan.getAllPhases().length];
        resolvedOverrides[0] = null;
        resolvedOverrides[1] = new DefaultPlanPhasePriceOverride(plan.getFinalPhase().getName(), Currency.USD, null, new BigDecimal("128.76"), null);
        final CatalogOverridePlanDefinitionModelDao newPlan = catalogOverrideDao.getOrCreateOverridePlanDefinition(plan, new DateTime(catalog.getEffectiveDate()), resolvedOverrides, internalCallContext);
        assertEquals(newPlan.getParentPlanName(), "standard-monthly");
        assertTrue(newPlan.getIsActive());
    }

    @Test(groups = "slow")
    public void testOverrideTwoOutOfThreePhases() throws Exception {

        final StandaloneCatalog catalog = XMLLoader.getObjectFromString(Resources.getResource("SpyCarAdvanced.xml").toExternalForm(), StandaloneCatalog.class);
        final Plan plan = catalog.findCurrentPlan("discount-standard-monthly");

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

        final StandaloneCatalog catalog = XMLLoader.getObjectFromString(Resources.getResource("SpyCarAdvanced.xml").toExternalForm(), StandaloneCatalog.class);
        final Plan plan = catalog.findCurrentPlan("discount-standard-monthly");

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

        final StandaloneCatalog catalog = XMLLoader.getObjectFromString(Resources.getResource("SpyCarAdvanced.xml").toExternalForm(), StandaloneCatalog.class);
        final Plan plan = catalog.findCurrentPlan("gas-monthly");

        final PlanPhasePriceOverride[] resolvedOverrides = new PlanPhasePriceOverride[plan.getAllPhases().length];

        List<TieredBlockPriceOverride> tieredBlockPriceOverrides = new ArrayList<TieredBlockPriceOverride>();
        DefaultTieredBlockPriceOverride tieredBlockPriceOverride = new DefaultTieredBlockPriceOverride("gallons", new Double("1"), new BigDecimal(4), Currency.USD, new Double("100"));
        tieredBlockPriceOverrides.add(tieredBlockPriceOverride);

        List<TierPriceOverride> tierPriceOverrides = new ArrayList<TierPriceOverride>();
        DefaultTierPriceOverride tierPriceOverride = new DefaultTierPriceOverride(tieredBlockPriceOverrides);
        tierPriceOverrides.add(tierPriceOverride);

        List<UsagePriceOverride> usagePriceOverrides = new ArrayList<UsagePriceOverride>();
        DefaultUsagePriceOverride usagePriceOverride = new DefaultUsagePriceOverride("gas-monthly-in-arrear", UsageType.CONSUMABLE, tierPriceOverrides);
        usagePriceOverrides.add(usagePriceOverride);

       //Override the gallons price from $3.95 to $4 and also the recurring price from  $0 to $348.64
        resolvedOverrides[0]  = new DefaultPlanPhasePriceOverride(plan.getFinalPhase().getName(), Currency.USD, BigDecimal.ZERO, new BigDecimal("348.64"), usagePriceOverrides);

        final CatalogOverridePlanDefinitionModelDao newPlan = catalogOverrideDao.getOrCreateOverridePlanDefinition(plan, new DateTime(catalog.getEffectiveDate()), resolvedOverrides, internalCallContext);

        final List<CatalogOverridePhaseDefinitionModelDao> phases = catalogOverrideDao.getOverriddenPlanPhases(newPlan.getRecordId(), internalCallContext);
        assertEquals(phases.size(), 1);
        final CatalogOverridePhaseDefinitionModelDao curPhase = phases.get(0);

        assertEquals(curPhase.getCurrency(), resolvedOverrides[0].getCurrency().name());
        assertEquals(curPhase.getFixedPrice().compareTo(resolvedOverrides[0].getFixedPrice()), 0);
        assertEquals(curPhase.getRecurringPrice().compareTo(resolvedOverrides[0].getRecurringPrice()), 0);
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
        assertEquals(curTieredBlock.getSize(),tieredBlockPriceOverride.getSize());
        assertEquals(curTieredBlock.getMax(),tieredBlockPriceOverride.getMax());
    }

    @Test(groups = "slow")
    public void testGetOverrideOneOutOfTwoTieredBlocks() throws Exception {

        final StandaloneCatalog catalog = XMLLoader.getObjectFromString(Resources.getResource("UsageExperimental.xml").toExternalForm(), StandaloneCatalog.class);
        final Plan plan = catalog.findCurrentPlan("chocolate-monthly");

        final PlanPhasePriceOverride[] resolvedOverrides = new PlanPhasePriceOverride[plan.getAllPhases().length];

        List<TieredBlockPriceOverride> tieredBlockPriceOverrides = new ArrayList<TieredBlockPriceOverride>();
        DefaultTieredBlockPriceOverride tieredBlockPriceOverride = new DefaultTieredBlockPriceOverride("chocolate-videos", new Double("1"), new BigDecimal(0.75), Currency.USD, new Double("10000"));
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
        assertEquals(curTieredBlock.getSize(),tieredBlockPriceOverride.getSize());
        assertEquals(curTieredBlock.getMax(),tieredBlockPriceOverride.getMax());
    }

}

