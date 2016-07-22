/*
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

import org.joda.time.DateTime;
import org.killbill.billing.catalog.api.BillingMode;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PriceList;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.catalog.api.SimplePlanDescriptor;
import org.killbill.billing.catalog.api.TimeUnit;
import org.killbill.billing.catalog.api.user.DefaultSimplePlanDescriptor;
import org.killbill.xmlloader.XMLLoader;
import org.testng.annotations.Test;

import com.google.common.io.Resources;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class TestCatalogUpdater extends CatalogTestSuiteNoDB {

    @Test(groups = "fast")
    public void testAddNoTrialPlanOnFirstCatalog() throws CatalogApiException {

        final DateTime now = clock.getUTCNow();
        final SimplePlanDescriptor desc = new DefaultSimplePlanDescriptor("foo-monthly", "Foo", Currency.EUR, BigDecimal.TEN, BillingPeriod.MONTHLY, 0, TimeUnit.UNLIMITED);

        final CatalogUpdater catalogUpdater = new CatalogUpdater("dummy", BillingMode.IN_ARREAR, now, desc.getCurrency());

        catalogUpdater.addSimplePlanDescriptor(desc);

        final StandaloneCatalog catalog = catalogUpdater.getCatalog();

        assertEquals(catalog.getCurrentProducts().length, 1);

        final Product product = catalog.getCurrentProducts()[0];
        assertEquals(product.getName(), "Foo");
        assertEquals(product.getCategory(), ProductCategory.BASE);

        assertEquals(catalog.getCurrentPlans().length, 1);

        final Plan plan = catalog.findCurrentPlan("foo-monthly");
        assertEquals(plan.getName(), "foo-monthly");

        assertEquals(plan.getInitialPhases().length, 0);
        assertEquals(plan.getFinalPhase().getPhaseType(), PhaseType.EVERGREEN);
        assertNull(plan.getFinalPhase().getFixed());
        assertEquals(plan.getFinalPhase().getName(), "foo-monthly-evergreen");

        assertEquals(plan.getFinalPhase().getRecurring().getBillingPeriod(), BillingPeriod.MONTHLY);
        assertEquals(plan.getFinalPhase().getRecurring().getRecurringPrice().getPrices().length, 1);
        assertEquals(plan.getFinalPhase().getRecurring().getRecurringPrice().getPrices()[0].getValue(), BigDecimal.TEN);
        assertEquals(plan.getFinalPhase().getRecurring().getRecurringPrice().getPrices()[0].getCurrency(), Currency.EUR);

        assertEquals(catalog.getPriceLists().getAllPriceLists().size(), 1);
        final PriceList priceList = catalog.getPriceLists().getAllPriceLists().get(0);
        assertEquals(priceList.getName(), new PriceListDefault().getName());
        assertEquals(priceList.getPlans().length, 1);
        assertEquals(priceList.getPlans()[0].getName(), "foo-monthly");
    }


    @Test(groups = "fast")
    public void testAddTrialPlanOnFirstCatalog() throws CatalogApiException {

        final DateTime now = clock.getUTCNow();
        final SimplePlanDescriptor desc = new DefaultSimplePlanDescriptor("foo-monthly", "Foo", Currency.EUR, BigDecimal.TEN, BillingPeriod.MONTHLY, 14, TimeUnit.DAYS);

        final CatalogUpdater catalogUpdater = new CatalogUpdater("dummy", BillingMode.IN_ARREAR, now, desc.getCurrency());

        catalogUpdater.addSimplePlanDescriptor(desc);

        final StandaloneCatalog catalog = catalogUpdater.getCatalog();

        assertEquals(catalog.getCurrentProducts().length, 1);

        final Product product = catalog.getCurrentProducts()[0];
        assertEquals(product.getName(), "Foo");
        assertEquals(product.getCategory(), ProductCategory.BASE);

        assertEquals(catalog.getCurrentPlans().length, 1);

        final Plan plan = catalog.findCurrentPlan("foo-monthly");
        assertEquals(plan.getName(), "foo-monthly");

        assertEquals(plan.getInitialPhases().length, 1);
        assertEquals(plan.getInitialPhases()[0].getPhaseType(), PhaseType.TRIAL);
        assertEquals(plan.getInitialPhases()[0].getFixed().getPrice().getPrices().length, 1);
        assertEquals(plan.getInitialPhases()[0].getFixed().getPrice().getPrices()[0].getCurrency(), Currency.EUR);
        assertEquals(plan.getInitialPhases()[0].getFixed().getPrice().getPrices()[0].getValue(), BigDecimal.ZERO);
        assertEquals(plan.getInitialPhases()[0].getName(), "foo-monthly-trial");

        assertEquals(plan.getFinalPhase().getPhaseType(), PhaseType.EVERGREEN);
        assertNull(plan.getFinalPhase().getFixed());
        assertEquals(plan.getFinalPhase().getName(), "foo-monthly-evergreen");

        assertEquals(plan.getFinalPhase().getRecurring().getBillingPeriod(), BillingPeriod.MONTHLY);
        assertEquals(plan.getFinalPhase().getRecurring().getRecurringPrice().getPrices().length, 1);
        assertEquals(plan.getFinalPhase().getRecurring().getRecurringPrice().getPrices()[0].getValue(), BigDecimal.TEN);
        assertEquals(plan.getFinalPhase().getRecurring().getRecurringPrice().getPrices()[0].getCurrency(), Currency.EUR);

        assertEquals(catalog.getPriceLists().getAllPriceLists().size(), 1);
        final PriceList priceList = catalog.getPriceLists().getAllPriceLists().get(0);
        assertEquals(priceList.getName(), new PriceListDefault().getName());
        assertEquals(priceList.getPlans().length, 1);
        assertEquals(priceList.getPlans()[0].getName(), "foo-monthly");
    }



    @Test(groups = "fast")
    public void testAddPlanOnExistingCatalog() throws Exception {

        final StandaloneCatalog originalCatalog = XMLLoader.getObjectFromString(Resources.getResource("SpyCarBasic.xml").toExternalForm(), StandaloneCatalog.class);
        assertEquals(originalCatalog.getPriceLists().getAllPriceLists().size(), 1);
        assertEquals(originalCatalog.getPriceLists().getAllPriceLists().get(0).getName(), new PriceListDefault().getName());
        assertEquals(originalCatalog.getPriceLists().getAllPriceLists().get(0).getPlans().length, 3);

        final CatalogUpdater catalogUpdater = new CatalogUpdater(originalCatalog);

        final SimplePlanDescriptor desc = new DefaultSimplePlanDescriptor("standard-annual", "Standard", Currency.USD, BigDecimal.TEN, BillingPeriod.MONTHLY, 0, TimeUnit.UNLIMITED);
        catalogUpdater.addSimplePlanDescriptor(desc);

        final StandaloneCatalog catalog = catalogUpdater.getCatalog();

        final Plan plan = catalog.findCurrentPlan("standard-annual");
        assertEquals(plan.getName(), "standard-annual");

        assertEquals(plan.getInitialPhases().length, 0);
        assertEquals(plan.getFinalPhase().getPhaseType(), PhaseType.EVERGREEN);
        assertNull(plan.getFinalPhase().getFixed());
        assertEquals(plan.getFinalPhase().getName(), "standard-annual-evergreen");

        assertEquals(plan.getFinalPhase().getRecurring().getBillingPeriod(), BillingPeriod.MONTHLY);
        assertEquals(plan.getFinalPhase().getRecurring().getRecurringPrice().getPrices().length, 1);
        assertEquals(plan.getFinalPhase().getRecurring().getRecurringPrice().getPrices()[0].getValue(), BigDecimal.TEN);
        assertEquals(plan.getFinalPhase().getRecurring().getRecurringPrice().getPrices()[0].getCurrency(), Currency.USD);

        assertEquals(catalog.getPriceLists().getAllPriceLists().size(), 1);
        final PriceList priceList = catalog.getPriceLists().getAllPriceLists().get(0);
        assertEquals(priceList.getName(), new PriceListDefault().getName());
        assertEquals(priceList.getPlans().length, 4);

        //System.err.println(catalogUpdater.getCatalogXML());
    }




}