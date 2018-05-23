/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.jaxrs;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.nio.charset.Charset;
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
import org.killbill.billing.beatrix.integration.db.TestDBRouterAPI;
import org.killbill.billing.client.KillBillClientException;
import org.killbill.billing.client.KillBillHttpClient;
import org.killbill.billing.client.api.gen.AccountApi;
import org.killbill.billing.client.api.gen.AdminApi;
import org.killbill.billing.client.api.gen.BundleApi;
import org.killbill.billing.client.api.gen.CatalogApi;
import org.killbill.billing.client.api.gen.CreditApi;
import org.killbill.billing.client.api.gen.CustomFieldApi;
import org.killbill.billing.client.api.gen.ExportApi;
import org.killbill.billing.client.api.gen.InvoiceApi;
import org.killbill.billing.client.api.gen.InvoiceItemApi;
import org.killbill.billing.client.api.gen.InvoicePaymentApi;
import org.killbill.billing.client.api.gen.NodesInfoApi;
import org.killbill.billing.client.api.gen.OverdueApi;
import org.killbill.billing.client.api.gen.PaymentApi;
import org.killbill.billing.client.api.gen.PaymentGatewayApi;
import org.killbill.billing.client.api.gen.PaymentMethodApi;
import org.killbill.billing.client.api.gen.PaymentTransactionApi;
import org.killbill.billing.client.api.gen.PluginInfoApi;
import org.killbill.billing.client.api.gen.SecurityApi;
import org.killbill.billing.client.api.gen.SubscriptionApi;
import org.killbill.billing.client.api.gen.TagApi;
import org.killbill.billing.client.api.gen.TagDefinitionApi;
import org.killbill.billing.client.api.gen.TenantApi;
import org.killbill.billing.client.api.gen.UsageApi;
import org.killbill.billing.client.model.gen.InvoicePayment;
import org.killbill.billing.client.model.gen.Payment;
import org.killbill.billing.client.model.gen.PaymentTransaction;
import org.killbill.billing.client.model.gen.Tenant;
import org.killbill.billing.invoice.glue.DefaultInvoiceModule;
import org.killbill.billing.jaxrs.resources.TestDBRouterResource;
import org.killbill.billing.jetty.HttpServer;
import org.killbill.billing.jetty.HttpServerConfig;
import org.killbill.billing.lifecycle.glue.BusModule;
import org.killbill.billing.notification.plugin.api.ExtBusEventType;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.TransactionType;
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
import org.killbill.notificationq.api.NotificationQueueService;
import org.skife.config.ConfigurationObjectFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.util.Modules;

public class TestJaxrsBase extends KillbillClient {

    private static final Logger log = LoggerFactory.getLogger(TestJaxrsBase.class);

