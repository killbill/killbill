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

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.Charset;

import org.joda.time.DateTime;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.catalog.api.BillingMode;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.MutableStaticCatalog;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.PriceList;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.catalog.api.SimplePlanDescriptor;
import org.killbill.billing.catalog.api.StaticCatalog;
import org.killbill.billing.catalog.api.TimeUnit;
import org.killbill.billing.catalog.api.user.DefaultSimplePlanDescriptor;
import org.killbill.xmlloader.XMLLoader;
import org.killbill.xmlloader.XMLWriter;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.fail;

public class TestCatalogUpdater extends CatalogTestSuiteNoDB {

    @Test(groups = "fast")
    public void testEmptyDefaultCatalog() throws Exception {

        final DateTime now = clock.getUTCNow();

        final CatalogUpdater catalogUpdater = new CatalogUpdater(now, null);
        final String catalogXML = catalogUpdater.getCatalogXML();
        final StandaloneCatalog catalog = XMLLoader.getObjectFromStream(new URI("dummy"), new ByteArrayInputStream(catalogXML.getBytes(Charset.forName("UTF-8"))), StandaloneCatalog.class);
        assertEquals(catalog.getCurrentPlans().size(), 0);
    }

    @Test(groups = "fast", description = "https://github.com/killbill/killbill/issues/842")
    public void testCreateAmbiguousPlan() throws CatalogApiException {
        final DateTime now = clock.getUTCNow();
        final SimplePlanDescriptor desc = new DefaultSimplePlanDescriptor("foo-monthly-12345", "Foo", ProductCategory.BASE, Currency.EUR, BigDecimal.TEN, BillingPeriod.MONTHLY, 0, TimeUnit.UNLIMITED, ImmutableList.<String>of());

        final CatalogUpdater catalogUpdater = new CatalogUpdater(now, desc.getCurrency());
        catalogUpdater.addSimplePlanDescriptor(desc);
        final StandaloneCatalog catalog = catalogUpdater.getCatalog();

        assertEquals(catalog.getCurrentPlans().size(), 1);

        final StaticCatalog standaloneCatalogWithPriceOverride = new StandaloneCatalogWithPriceOverride(catalog,
                                                                                                        priceOverride,
                                                                                                        internalCallContext.getTenantRecordId(),
                                                                                                        internalCallContextFactory);

        final Plan plan = catalog.findCurrentPlan("foo-monthly-12345");
        assertEquals(plan.getName(), "foo-monthly-12345");

        // Verify PriceOverride logic
        final Plan plan2 = standaloneCatalogWithPriceOverride.findCurrentPlan("foo-monthly-12345");
        assertEquals(plan2.getName(), "foo-monthly-12345");

        final PlanPhase planPhase = catalog.findCurrentPhase("foo-monthly-12345-evergreen");
        assertEquals(planPhase.getName(), "foo-monthly-12345-evergreen");

        // Verify PriceOverride logic
        final PlanPhase phase2 = standaloneCatalogWithPriceOverride.findCurrentPhase("foo-monthly-12345-evergreen");
        assertEquals(phase2.getName(), "foo-monthly-12345-evergreen");
    }

    @Test(groups = "fast")
    public void testAddNoTrialPlanOnFirstCatalog() throws CatalogApiException {

        final DateTime now = clock.getUTCNow();
        final SimplePlanDescriptor desc = new DefaultSimplePlanDescriptor("foo-monthly", "Foo", ProductCategory.BASE, Currency.EUR, BigDecimal.TEN, BillingPeriod.MONTHLY, 0, TimeUnit.UNLIMITED, ImmutableList.<String>of());

        final CatalogUpdater catalogUpdater = new CatalogUpdater(now, desc.getCurrency());

        catalogUpdater.addSimplePlanDescriptor(desc);

        final StandaloneCatalog catalog = catalogUpdater.getCatalog();

        assertEquals(catalog.getCurrentProducts().size(), 1);

        final Product product = catalog.getCurrentProducts().iterator().next();
        assertEquals(product.getName(), "Foo");
        assertEquals(product.getCategory(), ProductCategory.BASE);

        assertEquals(catalog.getCurrentPlans().size(), 1);

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
        assertEquals(priceList.getPlans().size(), 1);
        assertEquals(priceList.getPlans().iterator().next().getName(), "foo-monthly");
    }

