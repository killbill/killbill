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

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response.Status;

import org.joda.time.DateTime;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.jaxrs.json.AccountJson;
import com.ning.billing.jaxrs.json.BundleJsonNoSubscriptions;
import com.ning.billing.jaxrs.json.EntitlementJsonNoEvents;
import com.ning.billing.jaxrs.resources.JaxrsResource;
import com.ning.http.client.Response;

import com.fasterxml.jackson.core.type.TypeReference;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;

public class TestBundle extends TestJaxrsBase {

    @Test(groups = "slow", enabled = true)
    public void testBundleOk() throws Exception {

        final AccountJson accountJson = createAccount("xlxl", "shdgfhkkl", "xlxl@yahoo.com");

        createEntitlement(accountJson.getAccountId(), "123467", "Shotgun", ProductCategory.BASE.toString(), BillingPeriod.MONTHLY.toString(), true);
        // Retrieves by external key
        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put(JaxrsResource.QUERY_EXTERNAL_KEY, "123467");
        final String uri = JaxrsResource.ACCOUNTS_PATH + "/" + accountJson.getAccountId() + "/" + JaxrsResource.BUNDLES;
        final Response response = doGet(uri, queryParams, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        final String baseJson = response.getResponseBody();
        final BundleJsonNoSubscriptions objFromJson = mapper.readValue(baseJson, BundleJsonNoSubscriptions.class);
    }

    @Test(groups = "slow", enabled = true)
    public void testBundleFromAccount() throws Exception {

        final AccountJson accountJson = createAccount("xaxa", "saagfhkkl", "xaxa@yahoo.com");
        createEntitlement(accountJson.getAccountId(), "156567", "Shotgun", ProductCategory.BASE.toString(), BillingPeriod.MONTHLY.toString(), true);
        createEntitlement(accountJson.getAccountId(), "265658", "Shotgun", ProductCategory.BASE.toString(), BillingPeriod.MONTHLY.toString(), true);


        final String uri = JaxrsResource.ACCOUNTS_PATH + "/" + accountJson.getAccountId().toString() + "/" + JaxrsResource.BUNDLES;
        final Response response = doGet(uri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        final String baseJson = response.getResponseBody();
        final List<BundleJsonNoSubscriptions> objFromJson = mapper.readValue(baseJson, new TypeReference<List<BundleJsonNoSubscriptions>>() {});

        Collections.sort(objFromJson, new Comparator<BundleJsonNoSubscriptions>() {
            @Override
            public int compare(final BundleJsonNoSubscriptions o1, final BundleJsonNoSubscriptions o2) {
                return o1.getExternalKey().compareTo(o2.getExternalKey());
            }
        });
    }

    @Test(groups = "slow", enabled = true)
    public void testBundleNonExistent() throws Exception {
        final AccountJson accountJson = createAccount("dfdf", "dfdfgfhkkl", "dfdf@yahoo.com");

        String uri = JaxrsResource.BUNDLES_PATH + "/99999999-b103-42f3-8b6e-dd244f1d0747";
        Response response = doGet(uri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.NOT_FOUND.getStatusCode());

        // Retrieves by external key
        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put(JaxrsResource.QUERY_EXTERNAL_KEY, "56566");
        uri = JaxrsResource.ACCOUNTS_PATH + "/" + accountJson.getAccountId() + "/" + JaxrsResource.BUNDLES;
        response = doGet(uri, queryParams, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.NOT_FOUND.getStatusCode());

        uri = JaxrsResource.ACCOUNTS_PATH + "/" + accountJson.getAccountId() + "/" + JaxrsResource.BUNDLES;
        response = doGet(uri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        final String baseJson = response.getResponseBody();
        final List<BundleJsonNoSubscriptions> objFromJson = mapper.readValue(baseJson, new TypeReference<List<BundleJsonNoSubscriptions>>() {});
        Assert.assertNotNull(objFromJson);
        Assert.assertEquals(objFromJson.size(), 0);
    }

    @Test(groups = "slow", enabled = true)
    public void testAppNonExistent() throws Exception {
        final String uri = JaxrsResource.ACCOUNTS_PATH + "/99999999-b103-42f3-8b6e-dd244f1d0747/" + JaxrsResource.BUNDLES;
        final Response response = doGet(uri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.NOT_FOUND.getStatusCode());
    }

    @Test(groups = "slow", enabled = true)
    public void testBundleTransfer() throws Exception {

        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final AccountJson accountJson = createAccountWithDefaultPaymentMethod("src", "src", "src@yahoo.com");

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.MONTHLY;

        final EntitlementJsonNoEvents entitlementJsonNoEvents = createEntitlement(accountJson.getAccountId(), "93199", productName, ProductCategory.BASE.toString(), term.toString(), true);


        Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put(JaxrsResource.QUERY_EXTERNAL_KEY, "93199");
        String uri = JaxrsResource.BUNDLES_PATH;
        Response response = doGet(uri, queryParams, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        final BundleJsonNoSubscriptions originalBundle = mapper.readValue(response.getResponseBody(), BundleJsonNoSubscriptions.class);
        assertEquals(originalBundle.getAccountId(), accountJson.getAccountId());
        assertEquals(originalBundle.getExternalKey(), "93199");


        final AccountJson newAccount = createAccountWithDefaultPaymentMethod("dst", "dst", "dst@yahoo.com");

        final BundleJsonNoSubscriptions newBundleInput = new BundleJsonNoSubscriptions(null, newAccount.getAccountId(), null, null, null);
        final String newBundleInputJson = mapper.writeValueAsString(newBundleInput);
        uri = JaxrsResource.BUNDLES_PATH + "/" + entitlementJsonNoEvents.getBundleId();
        response = doPut(uri, newBundleInputJson, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.CREATED.getStatusCode());

        final String locationCC = response.getHeader("Location");
        Assert.assertNotNull(locationCC);

        response = doGetWithUrl(locationCC, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());

        queryParams = new HashMap<String, String>();
        queryParams.put(JaxrsResource.QUERY_EXTERNAL_KEY, "93199");
        uri = JaxrsResource.BUNDLES_PATH;
        response = doGet(uri, queryParams, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        final BundleJsonNoSubscriptions newBundle = mapper.readValue(response.getResponseBody(), BundleJsonNoSubscriptions.class);

        assertNotEquals(newBundle.getBundleId(), originalBundle.getBundleId());
        assertEquals(newBundle.getExternalKey(), originalBundle.getExternalKey());
        assertEquals(newBundle.getAccountId(), newAccount.getAccountId());

    }
}
