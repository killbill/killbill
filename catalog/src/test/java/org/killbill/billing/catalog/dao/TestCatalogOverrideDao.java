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

import org.joda.time.DateTime;
import org.killbill.billing.catalog.CatalogTestSuiteWithEmbeddedDB;
import org.killbill.billing.catalog.DefaultPlanPhasePriceOverride;
import org.killbill.billing.catalog.StandaloneCatalog;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhasePriceOverride;
import org.killbill.xmlloader.XMLLoader;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.tweak.HandleCallback;
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
        resolvedOverrides[1] = new DefaultPlanPhasePriceOverride(plan.getFinalPhase().getName(), Currency.USD, null, new BigDecimal("128.76"));
        final CatalogOverridePlanDefinitionModelDao newPlan = catalogOverrideDao.getOrCreateOverridePlanDefinition(plan.getName(), new DateTime(catalog.getEffectiveDate()), resolvedOverrides, internalCallContext);
        assertEquals(newPlan.getParentPlanName(), "standard-monthly");
        assertTrue(newPlan.getIsActive());
    }

    @Test(groups = "slow")
    public void testOverrideTwoOutOfThreePhases() throws Exception {

        final StandaloneCatalog catalog = XMLLoader.getObjectFromString(Resources.getResource("SpyCarAdvanced.xml").toExternalForm(), StandaloneCatalog.class);
        final Plan plan = catalog.findCurrentPlan("discount-standard-monthly");

        final PlanPhasePriceOverride[] resolvedOverrides = new PlanPhasePriceOverride[plan.getAllPhases().length];
        resolvedOverrides[0] = new DefaultPlanPhasePriceOverride(plan.getAllPhases()[0].getName(), Currency.USD, BigDecimal.TEN, null);
        resolvedOverrides[1] = null;
        resolvedOverrides[2] = new DefaultPlanPhasePriceOverride(plan.getFinalPhase().getName(), Currency.USD, null, new BigDecimal("348.64"));
        final CatalogOverridePlanDefinitionModelDao newPlan = catalogOverrideDao.getOrCreateOverridePlanDefinition(plan.getName(), new DateTime(catalog.getEffectiveDate()), resolvedOverrides, internalCallContext);
        assertEquals(newPlan.getParentPlanName(), "discount-standard-monthly");
        assertTrue(newPlan.getIsActive());

    }
}

