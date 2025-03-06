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

package org.killbill.billing.beatrix.integration;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountData;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.DefaultVersionedCatalog;
import org.killbill.billing.catalog.StandaloneCatalog;
import org.killbill.billing.catalog.StandaloneCatalogWithPriceOverride;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.CatalogUserApi;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.catalog.api.VersionedCatalog;
import org.killbill.billing.catalog.override.PriceOverrideSvc;
import org.killbill.billing.catalog.plugin.TestModelStandalonePluginCatalog;
import org.killbill.billing.catalog.plugin.TestModelVersionedPluginCatalog;
import org.killbill.billing.catalog.plugin.api.CatalogPluginApi;
import org.killbill.billing.catalog.plugin.api.StandalonePluginCatalog;
import org.killbill.billing.catalog.plugin.api.VersionedPluginCatalog;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.osgi.api.OSGIServiceDescriptor;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.commons.utils.io.Resources;
import org.killbill.clock.Clock;
import org.killbill.xmlloader.XMLLoader;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class TestWithCatalogPlugin extends TestIntegrationBase {

    @Inject
    private OSGIServiceRegistration<CatalogPluginApi> pluginRegistry;

    @Inject
    private PriceOverrideSvc priceOverride;

    @Inject
    private InternalCallContextFactory internalCallContextFactory;

    @Inject
    private CatalogUserApi catalogUserApi;

    private TestCatalogPluginApi testCatalogPluginApi;

    @BeforeClass(groups = "slow")
    public void beforeClass() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeClass();

        this.testCatalogPluginApi = new TestCatalogPluginApi(priceOverride, clock, internalCallContext, internalCallContextFactory);
        pluginRegistry.registerService(new OSGIServiceDescriptor() {
            @Override
            public String getPluginSymbolicName() {
                return "TestCatalogPluginApi";
            }

            @Override
            public String getPluginName() {
                return "TestCatalogPluginApi";
            }

            @Override
            public String getRegistrationName() {
                return "TestCatalogPluginApi";
            }
        }, testCatalogPluginApi);
    }

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeMethod();
        testCatalogPluginApi.reset();
    }

    @Test(groups = "slow")
    public void testCreateSubscriptionWithCatalogPlugin() throws Exception {

        testCatalogPluginApi.addCatalogVersion("org/killbill/billing/catalog/WeaponsHire.xml");

        // We take april as it has 30 days (easier to play with BCD)
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(new LocalDate(2012, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        // Create original subscription (Trial PHASE) -> $0 invoice.
        final DefaultEntitlement bpSubscription = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Pistol", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), new LocalDate(2012, 5, 1), InvoiceItemType.FIXED, new BigDecimal("0")));
        subscriptionChecker.checkSubscriptionCreated(bpSubscription.getId(), internalCallContext);

        // Code went to retrieve catalog more than one time
        Assert.assertTrue(testCatalogPluginApi.getNbLatestCatalogVersionApiCalls() > 1);

        // Code only retrieved catalog from plugin once (caching works!)
        Assert.assertEquals(testCatalogPluginApi.getNbVersionedPluginCatalogApiCalls(), 1);
    }


    @Test(groups = "slow")
    public void testWithMultipleVersions() throws Exception {

        testCatalogPluginApi.addCatalogVersion("org/killbill/billing/catalog/versionedCatalog/WeaponsHireSmall-1.xml");

        final VersionedCatalog catalog1 = catalogUserApi.getCatalog("whatever", callContext);
        Assert.assertEquals(testCatalogPluginApi.getNbLatestCatalogVersionApiCalls(), 1);
        Assert.assertEquals(testCatalogPluginApi.getNbVersionedPluginCatalogApiCalls(), 1);
        Assert.assertEquals(catalog1.getVersions().get(0).getEffectiveDate().compareTo(testCatalogPluginApi.getLatestCatalogUpdate().toDate()), 0);

        // Retrieve 3 more times
        catalogUserApi.getCatalog("whatever", callContext);
        catalogUserApi.getCatalog("whatever", callContext);
        catalogUserApi.getCatalog("whatever", callContext);
        Assert.assertEquals(testCatalogPluginApi.getNbLatestCatalogVersionApiCalls(), 4);
        Assert.assertEquals(testCatalogPluginApi.getNbVersionedPluginCatalogApiCalls(), 1);

        testCatalogPluginApi.addCatalogVersion("org/killbill/billing/catalog/versionedCatalog/WeaponsHireSmall-2.xml");

        final VersionedCatalog catalog2 = catalogUserApi.getCatalog("whatever", callContext);
        Assert.assertEquals(testCatalogPluginApi.getNbLatestCatalogVersionApiCalls(), 5);
        Assert.assertEquals(testCatalogPluginApi.getNbVersionedPluginCatalogApiCalls(), 2);
        Assert.assertEquals(catalog2.getVersions().get(1).getEffectiveDate().compareTo(testCatalogPluginApi.getLatestCatalogUpdate().toDate()), 0);

        testCatalogPluginApi.addCatalogVersion("org/killbill/billing/catalog/versionedCatalog/WeaponsHireSmall-3.xml");

        final VersionedCatalog catalog3 = catalogUserApi.getCatalog("whatever", callContext);
        Assert.assertEquals(testCatalogPluginApi.getNbLatestCatalogVersionApiCalls(), 6);
        Assert.assertEquals(testCatalogPluginApi.getNbVersionedPluginCatalogApiCalls(), 3);
        Assert.assertEquals(catalog3.getVersions().get(2).getEffectiveDate().compareTo(testCatalogPluginApi.getLatestCatalogUpdate().toDate()), 0);

        // Retrieve 4 more times
        catalogUserApi.getCatalog("whatever", callContext);
        catalogUserApi.getCatalog("whatever", callContext);
        catalogUserApi.getCatalog("whatever", callContext);
        catalogUserApi.getCatalog("whatever", callContext);
        Assert.assertEquals(testCatalogPluginApi.getNbLatestCatalogVersionApiCalls(), 10);
        Assert.assertEquals(testCatalogPluginApi.getNbVersionedPluginCatalogApiCalls(), 3);

    }

    @Test(groups = "slow")
    public void testPrettyNamesWithCatalogPlugin() throws Exception {

        testCatalogPluginApi.addCatalogVersion("org/killbill/billing/catalog/WeaponsHire.xml");

        // We take april as it has 30 days (easier to play with BCD)
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(new LocalDate(2012, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        // Create subscription
        final DefaultEntitlement bpSubscription = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Pistol", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), new LocalDate(2012, 5, 1), InvoiceItemType.FIXED, new BigDecimal("0")));
        subscriptionChecker.checkSubscriptionCreated(bpSubscription.getId(), internalCallContext);

        //trial phase
        Invoice invoice = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext).get(0);
        Assert.assertEquals(invoice.getInvoiceItems().size(),1);
        InvoiceItem invoiceItem = invoice.getInvoiceItems().get(0);
        Assert.assertNotNull(invoiceItem);
        Assert.assertEquals(invoiceItem.getPrettyProductName(),"Pistol For Hire");
        Assert.assertEquals(invoiceItem.getPrettyPlanName(),"Pistol Monthly Plan");
        Assert.assertEquals(invoiceItem.getPrettyPhaseName(),"Pistol Monthly Plan Trial Phase");
        Assert.assertNull(invoiceItem.getPrettyUsageName());

        //Move out of trial phase
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        invoice = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext).get(1);
        Assert.assertEquals(invoice.getInvoiceItems().size(),1);
        invoiceItem = invoice.getInvoiceItems().get(0);
        Assert.assertNotNull(invoiceItem);
        Assert.assertEquals(invoiceItem.getPrettyProductName(),"Pistol For Hire");
        Assert.assertEquals(invoiceItem.getPrettyPlanName(),"Pistol Monthly Plan");
        Assert.assertEquals(invoiceItem.getPrettyPhaseName(),"Pistol Monthly Plan Evergreen Phase");
        Assert.assertNull(invoiceItem.getPrettyUsageName());

        //record usage and move clock by a month
        recordUsageData(bpSubscription.getBaseEntitlementId(), "t1", "hours", clock.getUTCToday().plusDays(1), new BigDecimal(2), callContext);

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        invoice = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext).get(2);
        Assert.assertEquals(invoice.getInvoiceItems().size(),2);

        //retrieve recurring item
        invoiceItem = invoice.getInvoiceItems().stream().filter(item -> item.getInvoiceItemType() == InvoiceItemType.RECURRING).findFirst().get();
        Assert.assertNotNull(invoiceItem);
        Assert.assertEquals(invoiceItem.getPrettyProductName(),"Pistol For Hire");
        Assert.assertEquals(invoiceItem.getPrettyPlanName(),"Pistol Monthly Plan");
        Assert.assertEquals(invoiceItem.getPrettyPhaseName(),"Pistol Monthly Plan Evergreen Phase");
        Assert.assertNull(invoiceItem.getPrettyUsageName());

        //retrieve usage item
        invoiceItem = invoice.getInvoiceItems().stream().filter(item -> item.getInvoiceItemType() == InvoiceItemType.USAGE).findFirst().get();
        Assert.assertNotNull(invoiceItem);
        Assert.assertEquals(invoiceItem.getPrettyProductName(),"Pistol For Hire");
        Assert.assertEquals(invoiceItem.getPrettyPlanName(),"Pistol Monthly Plan");
        Assert.assertEquals(invoiceItem.getPrettyPhaseName(),"Pistol Monthly Plan Evergreen Phase");
        Assert.assertEquals(invoiceItem.getPrettyUsageName(),"Pistol Monthly Plan Training Usage");
    }


    public static class TestCatalogPluginApi implements CatalogPluginApi {

        private final PriceOverrideSvc priceOverride;
        private final Clock clock;
        private final InternalTenantContext internalTenantContext;
        private final InternalCallContextFactory internalCallContextFactory;

        private DefaultVersionedCatalog versionedCatalog;
        private DateTime latestCatalogUpdate;
        private int nbLatestCatalogVersionApiCalls;
        private int nbVersionedPluginCatalogApiCalls;

        public TestCatalogPluginApi(final PriceOverrideSvc priceOverride, final Clock clock, final InternalTenantContext internalTenantContext, final InternalCallContextFactory internalCallContextFactory) throws Exception {
            this.priceOverride = priceOverride;
            this.clock = clock;
            this.internalTenantContext = internalTenantContext;
            this.internalCallContextFactory = internalCallContextFactory;
            reset();
        }

        @Override
        public DateTime getLatestCatalogVersion(final Iterable<PluginProperty> iterable, final TenantContext tenantContext) {
            nbLatestCatalogVersionApiCalls++;
            return latestCatalogUpdate;
        }

        @Override
        public VersionedPluginCatalog getVersionedPluginCatalog(final Iterable<PluginProperty> properties, final TenantContext tenantContext) {
            nbVersionedPluginCatalogApiCalls++;
            Assert.assertNotNull(versionedCatalog, "test did not initialize plugin catalog");
            return new TestModelVersionedPluginCatalog(versionedCatalog.getCatalogName(), toStandalonePluginCatalogs(versionedCatalog));
        }

        // This actually pulls catalog resources from `catalog` module and not the one from beatrix/src/test/resources//catalogs
        public void addCatalogVersion(final String catalogResource) throws Exception {

            final StandaloneCatalog inputCatalogVersion = XMLLoader.getObjectFromString(Resources.getResource(catalogResource).toExternalForm(), StandaloneCatalog.class);
            final StandaloneCatalogWithPriceOverride inputCatalogVersionWithOverride = new StandaloneCatalogWithPriceOverride(inputCatalogVersion, priceOverride, internalTenantContext.getTenantRecordId(), internalCallContextFactory);

            this.latestCatalogUpdate = new DateTime(inputCatalogVersion.getEffectiveDate());
            if (versionedCatalog == null) {
                versionedCatalog = new DefaultVersionedCatalog();
            }
            versionedCatalog.add(inputCatalogVersionWithOverride);
        }

        public void reset() {
            this.versionedCatalog = null;
            this.latestCatalogUpdate = null;
            this.nbLatestCatalogVersionApiCalls = 0;
            this.nbVersionedPluginCatalogApiCalls = 0;
        }

        public int getNbLatestCatalogVersionApiCalls() {
            return nbLatestCatalogVersionApiCalls;
        }

        public int getNbVersionedPluginCatalogApiCalls() {
            return nbVersionedPluginCatalogApiCalls;
        }

        public DateTime getLatestCatalogUpdate() {
            return latestCatalogUpdate;
        }

        private Iterable<StandalonePluginCatalog> toStandalonePluginCatalogs(final VersionedCatalog versionedCatalog) {
            return versionedCatalog.getVersions()
                    .stream()
                    .map(input -> {
                        final StandaloneCatalog standaloneCatalog = (StandaloneCatalog) input;
                        return new TestModelStandalonePluginCatalog(new DateTime(input.getEffectiveDate()),
                                                                    List.of(standaloneCatalog.getSupportedCurrencies()),
                                                                    List.copyOf(standaloneCatalog.getProducts()),
                                                                    List.copyOf(standaloneCatalog.getPlans()),
                                                                    standaloneCatalog.getPriceLists().getDefaultPricelist(),
                                                                    List.of(standaloneCatalog.getPriceLists().getChildPriceLists()),
                                                                    standaloneCatalog.getPlanRules(),
                                                                    null /* ImmutableList.<Unit>copyOf(input.getCurrentUnits()) */);
                    }).collect(Collectors.toUnmodifiableList());
        }
    }
}
