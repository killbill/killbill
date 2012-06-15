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

import java.io.IOException;
import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.jaxrs.json.ChargebackCollectionJson;
import com.ning.billing.jaxrs.json.ChargebackJson;
import com.ning.billing.jaxrs.resources.JaxrsResource;
import com.ning.http.client.Response;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class TestChargeback extends TestJaxrsBase {
    private final String accountId = UUID.randomUUID().toString();

    @BeforeMethod(groups = "slow")
    public void setUp() throws Exception {
        final InvoicePayment invoicePayment = createInvoicePayment();
    }

    @Test(groups = "slow", enabled = false)
    public void testAddChargeback() throws Exception {
        final ChargebackJson input = new ChargebackJson(new DateTime(DateTimeZone.UTC), new DateTime(DateTimeZone.UTC),
                                                        BigDecimal.TEN, UUID.randomUUID().toString(), UUID.randomUUID().toString());
        final String jsonInput = mapper.writeValueAsString(input);

        // Create the chargeback
        Response response = doPost(JaxrsResource.CHARGEBACKS_PATH, jsonInput, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), javax.ws.rs.core.Response.Status.OK.getStatusCode(), response.getResponseBody());

        // Find the chargeback by location
        final String location = response.getHeader("Location");
        assertNotNull(location);
        response = doGetWithUrl(location, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        verifySingleChargebackResponse(response, input);

        // Find the chargeback by account
        response = doGet(JaxrsResource.CHARGEBACKS_PATH + "/accounts/" + accountId, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        verifyCollectionChargebackResponse(response, input);

        // Find the chargeback by payment
        response = doGet(JaxrsResource.CHARGEBACKS_PATH + "/payments/" + input.getPaymentId(), DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        verifyCollectionChargebackResponse(response, input);
    }

    private void verifyCollectionChargebackResponse(final Response response, final ChargebackJson input) throws IOException {
        assertEquals(response.getStatusCode(), javax.ws.rs.core.Response.Status.OK.getStatusCode());
        final ChargebackCollectionJson objFromJson = mapper.readValue(response.getResponseBody(), ChargebackCollectionJson.class);
        assertEquals(objFromJson.getChargebacks().size(), 1);
        assertEquals(objFromJson.getChargebacks().get(0), input);
    }

    private void verifySingleChargebackResponse(final Response response, final ChargebackJson input) throws IOException {
        assertEquals(response.getStatusCode(), javax.ws.rs.core.Response.Status.OK.getStatusCode());
        final ChargebackJson objFromJson = mapper.readValue(response.getResponseBody(), ChargebackJson.class);
        assertEquals(objFromJson, input);
    }

    @Test(groups = "slow")
    public void testInvoicePaymentDoesNotExist() throws Exception {
        final ChargebackJson input = new ChargebackJson(new DateTime(DateTimeZone.UTC), new DateTime(DateTimeZone.UTC),
                                                        BigDecimal.TEN, UUID.randomUUID().toString(), UUID.randomUUID().toString());
        final String jsonInput = mapper.writeValueAsString(input);

        // Try to create the chargeback
        final Response response = doPost(JaxrsResource.CHARGEBACKS_PATH, jsonInput, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), javax.ws.rs.core.Response.Status.BAD_REQUEST.getStatusCode(), response.getResponseBody());
    }

    @Test(groups = "slow")
    public void testBadRequest() throws Exception {
        final ChargebackJson input = new ChargebackJson(null, null, null, null, null);
        final String jsonInput = mapper.writeValueAsString(input);

        // Try to create the chargeback
        final Response response = doPost(JaxrsResource.CHARGEBACKS_PATH, jsonInput, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getResponseBody());
    }

    @Test(groups = "slow")
    public void testNoChargebackForAccount() throws Exception {
        final String accountId = UUID.randomUUID().toString();
        final Response response = doGet(JaxrsResource.CHARGEBACKS_PATH + "/accounts/" + accountId, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), javax.ws.rs.core.Response.Status.OK.getStatusCode(), response.getResponseBody());

        final ChargebackCollectionJson chargebackCollectionJson = mapper.readValue(response.getResponseBody(), ChargebackCollectionJson.class);
        Assert.assertEquals(chargebackCollectionJson.getAccountId(), accountId);
        Assert.assertEquals(chargebackCollectionJson.getChargebacks().size(), 0);
    }


    @Test(groups = "slow")
    public void testNoChargebackForPayment() throws Exception {
        final String payment = UUID.randomUUID().toString();
        final Response response = doGet(JaxrsResource.CHARGEBACKS_PATH + "/payments/" + payment, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        // STEPH needs to fix that we get 200 instaed of 204 although stepping through code, i see we do return NO_CONTENT. mistery that needs to be solved!!!!
        //assertEquals(response.getStatusCode(), javax.ws.rs.core.Response.Status.NO_CONTENT.getStatusCode(), response.getResponseBody());
    }

    private InvoicePayment createInvoicePayment() {
        // TODO - blocked on payment resource
        return null;
    }
}
