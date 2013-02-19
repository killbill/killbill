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
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;
import javax.ws.rs.core.Response.Status;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;

import com.ning.billing.GuicyKillbillTestSuiteWithEmbeddedDB;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.jaxrs.json.AccountEmailJson;
import com.ning.billing.jaxrs.json.AccountJson;
import com.ning.billing.jaxrs.json.AccountTimelineJson;
import com.ning.billing.jaxrs.json.BillCycleDayJson;
import com.ning.billing.jaxrs.json.BundleJsonNoSubscriptions;
import com.ning.billing.jaxrs.json.ChargebackJson;
import com.ning.billing.jaxrs.json.CreditJson;
import com.ning.billing.jaxrs.json.InvoiceItemJsonSimple;
import com.ning.billing.jaxrs.json.InvoiceJsonSimple;
import com.ning.billing.jaxrs.json.InvoiceJsonWithItems;
import com.ning.billing.jaxrs.json.OverdueStateJson;
import com.ning.billing.jaxrs.json.PaymentJsonSimple;
import com.ning.billing.jaxrs.json.PaymentJsonWithBundleKeys;
import com.ning.billing.jaxrs.json.PaymentMethodJson;
import com.ning.billing.jaxrs.json.PaymentMethodJson.PaymentMethodPluginDetailJson;
import com.ning.billing.jaxrs.json.PaymentMethodJson.PaymentMethodProperties;
import com.ning.billing.jaxrs.json.RefundJson;
import com.ning.billing.jaxrs.json.SubscriptionJsonNoEvents;
import com.ning.billing.jaxrs.json.TenantJson;
import com.ning.billing.jaxrs.resources.JaxrsResource;
import com.ning.billing.util.api.AuditLevel;
import com.ning.http.client.AsyncCompletionHandler;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClient.BoundRequestBuilder;
import com.ning.http.client.ListenableFuture;
import com.ning.http.client.Response;
import com.ning.jetty.core.CoreConfig;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableMap;