    @Test(groups = "fast")
    public void testAddTrialPlanOnFirstCatalog() throws CatalogApiException {

        final DateTime now = clock.getUTCNow();
        final SimplePlanDescriptor desc = new DefaultSimplePlanDescriptor("foo-monthly", "Foo", ProductCategory.BASE, Currency.EUR, BigDecimal.TEN, BillingPeriod.MONTHLY, 14, TimeUnit.DAYS, ImmutableList.<String>of());

        final CatalogUpdater catalogUpdater = new CatalogUpdater(now, desc.getCurrency());

        catalogUpdater.addSimplePlanDescriptor(desc);

        final StandaloneCatalog catalog = catalogUpdater.getCatalog();

        assertEquals(catalog.getCurrentProducts().size(), 1);

        final Product product = catalog.getCurrentProducts().iterator().next();
        assertEquals(product.getName(), "Foo");
        assertEquals(product.getCategory(), ProductCategory.BASE);

        assertEquals(catalog.getCurrentPlans().size(), 1);

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
        assertEquals(priceList.getPlans().size(), 1);
        assertEquals(priceList.getPlans().iterator().next().getName(), "foo-monthly");
    }

    @Test(groups = "fast")
    public void testAddPlanOnExistingCatalog() throws Exception {

        final StandaloneCatalog originalCatalog = XMLLoader.getObjectFromString(Resources.getResource("SpyCarBasic.xml").toExternalForm(), StandaloneCatalog.class);
        assertEquals(originalCatalog.getPriceLists().getAllPriceLists().size(), 1);
        assertEquals(originalCatalog.getPriceLists().getAllPriceLists().get(0).getName(), new PriceListDefault().getName());
        assertEquals(originalCatalog.getPriceLists().getAllPriceLists().get(0).getPlans().size(), 3);

        final CatalogUpdater catalogUpdater = new CatalogUpdater(originalCatalog);

        final SimplePlanDescriptor desc = new DefaultSimplePlanDescriptor("standard-annual", "Standard", ProductCategory.BASE, Currency.USD, BigDecimal.TEN, BillingPeriod.MONTHLY, 0, TimeUnit.UNLIMITED, ImmutableList.<String>of());
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
        assertEquals(priceList.getPlans().size(), 4);
    }

    @Test(groups = "fast")
    public void testAddExistingPlanWithNewCurrency() throws Exception {
        final StandaloneCatalog originalCatalog = XMLLoader.getObjectFromString(Resources.getResource("SpyCarBasic.xml").toExternalForm(), StandaloneCatalog.class);
        assertEquals(originalCatalog.getPriceLists().getAllPriceLists().size(), 1);
        assertEquals(originalCatalog.getPriceLists().getAllPriceLists().get(0).getName(), new PriceListDefault().getName());
        assertEquals(originalCatalog.getPriceLists().getAllPriceLists().get(0).getPlans().size(), 3);

        final CatalogUpdater catalogUpdater = new CatalogUpdater(originalCatalog);

        final SimplePlanDescriptor desc = new DefaultSimplePlanDescriptor("standard-monthly", "Standard", ProductCategory.BASE, Currency.EUR, BigDecimal.TEN, BillingPeriod.MONTHLY, 30, TimeUnit.DAYS, ImmutableList.<String>of());
        catalogUpdater.addSimplePlanDescriptor(desc);

        final StandaloneCatalog catalog = catalogUpdater.getCatalog();

        final Plan plan = catalog.findCurrentPlan("standard-monthly");
        assertEquals(plan.getName(), "standard-monthly");

        assertEquals(plan.getInitialPhases().length, 1);
        assertEquals(plan.getInitialPhases()[0].getPhaseType(), PhaseType.TRIAL);
        assertEquals(plan.getInitialPhases()[0].getFixed().getPrice().getPrices().length, 0);
        assertEquals(plan.getInitialPhases()[0].getFixed().getPrice().getPrice(Currency.EUR), BigDecimal.ZERO);
        assertEquals(plan.getInitialPhases()[0].getName(), "standard-monthly-trial");

        assertEquals(plan.getFinalPhase().getPhaseType(), PhaseType.EVERGREEN);
        assertNull(plan.getFinalPhase().getFixed());
        assertEquals(plan.getFinalPhase().getName(), "standard-monthly-evergreen");
        assertEquals(plan.getFinalPhase().getRecurring().getBillingPeriod(), BillingPeriod.MONTHLY);
        assertEquals(plan.getFinalPhase().getRecurring().getRecurringPrice().getPrices().length, 3);
        assertEquals(plan.getFinalPhase().getRecurring().getRecurringPrice().getPrice(Currency.EUR), BigDecimal.TEN);
    }

