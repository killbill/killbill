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

import java.util.Map;
import java.util.UUID;

import javax.ws.rs.core.Response.Status;

import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.joda.time.LocalDate;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.jaxrs.json.AccountJson;
import com.ning.billing.jaxrs.json.BundleJsonNoSubscriptions;
import com.ning.billing.jaxrs.json.SubscriptionJsonNoEvents;
import com.ning.billing.jaxrs.resources.JaxrsResource;
import com.ning.http.client.Response;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class TestSubscription extends TestJaxrsBase {

    private static final String CALL_COMPLETION_TIMEOUT_SEC = "5";

    @Test(groups = "slow")
    public void testSubscriptionInTrialOk() throws Exception {

        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final AccountJson accountJson = createAccountWithDefaultPaymentMethod("xil", "shdxilhkkl", "xil@yahoo.com");
        final BundleJsonNoSubscriptions bundleJson = createBundle(accountJson.getAccountId(), "99999");

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.MONTHLY;

        final SubscriptionJsonNoEvents subscriptionJson = createSubscription(bundleJson.getBundleId(), productName, ProductCategory.BASE.toString(), term.toString(), true);
        Assert.assertNotNull(subscriptionJson.getChargedThroughDate());
        Assert.assertEquals(subscriptionJson.getChargedThroughDate().toLocalDate(), new LocalDate("2012-04-25"));

        String uri = JaxrsResource.SUBSCRIPTIONS_PATH + "/" + subscriptionJson.getSubscriptionId();

        // Retrieves with GET
        Response response = doGet(uri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        String baseJson = response.getResponseBody();
        SubscriptionJsonNoEvents objFromJson = mapper.readValue(baseJson, SubscriptionJsonNoEvents.class);
        Assert.assertTrue(objFromJson.equals(subscriptionJson));

        // Change plan IMM
        final String newProductName = "Assault-Rifle";

        final SubscriptionJsonNoEvents newInput = new SubscriptionJsonNoEvents(subscriptionJson.getSubscriptionId(),
                                                                               subscriptionJson.getBundleId(),
                                                                               null,
                                                                               newProductName,
                                                                               subscriptionJson.getProductCategory(),
                                                                               subscriptionJson.getBillingPeriod(),
                                                                               subscriptionJson.getPriceList(), null, null, null);
        baseJson = mapper.writeValueAsString(newInput);

        final Map<String, String> queryParams = getQueryParamsForCallCompletion(CALL_COMPLETION_TIMEOUT_SEC);
        response = doPut(uri, baseJson, queryParams, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        baseJson = response.getResponseBody();
        objFromJson = mapper.readValue(baseJson, SubscriptionJsonNoEvents.class);
        assertTrue(objFromJson.equalsNoSubscriptionIdNoStartDateNoCTD(newInput));

        // MOVE AFTER TRIAL
        final Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(31));
        clock.addDeltaFromReality(it.toDurationMillis());

        crappyWaitForLackOfProperSynchonization();

        // Cancel EOT
        uri = JaxrsResource.SUBSCRIPTIONS_PATH + "/" + subscriptionJson.getSubscriptionId();
        response = doDelete(uri, queryParams, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());

        // Retrieves to check EndDate
        uri = JaxrsResource.SUBSCRIPTIONS_PATH + "/" + subscriptionJson.getSubscriptionId();
        response = doGet(uri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);

        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        baseJson = response.getResponseBody();
        objFromJson = mapper.readValue(baseJson, SubscriptionJsonNoEvents.class);
        assertNotNull(objFromJson.getCancelledDate());
        assertTrue(objFromJson.getCancelledDate().compareTo(clock.getUTCNow()) > 0);

        // Uncancel
        uri = JaxrsResource.SUBSCRIPTIONS_PATH + "/" + subscriptionJson.getSubscriptionId() + "/uncancel";
        response = doPut(uri, baseJson, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
    }

    @Test(groups = "slow")
    public void testWithNonExistentSubscription() throws Exception {
        final String uri = JaxrsResource.SUBSCRIPTIONS_PATH + "/" + UUID.randomUUID().toString();
        final SubscriptionJsonNoEvents subscriptionJson = new SubscriptionJsonNoEvents(null, UUID.randomUUID().toString(), null, "Pistol", ProductCategory.BASE.toString(), BillingPeriod.MONTHLY.toString(),
                                                                                       PriceListSet.DEFAULT_PRICELIST_NAME, null, null, null);
        final String baseJson = mapper.writeValueAsString(subscriptionJson);

        Response response = doPut(uri, baseJson, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.NOT_FOUND.getStatusCode());

        response = doDelete(uri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.NOT_FOUND.getStatusCode());

        response = doGet(uri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.NOT_FOUND.getStatusCode());
    }

    @Test(groups = "slow")
    public void testOverridePolicy() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final AccountJson accountJson = createAccountWithDefaultPaymentMethod("xil", "shdxilhkkl", "xil@yahoo.com");
        final BundleJsonNoSubscriptions bundleJson = createBundle(accountJson.getAccountId(), "99999");

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.ANNUAL;

        final SubscriptionJsonNoEvents subscriptionJson = createSubscription(bundleJson.getBundleId(), productName, ProductCategory.BASE.toString(), term.toString(), true);
        Assert.assertNotNull(subscriptionJson.getChargedThroughDate());
        Assert.assertEquals(subscriptionJson.getChargedThroughDate().toLocalDate(), new LocalDate("2012-04-25"));

        final String uri = JaxrsResource.SUBSCRIPTIONS_PATH + "/" + subscriptionJson.getSubscriptionId();

        // Retrieves with GET
        Response response = doGet(uri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        String baseJson = response.getResponseBody();
        SubscriptionJsonNoEvents objFromJson = mapper.readValue(baseJson, SubscriptionJsonNoEvents.class);
        Assert.assertTrue(objFromJson.equals(subscriptionJson));
        assertEquals(objFromJson.getBillingPeriod(), BillingPeriod.ANNUAL.toString());

        // Change billing period immediately
        final SubscriptionJsonNoEvents newInput = new SubscriptionJsonNoEvents(subscriptionJson.getSubscriptionId(),
                                                                               subscriptionJson.getBundleId(),
                                                                               subscriptionJson.getStartDate(),
                                                                               subscriptionJson.getProductName(),
                                                                               subscriptionJson.getProductCategory(),
                                                                               BillingPeriod.MONTHLY.toString(),
                                                                               subscriptionJson.getPriceList(),
                                                                               subscriptionJson.getChargedThroughDate(),
                                                                               subscriptionJson.getCancelledDate(),
                                                                               null);
        baseJson = mapper.writeValueAsString(newInput);
        final Map<String, String> queryParams = getQueryParamsForCallCompletion(CALL_COMPLETION_TIMEOUT_SEC);
        queryParams.put(JaxrsResource.QUERY_POLICY, "immediate");
        response = doPut(uri, baseJson, queryParams, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        baseJson = response.getResponseBody();
        objFromJson = mapper.readValue(baseJson, SubscriptionJsonNoEvents.class);
        assertEquals(objFromJson.getBillingPeriod(), BillingPeriod.MONTHLY.toString());
    }
}