import static com.ning.billing.jaxrs.resources.JaxrsResource.ACCOUNTS;
import static com.ning.billing.jaxrs.resources.JaxrsResource.BUNDLES;
import static com.ning.billing.jaxrs.resources.JaxrsResource.QUERY_DELETE_DEFAULT_PM_WITH_AUTO_PAY_OFF;
import static com.ning.billing.jaxrs.resources.JaxrsResource.QUERY_PAYMENT_METHOD_PLUGIN_INFO;
import static com.ning.billing.jaxrs.resources.JaxrsResource.SUBSCRIPTIONS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public abstract class KillbillClient extends GuicyKillbillTestSuiteWithEmbeddedDB {

    protected static final String PLUGIN_NAME = "noop";

    protected static final int DEFAULT_HTTP_TIMEOUT_SEC = 5;

    protected static final Map<String, String> DEFAULT_EMPTY_QUERY = new HashMap<String, String>();

    private static final Logger log = LoggerFactory.getLogger(TestJaxrsBase.class);

    public static final String HEADER_CONTENT_TYPE = "Content-type";
    public static final String CONTENT_TYPE = "application/json";

    protected static final String DEFAULT_CURRENCY = "USD";

    protected CoreConfig config;
    protected AsyncHttpClient httpClient;
    protected ObjectMapper mapper;

    // Context information to be passed around
    protected static final String createdBy = "Toto";
    protected static final String reason = "i am god";
    protected static final String comment = "no comment";

    protected List<PaymentMethodProperties> getPaymentMethodCCProperties() {
        final List<PaymentMethodProperties> properties = new ArrayList<PaymentMethodProperties>();
        properties.add(new PaymentMethodProperties("type", "CreditCard", false));
        properties.add(new PaymentMethodProperties("cardType", "Visa", false));
        properties.add(new PaymentMethodProperties("cardHolderName", "Mr Sniff", false));
        properties.add(new PaymentMethodProperties("expirationDate", "2015-08", false));
        properties.add(new PaymentMethodProperties("maskNumber", "3451", false));
        properties.add(new PaymentMethodProperties("address1", "23, rue des cerisiers", false));
        properties.add(new PaymentMethodProperties("address2", "", false));
        properties.add(new PaymentMethodProperties("city", "Toulouse", false));
        properties.add(new PaymentMethodProperties("country", "France", false));
        properties.add(new PaymentMethodProperties("postalCode", "31320", false));
        properties.add(new PaymentMethodProperties("state", "Midi-Pyrenees", false));
        return properties;
    }

    protected List<PaymentMethodProperties> getPaymentMethodPaypalProperties() {
        final List<PaymentMethodProperties> properties = new ArrayList<PaymentMethodJson.PaymentMethodProperties>();
        properties.add(new PaymentMethodProperties("type", "CreditCard", false));
        properties.add(new PaymentMethodProperties("email", "zouzou@laposte.fr", false));
        properties.add(new PaymentMethodProperties("baid", "23-8787d-R", false));
        return properties;
    }

    protected PaymentMethodJson getPaymentMethodJson(final String accountId, final List<PaymentMethodProperties> properties) {
        final PaymentMethodPluginDetailJson info = new PaymentMethodPluginDetailJson(null, properties);
        return new PaymentMethodJson(null, accountId, true, PLUGIN_NAME, info);
    }

    //
    // TENANT UTILITIES
    //

    protected String createTenant(final String apiKey, final String apiSecret) throws Exception {
        final String baseJson = mapper.writeValueAsString(new TenantJson(null, null, apiKey, apiSecret));
        final Response response = doPost(JaxrsResource.TENANTS_PATH, baseJson, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.CREATED.getStatusCode());
        return response.getHeader("Location");
    }

    protected String registerCallbackNotificationForTenant(final String callback) throws Exception {
        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put(JaxrsResource.QUERY_NOTIFICATION_CALLBACK, callback);
        final String uri = JaxrsResource.TENANTS_PATH + "/" + JaxrsResource.REGISTER_NOTIFICATION_CALLBACK;
        final Response response = doPost(uri, null, queryParams, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.CREATED.getStatusCode());
        return response.getHeader("Location");
    }

    //
    // ACCOUNT UTILITIES
    //

    protected AccountJson getAccountById(final String id) throws Exception {
        final String uri = JaxrsResource.ACCOUNTS_PATH + "/" + id;
        final Response response = doGet(uri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());

        final String baseJson = response.getResponseBody();
        final AccountJson objFromJson = mapper.readValue(baseJson, AccountJson.class);
        Assert.assertNotNull(objFromJson);

        return objFromJson;
    }

    protected AccountJson getAccountByExternalKey(final String externalKey) throws Exception {
        final Response response = getAccountByExternalKeyNoValidation(externalKey);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());

        final String baseJson = response.getResponseBody();
        final AccountJson objFromJson = mapper.readValue(baseJson, AccountJson.class);
        Assert.assertNotNull(objFromJson);

        return objFromJson;
    }

    protected Response getAccountByExternalKeyNoValidation(final String externalKey) {
        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put(JaxrsResource.QUERY_EXTERNAL_KEY, externalKey);
        return doGet(JaxrsResource.ACCOUNTS_PATH, queryParams, DEFAULT_HTTP_TIMEOUT_SEC);
    }

    protected AccountTimelineJson getAccountTimeline(final String accountId) throws Exception {
        return doGetAccountTimeline(accountId, AuditLevel.NONE);
    }

    protected AccountTimelineJson getAccountTimelineWithAudits(final String accountId, final AuditLevel auditLevel) throws Exception {
        return doGetAccountTimeline(accountId, auditLevel);
    }

    private AccountTimelineJson doGetAccountTimeline(final String accountId, final AuditLevel auditLevel) throws Exception {
        final String uri = JaxrsResource.ACCOUNTS_PATH + "/" + accountId + "/" + JaxrsResource.TIMELINE;

        final Response response = doGet(uri, ImmutableMap.<String, String>of(JaxrsResource.QUERY_AUDIT, auditLevel.toString()), DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());

        final String baseJson = response.getResponseBody();
        final AccountTimelineJson objFromJson = mapper.readValue(baseJson, AccountTimelineJson.class);
        assertNotNull(objFromJson);

        return objFromJson;
    }

    protected AccountJson createAccountWithDefaultPaymentMethod() throws Exception {
        final AccountJson input = createAccount();
        return doCreateAccountWithDefaultPaymentMethod(input);
    }

    protected AccountJson createAccountWithDefaultPaymentMethod(final String name, final String key, final String email) throws Exception {
        final AccountJson input = createAccount(name, key, email);
        return doCreateAccountWithDefaultPaymentMethod(input);
    }

    protected AccountJson doCreateAccountWithDefaultPaymentMethod(final AccountJson input) throws Exception {
        final String uri = JaxrsResource.ACCOUNTS_PATH + "/" + input.getAccountId() + "/" + JaxrsResource.PAYMENT_METHODS;
        final PaymentMethodJson paymentMethodJson = getPaymentMethodJson(input.getAccountId(), null);
        String baseJson = mapper.writeValueAsString(paymentMethodJson);
        Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put(JaxrsResource.QUERY_PAYMENT_METHOD_IS_DEFAULT, "true");

        Response response = doPost(uri, baseJson, queryParams, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.CREATED.getStatusCode());

        queryParams = new HashMap<String, String>();
        queryParams.put(JaxrsResource.QUERY_EXTERNAL_KEY, input.getExternalKey());
        response = doGet(JaxrsResource.ACCOUNTS_PATH, queryParams, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        baseJson = response.getResponseBody();
        final AccountJson objFromJson = mapper.readValue(baseJson, AccountJson.class);
        Assert.assertNotNull(objFromJson);
        Assert.assertNotNull(objFromJson.getPaymentMethodId());
        return objFromJson;
    }

    protected AccountJson createAccount() throws Exception {
        return createAccount(UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString().substring(0, 5) + '@' + UUID.randomUUID().toString().substring(0, 5));
    }

    protected AccountJson createAccount(final String name, final String key, final String email) throws Exception {
        Response response = createAccountNoValidation(name, key, email);
        final String baseJson;
        Assert.assertEquals(response.getStatusCode(), Status.CREATED.getStatusCode());

        final String location = response.getHeader("Location");
        Assert.assertNotNull(location);

        // Retrieves by Id based on Location returned
        response = doGetWithUrl(location, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());

        baseJson = response.getResponseBody();
        final AccountJson objFromJson = mapper.readValue(baseJson, AccountJson.class);
        Assert.assertNotNull(objFromJson);
        return objFromJson;
    }

    protected Response createAccountNoValidation() throws IOException {
        return createAccountNoValidation(UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString().substring(0, 5) + '@' + UUID.randomUUID().toString().substring(0, 5));
    }

    protected Response createAccountNoValidation(final String name, final String key, final String email) throws IOException {
        final AccountJson input = getAccountJson(name, key, email);
        final String baseJson = mapper.writeValueAsString(input);
        return doPost(JaxrsResource.ACCOUNTS_PATH, baseJson, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
    }

    protected AccountJson updateAccount(final String accountId, final AccountJson newInput) throws Exception {
        final String baseJson = mapper.writeValueAsString(newInput);

        final String uri = JaxrsResource.ACCOUNTS_PATH + "/" + accountId;
        final Response response = doPut(uri, baseJson, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());

        final String retrievedJson = response.getResponseBody();
        final AccountJson objFromJson = mapper.readValue(retrievedJson, AccountJson.class);
        assertNotNull(objFromJson);

        return objFromJson;
    }

    protected List<AccountEmailJson> getEmailsForAccount(final String accountId) throws Exception {
        final String uri = JaxrsResource.ACCOUNTS_PATH + "/" + accountId + "/" + JaxrsResource.EMAILS;

        final Response response = doGet(uri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());

        return mapper.readValue(response.getResponseBody(), new TypeReference<List<AccountEmailJson>>() {});
    }

    protected void addEmailToAccount(final String accountId, final AccountEmailJson email) throws Exception {
        final String uri = JaxrsResource.ACCOUNTS_PATH + "/" + accountId + "/" + JaxrsResource.EMAILS;

        final String emailString = mapper.writeValueAsString(email);
        final Response response = doPost(uri, emailString, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.CREATED.getStatusCode());
    }

    protected void removeEmailFromAccount(final String accountId, final String email) throws Exception {
        final String uri = JaxrsResource.ACCOUNTS_PATH + "/" + accountId + "/" + JaxrsResource.EMAILS;

        final Response fifthResponse = doDelete(uri + "/" + email, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(fifthResponse.getStatusCode(), javax.ws.rs.core.Response.Status.OK.getStatusCode());
    }

    protected BundleJsonNoSubscriptions createBundle(final String accountId, final String key) throws Exception {
        final BundleJsonNoSubscriptions input = new BundleJsonNoSubscriptions(null, accountId, key, null, null);
        String baseJson = mapper.writeValueAsString(input);
        Response response = doPost(JaxrsResource.BUNDLES_PATH, baseJson, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.CREATED.getStatusCode());

        final String location = response.getHeader("Location");
        Assert.assertNotNull(location);

        // Retrieves by Id based on Location returned
        response = doGetWithUrl(location, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());

        baseJson = response.getResponseBody();
        final BundleJsonNoSubscriptions objFromJson = mapper.readValue(baseJson, BundleJsonNoSubscriptions.class);
        Assert.assertTrue(objFromJson.equalsNoId(input));
        return objFromJson;
    }

    protected SubscriptionJsonNoEvents createSubscription(final String bundleId, final String productName, final String productCategory, final String billingPeriod, final boolean waitCompletion) throws Exception {

        final SubscriptionJsonNoEvents input = new SubscriptionJsonNoEvents(null, bundleId, null, productName, productCategory,
                                                                            billingPeriod, PriceListSet.DEFAULT_PRICELIST_NAME,
                                                                            null, null, null);
        String baseJson = mapper.writeValueAsString(input);

        final Map<String, String> queryParams = waitCompletion ? getQueryParamsForCallCompletion("5") : DEFAULT_EMPTY_QUERY;
        Response response = doPost(JaxrsResource.SUBSCRIPTIONS_PATH, baseJson, queryParams, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.CREATED.getStatusCode());

        final String location = response.getHeader("Location");
        Assert.assertNotNull(location);

        // Retrieves by Id based on Location returned

        response = doGetWithUrl(location, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());

        baseJson = response.getResponseBody();
        final SubscriptionJsonNoEvents objFromJson = mapper.readValue(baseJson, SubscriptionJsonNoEvents.class);
        Assert.assertTrue(objFromJson.equalsNoSubscriptionIdNoStartDateNoCTD(input));
        return objFromJson;
    }

    //
    // INVOICE UTILITIES
    //

    protected AccountJson createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice() throws Exception {
        final AccountJson accountJson = createAccountWithDefaultPaymentMethod();
        assertNotNull(accountJson);

        // Add a bundle, subscription and move the clock to get the first invoice
        final BundleJsonNoSubscriptions bundleJson = createBundle(accountJson.getAccountId(), UUID.randomUUID().toString());
        assertNotNull(bundleJson);
        final SubscriptionJsonNoEvents subscriptionJson = createSubscription(bundleJson.getBundleId(), "Shotgun", ProductCategory.BASE.toString(), BillingPeriod.MONTHLY.toString(), true);
        assertNotNull(subscriptionJson);
        clock.addDays(32);
        crappyWaitForLackOfProperSynchonization();

        return accountJson;
    }

    protected AccountJson createAccountNoPMBundleAndSubscriptionAndWaitForFirstInvoice() throws Exception {
        // Create an account with no payment method
        final AccountJson accountJson = createAccount();
        assertNotNull(accountJson);

        // Add a bundle, subscription and move the clock to get the first invoice
        final BundleJsonNoSubscriptions bundleJson = createBundle(accountJson.getAccountId(), UUID.randomUUID().toString());
        assertNotNull(bundleJson);
        final SubscriptionJsonNoEvents subscriptionJson = createSubscription(bundleJson.getBundleId(), "Shotgun", ProductCategory.BASE.toString(), BillingPeriod.MONTHLY.toString(), true);
        assertNotNull(subscriptionJson);
        clock.addMonths(1);
        crappyWaitForLackOfProperSynchonization();

        // No payment will be triggered as the account doesn't have a payment method

        return accountJson;
    }

    protected InvoiceJsonSimple getInvoice(final String invoiceId) throws IOException {
        return doGetInvoice(invoiceId, Boolean.FALSE, InvoiceJsonSimple.class);
    }

    protected InvoiceJsonWithItems getInvoiceWithItems(final String invoiceId) throws IOException {
        return doGetInvoice(invoiceId, Boolean.TRUE, InvoiceJsonWithItems.class);
    }

    private <T> T doGetInvoice(final String invoiceId, final Boolean withItems, final Class<T> clazz) throws IOException {
        final String uri = JaxrsResource.INVOICES_PATH + "/" + invoiceId;

        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put(JaxrsResource.QUERY_INVOICE_WITH_ITEMS, withItems.toString());

        final Response response = doGet(uri, queryParams, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        final String baseJson = response.getResponseBody();

        final T firstInvoiceJson = mapper.readValue(baseJson, clazz);
        assertNotNull(firstInvoiceJson);

        return firstInvoiceJson;
    }

    protected List<InvoiceJsonSimple> getInvoicesForAccount(final String accountId) throws IOException {
        return doGetInvoicesForAccount(accountId, Boolean.FALSE, new TypeReference<List<InvoiceJsonSimple>>() {});
    }

    protected List<InvoiceJsonWithItems> getInvoicesWithItemsForAccount(final String accountId) throws IOException {
        return doGetInvoicesForAccount(accountId, Boolean.TRUE, new TypeReference<List<InvoiceJsonWithItems>>() {});
    }

    private <T> List<T> doGetInvoicesForAccount(final String accountId, final Boolean withItems, final TypeReference<List<T>> clazz) throws IOException {
        final String invoicesURI = JaxrsResource.INVOICES_PATH;

        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put(JaxrsResource.QUERY_ACCOUNT_ID, accountId);
        queryParams.put(JaxrsResource.QUERY_INVOICE_WITH_ITEMS, withItems.toString());

        final Response invoicesResponse = doGet(invoicesURI, queryParams, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(invoicesResponse.getStatusCode(), Status.OK.getStatusCode());

        final String invoicesBaseJson = invoicesResponse.getResponseBody();
        final List<T> invoices = mapper.readValue(invoicesBaseJson, clazz);
        assertNotNull(invoices);

        return invoices;
    }

    protected InvoiceJsonSimple createDryRunInvoice(final String accountId, final DateTime futureDate) throws IOException {
        final String uri = JaxrsResource.INVOICES_PATH;

        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put(JaxrsResource.QUERY_ACCOUNT_ID, accountId);
        queryParams.put(JaxrsResource.QUERY_TARGET_DATE, futureDate.toString());
        queryParams.put(JaxrsResource.QUERY_DRY_RUN, "true");

        final Response response = doPost(uri, null, queryParams, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());

        final String baseJson = response.getResponseBody();
        final InvoiceJsonSimple futureInvoice = mapper.readValue(baseJson, InvoiceJsonSimple.class);
        assertNotNull(futureInvoice);

        return futureInvoice;
    }

    protected void createInvoice(final String accountId, final DateTime futureDate) throws IOException {
        final String uri = JaxrsResource.INVOICES_PATH;

        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put(JaxrsResource.QUERY_ACCOUNT_ID, accountId);
        queryParams.put(JaxrsResource.QUERY_TARGET_DATE, futureDate.toString());

        final Response response = doPost(uri, null, queryParams, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.CREATED.getStatusCode());

        final String location = response.getHeader("Location");
        Assert.assertNotNull(location);
    }

    protected void adjustInvoiceItem(final String accountId, final String invoiceId, final String invoiceItemId,
                                     @Nullable final DateTime requestedDate, @Nullable final BigDecimal amount, @Nullable final Currency currency) throws IOException {
        final String uri = JaxrsResource.INVOICES_PATH + "/" + invoiceId;

        final Map<String, String> queryParams = new HashMap<String, String>();
        if (requestedDate != null) {
            queryParams.put(JaxrsResource.QUERY_REQUESTED_DT, requestedDate.toDateTimeISO().toString());
        }

        final InvoiceItemJsonSimple adjustment = new InvoiceItemJsonSimple(invoiceItemId, null, null, accountId, null, null, null, null,
                                                                           null, null, null, amount, currency, null);
        final String adjustmentJson = mapper.writeValueAsString(adjustment);
        final Response response = doPost(uri, adjustmentJson, queryParams, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.CREATED.getStatusCode());
    }

    protected InvoiceJsonWithItems createExternalCharge(final String accountId, final BigDecimal amount, @Nullable final String bundleId,
                                                        @Nullable final Currency currency, @Nullable final DateTime requestedDate) throws Exception {
        return doCreateExternalCharge(accountId, null, bundleId, amount, currency, requestedDate, JaxrsResource.CHARGES_PATH);
    }

    protected InvoiceJsonWithItems createExternalChargeForInvoice(final String accountId, final String invoiceId, @Nullable final String bundleId, final BigDecimal amount,
                                                                  @Nullable final Currency currency, @Nullable final DateTime requestedDate) throws Exception {
        final String uri = JaxrsResource.INVOICES_PATH + "/" + invoiceId + "/" + JaxrsResource.CHARGES;
        return doCreateExternalCharge(accountId, invoiceId, bundleId, amount, currency, requestedDate, uri);
    }

    private InvoiceJsonWithItems doCreateExternalCharge(final String accountId, @Nullable final String invoiceId, @Nullable final String bundleId, @Nullable final BigDecimal amount,
                                                        @Nullable final Currency currency, final DateTime requestedDate, final String uri) throws IOException {
        final Map<String, String> queryParams = new HashMap<String, String>();
        if (requestedDate != null) {
            queryParams.put(JaxrsResource.QUERY_REQUESTED_DT, requestedDate.toDateTimeISO().toString());
        }

        final InvoiceItemJsonSimple externalCharge = new InvoiceItemJsonSimple(null, invoiceId, null, accountId, bundleId, null, null, null,
                                                                               null, null, null, amount, currency, null);
        final String externalChargeJson = mapper.writeValueAsString(externalCharge);
        final Response response = doPost(uri, externalChargeJson, queryParams, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.CREATED.getStatusCode());

        final String location = response.getHeader("Location");
        Assert.assertNotNull(location);

        final Map<String, String> queryParamsForInvoice = new HashMap<String, String>();
        queryParamsForInvoice.put(JaxrsResource.QUERY_ACCOUNT_ID, accountId);
        queryParamsForInvoice.put(JaxrsResource.QUERY_INVOICE_WITH_ITEMS, "true");
        final Response invoiceResponse = doGetWithUrl(location, queryParamsForInvoice, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(invoiceResponse.getStatusCode(), Status.OK.getStatusCode());

        final String invoicesBaseJson = invoiceResponse.getResponseBody();
        final InvoiceJsonWithItems invoice = mapper.readValue(invoicesBaseJson, new TypeReference<InvoiceJsonWithItems>() {});
        assertNotNull(invoice);

        return invoice;
    }

    //
    // PAYMENT UTILITIES
    //

    protected PaymentJsonSimple getPayment(final String paymentId) throws IOException {
        return doGetPayment(paymentId, DEFAULT_EMPTY_QUERY, PaymentJsonSimple.class);
    }

    protected PaymentJsonWithBundleKeys getPaymentWithRefundsAndChargebacks(final String paymentId) throws IOException {
        return doGetPayment(paymentId, ImmutableMap.<String, String>of(JaxrsResource.QUERY_PAYMENT_WITH_REFUNDS_AND_CHARGEBACKS, "true"), PaymentJsonWithBundleKeys.class);
    }

    protected <T extends PaymentJsonSimple> T doGetPayment(final String paymentId, final Map<String, String> queryParams, final Class<T> clazz) throws IOException {
        final String paymentURI = JaxrsResource.PAYMENTS_PATH + "/" + paymentId;

        final Response paymentResponse = doGet(paymentURI, queryParams, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(paymentResponse.getStatusCode(), Status.OK.getStatusCode());

        final T paymentJsonSimple = mapper.readValue(paymentResponse.getResponseBody(), clazz);
        assertNotNull(paymentJsonSimple);

        return paymentJsonSimple;
    }

    protected PaymentMethodJson getPaymentMethod(final String paymentMethodId) throws IOException {
        final String paymentMethodURI = JaxrsResource.PAYMENT_METHODS_PATH + "/" + paymentMethodId;
        final Response paymentMethodResponse = doGet(paymentMethodURI, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(paymentMethodResponse.getStatusCode(), Status.OK.getStatusCode());

        final PaymentMethodJson paymentMethodJson = mapper.readValue(paymentMethodResponse.getResponseBody(), PaymentMethodJson.class);
        assertNotNull(paymentMethodJson);

        return paymentMethodJson;
    }

    protected PaymentMethodJson getPaymentMethodWithPluginInfo(final String paymentMethodId) throws IOException {
        final String paymentMethodURI = JaxrsResource.PAYMENT_METHODS_PATH + "/" + paymentMethodId;

        final Map<String, String> queryPaymentMethods = new HashMap<String, String>();
        queryPaymentMethods.put(QUERY_PAYMENT_METHOD_PLUGIN_INFO, "true");
        final Response paymentMethodResponse = doGet(paymentMethodURI, queryPaymentMethods, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(paymentMethodResponse.getStatusCode(), Status.OK.getStatusCode());

        final PaymentMethodJson paymentMethodJson = mapper.readValue(paymentMethodResponse.getResponseBody(), PaymentMethodJson.class);
        assertNotNull(paymentMethodJson);

        return paymentMethodJson;
    }

    protected void deletePaymentMethod(final String paymentMethodId, final Boolean deleteDefault) throws IOException {
        final String paymentMethodURI = JaxrsResource.PAYMENT_METHODS_PATH + "/" + paymentMethodId;

        final Response response = doDelete(paymentMethodURI, ImmutableMap.<String, String>of(QUERY_DELETE_DEFAULT_PM_WITH_AUTO_PAY_OFF, deleteDefault.toString()), DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), javax.ws.rs.core.Response.Status.OK.getStatusCode());
    }

    protected List<PaymentJsonSimple> getPaymentsForAccount(final String accountId) throws IOException {
        final String paymentsURI = JaxrsResource.ACCOUNTS_PATH + "/" + accountId + "/" + JaxrsResource.PAYMENTS;
        final Response paymentsResponse = doGet(paymentsURI, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(paymentsResponse.getStatusCode(), Status.OK.getStatusCode());
        final String paymentsBaseJson = paymentsResponse.getResponseBody();

        final List<PaymentJsonSimple> paymentJsonSimples = mapper.readValue(paymentsBaseJson, new TypeReference<List<PaymentJsonSimple>>() {});
        assertNotNull(paymentJsonSimples);

        return paymentJsonSimples;
    }

    protected List<PaymentJsonSimple> getPaymentsForInvoice(final String invoiceId) throws IOException {
        final String uri = JaxrsResource.INVOICES_PATH + "/" + invoiceId + "/" + JaxrsResource.PAYMENTS;
        final Response response = doGet(uri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);

        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        final String baseJson = response.getResponseBody();
        final List<PaymentJsonSimple> objFromJson = mapper.readValue(baseJson, new TypeReference<List<PaymentJsonSimple>>() {});
        assertNotNull(objFromJson);

        return objFromJson;
    }

    protected void payAllInvoices(final AccountJson accountJson, final Boolean externalPayment) throws IOException {
        final PaymentJsonSimple payment = new PaymentJsonSimple(null, null, accountJson.getAccountId(), null, null, null, null,
                                                                null, 0, null, null, null, null, null, null, null);
        final String postJson = mapper.writeValueAsString(payment);

        final String uri = JaxrsResource.INVOICES_PATH + "/" + JaxrsResource.PAYMENTS;
        doPost(uri, postJson, ImmutableMap.<String, String>of("externalPayment", externalPayment.toString()), DEFAULT_HTTP_TIMEOUT_SEC);
    }

    protected List<PaymentJsonSimple> createInstaPayment(final AccountJson accountJson, final InvoiceJsonSimple invoice) throws IOException {
        final PaymentJsonSimple payment = new PaymentJsonSimple(invoice.getAmount(), BigDecimal.ZERO, accountJson.getAccountId(),
                                                                invoice.getInvoiceId(), null, null, null, null, 0, null, null, null, null, null, null, null);
        final String postJson = mapper.writeValueAsString(payment);

        final String uri = JaxrsResource.INVOICES_PATH + "/" + invoice.getInvoiceId() + "/" + JaxrsResource.PAYMENTS;
        doPost(uri, postJson, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);

        return getPaymentsForInvoice(invoice.getInvoiceId());
    }

    protected List<PaymentJsonSimple> createExternalPayment(final AccountJson accountJson, final String invoiceId, final BigDecimal paidAmount) throws IOException {
        final PaymentJsonSimple payment = new PaymentJsonSimple(paidAmount, BigDecimal.ZERO, accountJson.getAccountId(),
                                                                invoiceId, null, null, null, null, 0,
                                                                null, null, null, null, null, null, null);
        final String postJson = mapper.writeValueAsString(payment);

        final String paymentURI = JaxrsResource.INVOICES_PATH + "/" + invoiceId + "/" + JaxrsResource.PAYMENTS;
        final Response paymentResponse = doPost(paymentURI, postJson, ImmutableMap.<String, String>of("externalPayment", "true"), DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(paymentResponse.getStatusCode(), Status.CREATED.getStatusCode());

        return getPaymentsForInvoice(invoiceId);
    }

    //
    // CHARGEBACKS
    //

    protected ChargebackJson createChargeBack(final String paymentId, final BigDecimal chargebackAmount) throws IOException {
        final ChargebackJson input = new ChargebackJson(null, null, chargebackAmount, paymentId, null, null);
        final String jsonInput = mapper.writeValueAsString(input);

        // Create the chargeback
        final Response response = doPost(JaxrsResource.CHARGEBACKS_PATH, jsonInput, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.CREATED.getStatusCode(), response.getResponseBody());

        // Find the chargeback by location
        final String location = response.getHeader("Location");
        assertNotNull(location);
        final Response responseByLocation = doGetWithUrl(location, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(responseByLocation.getStatusCode(), Status.OK.getStatusCode());

        return mapper.readValue(responseByLocation.getResponseBody(), ChargebackJson.class);
    }

    //
    // REFUNDS
    //

    protected List<RefundJson> getRefundsForAccount(final String accountId) throws IOException {
        final String uri = JaxrsResource.ACCOUNTS_PATH + "/" + accountId + "/" + JaxrsResource.REFUNDS;
        final Response response = doGet(uri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);

        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        final String baseJson = response.getResponseBody();
        final List<RefundJson> refunds = mapper.readValue(baseJson, new TypeReference<List<RefundJson>>() {});
        assertNotNull(refunds);

        return refunds;
    }

    protected List<RefundJson> getRefundsForPayment(final String paymentId) throws IOException {
        final String uri = JaxrsResource.PAYMENTS_PATH + "/" + paymentId + "/" + JaxrsResource.REFUNDS;
        final Response response = doGet(uri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);

        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        final String baseJson = response.getResponseBody();
        final List<RefundJson> refunds = mapper.readValue(baseJson, new TypeReference<List<RefundJson>>() {});
        assertNotNull(refunds);

        return refunds;
    }

    protected RefundJson createRefund(final String paymentId, final BigDecimal amount) throws IOException {
        return doCreateRefund(paymentId, amount, false, ImmutableMap.<String, BigDecimal>of());
    }

    protected RefundJson createRefundWithInvoiceAdjustment(final String paymentId, final BigDecimal amount) throws IOException {
        return doCreateRefund(paymentId, amount, true, ImmutableMap.<String, BigDecimal>of());
    }

    protected RefundJson createRefundWithInvoiceItemAdjustment(final String paymentId, final String invoiceItemId, final BigDecimal amount) throws IOException {
        final Map<String, BigDecimal> adjustments = new HashMap<String, BigDecimal>();
        adjustments.put(invoiceItemId, amount);
        return doCreateRefund(paymentId, amount, true, adjustments);
    }

    private RefundJson doCreateRefund(final String paymentId, final BigDecimal amount, final boolean adjusted, final Map<String, BigDecimal> itemAdjustments) throws IOException {
        final String uri = JaxrsResource.PAYMENTS_PATH + "/" + paymentId + "/" + JaxrsResource.REFUNDS;

        final List<InvoiceItemJsonSimple> adjustments = new ArrayList<InvoiceItemJsonSimple>();
        for (final String itemId : itemAdjustments.keySet()) {
            adjustments.add(new InvoiceItemJsonSimple(itemId, null, null, null, null, null, null, null, null, null, null,
                                                      itemAdjustments.get(itemId), null, null));
        }
        final RefundJson refundJson = new RefundJson(null, paymentId, amount, DEFAULT_CURRENCY, adjusted, null, null, adjustments, null);
        final String baseJson = mapper.writeValueAsString(refundJson);
        final Response response = doPost(uri, baseJson, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.CREATED.getStatusCode());

        final String locationCC = response.getHeader("Location");
        Assert.assertNotNull(locationCC);

        // Retrieves by Id based on Location returned
        final Response retrievedResponse = doGetWithUrl(locationCC, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(retrievedResponse.getStatusCode(), Status.OK.getStatusCode());
        final String retrievedBaseJson = retrievedResponse.getResponseBody();
        final RefundJson retrievedRefundJson = mapper.readValue(retrievedBaseJson, RefundJson.class);
        assertNotNull(retrievedRefundJson);
        // Verify we have the adjusted items
        if (retrievedRefundJson.getAdjustments() != null) {
            final Set<String> allLinkedItemIds = new HashSet<String>(Collections2.transform(retrievedRefundJson.getAdjustments(), new Function<InvoiceItemJsonSimple, String>() {
                @Override
                public String apply(@Nullable final InvoiceItemJsonSimple input) {
                    if (input != null) {
                        return input.getLinkedInvoiceItemId();
                    } else {
                        return null;
                    }
                }
            }));
            assertEquals(allLinkedItemIds, itemAdjustments.keySet());
        }

        return retrievedRefundJson;
    }

    protected Map<String, String> getQueryParamsForCallCompletion(final String timeoutSec) {
        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put(JaxrsResource.QUERY_CALL_COMPLETION, "true");
        queryParams.put(JaxrsResource.QUERY_CALL_TIMEOUT, timeoutSec);
        return queryParams;
    }

    //
    // CREDITS
    //

    protected CreditJson createCreditForAccount(final String accountId, final BigDecimal creditAmount,
                                                final DateTime requestedDate, final DateTime effectiveDate) throws IOException {
        return createCreditForInvoice(accountId, null, creditAmount, requestedDate, effectiveDate);
    }

    protected CreditJson createCreditForInvoice(final String accountId, final String invoiceId, final BigDecimal creditAmount,
                                                final DateTime requestedDate, final DateTime effectiveDate) throws IOException {
        final CreditJson input = new CreditJson(creditAmount, invoiceId, UUID.randomUUID().toString(),
                                                requestedDate, effectiveDate,
                                                UUID.randomUUID().toString(), accountId,
                                                null);
        final String jsonInput = mapper.writeValueAsString(input);

        // Create the credit
        Response response = doPost(JaxrsResource.CREDITS_PATH, jsonInput, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.CREATED.getStatusCode(), response.getResponseBody());

        final String location = response.getHeader("Location");
        assertNotNull(location);

        // Retrieves by Id based on Location returned
        response = doGetWithUrl(location, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());

        return mapper.readValue(response.getResponseBody(), CreditJson.class);
    }

    //
    // OVERDUE
    //

    protected OverdueStateJson getOverdueStateForAccount(final String accountId) throws Exception {
        return doGetOverdueState(accountId, ACCOUNTS);
    }

    protected OverdueStateJson getOverdueStateForBundle(final String bundleId) throws Exception {
        return doGetOverdueState(bundleId, BUNDLES);
    }

    protected OverdueStateJson getOverdueStateForSubscription(final String subscriptionId) throws Exception {
        return doGetOverdueState(subscriptionId, SUBSCRIPTIONS);
    }

    protected OverdueStateJson doGetOverdueState(final String id, final String resourceType) throws Exception {
        final String overdueURI = JaxrsResource.OVERDUE_PATH + "/" + resourceType + "/" + id;
        final Response overdueResponse = doGet(overdueURI, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(overdueResponse.getStatusCode(), Status.OK.getStatusCode());

        final OverdueStateJson overdueStateJson = mapper.readValue(overdueResponse.getResponseBody(), OverdueStateJson.class);
        assertNotNull(overdueStateJson);

        return overdueStateJson;
    }

    //
    // HTTP CLIENT HELPERS
    //
    protected Response doPost(final String uri, @Nullable final String body, final Map<String, String> queryParams, final int timeoutSec) {
        final BoundRequestBuilder builder = getBuilderWithHeaderAndQuery("POST", getUrlFromUri(uri), queryParams);
        if (body != null) {
            builder.setBody(body);
        } else {
            builder.setBody("{}");
        }
        return executeAndWait(builder, timeoutSec, true);
    }

    protected Response doPut(final String uri, final String body, final Map<String, String> queryParams, final int timeoutSec) {
        final String url = String.format("http://%s:%d%s", config.getServerHost(), config.getServerPort(), uri);
        final BoundRequestBuilder builder = getBuilderWithHeaderAndQuery("PUT", url, queryParams);
        if (body != null) {
            builder.setBody(body);
        } else {
            builder.setBody("{}");
        }
        return executeAndWait(builder, timeoutSec, true);
    }

    protected Response doDelete(final String uri, final Map<String, String> queryParams, final int timeoutSec) {
        final String url = String.format("http://%s:%d%s", config.getServerHost(), config.getServerPort(), uri);
        final BoundRequestBuilder builder = getBuilderWithHeaderAndQuery("DELETE", url, queryParams);
        return executeAndWait(builder, timeoutSec, true);
    }

    protected Response doGet(final String uri, final Map<String, String> queryParams, final int timeoutSec) {
        final String url = String.format("http://%s:%d%s", config.getServerHost(), config.getServerPort(), uri);
        return doGetWithUrl(url, queryParams, timeoutSec);
    }

    protected Response doGetWithUrl(final String url, final Map<String, String> queryParams, final int timeoutSec) {
        final BoundRequestBuilder builder = getBuilderWithHeaderAndQuery("GET", url, queryParams);
        return executeAndWait(builder, timeoutSec, false);
    }

    private Response executeAndWait(final BoundRequestBuilder builder, final int timeoutSec, final boolean addContextHeader) {

        if (addContextHeader) {
            builder.addHeader(JaxrsResource.HDR_CREATED_BY, createdBy);
            builder.addHeader(JaxrsResource.HDR_REASON, reason);
            builder.addHeader(JaxrsResource.HDR_COMMENT, comment);
        }

        Response response = null;
        try {
            final ListenableFuture<Response> futureStatus =
                    builder.execute(new AsyncCompletionHandler<Response>() {
                        @Override
                        public Response onCompleted(final Response response) throws Exception {
                            return response;
                        }
                    });
            response = futureStatus.get(timeoutSec, TimeUnit.SECONDS);
        } catch (final Exception e) {
            Assert.fail(e.getMessage());
        }
        Assert.assertNotNull(response);
        return response;
    }

    protected String getUrlFromUri(final String uri) {
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
        } else {
            Assert.fail("Unknown verb " + verb);
        }

        builder.addHeader(HEADER_CONTENT_TYPE, CONTENT_TYPE);
        for (final Entry<String, String> q : queryParams.entrySet()) {
            builder.addQueryParameter(q.getKey(), q.getValue());
        }

        return builder;
    }

    protected AccountJson getAccountJson() {
        return getAccountJson(UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString().substring(0, 5) + '@' + UUID.randomUUID().toString().substring(0, 5));
    }

    public AccountJson getAccountJson(final String name, final String externalKey, final String email) {
        final String accountId = UUID.randomUUID().toString();
        final int length = 4;
        // Let junction figure it out
        final BillCycleDayJson billCycleDay = null;
        final String currency = DEFAULT_CURRENCY;
        final String timeZone = "UTC";
        final String address1 = "12 rue des ecoles";
        final String address2 = "Poitier";
        final String postalCode = "44 567";
        final String company = "Renault";
        final String city = "Quelque part";
        final String state = "Poitou";
        final String country = "France";
        final String locale = "fr";
        final String phone = "81 53 26 56";

        // Note: the accountId payload is ignored on account creation
        return new AccountJson(accountId, name, length, externalKey, email, billCycleDay, currency, null, timeZone,
                               address1, address2, postalCode, company, city, state, country, locale, phone, false, false);
    }

    /**
     * We could implement a ClockResource in jaxrs with the ability to sync on user token
     * but until we have a strong need for it, this is in the TODO list...
     */
    protected void crappyWaitForLackOfProperSynchonization() throws Exception {
        Thread.sleep(5000);
    }
}