    @Test(groups = "fast")
    public void testInvalidPlanDescriptors() throws Exception {
        final StandaloneCatalog originalCatalog = enhanceOriginalCatalogForInvalidTestCases("SpyCarBasic.xml");
        assertEquals(originalCatalog.getPriceLists().getAllPriceLists().size(), 1);
        assertEquals(originalCatalog.getPriceLists().getAllPriceLists().get(0).getName(), new PriceListDefault().getName());
        assertEquals(originalCatalog.getPriceLists().getAllPriceLists().get(0).getPlans().size(), 5);

        CatalogUpdater catalogUpdater = new CatalogUpdater(originalCatalog);

        // Existing Plan has a 30 days trial => try with no TRIAL
        SimplePlanDescriptor desc = new DefaultSimplePlanDescriptor("standard-monthly", "Standard", ProductCategory.BASE, Currency.EUR, BigDecimal.TEN, BillingPeriod.MONTHLY, 0, TimeUnit.DAYS, ImmutableList.<String>of());
        addBadSimplePlanDescriptor(catalogUpdater, desc);

        // Existing Plan has a 30 days trial => try different trial length
        desc = new DefaultSimplePlanDescriptor("standard-monthly", "Standard", ProductCategory.BASE, Currency.EUR, BigDecimal.TEN, BillingPeriod.MONTHLY, 14, TimeUnit.DAYS, ImmutableList.<String>of());
        addBadSimplePlanDescriptor(catalogUpdater, desc);

        // Existing Plan has a 30 days trial => try different trial unit
        desc = new DefaultSimplePlanDescriptor("standard-monthly", "Standard", ProductCategory.BASE, Currency.EUR, BigDecimal.TEN, BillingPeriod.MONTHLY, 30, TimeUnit.MONTHS, ImmutableList.<String>of());
        addBadSimplePlanDescriptor(catalogUpdater, desc);

        // Existing Plan has a MONTHLY recurring => try with ANNUAL BillingPeriod
        desc = new DefaultSimplePlanDescriptor("standard-monthly", "Standard", ProductCategory.BASE, Currency.EUR, BigDecimal.TEN, BillingPeriod.ANNUAL, 30, TimeUnit.DAYS, ImmutableList.<String>of());
        addBadSimplePlanDescriptor(catalogUpdater, desc);

        // Existing Plan has a discount phase
        desc = new DefaultSimplePlanDescriptor("dynamic-monthly", "Dynamic", ProductCategory.BASE, Currency.EUR, BigDecimal.TEN, BillingPeriod.MONTHLY, 30, TimeUnit.MONTHS, ImmutableList.<String>of());
        addBadSimplePlanDescriptor(catalogUpdater, desc);

        // Existing Plan has final fixedterm phase
        desc = new DefaultSimplePlanDescriptor("superdynamic-fixedterm", "SuperDynamic", ProductCategory.BASE, Currency.EUR, BigDecimal.TEN, BillingPeriod.MONTHLY, 30, TimeUnit.DAYS, ImmutableList.<String>of());
        addBadSimplePlanDescriptor(catalogUpdater, desc);

        // Existing Plan a different recurring price ($100)
        desc = new DefaultSimplePlanDescriptor("standard-monthly", "Standard", ProductCategory.BASE, Currency.USD, BigDecimal.TEN, BillingPeriod.MONTHLY, 30, TimeUnit.DAYS, ImmutableList.<String>of());
        addBadSimplePlanDescriptor(catalogUpdater, desc);
    }

