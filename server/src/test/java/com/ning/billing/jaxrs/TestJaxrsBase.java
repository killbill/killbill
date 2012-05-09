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

import static org.testng.Assert.assertNotNull;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.EventListener;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.core.Response.Status;

import com.ning.billing.util.email.EmailModule;
import com.ning.billing.util.email.templates.TemplateModule;
import com.ning.billing.util.glue.GlobalLockerModule;
import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.servlet.FilterHolder;
import org.skife.config.ConfigurationObjectFactory;
import org.skife.jdbi.v2.IDBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.google.inject.Module;
import com.ning.billing.account.glue.AccountModule;
import com.ning.billing.analytics.setup.AnalyticsModule;
import com.ning.billing.api.TestApiListener;
import com.ning.billing.beatrix.glue.BeatrixModule;
import com.ning.billing.beatrix.integration.TestIntegration;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.catalog.glue.CatalogModule;
import com.ning.billing.config.PaymentConfig;
import com.ning.billing.dbi.DBIProvider;
import com.ning.billing.dbi.DbiConfig;
import com.ning.billing.dbi.MysqlTestingHelper;
import com.ning.billing.entitlement.glue.DefaultEntitlementModule;
import com.ning.billing.invoice.glue.DefaultInvoiceModule;
import com.ning.billing.jaxrs.json.AccountJson;
import com.ning.billing.jaxrs.json.BundleJsonNoSubsciptions;
import com.ning.billing.jaxrs.json.SubscriptionJsonNoEvents;
import com.ning.billing.jaxrs.resources.BaseJaxrsResource;
import com.ning.billing.junction.glue.DefaultJunctionModule;
import com.ning.billing.payment.provider.MockPaymentProviderPluginModule;
import com.ning.billing.payment.setup.PaymentModule;
import com.ning.billing.server.listeners.KillbillGuiceListener;
import com.ning.billing.server.modules.KillbillServerModule;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.ClockMock;
import com.ning.billing.util.glue.BusModule;
import com.ning.billing.util.glue.CallContextModule;
import com.ning.billing.util.glue.FieldStoreModule;
import com.ning.billing.util.glue.NotificationQueueModule;
import com.ning.billing.util.glue.TagStoreModule;
import com.ning.http.client.AsyncCompletionHandler;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClient.BoundRequestBuilder;
import com.ning.http.client.ListenableFuture;
import com.ning.http.client.Response;
import com.ning.jetty.core.CoreConfig;
import com.ning.jetty.core.server.HttpServer;

public class TestJaxrsBase {

    private final static String PLUGIN_NAME = "noop";

    protected static final int DEFAULT_HTTP_TIMEOUT_SEC = 5;

    protected static final Map<String, String> DEFAULT_EMPTY_QUERY = new HashMap<String, String>();

    private static final Logger log = LoggerFactory.getLogger(TestJaxrsBase.class);

    public static final String HEADER_CONTENT_TYPE = "Content-type";
    public static final String CONTENT_TYPE = "application/json";

    private static TestKillbillGuiceListener listener;
    
    
    private MysqlTestingHelper helper;
    private HttpServer server;

    protected CoreConfig config;
    protected AsyncHttpClient httpClient;	
    protected ObjectMapper mapper;
    protected ClockMock clock;
    protected TestApiListener busHandler;

    // Context informtation to be passed around
    private static final String createdBy = "Toto";
    private static final String reason = "i am god";
    private static final String comment = "no comment";    
    
