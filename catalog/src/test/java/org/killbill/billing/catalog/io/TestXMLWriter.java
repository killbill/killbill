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

package org.killbill.billing.catalog.io;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.Charset;

import org.killbill.billing.catalog.CatalogTestSuiteNoDB;
import org.killbill.billing.catalog.DefaultDuration;
import org.killbill.billing.catalog.DefaultFixed;
import org.killbill.billing.catalog.DefaultInternationalPrice;
import org.killbill.billing.catalog.DefaultMutableStaticCatalog;
import org.killbill.billing.catalog.DefaultPlan;
import org.killbill.billing.catalog.DefaultPlanPhase;
import org.killbill.billing.catalog.DefaultPrice;
import org.killbill.billing.catalog.DefaultPriceListSet;
import org.killbill.billing.catalog.DefaultProduct;
import org.killbill.billing.catalog.DefaultRecurring;
import org.killbill.billing.catalog.DefaultVersionedCatalog;
import org.killbill.billing.catalog.StandaloneCatalog;
import org.killbill.billing.catalog.api.BillingMode;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.MutableStaticCatalog;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.catalog.api.TimeUnit;
import org.killbill.billing.catalog.api.VersionedCatalog;
import org.killbill.xmlloader.XMLLoader;
import org.killbill.xmlloader.XMLWriter;
import org.testng.annotations.Test;

import com.google.common.io.Resources;

import static org.testng.Assert.assertEquals;

public class TestXMLWriter extends CatalogTestSuiteNoDB {

    // Verifies we can generate the XML associated with a VersionedCatalog
    @Test(groups = "fast")
    public void testVersionedCatalog() throws Exception {
        final StandaloneCatalog catalog = XMLLoader.getObjectFromString(Resources.getResource("SpyCarAdvanced.xml").toExternalForm(), StandaloneCatalog.class);
        final DefaultVersionedCatalog versionedCatalog = new DefaultVersionedCatalog(clock);
        versionedCatalog.add(catalog);
        final String newCatalogStr = XMLWriter.writeXML(versionedCatalog, DefaultVersionedCatalog.class);
        //System.err.println(newCatalogStr);
    }

    // Verify we can marshall/unmarshall a (fairly complex catalog) catalog and get back the same result (Required to support catalog update)
    @Test(groups = "fast")
    public void testMarshallUnmarshall() throws Exception {
        final StandaloneCatalog catalog = XMLLoader.getObjectFromString(Resources.getResource("SpyCarAdvanced.xml").toExternalForm(), StandaloneCatalog.class);
        final String oldCatalogStr = XMLWriter.writeXML(catalog, StandaloneCatalog.class);
        //System.err.println(oldCatalogStr);

        final StandaloneCatalog oldCatalog = XMLLoader.getObjectFromStream(new URI("dummy"), new ByteArrayInputStream(oldCatalogStr.getBytes(Charset.forName("UTF-8"))), StandaloneCatalog.class);
        final String oldCatalogStr2 = XMLWriter.writeXML(oldCatalog, StandaloneCatalog.class);
        assertEquals(oldCatalogStr2, oldCatalogStr);
    }

