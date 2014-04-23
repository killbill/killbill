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

package org.killbill.billing.jaxrs;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.EventListener;
import java.util.Iterator;
import java.util.Map;

import javax.inject.Inject;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;

import org.apache.shiro.web.servlet.ShiroFilter;
import org.eclipse.jetty.servlet.FilterHolder;
import org.joda.time.LocalDate;
import org.killbill.billing.DBTestingHelper;
import org.killbill.billing.GuicyKillbillTestWithEmbeddedDBModule;
import org.killbill.billing.TestKillbillConfigSource;
import org.killbill.billing.account.glue.DefaultAccountModule;
import org.killbill.billing.api.TestApiListener;
import org.killbill.billing.beatrix.glue.BeatrixModule;
import org.killbill.billing.catalog.glue.CatalogModule;
import org.killbill.billing.client.KillBillClient;
import org.killbill.billing.client.KillBillHttpClient;
import org.killbill.billing.client.model.Tenant;
import org.killbill.billing.currency.glue.CurrencyModule;
import org.killbill.billing.entitlement.glue.DefaultEntitlementModule;
import org.killbill.billing.invoice.api.InvoiceNotifier;
import org.killbill.billing.invoice.glue.DefaultInvoiceModule;
import org.killbill.billing.invoice.notification.NullInvoiceNotifier;
import org.killbill.billing.jetty.HttpServer;
import org.killbill.billing.jetty.HttpServerConfig;
import org.killbill.billing.junction.glue.DefaultJunctionModule;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.osgi.glue.DefaultOSGIModule;
import org.killbill.billing.overdue.glue.DefaultOverdueModule;
import org.killbill.billing.payment.glue.PaymentModule;
import org.killbill.billing.payment.provider.MockPaymentProviderPluginModule;
import org.killbill.billing.server.config.DaoConfig;
import org.killbill.billing.server.config.KillbillServerConfig;
import org.killbill.billing.server.listeners.KillbillGuiceListener;
import org.killbill.billing.server.modules.KillBillShiroWebModule;
import org.killbill.billing.server.modules.KillbillServerModule;
import org.killbill.billing.subscription.glue.DefaultSubscriptionModule;
import org.killbill.billing.tenant.glue.TenantModule;
import org.killbill.billing.usage.glue.UsageModule;
import org.killbill.billing.util.config.KillbillConfigSource;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.config.PaymentConfig;
import org.killbill.billing.util.email.EmailModule;
import org.killbill.billing.util.email.templates.TemplateModule;
import org.killbill.billing.util.glue.AuditModule;
import org.killbill.billing.util.glue.BusModule;
import org.killbill.billing.util.glue.CacheModule;
import org.killbill.billing.util.glue.CallContextModule;
import org.killbill.billing.util.glue.CustomFieldModule;
import org.killbill.billing.util.glue.ExportModule;
import org.killbill.billing.util.glue.GlobalLockerModule;
import org.killbill.billing.util.glue.KillBillShiroAopModule;
import org.killbill.billing.util.glue.NonEntityDaoModule;
import org.killbill.billing.util.glue.NotificationQueueModule;
import org.killbill.billing.util.glue.RecordIdModule;
import org.killbill.billing.util.glue.SecurityModule;
import org.killbill.billing.util.glue.TagStoreModule;
import org.killbill.bus.api.PersistentBus;
import org.skife.config.ConfigSource;
import org.skife.config.ConfigurationObjectFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Module;

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

    protected DaoConfig daoConfig;
    protected KillbillServerConfig serverConfig;

    protected static TestKillbillGuiceListener listener;

    protected HttpServerConfig config;
    private HttpServer server;

    @Override
    protected KillbillConfigSource getConfigSource() throws IOException, URISyntaxException {
        return new TestKillbillConfigSource("/killbill.properties");
    }

    public class TestKillbillGuiceListener extends KillbillGuiceListener {

        private final KillbillServerConfig serverConfig;
        private final KillbillConfigSource configSource;

        public TestKillbillGuiceListener(final KillbillServerConfig serverConfig, final KillbillConfigSource configSource) {
            super();
            this.serverConfig = serverConfig;
            this.configSource = configSource;
        }

        @Override
        protected Module getModule(final ServletContext servletContext) {
            return new TestKillbillServerModule(servletContext, serverConfig, configSource);
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

    public class TestKillbillServerModule extends KillbillServerModule {

        public TestKillbillServerModule(final ServletContext servletContext, final KillbillServerConfig serverConfig, final KillbillConfigSource configSource) {
            super(servletContext, serverConfig, configSource);
        }

        @Override
        protected void installClock() {
            // Already done By Top test class
        }

        @Override
        protected void configureDao() {
            // Already done By Top test class
        }

        private final class PaymentMockModule extends PaymentModule {

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
            /*
             * For a lack of getting module override working, copy all install modules from parent class...
             *
            super.installKillbillModules();
            Modules.override(new org.killbill.billing.payment.setup.PaymentModule()).with(new PaymentMockModule());
            */

            bind(ConfigSource.class).toInstance(configSource);
            bind(KillbillServerConfig.class).toInstance(serverConfig);
            bind(DaoConfig.class).toInstance(daoConfig);

            install(new GuicyKillbillTestWithEmbeddedDBModule());

            install(new EmailModule(configSource));
            install(new CacheModule(configSource));
            install(new NonEntityDaoModule());
            install(new GlobalLockerModule(DBTestingHelper.get().getDBEngine()));
            install(new CustomFieldModule());
            install(new TagStoreModule());
            install(new AuditModule());
            install(new CatalogModule(configSource));
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
            install(new SecurityModule(configSource));
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
        // TODO PIERRE Unclear why both are needed in beforeClass and beforeSuite
        if (config == null) {
            config = new ConfigurationObjectFactory(System.getProperties()).build(HttpServerConfig.class);
        }
        if (daoConfig == null) {
            daoConfig = new ConfigurationObjectFactory(configSource).build(DaoConfig.class);
        }
        listener.getInstantiatedInjector().injectMembers(this);
    }

    @BeforeSuite(groups = "slow")
    public void beforeSuite() throws Exception {
        super.beforeSuite();

        if (config == null) {
            config = new ConfigurationObjectFactory(System.getProperties()).build(HttpServerConfig.class);
        }
        if (daoConfig == null) {
            daoConfig = new ConfigurationObjectFactory(configSource).build(DaoConfig.class);
        }

        serverConfig = new ConfigurationObjectFactory(configSource).build(KillbillServerConfig.class);
        listener = new TestKillbillGuiceListener(serverConfig, configSource);

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
