/*
 * Copyright 2010-2012 Ning, Inc.
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

import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.jaxrs.json.AccountJson;
import com.ning.billing.jaxrs.json.BundleJsonNoSubscriptions;
import com.ning.billing.jaxrs.json.PaymentJsonSimple;
import com.ning.billing.jaxrs.json.PaymentMethodJson;
import com.ning.billing.jaxrs.json.RefundJson;
import com.ning.billing.jaxrs.json.SubscriptionJsonNoEvents;
import com.ning.billing.jaxrs.resources.JaxrsResource;
import com.ning.http.client.Response;

import com.fasterxml.jackson.core.type.TypeReference;

import static com.ning.billing.jaxrs.resources.JaxrsResource.QUERY_PAYMENT_METHOD_PLUGIN_INFO;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class TestPayment extends TestJaxrsBase {

    @Test(groups = "slow")
    public void testPaymentWithRefund() throws Exception {
        final AccountJson accountJson = createAccountWithDefaultPaymentMethod("eraahahildo", "sheqrgfhwe", "eraahahildo@yahoo.com");
        assertNotNull(accountJson);

        final BundleJsonNoSubscriptions bundleJson = createBundle(accountJson.getAccountId(), "317199");
        assertNotNull(bundleJson);

        final SubscriptionJsonNoEvents subscriptionJson = createSubscription(bundleJson.getBundleId(), "Shotgun", ProductCategory.BASE.toString(), BillingPeriod.MONTHLY.toString(), true);
        assertNotNull(subscriptionJson);

        clock.addMonths(1);
        crappyWaitForLackOfProperSynchonization();

        String uri = JaxrsResource.ACCOUNTS_PATH + "/" + accountJson.getAccountId() + "/" + JaxrsResource.PAYMENTS;

        Response response = doGet(uri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        String baseJson = response.getResponseBody();
        final List<PaymentJsonSimple> objFromJson = mapper.readValue(baseJson, new TypeReference<List<PaymentJsonSimple>>() {});
        Assert.assertEquals(objFromJson.size(), 1);

        final String paymentId = objFromJson.get(0).getPaymentId();
        final BigDecimal paymentAmount = objFromJson.get(0).getAmount();

        // Check the PaymentMethod from paymentMethodId returned in the Payment object
        final String paymentMethodId = objFromJson.get(0).getPaymentMethodId();
        uri = JaxrsResource.PAYMENT_METHODS_PATH + "/" + paymentMethodId;

        final Map<String, String> queryPaymentMethods = new HashMap<String, String>();
        queryPaymentMethods.put(QUERY_PAYMENT_METHOD_PLUGIN_INFO, "true");
        response = doGet(uri, queryPaymentMethods, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        final PaymentMethodJson paymentMethodJson = mapper.readValue(response.getResponseBody(), PaymentMethodJson.class);
        Assert.assertEquals(paymentMethodJson.getPaymentMethodId(), paymentMethodId);
        Assert.assertEquals(paymentMethodJson.getAccountId(), accountJson.getAccountId());
        Assert.assertNotNull(paymentMethodJson.getPluginInfo().getExternalPaymentId());

        uri = JaxrsResource.PAYMENTS_PATH + "/" + paymentId + "/" + JaxrsResource.REFUNDS;
        response = doGet(uri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        baseJson = response.getResponseBody();
        List<RefundJson> objRefundFromJson = mapper.readValue(baseJson, new TypeReference<List<RefundJson>>() {});
        Assert.assertEquals(objRefundFromJson.size(), 0);

        // Issue the refund

        final RefundJson refundJson = new RefundJson(null, paymentId, paymentAmount, false, null, null, null, null);
        baseJson = mapper.writeValueAsString(refundJson);
        response = doPost(uri, baseJson, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.CREATED.getStatusCode());

        final String locationCC = response.getHeader("Location");
        Assert.assertNotNull(locationCC);

        // Retrieves by Id based on Location returned
        response = doGetWithUrl(locationCC, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        baseJson = response.getResponseBody();
        final RefundJson refundJsonCheck = mapper.readValue(baseJson, RefundJson.class);
        Assert.assertTrue(refundJsonCheck.equalsNoIdNoDates(refundJson));
        Assert.assertEquals(refundJsonCheck.getEffectiveDate().getYear(), clock.getUTCNow().getYear());
        Assert.assertEquals(refundJsonCheck.getEffectiveDate().getMonthOfYear(), clock.getUTCNow().getMonthOfYear());
        Assert.assertEquals(refundJsonCheck.getEffectiveDate().getDayOfMonth(), clock.getUTCNow().getDayOfMonth());
        Assert.assertEquals(refundJsonCheck.getRequestedDate().getYear(), clock.getUTCNow().getYear());
        Assert.assertEquals(refundJsonCheck.getRequestedDate().getMonthOfYear(), clock.getUTCNow().getMonthOfYear());
        Assert.assertEquals(refundJsonCheck.getRequestedDate().getDayOfMonth(), clock.getUTCNow().getDayOfMonth());

        uri = JaxrsResource.PAYMENTS_PATH + "/" + paymentId + "/" + JaxrsResource.REFUNDS;
        response = doGet(uri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        baseJson = response.getResponseBody();
        objRefundFromJson = mapper.readValue(baseJson, new TypeReference<List<RefundJson>>() {});
        Assert.assertEquals(objRefundFromJson.size(), 1);
    }
}
