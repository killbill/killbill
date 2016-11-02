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

package org.killbill.billing.beatrix.integration;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountData;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.StandaloneCatalog;
import org.killbill.billing.catalog.StandaloneCatalogWithPriceOverride;
import org.killbill.billing.catalog.VersionedCatalog;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PriceList;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.catalog.override.PriceOverride;
import org.killbill.billing.catalog.plugin.TestModelStandalonePluginCatalog;
import org.killbill.billing.catalog.plugin.TestModelVersionedPluginCatalog;
import org.killbill.billing.catalog.plugin.api.CatalogPluginApi;
import org.killbill.billing.catalog.plugin.api.StandalonePluginCatalog;
import org.killbill.billing.catalog.plugin.api.VersionedPluginCatalog;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.osgi.api.OSGIServiceDescriptor;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.xmlloader.XMLLoader;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.Resources;

public class TestWithCatalogPlugin extends TestIntegrationBase {

    @Inject
    private OSGIServiceRegistration<CatalogPluginApi> pluginRegistry;

    @Inject
    private PriceOverride priceOverride;

    @Inject
    private InternalCallContextFactory internalCallContextFactory;

    private TestCatalogPluginApi testCatalogPluginApi;

    @BeforeClass(groups = "slow")
    public void beforeClass() throws Exception {
        super.beforeClass();

        this.testCatalogPluginApi = new TestCatalogPluginApi(priceOverride, internalCallContext, internalCallContextFactory);
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

    @Test(groups = "slow")
    public void testCreateSubscriptionWithCatalogPlugin() throws Exception {
        // We take april as it has 30 days (easier to play with BCD)
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(new LocalDate(2012, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        // Create original subscription (Trial PHASE) -> $0 invoice.
        final DefaultEntitlement bpSubscription = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Pistol", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        subscriptionChecker.checkSubscriptionCreated(bpSubscription.getId(), internalCallContext);
    }

    public static class TestCatalogPluginApi implements CatalogPluginApi {

        private final VersionedCatalog versionedCatalog;

        public TestCatalogPluginApi(final PriceOverride priceOverride, final InternalTenantContext internalTenantContext, final InternalCallContextFactory internalCallContextFactory) throws Exception {
            final StandaloneCatalog inputCatalog = XMLLoader.getObjectFromString(Resources.getResource("WeaponsHire.xml").toExternalForm(), StandaloneCatalog.class);
            final List<StandaloneCatalog> versions = new ArrayList<StandaloneCatalog>();
            final StandaloneCatalogWithPriceOverride standaloneCatalogWithPriceOverride = new StandaloneCatalogWithPriceOverride(inputCatalog, priceOverride, internalTenantContext.getTenantRecordId(), internalCallContextFactory);
            versions.add(standaloneCatalogWithPriceOverride);
            versionedCatalog = new VersionedCatalog(getClock());
            versionedCatalog.addAll(versions);
        }

        @Override
        public VersionedPluginCatalog getVersionedPluginCatalog(final Iterable<PluginProperty> properties, final TenantContext tenantContext) {
            return new TestModelVersionedPluginCatalog(versionedCatalog.getCatalogName(), versionedCatalog.getRecurringBillingMode(), toStandalonePluginCatalogs(versionedCatalog.getVersions()));
        }

        private Iterable<StandalonePluginCatalog> toStandalonePluginCatalogs(final List<StandaloneCatalog> input) {
            return Iterables.transform(input, new Function<StandaloneCatalog, StandalonePluginCatalog>() {
                @Override
                public StandalonePluginCatalog apply(final StandaloneCatalog input) {

                    return new TestModelStandalonePluginCatalog(new DateTime(input.getEffectiveDate()),
                                                                ImmutableList.copyOf(input.getCurrentSupportedCurrencies()),
                                                                ImmutableList.<Product>copyOf(input.getCurrentProducts()),
                                                                ImmutableList.<Plan>copyOf(input.getCurrentPlans()),
                                                                input.getPriceLists().getDefaultPricelist(),
                                                                ImmutableList.<PriceList>copyOf(input.getPriceLists().getChildPriceLists()),
                                                                input.getPlanRules(),
                                                                null /* ImmutableList.<Unit>copyOf(input.getCurrentUnits()) */);
                }

                private <I, C extends I> List<I> listOf(@Nullable C[] input) {
                    return (input != null) ? ImmutableList.<I>copyOf(input) : ImmutableList.<I>of();
                }
            });
        }
    }
}