    @Test(groups = "fast")
    public void testPlanWithNonFinalFixedTermPhase() throws Exception {

        final StandaloneCatalog catalog = XMLLoader.getObjectFromString(Resources.getResource("SpyCarBasic.xml").toExternalForm(), StandaloneCatalog.class);

        final MutableStaticCatalog mutableCatalog = new DefaultMutableStaticCatalog(catalog);

        final DefaultProduct newProduct = new DefaultProduct();
        newProduct.setName("Something");
        newProduct.setCatagory(ProductCategory.BASE);
        newProduct.initialize((StandaloneCatalog) mutableCatalog, null);
        mutableCatalog.addProduct(newProduct);

        final DefaultPlanPhase trialPhase = new DefaultPlanPhase();
        trialPhase.setPhaseType(PhaseType.TRIAL);
        trialPhase.setDuration(new DefaultDuration().setUnit(TimeUnit.DAYS).setNumber(14));
        trialPhase.setFixed(new DefaultFixed().setFixedPrice(new DefaultInternationalPrice().setPrices(new DefaultPrice[]{new DefaultPrice().setCurrency(Currency.USD).setValue(BigDecimal.ZERO)})));

        // Add a Plan with a FIXEDTERM phase
        final DefaultPlanPhase fixedTermPhase = new DefaultPlanPhase();
        fixedTermPhase.setPhaseType(PhaseType.FIXEDTERM);
        fixedTermPhase.setDuration(new DefaultDuration().setUnit(TimeUnit.MONTHS).setNumber(3));
        fixedTermPhase.setRecurring(new DefaultRecurring().setBillingPeriod(BillingPeriod.MONTHLY).setRecurringPrice(new DefaultInternationalPrice().setPrices(new DefaultPrice[]{new DefaultPrice().setCurrency(Currency.USD).setValue(BigDecimal.TEN)})));

        final DefaultPlanPhase evergreenPhase = new DefaultPlanPhase();
        evergreenPhase.setPhaseType(PhaseType.EVERGREEN);
        evergreenPhase.setDuration(new DefaultDuration().setUnit(TimeUnit.MONTHS).setNumber(1));
        evergreenPhase.setRecurring(new DefaultRecurring().setBillingPeriod(BillingPeriod.MONTHLY).setRecurringPrice(new DefaultInternationalPrice().setPrices(new DefaultPrice[]{new DefaultPrice().setCurrency(Currency.USD).setValue(BigDecimal.TEN)})));

        final DefaultPlan newPlan = new DefaultPlan();
        newPlan.setName("something-with-fixed-term");
        newPlan.setPriceListName(DefaultPriceListSet.DEFAULT_PRICELIST_NAME);
        newPlan.setProduct(newProduct);
        newPlan.setInitialPhases(new DefaultPlanPhase[]{trialPhase, fixedTermPhase});
        newPlan.setFinalPhase(fixedTermPhase);
        mutableCatalog.addPlan(newPlan);
        newPlan.initialize((StandaloneCatalog) mutableCatalog, new URI("dummy"));

        final String newCatalogStr = XMLWriter.writeXML((StandaloneCatalog) mutableCatalog, StandaloneCatalog.class);
        final StandaloneCatalog newCatalog = XMLLoader.getObjectFromStream(new URI("dummy"), new ByteArrayInputStream(newCatalogStr.getBytes(Charset.forName("UTF-8"))), StandaloneCatalog.class);

        final DefaultPlan targetPlan = newCatalog.findCurrentPlan("something-with-fixed-term");
        Assert.assertEquals(targetPlan.getInitialPhases().length, 2);
        Assert.assertEquals(targetPlan.getInitialPhases()[1].getPhaseType(), PhaseType.FIXEDTERM);

    }

