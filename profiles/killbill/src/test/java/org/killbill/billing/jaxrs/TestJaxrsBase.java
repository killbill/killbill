/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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

package org.killbill.billing.jaxrs;

import java.util.EventListener;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.sql.DataSource;

import org.apache.shiro.web.servlet.ShiroFilter;
import org.eclipse.jetty.servlet.FilterHolder;
import org.joda.time.LocalDate;
import org.killbill.billing.GuicyKillbillTestWithEmbeddedDBModule;
import org.killbill.billing.api.TestApiListener;
import org.killbill.billing.client.KillBillClient;
import org.killbill.billing.client.KillBillHttpClient;
import org.killbill.billing.client.RequestOptions;
import org.killbill.billing.client.model.Payment;
import org.killbill.billing.client.model.PaymentTransaction;
import org.killbill.billing.client.model.Tenant;
import org.killbill.billing.invoice.api.InvoiceNotifier;
import org.killbill.billing.invoice.glue.DefaultInvoiceModule;
import org.killbill.billing.invoice.notification.NullInvoiceNotifier;
import org.killbill.billing.jetty.HttpServer;
import org.killbill.billing.jetty.HttpServerConfig;
import org.killbill.billing.lifecycle.glue.BusModule;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.glue.PaymentModule;
import org.killbill.billing.payment.provider.MockPaymentProviderPluginModule;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.server.config.KillbillServerConfig;
import org.killbill.billing.server.listeners.KillbillGuiceListener;
import org.killbill.billing.server.modules.KillbillServerModule;
import org.killbill.billing.tenant.api.TenantCacheInvalidation;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.config.definition.PaymentConfig;
import org.killbill.billing.util.config.definition.SecurityConfig;
import org.killbill.bus.api.PersistentBus;
import org.killbill.commons.jdbi.guice.DaoConfig;
import org.skife.config.ConfigurationObjectFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.inject.Module;
import com.google.inject.util.Modules;

public class TestJaxrsBase extends KillbillClient {

    protected static final String PLUGIN_NAME = "noop";

    @Inject
    protected OSGIServiceRegistration<Servlet> servletRouter;

    @Inject
    protected CacheControllerDispatcher cacheControllerDispatcher;

    @Inject
    protected
    @javax.inject.Named(BusModule.EXTERNAL_BUS_NAMED)
    PersistentBus externalBus;

    @Inject
    protected PersistentBus internalBus;

    @Inject
    protected TestApiListener busHandler;

    @Inject
    @Named(KillbillServerModule.SHIRO_DATA_SOURCE_ID)
    protected DataSource shiroDataSource;

    @Inject
    protected SecurityConfig securityConfig;

    @Inject
    protected TenantCacheInvalidation tenantCacheInvalidation;

    protected DaoConfig daoConfig;
    protected KillbillServerConfig serverConfig;

    protected static TestKillbillGuiceListener listener;

    protected HttpServerConfig config;
    private HttpServer server;

    @Override
    protected KillbillConfigSource getConfigSource() {
        return getConfigSource("/killbill.properties");
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
            return Modules.override(new KillbillServerModule(servletContext, serverConfig, configSource)).with(new GuicyKillbillTestWithEmbeddedDBModule(configSource),
                                                                                                               new InvoiceModuleWithMockSender(configSource),
                                                                                                               new PaymentMockModule(configSource));
        }

    }

    public static class InvoiceModuleWithMockSender extends DefaultInvoiceModule {

        public InvoiceModuleWithMockSender(final KillbillConfigSource configSource) {
            super(configSource);
        }

        @Override
        protected void installInvoiceNotifier() {
            bind(InvoiceNotifier.class).to(NullInvoiceNotifier.class).asEagerSingleton();
        }
    }

    private final class PaymentMockModule extends PaymentModule {

        public PaymentMockModule(final KillbillConfigSource configSource) {
            super(configSource);
        }

        @Override
        protected void installPaymentProviderPlugins(final PaymentConfig config) {
            install(new MockPaymentProviderPluginModule(PLUGIN_NAME, getClock(), configSource));
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

        // Because we truncate the tables, the database record_id auto_increment will be reset
        tenantCacheInvalidation.setLatestRecordIdProcessed(0L);

        externalBus.start();
        internalBus.start();
        cacheControllerDispatcher.clearAll();
        busHandler.reset();
        clock.resetDeltaFromReality();
        clock.setDay(new LocalDate(2012, 8, 25));

        // Make sure to re-generate the api key and secret (could be cached by Shiro)
        DEFAULT_API_KEY = UUID.randomUUID().toString();
        DEFAULT_API_SECRET = UUID.randomUUID().toString();
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
            daoConfig = new ConfigurationObjectFactory(skifeConfigSource).build(DaoConfig.class);
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
            daoConfig = new ConfigurationObjectFactory(skifeConfigSource).build(DaoConfig.class);
        }

        serverConfig = new ConfigurationObjectFactory(skifeConfigSource).build(KillbillServerConfig.class);
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

    protected static <T extends Payment> List<PaymentTransaction> getPaymentTransactions(final List<T> payments, final String transactionType) {
        return ImmutableList.copyOf(Iterables.concat(Iterables.transform(payments, new Function<T, Iterable<PaymentTransaction>>() {
            @Override
            public Iterable<PaymentTransaction> apply(final T input) {
                return Iterables.filter(input.getTransactions(), new Predicate<PaymentTransaction>() {
                    @Override
                    public boolean apply(final PaymentTransaction input) {
                        return input.getTransactionType().equals(transactionType);
                    }
                });
            }
        })));
    }

}