    @Test(groups = "fast")
    public void testAddPlan() throws Exception {
        final StandaloneCatalog catalog = XMLLoader.getObjectFromString(Resources.getResource("SpyCarBasic.xml").toExternalForm(), StandaloneCatalog.class);

        final MutableStaticCatalog mutableCatalog = new DefaultMutableStaticCatalog(catalog);

        final DefaultProduct newProduct = new DefaultProduct();
        newProduct.setName("Dynamic");
        newProduct.setCatagory(ProductCategory.BASE);
        newProduct.initialize((StandaloneCatalog) mutableCatalog, new URI("dummy"));

        mutableCatalog.addProduct(newProduct);

        final DefaultPlanPhase trialPhase = new DefaultPlanPhase();
        trialPhase.setPhaseType(PhaseType.TRIAL);
        trialPhase.setDuration(new DefaultDuration().setUnit(TimeUnit.DAYS).setNumber(14));
        trialPhase.setFixed(new DefaultFixed().setFixedPrice(new DefaultInternationalPrice().setPrices(new DefaultPrice[]{new DefaultPrice().setCurrency(Currency.USD).setValue(BigDecimal.ZERO)})));

        final DefaultPlanPhase evergreenPhase = new DefaultPlanPhase();
        evergreenPhase.setPhaseType(PhaseType.EVERGREEN);
        evergreenPhase.setDuration(new DefaultDuration().setUnit(TimeUnit.MONTHS).setNumber(1));
        evergreenPhase.setRecurring(new DefaultRecurring().setBillingPeriod(BillingPeriod.MONTHLY).setRecurringPrice(new DefaultInternationalPrice().setPrices(new DefaultPrice[]{new DefaultPrice().setCurrency(Currency.USD).setValue(BigDecimal.TEN)})));

        final DefaultPlan newPlan = new DefaultPlan();
        newPlan.setName("dynamic-monthly");
        newPlan.setPriceListName(DefaultPriceListSet.DEFAULT_PRICELIST_NAME);
        newPlan.setProduct(newProduct);
        newPlan.setInitialPhases(new DefaultPlanPhase[]{trialPhase});
        newPlan.setFinalPhase(evergreenPhase);
        newPlan.setRecurringBillingMode(BillingMode.IN_ADVANCE);
        // TODO Ordering breaks
        mutableCatalog.addPlan(newPlan);
        newPlan.initialize((StandaloneCatalog) mutableCatalog, new URI("dummy"));

        final String newCatalogStr = XMLWriter.writeXML((StandaloneCatalog) mutableCatalog, StandaloneCatalog.class);
        final StandaloneCatalog newCatalog = XMLLoader.getObjectFromStream(new URI("dummy"), new ByteArrayInputStream(newCatalogStr.getBytes(Charset.forName("UTF-8"))), StandaloneCatalog.class);
        assertEquals(newCatalog.getCurrentPlans().size(), catalog.getCurrentPlans().size() + 1);

        final Plan plan = newCatalog.findCurrentPlan("dynamic-monthly");
        assertEquals(plan.getName(), "dynamic-monthly");
        assertEquals(plan.getPriceListName(), DefaultPriceListSet.DEFAULT_PRICELIST_NAME);
        assertEquals(plan.getProduct().getName(), "Dynamic");
        assertEquals(plan.getProduct().getCategory(), ProductCategory.BASE);
        assertEquals(plan.getInitialPhases().length, 1);
        assertEquals(plan.getInitialPhases()[0].getName(), "dynamic-monthly-trial");
        assertEquals(plan.getInitialPhases()[0].getPhaseType(), PhaseType.TRIAL);
        assertEquals(plan.getInitialPhases()[0].getFixed().getPrice().getPrices().length, 1);
        assertEquals(plan.getInitialPhases()[0].getFixed().getPrice().getPrices()[0].getCurrency(), Currency.USD);
        assertEquals(plan.getInitialPhases()[0].getFixed().getPrice().getPrices()[0].getValue(), BigDecimal.ZERO);

        assertEquals(plan.getFinalPhase().getName(), "dynamic-monthly-evergreen");
        assertEquals(plan.getFinalPhase().getPhaseType(), PhaseType.EVERGREEN);
        assertEquals(plan.getFinalPhase().getRecurring().getBillingPeriod(), BillingPeriod.MONTHLY);
        assertEquals(plan.getFinalPhase().getRecurring().getRecurringPrice().getPrices().length, 1);
        assertEquals(plan.getFinalPhase().getRecurring().getRecurringPrice().getPrices()[0].getCurrency(), Currency.USD);
        assertEquals(plan.getFinalPhase().getRecurring().getRecurringPrice().getPrices()[0].getValue(), BigDecimal.TEN);
    }

}
