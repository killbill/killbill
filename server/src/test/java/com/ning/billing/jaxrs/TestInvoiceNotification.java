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

import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.jaxrs.json.AccountJson;
import com.ning.billing.jaxrs.json.InvoiceJsonSimple;
import com.ning.billing.jaxrs.json.SubscriptionJson;
import com.ning.billing.jaxrs.resources.JaxrsResource;
import com.ning.http.client.Response;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableMap;

public class TestInvoiceNotification extends TestJaxrsBase {

    @Test(groups = "slow")
    public void testTriggerNotification() throws Exception {
        final AccountJson accountJson = createScenarioWithOneInvoice();

        final String uri = JaxrsResource.INVOICES_PATH;
        final Response response = doGet(uri, ImmutableMap.<String, String>of(JaxrsResource.QUERY_ACCOUNT_ID, accountJson.getAccountId()), DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), javax.ws.rs.core.Response.Status.OK.getStatusCode());
        final String baseJson = response.getResponseBody();
        final List<InvoiceJsonSimple> objFromJson = mapper.readValue(baseJson, new TypeReference<List<InvoiceJsonSimple>>() {});
        Assert.assertEquals(objFromJson.size(), 1);

        final InvoiceJsonSimple invoice = objFromJson.get(0);
        final Response triggerResponse = doPost(uri + "/" + invoice.getInvoiceId() + "/" + JaxrsResource.EMAIL_NOTIFICATIONS,
                                                null, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(triggerResponse.getStatusCode(), javax.ws.rs.core.Response.Status.OK.getStatusCode());
    }

    private AccountJson createScenarioWithOneInvoice() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final AccountJson accountJson = createAccountWithDefaultPaymentMethod(UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString());
        Assert.assertNotNull(accountJson);

        final SubscriptionJson subscriptionJson = createEntitlement(accountJson.getAccountId(), "76213", "Shotgun", ProductCategory.BASE.toString(), BillingPeriod.MONTHLY.toString(), true);
        Assert.assertNotNull(subscriptionJson);

        return accountJson;
    }
}
