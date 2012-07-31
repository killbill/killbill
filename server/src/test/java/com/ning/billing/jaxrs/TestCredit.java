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
import java.util.UUID;

import javax.ws.rs.core.Response.Status;

import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.jaxrs.json.AccountJson;
import com.ning.billing.jaxrs.json.BundleJsonNoSubscriptions;
import com.ning.billing.jaxrs.json.CreditJson;
import com.ning.billing.jaxrs.json.InvoiceJsonSimple;
import com.ning.billing.jaxrs.json.SubscriptionJsonNoEvents;
import com.ning.billing.jaxrs.resources.JaxrsResource;
import com.ning.http.client.Response;

import com.fasterxml.jackson.core.type.TypeReference;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class TestCredit extends TestJaxrsBase {

    AccountJson accountJson;

    @BeforeMethod(groups = "slow")
    public void setUp() throws Exception {
        accountJson = createAccount(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "foo@bar.com");
        assertNotNull(accountJson);
    }

    @Test(groups = "slow")
    public void testAddCreditToInvoice() throws Exception {
        final DateTime requestedDate = clock.getUTCNow();
        final DateTime effectiveDate = clock.getUTCNow();
        final InvoiceJsonSimple invoice = createInvoice();
        final CreditJson input = new CreditJson(BigDecimal.TEN, UUID.fromString(invoice.getInvoiceId()), UUID.randomUUID().toString(),
                                                requestedDate, effectiveDate,
                                                UUID.randomUUID().toString(), UUID.fromString(accountJson.getAccountId()),
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

        // We can't just compare the object via .equals() due e.g. to the invoice id
        final CreditJson objFromJson = mapper.readValue(response.getResponseBody(), CreditJson.class);
        assertEquals(objFromJson.getAccountId(), input.getAccountId());
        assertEquals(objFromJson.getCreditAmount().compareTo(input.getCreditAmount()), 0);
        assertEquals(objFromJson.getEffectiveDate().toLocalDate().compareTo(input.getEffectiveDate().toLocalDate()), 0);
    }

    private InvoiceJsonSimple createInvoice() throws Exception {
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

        final String uri = JaxrsResource.INVOICES_PATH;
        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put(JaxrsResource.QUERY_ACCOUNT_ID, accountJson.getAccountId());

        final Response response = doGet(uri, queryParams, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        final String baseJson = response.getResponseBody();
        final List<InvoiceJsonSimple> objFromJson = mapper.readValue(baseJson, new TypeReference<List<InvoiceJsonSimple>>() {});
        assertNotNull(objFromJson);
        assertEquals(objFromJson.size(), 2);

        return objFromJson.get(1);
    }

    @Test(groups = "slow")
    public void testAccountDoesNotExist() throws Exception {
        final DateTime requestedDate = clock.getUTCNow();
        final DateTime effectiveDate = clock.getUTCNow();
        final CreditJson input = new CreditJson(BigDecimal.TEN, UUID.randomUUID(), UUID.randomUUID().toString(),
                                                requestedDate, effectiveDate,
                                                UUID.randomUUID().toString(), UUID.randomUUID(), null);
        final String jsonInput = mapper.writeValueAsString(input);

        // Try to create the credit
        final Response response = doPost(JaxrsResource.CREDITS_PATH, jsonInput, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.NOT_FOUND.getStatusCode(), response.getResponseBody());
    }

    @Test(groups = "slow")
    public void testBadRequest() throws Exception {
        final CreditJson input = new CreditJson(null, null, null, null, null, null, null, null);
        final String jsonInput = mapper.writeValueAsString(input);

        // Try to create the credit
        final Response response = doPost(JaxrsResource.CREDITS_PATH, jsonInput, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.BAD_REQUEST.getStatusCode(), response.getResponseBody());
    }

    @Test(groups = "slow")
    public void testCreditDoesNotExist() throws Exception {
        final Response response = doGet(JaxrsResource.CREDITS_PATH + "/" + UUID.randomUUID().toString(), DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.NOT_FOUND.getStatusCode(), response.getResponseBody());
    }
}
