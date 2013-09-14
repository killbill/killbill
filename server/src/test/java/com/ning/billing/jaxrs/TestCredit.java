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

import java.math.BigDecimal;
import java.util.UUID;

import javax.ws.rs.core.Response.Status;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.jaxrs.json.AccountJson;
import com.ning.billing.jaxrs.json.CreditJson;
import com.ning.billing.jaxrs.json.InvoiceJson;
import com.ning.billing.jaxrs.resources.JaxrsResource;
import com.ning.http.client.Response;

import static org.testng.Assert.assertEquals;

public class TestCredit extends TestJaxrsBase {

    AccountJson accountJson;

    @BeforeMethod(groups = "slow")
    public void setUp() throws Exception {
        accountJson = createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice();
    }

    @Test(groups = "slow")
    public void testAddCreditToInvoice() throws Exception {
        final InvoiceJson invoice = getInvoicesForAccount(accountJson.getAccountId()).get(1);

        final DateTime effectiveDate = clock.getUTCNow();
        final BigDecimal creditAmount = BigDecimal.ONE;
        final CreditJson objFromJson = createCreditForInvoice(accountJson.getAccountId(), invoice.getInvoiceId(),
                                                              creditAmount, clock.getUTCNow(), effectiveDate);

        // We can't just compare the object via .equals() due e.g. to the invoice id
        assertEquals(objFromJson.getAccountId(), accountJson.getAccountId());
        assertEquals(objFromJson.getCreditAmount().compareTo(creditAmount), 0);
        assertEquals(objFromJson.getEffectiveDate().compareTo(effectiveDate.toLocalDate()), 0);
    }

    @Test(groups = "slow")
    public void testAccountDoesNotExist() throws Exception {
        final LocalDate effectiveDate = clock.getUTCToday();
        final CreditJson input = new CreditJson(BigDecimal.TEN, UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                                                effectiveDate,
                                                UUID.randomUUID().toString(), null);
        final String jsonInput = mapper.writeValueAsString(input);

        // Try to create the credit
        final Response response = doPost(JaxrsResource.CREDITS_PATH, jsonInput, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.NOT_FOUND.getStatusCode(), response.getResponseBody());
    }

    @Test(groups = "slow")
    public void testBadRequest() throws Exception {
        final CreditJson input = new CreditJson(null, null, null, null, null, null);
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
