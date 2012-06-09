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
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.jaxrs.json.AccountJson;
import com.ning.billing.jaxrs.json.CreditJson;
import com.ning.billing.jaxrs.resources.JaxrsResource;
import com.ning.billing.util.clock.DefaultClock;
import com.ning.http.client.Response;

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
        final DateTime requestedDate = DefaultClock.truncateMs(new DateTime(DateTimeZone.UTC));
        final DateTime effectiveDate = DefaultClock.truncateMs(new DateTime(DateTimeZone.UTC));
        final CreditJson input = new CreditJson(BigDecimal.TEN, UUID.randomUUID(), UUID.randomUUID().toString(),
                                                requestedDate, effectiveDate,
                                                UUID.randomUUID().toString(), UUID.fromString(accountJson.getAccountId()));
        final String jsonInput = mapper.writeValueAsString(input);

        // Create the credit
        Response response = doPost(JaxrsResource.CREDITS_PATH, jsonInput, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), javax.ws.rs.core.Response.Status.CREATED.getStatusCode(), response.getResponseBody());

        final String location = response.getHeader("Location");
        assertNotNull(location);

        // Retrieves by Id based on Location returned
        response = doGetWithUrl(location, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), javax.ws.rs.core.Response.Status.OK.getStatusCode());

        // We can't just compare the object via .equals() due e.g. to the invoice id
        final CreditJson objFromJson = mapper.readValue(response.getResponseBody(), CreditJson.class);
        assertEquals(objFromJson.getAccountId(), input.getAccountId());
        assertEquals(objFromJson.getCreditAmount().compareTo(input.getCreditAmount()), 0);
        assertEquals(objFromJson.getEffectiveDate().compareTo(input.getEffectiveDate()), 0);
    }

    @Test(groups = "slow")
    public void testAccountDoesNotExist() throws Exception {
        final CreditJson input = new CreditJson(BigDecimal.TEN, UUID.randomUUID(), UUID.randomUUID().toString(),
                                                new DateTime(DateTimeZone.UTC), new DateTime(DateTimeZone.UTC),
                                                UUID.randomUUID().toString(), UUID.randomUUID());
        final String jsonInput = mapper.writeValueAsString(input);

        // Try to create the credit
        final Response response = doPost(JaxrsResource.CREDITS_PATH, jsonInput, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), javax.ws.rs.core.Response.Status.BAD_REQUEST.getStatusCode(), response.getResponseBody());
    }

    @Test(groups = "slow")
    public void testBadRequest() throws Exception {
        final CreditJson input = new CreditJson(null, null, null, null, null, null, null);
        final String jsonInput = mapper.writeValueAsString(input);

        // Try to create the credit
        final Response response = doPost(JaxrsResource.CREDITS_PATH, jsonInput, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), javax.ws.rs.core.Response.Status.BAD_REQUEST.getStatusCode(), response.getResponseBody());
    }

    @Test(groups = "slow")
    public void testCreditDoesNotExist() throws Exception {
        final Response response = doGet(JaxrsResource.CREDITS_PATH + "/" + UUID.randomUUID().toString(), DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), javax.ws.rs.core.Response.Status.NOT_FOUND.getStatusCode(), response.getResponseBody());
    }
}
