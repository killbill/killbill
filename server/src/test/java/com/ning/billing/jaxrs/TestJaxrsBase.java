/*
 * Copyright 2010-2011 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
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

package com.ning.billing.jaxrs;

import java.io.IOException;
import java.net.URL;
import java.util.EventListener;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.eclipse.jetty.servlet.FilterHolder;
import org.joda.time.LocalDate;
import org.skife.config.ConfigurationObjectFactory;
import org.skife.jdbi.v2.IDBI;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;

import com.ning.billing.KillbillTestSuiteWithEmbeddedDB;
import com.ning.billing.account.glue.DefaultAccountModule;
import com.ning.billing.analytics.setup.AnalyticsModule;
import com.ning.billing.api.TestApiListener;
import com.ning.billing.beatrix.glue.BeatrixModule;
import com.ning.billing.catalog.glue.CatalogModule;
import com.ning.billing.dbi.DBIProvider;
import com.ning.billing.dbi.DBTestingHelper;
import com.ning.billing.dbi.DbiConfig;
import com.ning.billing.dbi.MysqlTestingHelper;
import com.ning.billing.entitlement.glue.DefaultEntitlementModule;
import com.ning.billing.invoice.api.InvoiceNotifier;
import com.ning.billing.invoice.glue.DefaultInvoiceModule;
import com.ning.billing.invoice.notification.NullInvoiceNotifier;
import com.ning.billing.junction.glue.DefaultJunctionModule;
import com.ning.billing.meter.glue.MeterModule;
import com.ning.billing.overdue.glue.DefaultOverdueModule;
import com.ning.billing.payment.glue.PaymentModule;
import com.ning.billing.payment.provider.MockPaymentProviderPluginModule;
import com.ning.billing.server.listeners.KillbillGuiceListener;
import com.ning.billing.server.modules.KillbillServerModule;
import com.ning.billing.tenant.glue.TenantModule;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.ClockMock;
import com.ning.billing.util.config.PaymentConfig;
import com.ning.billing.util.email.EmailModule;
import com.ning.billing.util.email.templates.TemplateModule;
import com.ning.billing.util.globallocker.TestGlobalLockerModule;
import com.ning.billing.util.glue.AuditModule;
import com.ning.billing.util.glue.BusModule;
import com.ning.billing.util.glue.CacheModule;
import com.ning.billing.util.glue.CallContextModule;
import com.ning.billing.util.glue.CustomFieldModule;
import com.ning.billing.util.glue.ExportModule;
import com.ning.billing.util.glue.NonEntityDaoModule;
import com.ning.billing.util.glue.NotificationQueueModule;
import com.ning.billing.util.glue.TagStoreModule;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.jetty.core.CoreConfig;
import com.ning.jetty.core.server.HttpServer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.google.common.collect.ImmutableList;
import com.google.inject.Module;

import static org.testng.Assert.assertNotNull;

public class TestJaxrsBase extends KillbillClient {

    protected static final String PLUGIN_NAME = "noop";

    protected static final int DEFAULT_HTTP_TIMEOUT_SEC = 6000; // 5;

    protected static TestKillbillGuiceListener listener;

    private final DBTestingHelper helper = KillbillTestSuiteWithEmbeddedDB.getDBTestingHelper();

    private HttpServer server;
    protected TestApiListener busHandler;

    public static void loadSystemPropertiesFromClasspath(final String resource) {
        final URL url = TestJaxrsBase.class.getResource(resource);
        assertNotNull(url);
        try {
            System.getProperties().load(url.openStream());
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static class TestKillbillGuiceListener extends KillbillGuiceListener {

        private final DBTestingHelper helper;
        private final Clock clock;

        public TestKillbillGuiceListener(final DBTestingHelper helper, final Clock clock) {
            super();
            this.helper = helper;
            this.clock = clock;
        }

        @Override
        protected Module getModule() {
            return new TestKillbillServerModule(helper, clock);
        }

        public Clock getClock() {
            return clock;
        }
    }

    public static class InvoiceModuleWithMockSender extends DefaultInvoiceModule {

        @Override
        protected void installInvoiceNotifier() {
            bind(InvoiceNotifier.class).to(NullInvoiceNotifier.class).asEagerSingleton();
        }
    }

    public static class TestKillbillServerModule extends KillbillServerModule {

        private final DBTestingHelper helper;
        private final Clock clock;

        public TestKillbillServerModule(final DBTestingHelper helper, final Clock clock) {
            super();
            this.helper = helper;
            this.clock = clock;
        }

        @Override
        protected void installClock() {
            bind(Clock.class).toInstance(clock);
        }

        private static final class PaymentMockModule extends PaymentModule {

            @Override
            protected void installPaymentProviderPlugins(final PaymentConfig config) {
                install(new MockPaymentProviderPluginModule(PLUGIN_NAME));
            }
        }

        @Override
        protected void installKillbillModules() {

            /*
             * For a lack of getting module override working, copy all install modules from parent class...
             *
            super.installKillbillModules();
            Modules.override(new com.ning.billing.payment.setup.PaymentModule()).with(new PaymentMockModule());
            */
            install(new EmailModule());
            install(new CacheModule());
            install(new NonEntityDaoModule());
            install(new TestGlobalLockerModule(helper));
            install(new CustomFieldModule());
            install(new TagStoreModule());
            install(new AuditModule());
            install(new CatalogModule());
            install(new BusModule());
            install(new NotificationQueueModule());
            install(new CallContextModule());
            install(new DefaultAccountModule());
            install(new InvoiceModuleWithMockSender());
            install(new TemplateModule());
            install(new DefaultEntitlementModule());
            install(new AnalyticsModule());
            install(new PaymentMockModule());
            install(new BeatrixModule());
            install(new DefaultJunctionModule());
            install(new DefaultOverdueModule());
            install(new TenantModule());
            install(new ExportModule());
            install(new MeterModule());
            installClock();
        }

        @Override
        protected void configureDao() {
            if (helper.isUsingLocalInstance()) {
                bind(IDBI.class).toProvider(DBIProvider.class).asEagerSingleton();
                final DbiConfig config = new ConfigurationObjectFactory(System.getProperties()).build(DbiConfig.class);
                bind(DbiConfig.class).toInstance(config);
            } else {
                final IDBI dbi = helper.getDBI();
                bind(IDBI.class).toInstance(dbi);
            }
        }
    }

    @BeforeMethod(groups = "slow")
    public void cleanupBeforeMethod() {
        busHandler.reset();
        clock.reset();
        clock.setDay(new LocalDate(2012, 8, 25));
    }

    @BeforeClass(groups = "slow")
    public void setupClass() throws IOException {
        loadConfig();
        httpClient = new AsyncHttpClient(new AsyncHttpClientConfig.Builder().setRequestTimeoutInMs(DEFAULT_HTTP_TIMEOUT_SEC * 1000).build());
        mapper = new ObjectMapper();
        mapper.registerModule(new JodaModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        //mapper.setPropertyNamingStrategy(new PropertyNamingStrategy.LowerCaseWithUnderscoresStrategy());

        busHandler = new TestApiListener(null);
        this.clock = (ClockMock) listener.getClock();
    }

    protected void loadConfig() {
        if (config == null) {
            config = new ConfigurationObjectFactory(System.getProperties()).build(CoreConfig.class);
        }

        // For shiro (outside of Guice control)
        System.setProperty("com.ning.jetty.jdbi.url", helper.getJdbcConnectionString());
        System.setProperty("com.ning.jetty.jdbi.user", MysqlTestingHelper.USERNAME);
        System.setProperty("com.ning.jetty.jdbi.password", MysqlTestingHelper.PASSWORD);
    }

    @BeforeSuite(groups = "slow")
    public void setup() throws Exception {
        loadSystemPropertiesFromClasspath("/killbill.properties");
        loadConfig();

        this.clock = new ClockMock();
        listener = new TestKillbillGuiceListener(helper, clock);
        server = new HttpServer();

        server.configure(config, getListeners(), getFilters());

        server.start();
    }

    protected Iterable<EventListener> getListeners() {
        return new Iterable<EventListener>() {
            @Override
            public Iterator<EventListener> iterator() {
                return ImmutableList.<EventListener>of(listener).iterator();
            }
        };
    }

    protected Map<FilterHolder, String> getFilters() {
        return new HashMap<FilterHolder, String>();
    }

    @AfterSuite(groups = "slow")
    public void tearDown() {
        try {
            server.stop();
        } catch (final Exception ignored) {
        }
    }
}
