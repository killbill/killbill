/*
 * Copyright 2010-2013 Ning, Inc.
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
import java.util.Iterator;
import java.util.Map;

import javax.inject.Inject;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;

import org.apache.shiro.web.servlet.ShiroFilter;
import org.eclipse.jetty.servlet.FilterHolder;
import org.joda.time.LocalDate;
import org.skife.config.ConfigSource;
import org.skife.config.ConfigurationObjectFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;

import com.ning.billing.DBTestingHelper;
import com.ning.billing.GuicyKillbillTestWithEmbeddedDBModule;
import com.ning.billing.KillbillConfigSource;
import com.ning.billing.account.glue.DefaultAccountModule;
import com.ning.billing.api.TestApiListener;
import com.ning.billing.beatrix.glue.BeatrixModule;
import com.ning.billing.bus.api.PersistentBus;
import com.ning.billing.catalog.glue.CatalogModule;
import com.ning.billing.client.KillBillClient;
import com.ning.billing.client.KillBillHttpClient;
import com.ning.billing.client.model.Tenant;
import com.ning.billing.commons.embeddeddb.EmbeddedDB;
import com.ning.billing.currency.glue.CurrencyModule;
import com.ning.billing.entitlement.glue.DefaultEntitlementModule;
import com.ning.billing.invoice.api.InvoiceNotifier;
import com.ning.billing.invoice.glue.DefaultInvoiceModule;
import com.ning.billing.invoice.notification.NullInvoiceNotifier;
import com.ning.billing.junction.glue.DefaultJunctionModule;
import com.ning.billing.osgi.api.OSGIServiceRegistration;
import com.ning.billing.osgi.glue.DefaultOSGIModule;
import com.ning.billing.overdue.glue.DefaultOverdueModule;
import com.ning.billing.payment.glue.PaymentModule;
import com.ning.billing.payment.provider.MockPaymentProviderPluginModule;
import com.ning.billing.server.listeners.KillbillGuiceListener;
import com.ning.billing.server.modules.KillBillShiroWebModule;
import com.ning.billing.server.modules.KillbillServerModule;
import com.ning.billing.subscription.glue.DefaultSubscriptionModule;
import com.ning.billing.tenant.glue.TenantModule;
import com.ning.billing.usage.glue.UsageModule;
import com.ning.billing.util.cache.CacheControllerDispatcher;
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
import com.ning.billing.util.glue.KillBillShiroAopModule;
import com.ning.billing.util.glue.MetricsModule;
import com.ning.billing.util.glue.NonEntityDaoModule;
import com.ning.billing.util.glue.NotificationQueueModule;
import com.ning.billing.util.glue.RecordIdModule;
import com.ning.billing.util.glue.SecurityModule;
import com.ning.billing.util.glue.TagStoreModule;
import com.ning.jetty.core.CoreConfig;
import com.ning.jetty.core.server.HttpServer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Module;

import static org.testng.Assert.assertNotNull;

public class TestJaxrsBase extends KillbillClient {

    protected static final String PLUGIN_NAME = "noop";

    @Inject
    protected OSGIServiceRegistration<Servlet> servletRouter;

    @Inject
    protected CacheControllerDispatcher cacheControllerDispatcher;

    @Inject
    protected @javax.inject.Named(BeatrixModule.EXTERNAL_BUS) PersistentBus externalBus;

    @Inject
    protected PersistentBus internalBus;

    @Inject
    protected TestApiListener busHandler;

    protected static TestKillbillGuiceListener listener;

    protected CoreConfig config;
    private HttpServer server;

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

        private final EmbeddedDB helper;

        public TestKillbillGuiceListener(final EmbeddedDB helper) {
            super();
            this.helper = helper;
        }

        @Override
        protected Module getModule(final ServletContext servletContext) {
            return new TestKillbillServerModule(helper, servletContext);
        }

    }

    public static class InvoiceModuleWithMockSender extends DefaultInvoiceModule {

        public InvoiceModuleWithMockSender(final ConfigSource configSource) {
            super(configSource);
        }

        @Override
        protected void installInvoiceNotifier() {
            bind(InvoiceNotifier.class).to(NullInvoiceNotifier.class).asEagerSingleton();
        }
    }

    public static class TestKillbillServerModule extends KillbillServerModule {

        private final EmbeddedDB helper;

        public TestKillbillServerModule(final EmbeddedDB helper, final ServletContext servletContext) {
            super(servletContext, false);
            this.helper = helper;
        }

        @Override
        protected void installClock() {
            // Already done By Top test class
        }

        @Override
        protected void configureDao() {
            // Already done By Top test class
        }

        private static final class PaymentMockModule extends PaymentModule {

            public PaymentMockModule(final ConfigSource configSource) {
                super(configSource);
            }

            @Override
            protected void installPaymentProviderPlugins(final PaymentConfig config) {
                install(new MockPaymentProviderPluginModule(PLUGIN_NAME, getClock()));
            }
        }

        @Override
        protected void installKillbillModules() {
            final KillbillConfigSource configSource = new KillbillConfigSource(System.getProperties());

            /*
             * For a lack of getting module override working, copy all install modules from parent class...
             *
            super.installKillbillModules();
            Modules.override(new com.ning.billing.payment.setup.PaymentModule()).with(new PaymentMockModule());
            */

            install(new GuicyKillbillTestWithEmbeddedDBModule());

            install(new EmailModule(configSource));
            install(new CacheModule(configSource));
            install(new NonEntityDaoModule());
            install(new TestGlobalLockerModule(DBTestingHelper.get()));
            install(new CustomFieldModule());
            install(new TagStoreModule());
            install(new AuditModule());
            install(new CatalogModule(configSource));
            install(new MetricsModule());
            install(new BusModule(configSource));
            install(new NotificationQueueModule(configSource));
            install(new CallContextModule());
            install(new DefaultAccountModule(configSource));
            install(new InvoiceModuleWithMockSender(configSource));
            install(new TemplateModule());
            install(new DefaultSubscriptionModule(configSource));
            install(new DefaultEntitlementModule(configSource));
            install(new PaymentMockModule(configSource));
            install(new BeatrixModule(configSource));
            install(new DefaultJunctionModule(configSource));
            install(new DefaultOverdueModule(configSource));
            install(new TenantModule(configSource));
            install(new CurrencyModule(configSource));
            install(new ExportModule());
            install(new DefaultOSGIModule(configSource));
            install(new UsageModule(configSource));
            install(new RecordIdModule());
            installClock();
            install(new KillBillShiroWebModule(servletContext, configSource));
            install(new KillBillShiroAopModule());
            install(new SecurityModule());
        }
    }

    protected void setupClient(final String username, final String password, final String apiKey, final String apiSecret) {
        killBillHttpClient = new KillBillHttpClient(String.format("http://%s:%d", config.getServerHost(), config.getServerPort()),
                                                    username,
                                                    password,
                                                    apiKey,
                                                    apiSecret);
        killBillClient = new KillBillClient(killBillHttpClient);
    }

    protected void loginTenant(final String apiKey, final String apiSecret) {
        setupClient(USERNAME, PASSWORD, apiKey, apiSecret);
    }

    protected void logoutTenant() {
        setupClient(USERNAME, PASSWORD, null, null);
    }

    protected void login() {
        login(USERNAME, PASSWORD);
    }

    protected void login(final String username, final String password) {
        setupClient(username, password, DEFAULT_API_KEY, DEFAULT_API_SECRET);
    }

    protected void logout() {
        setupClient(null, null, null, null);
    }

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        super.beforeMethod();
        externalBus.start();
        internalBus.start();
        cacheControllerDispatcher.clearAll();
        busHandler.reset();
        clock.resetDeltaFromReality();
        clock.setDay(new LocalDate(2012, 8, 25));

        loginTenant(DEFAULT_API_KEY, DEFAULT_API_SECRET);

        // Recreate the tenant (tables have been cleaned-up)
        final Tenant tenant = new Tenant();
        tenant.setApiKey(DEFAULT_API_KEY);
        tenant.setApiSecret(DEFAULT_API_SECRET);
        killBillClient.createTenant(tenant, createdBy, reason, comment);
    }

    @AfterMethod(groups = "slow")
    public void afterMethod() throws Exception {
        killBillClient.close();
        externalBus.stop();
        internalBus.stop();
    }

    @BeforeClass(groups = "slow")
    public void beforeClass() throws Exception {
        loadConfig();

        listener.getInstantiatedInjector().injectMembers(this);
    }

    protected void loadConfig() {
        if (config == null) {
            config = new ConfigurationObjectFactory(System.getProperties()).build(CoreConfig.class);
        }

        // For shiro (outside of Guice control)
        System.setProperty("com.ning.jetty.jdbi.url", DBTestingHelper.get().getJdbcConnectionString());
        System.setProperty("com.ning.jetty.jdbi.user", DBTestingHelper.get().getUsername());
        System.setProperty("com.ning.jetty.jdbi.password", DBTestingHelper.get().getPassword());
    }

    @BeforeSuite(groups = "slow")
    public void beforeSuite() throws Exception {
        super.beforeSuite();
        loadSystemPropertiesFromClasspath("/killbill.properties");
        loadConfig();

        listener = new TestKillbillGuiceListener(helper);

        server = new HttpServer();
        server.configure(config, getListeners(), getFilters());
        server.start();
    }

    protected Iterable<EventListener> getListeners() {
        return new Iterable<EventListener>() {
            @Override
            public Iterator<EventListener> iterator() {
                // Note! This needs to be in sync with web.xml
                return ImmutableList.<EventListener>of(listener).iterator();
            }
        };
    }

    protected Map<FilterHolder, String> getFilters() {
        // Note! This needs to be in sync with web.xml
        return ImmutableMap.<FilterHolder, String>of(new FilterHolder(new ShiroFilter()), "/*");
    }

    @AfterSuite(groups = "slow")
    public void afterSuite() {
        try {
            server.stop();
        } catch (final Exception ignored) {
        }
    }
}
