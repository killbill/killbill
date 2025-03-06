/*
 * Copyright 2014-2019 Groupon, Inc
 * Copyright 2014-2019 The Billing Project, LLC
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

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.Period;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountData;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.DefaultVersionedCatalog;
import org.killbill.billing.catalog.StandaloneCatalog;
import org.killbill.billing.catalog.StandaloneCatalogWithPriceOverride;
import org.killbill.billing.catalog.api.CatalogUserApi;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.StaticCatalog;
import org.killbill.billing.catalog.override.PriceOverrideSvc;
import org.killbill.billing.catalog.plugin.TestModelStandalonePluginCatalog;
import org.killbill.billing.catalog.plugin.TestModelVersionedPluginCatalog;
import org.killbill.billing.catalog.plugin.api.CatalogPluginApi;
import org.killbill.billing.catalog.plugin.api.StandalonePluginCatalog;
import org.killbill.billing.catalog.plugin.api.VersionedPluginCatalog;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.osgi.api.OSGIServiceDescriptor;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.commons.utils.io.Resources;
import org.killbill.billing.util.queue.QueueRetryException;
import org.killbill.clock.Clock;
import org.killbill.xmlloader.XMLLoader;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertNotNull;

public class TestHardenCatalogPlugin extends TestIntegrationBase {

    private static final int NB_CATALOG_ITERATIONS_FOR_PULLING_BILLING_EVENTS = 2;
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
                return "TestHardenCatalogPlugin";
            }

            @Override
            public String getPluginName() {
                return "TestHardenCatalogPlugin";
            }

            @Override
            public String getRegistrationName() {
                return "TestHardenCatalogPlugin";
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
    public void testCreateWithCatalogPluginRetryableException() throws Exception {

        testCatalogPluginApi.addCatalogVersion("org/killbill/billing/catalog/WeaponsHire.xml");

        // We setup the test to throw ONCE a QueueRetryException upon pulling billing events from invoice and ask for a retry 3 days later
        testCatalogPluginApi.setupExceptionBehavior(NB_CATALOG_ITERATIONS_FOR_PULLING_BILLING_EVENTS, Period.days(1), true);

        clock.setDay(new LocalDate(2019, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("assault-rifle-annual-rescue"); // this plan does not have a TRIAL
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, null, null, UUID.randomUUID().toString(), null), "something", null, null, false, true, Collections.emptyList(), callContext);
        assertNotNull(entitlementId);
        assertListenerStatus();

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addDays(1);
        assertListenerStatus();
    }

    @Test(groups = "slow")
    public void testCreateWithCatalogPluginSingleRuntimeException() throws Exception {

        testCatalogPluginApi.addCatalogVersion("org/killbill/billing/catalog/WeaponsHire.xml");

        // We set the test to throw ONCE a RuntimeException upon pulling billing events from invoice
        testCatalogPluginApi.setupExceptionBehavior(NB_CATALOG_ITERATIONS_FOR_PULLING_BILLING_EVENTS, null, true);

        clock.setDay(new LocalDate(2019, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        // The EffectiveSubscriptionEvent bus event (NextEvent.CREATE) will be retried right away upon RuntimeException and therefore
        // we should except to receive this event twice
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("assault-rifle-annual-rescue"); // this plan does not have a TRIAL
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, null, null, UUID.randomUUID().toString(), null), "something", null, null, false, true, Collections.emptyList(), callContext);
        assertNotNull(entitlementId);
        assertListenerStatus();
    }

    @Test(groups = "slow")
    public void testCreateWithCatalogPluginNonSingleRuntimeException() throws Exception {

        testCatalogPluginApi.addCatalogVersion("org/killbill/billing/catalog/WeaponsHire.xml");

        // We set the test to keep throwing RuntimeException upon pulling billing events from invoice
        testCatalogPluginApi.setupExceptionBehavior(NB_CATALOG_ITERATIONS_FOR_PULLING_BILLING_EVENTS, null, false);

        clock.setDay(new LocalDate(2019, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        // We see 4 times the EffectiveSubscriptionEvent (3 retries) along with the BlockingTransitionInternalEvent and nothing else
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.CREATE, NextEvent.CREATE, NextEvent.CREATE, NextEvent.BLOCK);
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("assault-rifle-annual-rescue"); // this plan does not have a TRIAL
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, null, null, UUID.randomUUID().toString(), null), "something", null, null, false, true, Collections.emptyList(), callContext);
        assertNotNull(entitlementId);
        assertListenerStatus();
    }

    public static class TestCatalogPluginApi implements CatalogPluginApi {

        private final PriceOverrideSvc priceOverride;
        private final Clock clock;
        private final InternalTenantContext internalTenantContext;
        private final InternalCallContextFactory internalCallContextFactory;

        private DefaultVersionedCatalog versionedCatalog;

        private int throwOnZeroCounter;
        private Period retryPeriod;
        private boolean throwOnce;

        public TestCatalogPluginApi(final PriceOverrideSvc priceOverride, final Clock clock, final InternalTenantContext internalTenantContext, final InternalCallContextFactory internalCallContextFactory) throws Exception {
            this.priceOverride = priceOverride;
            this.clock = clock;
            this.internalTenantContext = internalTenantContext;
            this.internalCallContextFactory = internalCallContextFactory;
            reset();
        }

        @Override
        public DateTime getLatestCatalogVersion(final Iterable<PluginProperty> iterable, final TenantContext tenantContext) {
            // Resulting in never caching the catalog returned by the plugin
            return null;
        }

        @Override
        public VersionedPluginCatalog getVersionedPluginCatalog(final Iterable<PluginProperty> properties, final TenantContext tenantContext) {
            Assert.assertNotNull(versionedCatalog, "test did not initialize plugin catalog");

            // Will throw once when we transition from 1 -> 0
            if (throwOnZeroCounter > 0) {
                throwOnZeroCounter--;
                if (throwOnZeroCounter == 0) {
                    // Re-increment for next round if we want to keep failing
                    if (!throwOnce) {
                        throwOnZeroCounter++;
                    }
                    if (retryPeriod != null) {
                        throw new CatalogPluginApiRetryException(List.of(retryPeriod));
                    } else {
                        throw new RuntimeException("****  CATALOG PLUGIN RUNTIME EXCEPTION ****");
                    }
                }
            }

            return new TestModelVersionedPluginCatalog(versionedCatalog.getCatalogName(), toStandalonePluginCatalogs(versionedCatalog.getVersions()));
        }

        // This actually pulls catalog resources from `catalog` module and not the one from beatrix/src/test/resources//catalogs
        public void addCatalogVersion(final String catalogResource) throws Exception {

            final StandaloneCatalog inputCatalogVersion = XMLLoader.getObjectFromString(Resources.getResource(catalogResource).toExternalForm(), StandaloneCatalog.class);
            final StandaloneCatalogWithPriceOverride inputCatalogVersionWithOverride = new StandaloneCatalogWithPriceOverride(inputCatalogVersion, priceOverride, internalTenantContext.getTenantRecordId(), internalCallContextFactory);

            if (versionedCatalog == null) {
                versionedCatalog = new DefaultVersionedCatalog();
            }
            versionedCatalog.add(inputCatalogVersionWithOverride);
        }

        public void setupExceptionBehavior(final int throwOnZeroCounter, final Period retryPeriod, boolean throwOnce) {
            this.throwOnZeroCounter = throwOnZeroCounter;
            this.retryPeriod = retryPeriod;
            this.throwOnce = throwOnce;
        }

        public void reset() {
            this.versionedCatalog = null;
            this.throwOnZeroCounter = 0;
        }

        private Iterable<StandalonePluginCatalog> toStandalonePluginCatalogs(final List<StaticCatalog> input) {
            return input.stream()
                    .map(staticCatalog -> {
                        final StandaloneCatalog standaloneCatalog = (StandaloneCatalog) staticCatalog;
                        return new TestModelStandalonePluginCatalog(new DateTime(staticCatalog.getEffectiveDate()),
                                                                    List.of(standaloneCatalog.getSupportedCurrencies()),
                                                                    List.copyOf(standaloneCatalog.getProducts()),
                                                                    List.copyOf(standaloneCatalog.getPlans()),
                                                                    standaloneCatalog.getPriceLists().getDefaultPricelist(),
                                                                    List.of(standaloneCatalog.getPriceLists().getChildPriceLists()),
                                                                    standaloneCatalog.getPlanRules(),
                                                                    null /* ImmutableList.<Unit>copyOf(input.getCurrentUnits()) */);
                    })
                    .collect(Collectors.toUnmodifiableList());
        }
    }

    public static class CatalogPluginApiRetryException extends QueueRetryException {

        public CatalogPluginApiRetryException() {
        }

        public CatalogPluginApiRetryException(final Exception e) {
            super(e);
        }

        public CatalogPluginApiRetryException(final List<Period> retrySchedule) {
            super(retrySchedule);
        }

        public CatalogPluginApiRetryException(final Exception e, final List<Period> retrySchedule) {
            super(e, retrySchedule);
        }
    }
}