    @Test(groups = "fast")
    public void testVerifyXML() throws Exception {

        final StandaloneCatalog originalCatalog = XMLLoader.getObjectFromString(Resources.getResource("SpyCarBasic.xml").toExternalForm(), StandaloneCatalog.class);
        assertEquals(originalCatalog.getPriceLists().getAllPriceLists().size(), 1);
        assertEquals(originalCatalog.getPriceLists().getAllPriceLists().get(0).getName(), new PriceListDefault().getName());
        assertEquals(originalCatalog.getPriceLists().getAllPriceLists().get(0).getPlans().size(), 3);

        final CatalogUpdater catalogUpdater = new CatalogUpdater(originalCatalog);

        final SimplePlanDescriptor desc = new DefaultSimplePlanDescriptor("dynamic-annual", "Dynamic", ProductCategory.BASE, Currency.USD, BigDecimal.TEN, BillingPeriod.MONTHLY, 14, TimeUnit.DAYS, ImmutableList.<String>of());
        catalogUpdater.addSimplePlanDescriptor(desc);

        final String expectedXML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                                   "<catalog>\n" +
                                   "    <effectiveDate>2013-02-08T00:00:00Z</effectiveDate>\n" +
                                   "    <catalogName>SpyCarBasic</catalogName>\n" +
                                   "    <recurringBillingMode>IN_ADVANCE</recurringBillingMode>\n" +
                                   "    <currencies>\n" +
                                   "        <currency>USD</currency>\n" +
                                   "        <currency>GBP</currency>\n" +
                                   "    </currencies>\n" +
                                   "    <units/>\n" +
                                   "    <products>\n" +
                                   "        <product name=\"Dynamic\" prettyName=\"Dynamic\">\n" +
                                   "            <category>BASE</category>\n" +
                                   "            <included/>\n" +
                                   "            <available/>\n" +
                                   "            <limits/>\n" +
                                   "        </product>\n" +
                                   "        <product name=\"Sports\" prettyName=\"Sports\">\n" +
                                   "            <category>BASE</category>\n" +
                                   "            <included/>\n" +
                                   "            <available/>\n" +
                                   "            <limits/>\n" +
                                   "        </product>\n" +
                                   "        <product name=\"Standard\" prettyName=\"Standard\">\n" +
                                   "            <category>BASE</category>\n" +
                                   "            <included/>\n" +
                                   "            <available/>\n" +
                                   "            <limits/>\n" +
                                   "        </product>\n" +
                                   "        <product name=\"Super\" prettyName=\"Super\">\n" +
                                   "            <category>BASE</category>\n" +
                                   "            <included/>\n" +
                                   "            <available/>\n" +
                                   "            <limits/>\n" +
                                   "        </product>\n" +
                                   "    </products>\n" +
                                   "    <rules>\n" +
                                   "        <changePolicy>\n" +
                                   "            <changePolicyCase>\n" +
                                   "                <policy>IMMEDIATE</policy>\n" +
                                   "            </changePolicyCase>\n" +
                                   "        </changePolicy>\n" +
                                   "        <changeAlignment>\n" +
                                   "            <changeAlignmentCase>\n" +
                                   "                <alignment>START_OF_BUNDLE</alignment>\n" +
                                   "            </changeAlignmentCase>\n" +
                                   "        </changeAlignment>\n" +
                                   "        <cancelPolicy>\n" +
                                   "            <cancelPolicyCase>\n" +
                                   "                <policy>IMMEDIATE</policy>\n" +
                                   "            </cancelPolicyCase>\n" +
                                   "        </cancelPolicy>\n" +
                                   "        <createAlignment>\n" +
                                   "            <createAlignmentCase>\n" +
                                   "                <alignment>START_OF_BUNDLE</alignment>\n" +
                                   "            </createAlignmentCase>\n" +
                                   "        </createAlignment>\n" +
                                   "        <billingAlignment>\n" +
                                   "            <billingAlignmentCase>\n" +
                                   "                <alignment>ACCOUNT</alignment>\n" +
                                   "            </billingAlignmentCase>\n" +
                                   "        </billingAlignment>\n" +
                                   "        <priceList>\n" +
                                   "            <priceListCase>\n" +
                                   "                <toPriceList>DEFAULT</toPriceList>\n" +
                                   "            </priceListCase>\n" +
                                   "        </priceList>\n" +
                                   "    </rules>\n" +
                                   "    <plans>\n" +
                                   "        <plan name=\"dynamic-annual\" prettyName=\"dynamic-annual\">\n" +
                                   "            <product>Dynamic</product>\n" +
                                   "            <recurringBillingMode>IN_ADVANCE</recurringBillingMode>\n" +
                                   "            <initialPhases>\n" +
                                   "                <phase type=\"TRIAL\">\n" +
                                   "                    <duration>\n" +
                                   "                        <unit>DAYS</unit>\n" +
                                   "                        <number>14</number>\n" +
                                   "                    </duration>\n" +
                                   "                    <fixed type=\"ONE_TIME\">\n" +
                                   "                        <fixedPrice>\n" +
                                   "                            <price>\n" +
                                   "<currency>USD</currency>\n" +
                                   "<value>0</value>\n" +
                                   "                            </price>\n" +
                                   "                        </fixedPrice>\n" +
                                   "                    </fixed>\n" +
                                   "                    <usages/>\n" +
                                   "                </phase>\n" +
                                   "            </initialPhases>\n" +
                                   "            <finalPhase type=\"EVERGREEN\">\n" +
                                   "                <duration>\n" +
                                   "                    <unit>UNLIMITED</unit>\n" +
                                   "                    <number>-1</number>\n" +
                                   "                </duration>\n" +
                                   "                <recurring>\n" +
                                   "                    <billingPeriod>MONTHLY</billingPeriod>\n" +
                                   "                    <recurringPrice>\n" +
                                   "                        <price>\n" +
                                   "                            <currency>USD</currency>\n" +
                                   "                            <value>10</value>\n" +
                                   "                        </price>\n" +
                                   "                    </recurringPrice>\n" +
                                   "                </recurring>\n" +
                                   "                <usages/>\n" +
                                   "            </finalPhase>\n" +
                                   "            <plansAllowedInBundle>-1</plansAllowedInBundle>\n" +
                                   "        </plan>\n" +
                                   "        <plan name=\"sports-monthly\" prettyName=\"sports-monthly\">\n" +
                                   "            <product>Sports</product>\n" +
                                   "            <recurringBillingMode>IN_ADVANCE</recurringBillingMode>\n" +
                                   "            <initialPhases>\n" +
                                   "                <phase type=\"TRIAL\">\n" +
                                   "                    <duration>\n" +
                                   "                        <unit>DAYS</unit>\n" +
                                   "                        <number>30</number>\n" +
                                   "                    </duration>\n" +
                                   "                    <fixed type=\"ONE_TIME\">\n" +
                                   "                        <fixedPrice/>\n" +
                                   "                    </fixed>\n" +
                                   "                    <usages/>\n" +
                                   "                </phase>\n" +
                                   "            </initialPhases>\n" +
                                   "            <finalPhase type=\"EVERGREEN\">\n" +
                                   "                <duration>\n" +
                                   "                    <unit>UNLIMITED</unit>\n" +
                                   "                    <number>-1</number>\n" +
                                   "                </duration>\n" +
                                   "                <recurring>\n" +
                                   "                    <billingPeriod>MONTHLY</billingPeriod>\n" +
                                   "                    <recurringPrice>\n" +
                                   "                        <price>\n" +
                                   "                            <currency>GBP</currency>\n" +
                                   "                            <value>375.00</value>\n" +
                                   "                        </price>\n" +
                                   "                        <price>\n" +
                                   "                            <currency>USD</currency>\n" +
                                   "                            <value>500.00</value>\n" +
                                   "                        </price>\n" +
                                   "                    </recurringPrice>\n" +
                                   "                </recurring>\n" +
                                   "                <usages/>\n" +
                                   "            </finalPhase>\n" +
                                   "            <plansAllowedInBundle>-1</plansAllowedInBundle>\n" +
                                   "        </plan>\n" +
                                   "        <plan name=\"standard-monthly\" prettyName=\"standard-monthly\">\n" +
                                   "            <product>Standard</product>\n" +
                                   "            <recurringBillingMode>IN_ADVANCE</recurringBillingMode>\n" +
                                   "            <initialPhases>\n" +
                                   "                <phase type=\"TRIAL\">\n" +
                                   "                    <duration>\n" +
                                   "                        <unit>DAYS</unit>\n" +
                                   "                        <number>30</number>\n" +
                                   "                    </duration>\n" +
                                   "                    <fixed type=\"ONE_TIME\">\n" +
                                   "                        <fixedPrice/>\n" +
                                   "                    </fixed>\n" +
                                   "                    <usages/>\n" +
                                   "                </phase>\n" +
                                   "            </initialPhases>\n" +
                                   "            <finalPhase type=\"EVERGREEN\">\n" +
                                   "                <duration>\n" +
                                   "                    <unit>UNLIMITED</unit>\n" +
                                   "                    <number>-1</number>\n" +
                                   "                </duration>\n" +
                                   "                <recurring>\n" +
                                   "                    <billingPeriod>MONTHLY</billingPeriod>\n" +
                                   "                    <recurringPrice>\n" +
                                   "                        <price>\n" +
                                   "                            <currency>GBP</currency>\n" +
                                   "                            <value>75.00</value>\n" +
                                   "                        </price>\n" +
                                   "                        <price>\n" +
                                   "                            <currency>USD</currency>\n" +
                                   "                            <value>100.00</value>\n" +
                                   "                        </price>\n" +
                                   "                    </recurringPrice>\n" +
                                   "                </recurring>\n" +
                                   "                <usages/>\n" +
                                   "            </finalPhase>\n" +
                                   "            <plansAllowedInBundle>-1</plansAllowedInBundle>\n" +
                                   "        </plan>\n" +
                                   "        <plan name=\"super-monthly\" prettyName=\"super-monthly\">\n" +
                                   "            <product>Super</product>\n" +
                                   "            <recurringBillingMode>IN_ADVANCE</recurringBillingMode>\n" +
                                   "            <initialPhases>\n" +
                                   "                <phase type=\"TRIAL\">\n" +
                                   "                    <duration>\n" +
                                   "                        <unit>DAYS</unit>\n" +
                                   "                        <number>30</number>\n" +
                                   "                    </duration>\n" +
                                   "                    <fixed type=\"ONE_TIME\">\n" +
                                   "                        <fixedPrice/>\n" +
                                   "                    </fixed>\n" +
                                   "                    <usages/>\n" +
                                   "                </phase>\n" +
                                   "            </initialPhases>\n" +
                                   "            <finalPhase type=\"EVERGREEN\">\n" +
                                   "                <duration>\n" +
                                   "                    <unit>UNLIMITED</unit>\n" +
                                   "                    <number>-1</number>\n" +
                                   "                </duration>\n" +
                                   "                <recurring>\n" +
                                   "                    <billingPeriod>MONTHLY</billingPeriod>\n" +
                                   "                    <recurringPrice>\n" +
                                   "                        <price>\n" +
                                   "                            <currency>GBP</currency>\n" +
                                   "                            <value>750.00</value>\n" +
                                   "                        </price>\n" +
                                   "                        <price>\n" +
                                   "                            <currency>USD</currency>\n" +
                                   "                            <value>1000.00</value>\n" +
                                   "                        </price>\n" +
                                   "                    </recurringPrice>\n" +
                                   "                </recurring>\n" +
                                   "                <usages/>\n" +
                                   "            </finalPhase>\n" +
                                   "            <plansAllowedInBundle>-1</plansAllowedInBundle>\n" +
                                   "        </plan>\n" +
                                   "    </plans>\n" +
                                   "    <priceLists>\n" +
                                   "        <defaultPriceList name=\"DEFAULT\">\n" +
                                   "            <plans>\n" +
                                   "                <plan>dynamic-annual</plan>\n" +
                                   "                <plan>sports-monthly</plan>\n" +
                                   "                <plan>standard-monthly</plan>\n" +
                                   "                <plan>super-monthly</plan>\n" +
                                   "            </plans>\n" +
                                   "        </defaultPriceList>\n" +
                                   "    </priceLists>\n" +
                                   "</catalog>\n";

        assertEquals(catalogUpdater.getCatalogXML(), expectedXML);
        System.err.println(catalogUpdater.getCatalogXML());
    }