    public static void loadSystemPropertiesFromClasspath(final String resource) {
        final URL url = TestJaxrsBase.class.getResource(resource);
        assertNotNull(url);
        try {
            System.getProperties().load(url.openStream());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static class TestKillbillGuiceListener extends KillbillGuiceListener {
        
        private final MysqlTestingHelper helper;
        private final Clock clock;
        
        public TestKillbillGuiceListener(final MysqlTestingHelper helper, final Clock clock) {
            super();
            this.helper = helper;
            this.clock = clock;
        }
        @Override
        protected Module getModule() {
            return new TestKillbillServerModule(helper, clock);
        }
        
        //
        // Listener is created once before Suite and keeps pointer to helper and clock so they can get
        // reset for each test Class-- needed in order to ONLY start Jetty once across all the test classes
        // while still being able to start mysql before Jetty is started
        //
        public MysqlTestingHelper getMysqlTestingHelper() {
            return helper;
        }
        
        public Clock getClock() {
            return clock;
        }
    }

    public static class TestKillbillServerModule extends KillbillServerModule {

        private final MysqlTestingHelper helper;
        private final Clock clock;
        
        public TestKillbillServerModule(final MysqlTestingHelper helper, final Clock clock) {
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
            protected void installPaymentProviderPlugins(PaymentConfig config) {
                install(new MockPaymentProviderPluginModule(PLUGIN_NAME));
            }
        }

        protected void installKillbillModules(){

            /*
             * For a lack of getting module override working, copy all install modules from parent class...
             * 
            super.installKillbillModules();
            Modules.override(new com.ning.billing.payment.setup.PaymentModule()).with(new PaymentMockModule());
            */
            install(new EmailModule());
            install(new GlobalLockerModule());
            install(new FieldStoreModule());
            install(new TagStoreModule());
            install(new CatalogModule());
            install(new BusModule());
            install(new NotificationQueueModule());
            install(new CallContextModule());
            install(new AccountModule());
            install(new DefaultInvoiceModule());
            install(new TemplateModule());
            install(new DefaultEntitlementModule());
            install(new AnalyticsModule());
            install(new PaymentMockModule());
            install(new BeatrixModule());
            install(new DefaultJunctionModule());
            installClock();
        }

        @Override
        protected void configureDao() {
            bind(MysqlTestingHelper.class).toInstance(helper);
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

    @BeforeMethod(groups="slow")
    public void cleanupBeforeMethod() {
        busHandler.reset();
        helper.cleanupAllTables();
    }

    @BeforeClass(groups="slow")
    public void setupClass() throws IOException {
        loadConfig();
        httpClient = new AsyncHttpClient();
        mapper = new ObjectMapper();
        mapper.registerModule(new JodaModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mapper.setPropertyNamingStrategy(new PropertyNamingStrategy.LowerCaseWithUnderscoresStrategy());        

        busHandler = new TestApiListener(null);
        this.helper = listener.getMysqlTestingHelper();
        this.clock =  (ClockMock) listener.getClock();
    }
    
    private void setupMySQL() throws IOException {
        final String accountDdl = IOUtils.toString(TestIntegration.class.getResourceAsStream("/com/ning/billing/account/ddl.sql"));
        final String entitlementDdl = IOUtils.toString(TestIntegration.class.getResourceAsStream("/com/ning/billing/entitlement/ddl.sql"));
        final String invoiceDdl = IOUtils.toString(TestIntegration.class.getResourceAsStream("/com/ning/billing/invoice/ddl.sql"));
        final String paymentDdl = IOUtils.toString(TestIntegration.class.getResourceAsStream("/com/ning/billing/payment/ddl.sql"));
        final String utilDdl = IOUtils.toString(TestIntegration.class.getResourceAsStream("/com/ning/billing/util/ddl.sql"));
        final String analyticsDdl = IOUtils.toString(TestIntegration.class.getResourceAsStream("/com/ning/billing/analytics/ddl.sql"));        
        final String junctionDdl = IOUtils.toString(TestIntegration.class.getResourceAsStream("/com/ning/billing/junction/ddl.sql"));        

        helper.startMysql();

        helper.initDb(accountDdl);
        helper.initDb(entitlementDdl);
        helper.initDb(invoiceDdl);
        helper.initDb(paymentDdl);
        helper.initDb(utilDdl);
        helper.initDb(analyticsDdl);        
        helper.initDb(junctionDdl);        
   }


    private void loadConfig() {
        if (config == null) {
            config = new ConfigurationObjectFactory(System.getProperties()).build(CoreConfig.class);
        }
    }

    @BeforeSuite(groups="slow")
    public void setup() throws Exception {

        loadSystemPropertiesFromClasspath("/killbill.properties");
        loadConfig();
        
        this.helper = new MysqlTestingHelper();
        this.clock = new ClockMock();
        listener = new TestKillbillGuiceListener(helper, clock);
        server = new HttpServer();

        final Iterable<EventListener> eventListeners = new Iterable<EventListener>() {
            @Override
            public Iterator<EventListener> iterator() {
                ArrayList<EventListener> array = new ArrayList<EventListener>();
                array.add(listener);
                return array.iterator();
            }
        };
        server.configure(config, eventListeners, new HashMap<FilterHolder, String>());

        setupMySQL();
        helper.cleanupAllTables();

        server.start();
    }

    @AfterSuite(groups="slow")
    public void tearDown() {
        try {
            server.stop();
        } catch (Exception e) {

        }
        if (helper != null) {
            helper.stopMysql();
        }
    }


    protected AccountJson createAccount(String name, String key, String email) throws Exception {
        AccountJson input = getAccountJson(name, key, email);
        String baseJson = mapper.writeValueAsString(input);
        Response response = doPost(BaseJaxrsResource.ACCOUNTS_PATH, baseJson, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.CREATED.getStatusCode());

        String location = response.getHeader("Location");
        Assert.assertNotNull(location);

        // Retrieves by Id based on Location returned
        response = doGetWithUrl(location, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());

        baseJson = response.getResponseBody();
        AccountJson objFromJson = mapper.readValue(baseJson, AccountJson.class);
        Assert.assertNotNull(objFromJson);
        return objFromJson;
    }



    protected BundleJsonNoSubsciptions createBundle(String accountId, String key) throws Exception {
        BundleJsonNoSubsciptions input = new BundleJsonNoSubsciptions(null, accountId, key, null);
        String baseJson = mapper.writeValueAsString(input);
        Response response = doPost(BaseJaxrsResource.BUNDLES_PATH, baseJson, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.CREATED.getStatusCode());

        String location = response.getHeader("Location");
        Assert.assertNotNull(location);

        // Retrieves by Id based on Location returned
        response = doGetWithUrl(location, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());

        baseJson = response.getResponseBody();
        BundleJsonNoSubsciptions objFromJson = mapper.readValue(baseJson, BundleJsonNoSubsciptions.class);
        Assert.assertTrue(objFromJson.equalsNoId(input));
        return objFromJson;
    }

    protected SubscriptionJsonNoEvents createSubscription(final String bundleId, final String productName, final String productCategory, final String billingPeriod, final boolean waitCompletion) throws Exception {

        SubscriptionJsonNoEvents input = new SubscriptionJsonNoEvents(null, bundleId, null, productName, productCategory, billingPeriod, PriceListSet.DEFAULT_PRICELIST_NAME, null);
        String baseJson = mapper.writeValueAsString(input);


        Map<String, String> queryParams = waitCompletion ? getQueryParamsForCallCompletion("5") : DEFAULT_EMPTY_QUERY;
        Response response = doPost(BaseJaxrsResource.SUBSCRIPTIONS_PATH, baseJson, queryParams, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.CREATED.getStatusCode());

        String location = response.getHeader("Location");
        Assert.assertNotNull(location);

        // Retrieves by Id based on Location returned

        response = doGetWithUrl(location, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());

        baseJson = response.getResponseBody();
        SubscriptionJsonNoEvents objFromJson = mapper.readValue(baseJson, SubscriptionJsonNoEvents.class);
        Assert.assertTrue(objFromJson.equalsNoId(input));
        return objFromJson;
    }

    protected Map<String, String> getQueryParamsForCallCompletion(final String timeoutSec) {
        Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put(BaseJaxrsResource.QUERY_CALL_COMPLETION, "true");
        queryParams.put(BaseJaxrsResource.QUERY_CALL_TIMEOUT, timeoutSec);
        return queryParams;
    }

    //
    // HTTP CLIENT HELPERS
    //
    protected Response doPost(final String uri, final String body, final Map<String, String> queryParams, final int timeoutSec) {
        BoundRequestBuilder builder = getBuilderWithHeaderAndQuery("POST", getUrlFromUri(uri), queryParams);
        if (body != null) {
            builder.setBody(body);
        } else {
            builder.setBody("{}");
        }
        return executeAndWait(builder, timeoutSec, true);
    }

    protected Response doPut(final String uri, final String body, final Map<String, String> queryParams, final int timeoutSec) {
        final String url = String.format("http://%s:%d%s", config.getServerHost(), config.getServerPort(), uri);
        BoundRequestBuilder builder = getBuilderWithHeaderAndQuery("PUT", url, queryParams);
        if (body != null) {
            builder.setBody(body);
        } else {
            builder.setBody("{}");
        }
        return executeAndWait(builder, timeoutSec, true);
    }

    protected Response doDelete(final String uri, final Map<String, String> queryParams, final int timeoutSec) {
        final String url = String.format("http://%s:%d%s", config.getServerHost(), config.getServerPort(), uri);
        BoundRequestBuilder builder = getBuilderWithHeaderAndQuery("DELETE", url, queryParams);
        return executeAndWait(builder, timeoutSec, true);
    }

    protected Response doGet(final String uri, final Map<String, String> queryParams, final int timeoutSec) {
        final String url = String.format("http://%s:%d%s", config.getServerHost(), config.getServerPort(), uri);
        return doGetWithUrl(url, queryParams, timeoutSec);
    }

    protected Response doGetWithUrl(final String url, final Map<String, String> queryParams, final int timeoutSec) {
        BoundRequestBuilder builder = getBuilderWithHeaderAndQuery("GET", url, queryParams);
        return executeAndWait(builder, timeoutSec, false);
    }

    private Response executeAndWait(final BoundRequestBuilder builder, final int timeoutSec, final boolean addContextHeader) {
        
        if (addContextHeader) {
            builder.addHeader(BaseJaxrsResource.HDR_CREATED_BY, createdBy);
            builder.addHeader(BaseJaxrsResource.HDR_REASON, reason);
            builder.addHeader(BaseJaxrsResource.HDR_COMMENT, comment);            
        }
        
        Response response = null;
        try {
            ListenableFuture<Response> futureStatus = 
                builder.execute(new AsyncCompletionHandler<Response>() {
                    @Override
                    public Response onCompleted(Response response) throws Exception {
                        return response;
                    }
                });
            response = futureStatus.get(timeoutSec, TimeUnit.SECONDS);
        } catch (Exception e) {
            Assert.fail(e.getMessage());			
        }
        Assert.assertNotNull(response);
        return response;
    }

    private String getUrlFromUri(final String uri) {
        return String.format("http://%s:%d%s", config.getServerHost(), config.getServerPort(), uri);
    }

    private BoundRequestBuilder getBuilderWithHeaderAndQuery(final String verb, final String url, final Map<String, String> queryParams) {
        BoundRequestBuilder builder = null;
        if (verb.equals("GET")) {
            builder = httpClient.prepareGet(url);
        } else if (verb.equals("POST")) {
            builder = httpClient.preparePost(url);
        } else if (verb.equals("PUT")) {
            builder = httpClient.preparePut(url);			
        } else if (verb.equals("DELETE")) {
            builder = httpClient.prepareDelete(url);			
        }
        builder.addHeader(HEADER_CONTENT_TYPE, CONTENT_TYPE);
        for (Entry<String, String> q : queryParams.entrySet()) {
            builder.addQueryParameter(q.getKey(), q.getValue());
        }
        return builder;
    }

    public AccountJson getAccountJson(final String name, final String externalKey, final String email) {
        String accountId = UUID.randomUUID().toString();
        int length = 4;
        int billCycleDay = 12;
        String currency = "USD";
        String paymentProvider = "noop";
        String timeZone = "UTC";
        String address1 = "12 rue des ecoles";
        String address2 = "Poitier";
        String company = "Renault";
        String state = "Poitou";
        String country = "France";
        String phone = "81 53 26 56";

        AccountJson accountJson = new AccountJson(accountId, name, length, externalKey, email, billCycleDay, currency, paymentProvider, timeZone, address1, address2, company, state, country, phone);
        return accountJson;
    }
    
    /**
     * 
     * We could implement a ClockResource in jaxrs with the ability to sync on user token
     * but until we have a strong need for it, this is in the TODO list...
     */
    protected void crappyWaitForLackOfProperSynchonization() throws Exception {
        Thread.sleep(7000);
    }

}
