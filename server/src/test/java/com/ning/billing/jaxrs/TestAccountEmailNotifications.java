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

import java.util.UUID;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ning.billing.jaxrs.json.AccountJson;
import com.ning.billing.jaxrs.json.InvoiceEmailJson;
import com.ning.billing.jaxrs.resources.JaxrsResource;
import com.ning.http.client.Response;

import static org.testng.Assert.assertEquals;

public class TestAccountEmailNotifications extends TestJaxrsBase {
    @Test(groups = "slow")
    public void testSetAndUnsetEmailNotifications() throws Exception {
        final AccountJson input = createAccount(UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString());
        final String accountId = input.getAccountId();

        final InvoiceEmailJson invoiceEmailJsonWithNotifications = new InvoiceEmailJson(accountId, true);
        final InvoiceEmailJson invoiceEmailJsonWithoutNotifications = new InvoiceEmailJson(accountId, false);
        final String baseUri = JaxrsResource.ACCOUNTS_PATH + "/" + accountId + "/" + JaxrsResource.EMAIL_NOTIFICATIONS;

        // Verify the initial state
        final Response firstResponse = doGet(baseUri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(firstResponse.getStatusCode(), javax.ws.rs.core.Response.Status.OK.getStatusCode());
        final InvoiceEmailJson firstInvoiceEmailJson = mapper.readValue(firstResponse.getResponseBody(), new TypeReference<InvoiceEmailJson>() {});
        Assert.assertEquals(firstInvoiceEmailJson.getAccountId(), accountId);
        Assert.assertFalse(firstInvoiceEmailJson.isNotifiedForInvoices());

        // Enable email notifications
        final String firstEmailString = mapper.writeValueAsString(invoiceEmailJsonWithNotifications);
        final Response secondResponse = doPut(baseUri, firstEmailString, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(secondResponse.getStatusCode(), javax.ws.rs.core.Response.Status.OK.getStatusCode());

        // Verify we can retrieve it
        final Response thirdResponse = doGet(baseUri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(thirdResponse.getStatusCode(), javax.ws.rs.core.Response.Status.OK.getStatusCode());
        final InvoiceEmailJson secondInvoiceEmailJson = mapper.readValue(thirdResponse.getResponseBody(), new TypeReference<InvoiceEmailJson>() {});
        Assert.assertEquals(secondInvoiceEmailJson.getAccountId(), accountId);
        Assert.assertTrue(secondInvoiceEmailJson.isNotifiedForInvoices());

        // Disable email notifications
        final String secondEmailString = mapper.writeValueAsString(invoiceEmailJsonWithoutNotifications);
        final Response fourthResponse = doPut(baseUri, secondEmailString, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(fourthResponse.getStatusCode(), javax.ws.rs.core.Response.Status.OK.getStatusCode());

        // Verify we can retrieve it
        final Response fifthResponse = doGet(baseUri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(fifthResponse.getStatusCode(), javax.ws.rs.core.Response.Status.OK.getStatusCode());
        final InvoiceEmailJson thirdInvoiceEmailJson = mapper.readValue(fifthResponse.getResponseBody(), new TypeReference<InvoiceEmailJson>() {});
        Assert.assertEquals(thirdInvoiceEmailJson.getAccountId(), accountId);
        Assert.assertFalse(thirdInvoiceEmailJson.isNotifiedForInvoices());
    }
}