    private StandaloneCatalog enhanceOriginalCatalogForInvalidTestCases(final String catalogName) throws Exception {

        final StandaloneCatalog catalog = XMLLoader.getObjectFromString(Resources.getResource(catalogName).toExternalForm(), StandaloneCatalog.class);

        final MutableStaticCatalog mutableCatalog = new DefaultMutableStaticCatalog(catalog);

        final DefaultProduct newProduct1 = new DefaultProduct();
        newProduct1.setName("Dynamic");
        newProduct1.setCatagory(ProductCategory.BASE);
        newProduct1.initialize((StandaloneCatalog) mutableCatalog, null);
        mutableCatalog.addProduct(newProduct1);

        final DefaultPlanPhase discountPhase1 = new DefaultPlanPhase();
        discountPhase1.setPhaseType(PhaseType.DISCOUNT);
        discountPhase1.setDuration(new DefaultDuration().setUnit(TimeUnit.DAYS).setNumber(14));
        discountPhase1.setRecurring(new DefaultRecurring().setBillingPeriod(BillingPeriod.MONTHLY).setRecurringPrice(new DefaultInternationalPrice().setPrices(new DefaultPrice[]{new DefaultPrice().setCurrency(Currency.USD).setValue(BigDecimal.TEN)})));

        final DefaultPlanPhase evergreenPhase1 = new DefaultPlanPhase();
        evergreenPhase1.setPhaseType(PhaseType.EVERGREEN);
        evergreenPhase1.setDuration(new DefaultDuration().setUnit(TimeUnit.MONTHS).setNumber(1));
        evergreenPhase1.setRecurring(new DefaultRecurring().setBillingPeriod(BillingPeriod.MONTHLY).setRecurringPrice(new DefaultInternationalPrice().setPrices(new DefaultPrice[]{new DefaultPrice().setCurrency(Currency.USD).setValue(BigDecimal.TEN)})));

        // Add a Plan with a DISCOUNT phase
        final DefaultPlan newPlan1 = new DefaultPlan();
        newPlan1.setName("dynamic-monthly");
        newPlan1.setPriceListName(DefaultPriceListSet.DEFAULT_PRICELIST_NAME);
        newPlan1.setProduct(newProduct1);
        newPlan1.setInitialPhases(new DefaultPlanPhase[]{discountPhase1});
        newPlan1.setFinalPhase(evergreenPhase1);
        mutableCatalog.addPlan(newPlan1);
        newPlan1.initialize((StandaloneCatalog) mutableCatalog, new URI("dummy"));

        final DefaultProduct newProduct2 = new DefaultProduct();
        newProduct2.setName("SuperDynamic");
        newProduct2.setCatagory(ProductCategory.BASE);
        newProduct2.initialize((StandaloneCatalog) mutableCatalog, null);
        mutableCatalog.addProduct(newProduct2);

        // Add a Plan with a FIXEDTERM phase
        final DefaultPlanPhase fixedterm2 = new DefaultPlanPhase();
        fixedterm2.setPhaseType(PhaseType.FIXEDTERM);
        fixedterm2.setDuration(new DefaultDuration().setUnit(TimeUnit.MONTHS).setNumber(3));
        fixedterm2.setRecurring(new DefaultRecurring().setBillingPeriod(BillingPeriod.MONTHLY).setRecurringPrice(new DefaultInternationalPrice().setPrices(new DefaultPrice[]{new DefaultPrice().setCurrency(Currency.USD).setValue(BigDecimal.TEN)})));

        final DefaultPlan newPlan2 = new DefaultPlan();
        newPlan2.setName("superdynamic-fixedterm");
        newPlan2.setPriceListName(DefaultPriceListSet.DEFAULT_PRICELIST_NAME);
        newPlan2.setProduct(newProduct2);
        newPlan2.setFinalPhase(fixedterm2);
        mutableCatalog.addPlan(newPlan2);
        newPlan2.initialize((StandaloneCatalog) mutableCatalog, new URI("dummy"));

        final String newCatalogStr = XMLWriter.writeXML((StandaloneCatalog) mutableCatalog, StandaloneCatalog.class);
        return XMLLoader.getObjectFromStream(new URI("dummy"), new ByteArrayInputStream(newCatalogStr.getBytes(Charset.forName("UTF-8"))), StandaloneCatalog.class);
    }

    private void addBadSimplePlanDescriptor(final CatalogUpdater catalogUpdater, final SimplePlanDescriptor desc) {
        try {
            catalogUpdater.addSimplePlanDescriptor(desc);
            fail("Should have failed to add invalid desc " + desc);
        } catch (final CatalogApiException e) {
            assertEquals(e.getCode(), ErrorCode.CAT_FAILED_SIMPLE_PLAN_VALIDATION.getCode());
        }
    }
}
