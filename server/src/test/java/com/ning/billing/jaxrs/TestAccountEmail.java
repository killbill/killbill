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

import java.util.List;
import java.util.UUID;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ning.billing.jaxrs.json.AccountEmailJson;
import com.ning.billing.jaxrs.json.AccountJson;
import com.ning.billing.jaxrs.resources.JaxrsResource;
import com.ning.http.client.Response;

import static org.testng.Assert.assertEquals;

public class TestAccountEmail extends TestJaxrsBase {
    @Test(groups = "slow")
    public void testAddAndRemoveAccountEmail() throws Exception {
        final AccountJson input = createAccount(UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString());
        final String accountId = input.getAccountId();

        final String email1 = UUID.randomUUID().toString();
        final String email2 = UUID.randomUUID().toString();
        final AccountEmailJson accountEmailJson1 = new AccountEmailJson(accountId, email1);
        final AccountEmailJson accountEmailJson2 = new AccountEmailJson(accountId, email2);

        final String baseUri = JaxrsResource.ACCOUNTS_PATH + "/" + accountId + "/" + JaxrsResource.EMAILS;

        // Verify the initial state
        final Response firstResponse = doGet(baseUri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(firstResponse.getStatusCode(), javax.ws.rs.core.Response.Status.OK.getStatusCode());
        final List<AccountEmailJson> firstEmails = mapper.readValue(firstResponse.getResponseBody(), new TypeReference<List<AccountEmailJson>>() {});
        Assert.assertEquals(firstEmails.size(), 0);

        // Add an email
        final String firstEmailString = mapper.writeValueAsString(accountEmailJson1);
        final Response secondResponse = doPost(baseUri, firstEmailString, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(secondResponse.getStatusCode(), javax.ws.rs.core.Response.Status.CREATED.getStatusCode());

        // Verify we can retrieve it
        final Response thirdResponse = doGet(baseUri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(thirdResponse.getStatusCode(), javax.ws.rs.core.Response.Status.OK.getStatusCode());
        final List<AccountEmailJson> secondEmails = mapper.readValue(thirdResponse.getResponseBody(), new TypeReference<List<AccountEmailJson>>() {});
        Assert.assertEquals(secondEmails.size(), 1);
        Assert.assertEquals(secondEmails.get(0).getAccountId(), accountId);
        Assert.assertEquals(secondEmails.get(0).getEmail(), email1);

        // Add another email
        final String secondEmailString = mapper.writeValueAsString(accountEmailJson2);
        final Response thridResponse = doPost(baseUri, secondEmailString, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(thridResponse.getStatusCode(), javax.ws.rs.core.Response.Status.CREATED.getStatusCode());

        // Verify we can retrieve both
        final Response fourthResponse = doGet(baseUri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(fourthResponse.getStatusCode(), javax.ws.rs.core.Response.Status.OK.getStatusCode());
        final List<AccountEmailJson> thirdEmails = mapper.readValue(fourthResponse.getResponseBody(), new TypeReference<List<AccountEmailJson>>() {});
        Assert.assertEquals(thirdEmails.size(), 2);
        Assert.assertEquals(thirdEmails.get(0).getAccountId(), accountId);
        Assert.assertEquals(thirdEmails.get(1).getAccountId(), accountId);
        Assert.assertTrue(thirdEmails.get(0).getEmail().equals(email1) || thirdEmails.get(0).getEmail().equals(email2));
        Assert.assertTrue(thirdEmails.get(1).getEmail().equals(email1) || thirdEmails.get(1).getEmail().equals(email2));

        // Delete the first email
        final Response fifthResponse = doDelete(baseUri + "/" + email1, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(fifthResponse.getStatusCode(), javax.ws.rs.core.Response.Status.OK.getStatusCode());

        // Verify it has been deleted
        final Response sixthResponse = doGet(baseUri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(sixthResponse.getStatusCode(), javax.ws.rs.core.Response.Status.OK.getStatusCode());
        final List<AccountEmailJson> fourthEmails = mapper.readValue(sixthResponse.getResponseBody(), new TypeReference<List<AccountEmailJson>>() {});
        Assert.assertEquals(fourthEmails.size(), 1);
        Assert.assertEquals(fourthEmails.get(0).getAccountId(), accountId);
        Assert.assertEquals(fourthEmails.get(0).getEmail(), email2);
    }
}
