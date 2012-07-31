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

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response.Status;

import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.jaxrs.json.AccountJson;
import com.ning.billing.jaxrs.json.BundleJsonNoSubscriptions;
import com.ning.billing.jaxrs.json.InvoiceJsonSimple;
import com.ning.billing.jaxrs.json.PaymentJsonSimple;
import com.ning.billing.jaxrs.json.PaymentMethodJson;
import com.ning.billing.jaxrs.json.SubscriptionJsonNoEvents;
import com.ning.billing.jaxrs.resources.JaxrsResource;
import com.ning.billing.payment.provider.ExternalPaymentProviderPlugin;
import com.ning.http.client.Response;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableMap;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class TestInvoice extends TestJaxrsBase {

    private static final Logger log = LoggerFactory.getLogger(TestInvoice.class);

    @Test(groups = "slow")
    public void testInvoiceOk() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final AccountJson accountJson = createAccountWithDefaultPaymentMethod("poupou", "qhddffrwe", "poupou@yahoo.com");
        assertNotNull(accountJson);

        final BundleJsonNoSubscriptions bundleJson = createBundle(accountJson.getAccountId(), "9967599");
        assertNotNull(bundleJson);

        final SubscriptionJsonNoEvents subscriptionJson = createSubscription(bundleJson.getBundleId(), "Shotgun", ProductCategory.BASE.toString(), BillingPeriod.MONTHLY.toString(), true);
        assertNotNull(subscriptionJson);

        // MOVE AFTER TRIAL
        final Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(30));
        clock.addDeltaFromReality(it.toDurationMillis());
        crappyWaitForLackOfProperSynchonization();

        String uri = JaxrsResource.INVOICES_PATH;
        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put(JaxrsResource.QUERY_ACCOUNT_ID, accountJson.getAccountId());

        Response response = doGet(uri, queryParams, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        String baseJson = response.getResponseBody();
        List<InvoiceJsonSimple> objFromJson = mapper.readValue(baseJson, new TypeReference<List<InvoiceJsonSimple>>() {});
        assertNotNull(objFromJson);
        log.info(baseJson);
        assertEquals(objFromJson.size(), 2);

        // Check we can retrieve an individual invoice
        uri = JaxrsResource.INVOICES_PATH + "/" + objFromJson.get(0).getInvoiceId();
        response = doGet(uri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        baseJson = response.getResponseBody();
        final InvoiceJsonSimple firstInvoiceJson = mapper.readValue(baseJson, InvoiceJsonSimple.class);
        assertNotNull(objFromJson);
        assertEquals(firstInvoiceJson, objFromJson.get(0));

        // Then create a dryRun Invoice
        final DateTime futureDate = clock.getUTCNow().plusMonths(1).plusDays(3);
        uri = JaxrsResource.INVOICES_PATH;
        queryParams.put(JaxrsResource.QUERY_TARGET_DATE, futureDate.toString());
        queryParams.put(JaxrsResource.QUERY_DRY_RUN, "true");
        response = doPost(uri, null, queryParams, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        baseJson = response.getResponseBody();
        final InvoiceJsonSimple futureInvoice = mapper.readValue(baseJson, InvoiceJsonSimple.class);
        assertNotNull(futureInvoice);
        log.info(baseJson);

        // The one more time with no DryRun
        queryParams.remove(JaxrsResource.QUERY_DRY_RUN);
        response = doPost(uri, null, queryParams, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.CREATED.getStatusCode());

        final String location = response.getHeader("Location");
        Assert.assertNotNull(location);

        // Check again # invoices, should be 5 this time
        uri = JaxrsResource.INVOICES_PATH;
        response = doGet(uri, queryParams, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        baseJson = response.getResponseBody();
        objFromJson = mapper.readValue(baseJson, new TypeReference<List<InvoiceJsonSimple>>() {});
        assertNotNull(objFromJson);
        log.info(baseJson);
        assertEquals(objFromJson.size(), 3);
    }

    @Test(groups = "slow")
    public void testInvoicePayments() throws Exception {
        clock.setTime(new DateTime(2012, 4, 25, 0, 3, 42, 0));

        final AccountJson accountJson = createAccountWithDefaultPaymentMethod("nohup", "shtergyhwF", "nohup@yahoo.com");
        assertNotNull(accountJson);

        final BundleJsonNoSubscriptions bundleJson = createBundle(accountJson.getAccountId(), "391193");
        assertNotNull(bundleJson);

        final SubscriptionJsonNoEvents subscriptionJson = createSubscription(bundleJson.getBundleId(), "Shotgun", ProductCategory.BASE.toString(), BillingPeriod.MONTHLY.toString(), true);
        assertNotNull(subscriptionJson);

        clock.addDays(31);

        crappyWaitForLackOfProperSynchonization();

        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put(JaxrsResource.QUERY_ACCOUNT_ID, accountJson.getAccountId());
        String uri = JaxrsResource.INVOICES_PATH;
        Response response = doGet(uri, queryParams, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        String baseJson = response.getResponseBody();
        final List<InvoiceJsonSimple> invoices = mapper.readValue(baseJson, new TypeReference<List<InvoiceJsonSimple>>() {});
        assertNotNull(invoices);
        log.info(baseJson);
        assertEquals(invoices.size(), 2);

        for (final InvoiceJsonSimple cur : invoices) {

            uri = JaxrsResource.INVOICES_PATH + "/" + cur.getInvoiceId() + "/" + JaxrsResource.PAYMENTS;
            response = doGet(uri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);

            Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
            baseJson = response.getResponseBody();
            final List<PaymentJsonSimple> objFromJson = mapper.readValue(baseJson, new TypeReference<List<PaymentJsonSimple>>() {});
            assertNotNull(objFromJson);
            log.info(cur.getAmount().toString());
            if (cur.getAmount().compareTo(BigDecimal.ZERO) == 0) {
                assertEquals(objFromJson.size(), 0);
            } else {
                assertEquals(objFromJson.size(), 1);
                assertTrue(cur.getAmount().compareTo(objFromJson.get(0).getAmount()) == 0);
            }
        }
    }

    @Test(groups = "slow")
    public void testInvoiceCreatePayment() throws Exception {
        clock.setTime(new DateTime(2012, 4, 25, 0, 3, 42, 0));

        final AccountJson accountJson = createAccountWithDefaultPaymentMethod("nohup", "shtergyhwF", "nohup@yahoo.com");
        assertNotNull(accountJson);

        // STEPH MISSING SET ACCOUNT AUTO_PAY_OFF

        final BundleJsonNoSubscriptions bundleJson = createBundle(accountJson.getAccountId(), "391193");
        assertNotNull(bundleJson);

        final SubscriptionJsonNoEvents subscriptionJson = createSubscription(bundleJson.getBundleId(), "Shotgun", ProductCategory.BASE.toString(), BillingPeriod.MONTHLY.toString(), true);
        assertNotNull(subscriptionJson);

        // MOVE AFTER TRIAL
        clock.addDays(31);

        crappyWaitForLackOfProperSynchonization();

        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put(JaxrsResource.QUERY_ACCOUNT_ID, accountJson.getAccountId());
        String uri = JaxrsResource.INVOICES_PATH;
        Response response = doGet(uri, queryParams, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        String baseJson = response.getResponseBody();
        final List<InvoiceJsonSimple> invoices = mapper.readValue(baseJson, new TypeReference<List<InvoiceJsonSimple>>() {});
        assertNotNull(invoices);
        log.info(baseJson);
        assertEquals(invoices.size(), 2);

        for (final InvoiceJsonSimple cur : invoices) {
            if (cur.getAmount().compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }

            // CREATE INSTA PAYMENT
            final PaymentJsonSimple payment = new PaymentJsonSimple(cur.getAmount(), BigDecimal.ZERO, accountJson.getAccountId(),
                                                                    cur.getInvoiceId(), null, null, null, null, 0, null, null, null, null, null, null, null);
            final String postJson = mapper.writeValueAsString(payment);

            uri = JaxrsResource.INVOICES_PATH + "/" + cur.getInvoiceId() + "/" + JaxrsResource.PAYMENTS;
            doPost(uri, postJson, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);

            response = doGet(uri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);

            Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
            baseJson = response.getResponseBody();
            final List<PaymentJsonSimple> objFromJson = mapper.readValue(baseJson, new TypeReference<List<PaymentJsonSimple>>() {});
            assertNotNull(objFromJson);
            log.info(cur.getAmount().toString());
            assertEquals(objFromJson.size(), 1);
            assertTrue(cur.getAmount().compareTo(objFromJson.get(0).getAmount()) == 0);
        }
    }

    @Test(groups = "slow")
    public void testExternalPayment() throws Exception {
        // Create an account with no payment method
        final AccountJson accountJson = createAccount("eraahahildo", "sheqrgfhwe", "eraahahildo@yahoo.com");
        assertNotNull(accountJson);

        // Add a bundle, subscription and move the clock to get the first invoice
        final BundleJsonNoSubscriptions bundleJson = createBundle(accountJson.getAccountId(), "317199");
        assertNotNull(bundleJson);
        final SubscriptionJsonNoEvents subscriptionJson = createSubscription(bundleJson.getBundleId(), "Shotgun", ProductCategory.BASE.toString(), BillingPeriod.MONTHLY.toString(), true);
        assertNotNull(subscriptionJson);
        clock.addMonths(1);
        crappyWaitForLackOfProperSynchonization();

        final String paymentsURI = JaxrsResource.ACCOUNTS_PATH + "/" + accountJson.getAccountId() + "/" + JaxrsResource.PAYMENTS;

        // Verify we didn't get any payment
        final Response noPaymentsResponse = doGet(paymentsURI, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(noPaymentsResponse.getStatusCode(), Status.OK.getStatusCode());
        final String noPaymentsBaseJson = noPaymentsResponse.getResponseBody();
        final List<PaymentJsonSimple> noPaymentsFromJson = mapper.readValue(noPaymentsBaseJson, new TypeReference<List<PaymentJsonSimple>>() {});
        assertEquals(noPaymentsFromJson.size(), 0);

        // Get the invoices
        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put(JaxrsResource.QUERY_ACCOUNT_ID, accountJson.getAccountId());
        final String invoicesURI = JaxrsResource.INVOICES_PATH;
        final Response invoicesResponse = doGet(invoicesURI, queryParams, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(invoicesResponse.getStatusCode(), Status.OK.getStatusCode());
        final String invoicesBaseJson = invoicesResponse.getResponseBody();
        final List<InvoiceJsonSimple> invoices = mapper.readValue(invoicesBaseJson, new TypeReference<List<InvoiceJsonSimple>>() {});
        assertNotNull(invoices);
        // 2 invoices but look for the non zero dollar one
        assertEquals(invoices.size(), 2);
        final String invoiceId = invoices.get(1).getInvoiceId();

        // Post an external payment
        final BigDecimal paidAmount = BigDecimal.TEN;
        final PaymentJsonSimple payment = new PaymentJsonSimple(paidAmount, BigDecimal.ZERO, accountJson.getAccountId(),
                                                                invoiceId, null, null, null, null, 0,
                                                                null, null, null, null, null, null, null);
        final String postJson = mapper.writeValueAsString(payment);
        final String paymentURI = JaxrsResource.INVOICES_PATH + "/" + invoiceId + "/" + JaxrsResource.PAYMENTS;
        final Response paymentResponse = doPost(paymentURI, postJson, ImmutableMap.<String, String>of("externalPayment", "true"), DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(paymentResponse.getStatusCode(), Status.CREATED.getStatusCode());

        // Verify we indeed got the payment
        final Response paymentsResponse = doGet(paymentsURI, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(paymentsResponse.getStatusCode(), Status.OK.getStatusCode());
        final String paymentsBaseJson = paymentsResponse.getResponseBody();
        final List<PaymentJsonSimple> paymentsFromJson = mapper.readValue(paymentsBaseJson, new TypeReference<List<PaymentJsonSimple>>() {});
        assertEquals(paymentsFromJson.size(), 1);
        assertEquals(paymentsFromJson.get(0).getPaidAmount().compareTo(paidAmount), 0);

        // Check the PaymentMethod from paymentMethodId returned in the Payment object
        final String paymentMethodId = paymentsFromJson.get(0).getPaymentMethodId();
        final String paymentMethodURI = JaxrsResource.PAYMENT_METHODS_PATH + "/" + paymentMethodId;

        final Response paymentMethodResponse = doGet(paymentMethodURI, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(paymentMethodResponse.getStatusCode(), Status.OK.getStatusCode());
        final PaymentMethodJson paymentMethodJson = mapper.readValue(paymentMethodResponse.getResponseBody(), PaymentMethodJson.class);
        assertEquals(paymentMethodJson.getPaymentMethodId(), paymentMethodId);
        assertEquals(paymentMethodJson.getAccountId(), accountJson.getAccountId());
        assertEquals(paymentMethodJson.getPluginName(), ExternalPaymentProviderPlugin.PLUGIN_NAME);
        assertNull(paymentMethodJson.getPluginInfo());
    }
}