    protected final int DEFAULT_CONNECT_TIMEOUT_SEC = 10;
    protected final int DEFAULT_READ_TIMEOUT_SEC = 60;
    protected final int DEFAULT_REQUEST_TIMEOUT_SEC = DEFAULT_READ_TIMEOUT_SEC;

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
    protected NotificationQueueService notificationQueueService;

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
    private CallbackServer callbackServer;

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
                                                                                                               new PaymentMockModule(configSource),
                                                                                                               new Module() {
                                                                                                                   @Override
                                                                                                                   public void configure(final Binder binder) {
                                                                                                                       binder.bind(TestDBRouterAPI.class).asEagerSingleton();
                                                                                                                       binder.bind(TestDBRouterResource.class).asEagerSingleton();
                                                                                                                   }
                                                                                                               });
        }

    }

    public static class InvoiceModuleWithMockSender extends DefaultInvoiceModule {

        public InvoiceModuleWithMockSender(final KillbillConfigSource configSource) {
            super(configSource);
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
        requestOptions = requestOptions.extend()
                                       .withTenantApiKey(apiKey)
                                       .withTenantApiSecret(apiSecret)
                                       .build();

        killBillHttpClient = new KillBillHttpClient(String.format("http://%s:%d", config.getServerHost(), config.getServerPort()),
                                                    username,
                                                    password,
                                                    apiKey,
                                                    apiSecret,
                                                    null,
                                                    null,
                                                    DEFAULT_CONNECT_TIMEOUT_SEC * 1000,
                                                    DEFAULT_READ_TIMEOUT_SEC * 1000,
                                                    DEFAULT_REQUEST_TIMEOUT_SEC * 1000);
        accountApi = new AccountApi(killBillHttpClient);
        adminApi = new AdminApi(killBillHttpClient);
        bundleApi = new BundleApi(killBillHttpClient);
        catalogApi = new CatalogApi(killBillHttpClient);
        creditApi = new CreditApi(killBillHttpClient);
        customFieldApi = new CustomFieldApi(killBillHttpClient);
        exportApi = new ExportApi(killBillHttpClient);
        invoiceApi = new InvoiceApi(killBillHttpClient);
        invoiceItemApi = new InvoiceItemApi(killBillHttpClient);
        invoicePaymentApi = new InvoicePaymentApi(killBillHttpClient);
        nodesInfoApi = new NodesInfoApi(killBillHttpClient);
        overdueApi = new OverdueApi(killBillHttpClient);
        paymentApi = new PaymentApi(killBillHttpClient);
        paymentGatewayApi = new PaymentGatewayApi(killBillHttpClient);
        paymentMethodApi = new PaymentMethodApi(killBillHttpClient);
        paymentTransactionApi = new PaymentTransactionApi(killBillHttpClient);
        pluginInfoApi = new PluginInfoApi(killBillHttpClient);
        securityApi = new SecurityApi(killBillHttpClient);
        subscriptionApi = new SubscriptionApi(killBillHttpClient);
        tagApi = new TagApi(killBillHttpClient);
        tagDefinitionApi = new TagDefinitionApi(killBillHttpClient);
        tenantApi = new TenantApi(killBillHttpClient);
        usageApi = new UsageApi(killBillHttpClient);
    }

    protected void loginTenant(final String apiKey, final String apiSecret) {
        setupClient(USERNAME, PASSWORD, apiKey, apiSecret);
    }

    protected void logoutTenant() {
        setupClient(USERNAME, PASSWORD, null, null);
    }

    protected void login(final String username, final String password) {
        setupClient(username, password, DEFAULT_API_KEY, DEFAULT_API_SECRET);
    }

    protected void logout() {
        setupClient(null, null, null, null);
    }

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeMethod();

        // Because we truncate the tables, the database record_id auto_increment will be reset
        tenantCacheInvalidation.setLatestRecordIdProcessed(0L);

        externalBus.start();
        internalBus.start();
        cacheControllerDispatcher.clearAll();
        busHandler.reset();
        callbackServlet.reset();

        clock.resetDeltaFromReality();
        clock.setDay(new LocalDate(2012, 8, 25));

        // Make sure to re-generate the api key and secret (could be cached by Shiro)
        DEFAULT_API_KEY = UUID.randomUUID().toString();
        DEFAULT_API_SECRET = UUID.randomUUID().toString();

        // Recreate the tenant (tables have been cleaned-up)
        createTenant(DEFAULT_API_KEY, DEFAULT_API_SECRET, true);
    }

    protected Tenant createTenant(final String apiKey, final String apiSecret, final boolean useGlobalDefault) throws KillBillClientException {
        callbackServlet.assertListenerStatus();
        callbackServlet.reset();

        loginTenant(apiKey, apiSecret);
        final Tenant tenant = new Tenant();
        tenant.setApiKey(apiKey);
        tenant.setApiSecret(apiSecret);

        requestOptions = requestOptions.extend()
                                       .withTenantApiKey(apiKey)
                                       .withTenantApiSecret(apiSecret)
                                       .build();

        callbackServlet.pushExpectedEvent(ExtBusEventType.TENANT_CONFIG_CHANGE);
        if (!useGlobalDefault) {
            // Catalog
            callbackServlet.pushExpectedEvent(ExtBusEventType.TENANT_CONFIG_CHANGE);
        }

        final Tenant createdTenant = tenantApi.createTenant(tenant, useGlobalDefault, requestOptions);

        // Register tenant for callback
        final String callback = callbackServer.getServletEndpoint();
        tenantApi.registerPushNotificationCallback(callback, requestOptions);
        callbackServlet.assertListenerStatus();

        createdTenant.setApiSecret(apiSecret);

        return createdTenant;
    }

    @AfterMethod(groups = "slow")
    public void afterMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        killBillHttpClient.close();
        externalBus.stop();
        internalBus.stop();
    }

    @BeforeClass(groups = "slow")
    public void beforeClass() throws Exception {
        if (hasFailed()) {
            return;
        }

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
        if (hasFailed()) {
            return;
        }

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

        callbackServlet = new CallbackServlet();
        callbackServer = new CallbackServer(callbackServlet);
        callbackServer.startServer();
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
            callbackServer.stopServer();
        } catch (final Exception ignored) {
        }
    }

    protected static List<PaymentTransaction> getInvoicePaymentTransactions(final List<InvoicePayment> payments, final TransactionType transactionType) {
        final Iterable<PaymentTransaction> transform = Iterables.concat(Iterables.transform(payments, new Function<InvoicePayment, Iterable<PaymentTransaction>>() {
            @Override
            public Iterable<PaymentTransaction> apply(final InvoicePayment input) {
                return input.getTransactions();
            }
        }));
        return filterTransactions(transform, transactionType);
    }

    protected static List<PaymentTransaction> getPaymentTransactions(final List<Payment> payments, final TransactionType transactionType) {
        final Iterable<PaymentTransaction> transform = Iterables.concat(Iterables.transform(payments, new Function<Payment, Iterable<PaymentTransaction>>() {
            @Override
            public Iterable<PaymentTransaction> apply(final Payment input) {
                return input.getTransactions();
            }
        }));
        return filterTransactions(transform, transactionType);
    }

    protected static List<PaymentTransaction> filterTransactions(final Iterable<PaymentTransaction> paymentTransaction, final TransactionType transactionType) {
        return ImmutableList.copyOf(Iterables.filter(paymentTransaction, new Predicate<PaymentTransaction>() {
            @Override
            public boolean apply(final PaymentTransaction input) {
                return input.getTransactionType().equals(transactionType);
            }
        }));
    }

    protected String uploadTenantCatalog(final String catalog, final boolean fetch) throws IOException, KillBillClientException {
        final String body = getResourceBodyString(catalog);
        catalogApi.uploadCatalogXml(body, requestOptions);
        return fetch ? catalogApi.getCatalogXml(null, null, requestOptions) : null;
    }

    protected void uploadTenantOverdueConfig(final String overdue) throws IOException, KillBillClientException {
        final String body = getResourceBodyString(overdue);
        callbackServlet.pushExpectedEvent(ExtBusEventType.TENANT_CONFIG_CHANGE);
        overdueApi.uploadOverdueConfigXml(body, requestOptions);
        callbackServlet.assertListenerStatus();
    }

    protected String getResourceBodyString(final String resource) throws IOException {
        final String resourcePath = Resources.getResource(resource).getPath();
        final File catalogFile = new File(resourcePath);
        return Files.toString(catalogFile, Charset.forName("UTF-8"));
    }

    protected void printThreadDump() {
        final StringBuilder dump = new StringBuilder("Thread dump:\n");
        final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        final ThreadInfo[] threadInfos = threadMXBean.getThreadInfo(threadMXBean.getAllThreadIds(), 100);
        for (final ThreadInfo threadInfo : threadInfos) {
            dump.append('"');
            dump.append(threadInfo.getThreadName());
            dump.append("\" ");
            final Thread.State state = threadInfo.getThreadState();
            dump.append("\n   java.lang.Thread.State: ");
            dump.append(state);
            final StackTraceElement[] stackTraceElements = threadInfo.getStackTrace();
            for (final StackTraceElement stackTraceElement : stackTraceElements) {
                dump.append("\n        at ");
                dump.append(stackTraceElement);
            }
            dump.append("\n\n");
        }
        log.warn(dump.toString());
    }
}
