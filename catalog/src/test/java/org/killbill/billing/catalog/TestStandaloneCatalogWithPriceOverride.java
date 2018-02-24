/*
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

package org.killbill.billing.catalog;

import java.math.BigDecimal;
import java.util.regex.Matcher;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhasePriceOverride;
import org.killbill.billing.catalog.api.PlanPhasePriceOverridesWithCallContext;
import org.killbill.billing.catalog.api.PlanSpecifier;
import org.killbill.billing.catalog.api.StaticCatalog;
import org.killbill.billing.catalog.api.UsagePriceOverride;
import org.killbill.billing.catalog.override.DefaultPriceOverride;
import org.killbill.xmlloader.XMLLoader;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;

public class TestStandaloneCatalogWithPriceOverride extends CatalogTestSuiteWithEmbeddedDB {

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/842")
    public void testCreateAmbiguousPlan() throws Exception {
        final StandaloneCatalog catalog = XMLLoader.getObjectFromString(Resources.getResource("SpyCarAdvanced.xml").toExternalForm(), StandaloneCatalog.class);
        final StaticCatalog standaloneCatalogWithPriceOverride = new StandaloneCatalogWithPriceOverride(catalog,
                                                                                                        priceOverride,
                                                                                                        internalCallContext.getTenantRecordId(),
                                                                                                        internalCallContextFactory);

        // Create ambiguous plan name
        final PlanSpecifier spec = new PlanSpecifier("standard-monthly-67890");
        final PlanPhasePriceOverridesWithCallContext overrides = Mockito.mock(PlanPhasePriceOverridesWithCallContext.class);
        Mockito.when(overrides.getCallContext()).thenReturn(callContext);
        final PlanPhasePriceOverride override = new DefaultPlanPhasePriceOverride("standard-monthly-evergreen", Currency.USD, null, BigDecimal.ONE, ImmutableList.<UsagePriceOverride>of());
        Mockito.when(overrides.getOverrides()).thenReturn(ImmutableList.of(override));
        final Plan plan = standaloneCatalogWithPriceOverride.createOrFindCurrentPlan(spec, overrides);
        Assert.assertTrue(plan.getName().startsWith("standard-monthly-67890-"));
        final Matcher m = DefaultPriceOverride.CUSTOM_PLAN_NAME_PATTERN.matcher(plan.getName());
        Assert.assertTrue(m.matches());

        // From the catalog
        Assert.assertNotNull(catalog.findCurrentPlan("standard-monthly"));
        Assert.assertNotNull(standaloneCatalogWithPriceOverride.findCurrentPlan("standard-monthly"));

        // Created on the fly
        try {
            catalog.findCurrentPlan(plan.getName());
            Assert.fail();
        } catch (final CatalogApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.CAT_NO_SUCH_PLAN.getCode());
        }
        Assert.assertNotNull(standaloneCatalogWithPriceOverride.findCurrentPlan("standard-monthly-1"));

        // From the catalog
        Assert.assertNotNull(catalog.findCurrentPlan("standard-monthly-12345"));
        Assert.assertNotNull(standaloneCatalogWithPriceOverride.findCurrentPlan("standard-monthly-12345"));
    }

    @Test(groups = "slow")
    public void testCreatePlanNoProduct() throws Exception {
        final StandaloneCatalog catalog = XMLLoader.getObjectFromString(Resources.getResource("SpyCarAdvanced.xml").toExternalForm(), StandaloneCatalog.class);
        final StaticCatalog standaloneCatalogWithPriceOverride = new StandaloneCatalogWithPriceOverride(catalog,
                                                                                                        priceOverride,
                                                                                                        internalCallContext.getTenantRecordId(),
                                                                                                        internalCallContextFactory);

        try {
            final PlanSpecifier specWithNullProduct = new PlanSpecifier(null, BillingPeriod.ANNUAL, "DEFAULT");
            standaloneCatalogWithPriceOverride.createOrFindCurrentPlan(specWithNullProduct, null);
            Assert.fail();
        } catch (final CatalogApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.CAT_NULL_PRODUCT_NAME.getCode());
        }
    }

    @Test(groups = "slow")
    public void testCreatePlanInvalidProduct() throws Exception {
        final StandaloneCatalog catalog = XMLLoader.getObjectFromString(Resources.getResource("SpyCarAdvanced.xml").toExternalForm(), StandaloneCatalog.class);
        final StaticCatalog standaloneCatalogWithPriceOverride = new StandaloneCatalogWithPriceOverride(catalog,
                                                                                                        priceOverride,
                                                                                                        internalCallContext.getTenantRecordId(),
                                                                                                        internalCallContextFactory);

        try {
            final PlanSpecifier specWithNullProduct = new PlanSpecifier("INVALID", BillingPeriod.ANNUAL, "DEFAULT");
            standaloneCatalogWithPriceOverride.createOrFindCurrentPlan(specWithNullProduct, null);
            Assert.fail();
        } catch (final CatalogApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.CAT_NO_SUCH_PRODUCT.getCode());
        }
    }
}
