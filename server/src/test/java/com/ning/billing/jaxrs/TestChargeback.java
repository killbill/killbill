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

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import javax.ws.rs.core.Response.Status;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.jaxrs.json.AccountJson;
import com.ning.billing.jaxrs.json.ChargebackCollectionJson;
import com.ning.billing.jaxrs.json.ChargebackJson;
import com.ning.billing.jaxrs.json.EntitlementJsonNoEvents;
import com.ning.billing.jaxrs.json.InvoiceJsonSimple;
import com.ning.billing.jaxrs.json.PaymentJsonSimple;
import com.ning.billing.jaxrs.resources.JaxrsResource;
import com.ning.http.client.Response;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableMap;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class TestChargeback extends TestJaxrsBase {

    @Test(groups = "slow")
    public void testAddChargeback() throws Exception {
        final PaymentJsonSimple payment = createAccountWithInvoiceAndPayment();
        createAndVerifyChargeback(payment);
    }

    @Test(groups = "slow")
    public void testMultipleChargeback() throws Exception {
        final PaymentJsonSimple payment = createAccountWithInvoiceAndPayment();

        // We get a 249.95 payment so we do 4 chargeback and then the fifth should fail
        final ChargebackJson input = new ChargebackJson(null, null, new BigDecimal("50.00"), payment.getPaymentId(), null, null);
        final String jsonInput = mapper.writeValueAsString(input);

        //
        int count = 4;
        Response response;
        while (count-- > 0) {
            response = doPost(JaxrsResource.CHARGEBACKS_PATH, jsonInput, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
            assertEquals(response.getStatusCode(), javax.ws.rs.core.Response.Status.CREATED.getStatusCode(), response.getResponseBody());
        }

        // Last attempt should fail because this is more than the Payment
        response = doPost(JaxrsResource.CHARGEBACKS_PATH, jsonInput, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), javax.ws.rs.core.Response.Status.BAD_REQUEST.getStatusCode(), response.getResponseBody());

        // Find the chargeback by account
        response = doGet(JaxrsResource.ACCOUNTS_PATH   +  "/" + payment.getAccountId() +  "/" +JaxrsResource.CHARGEBACKS, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), javax.ws.rs.core.Response.Status.OK.getStatusCode());
        ChargebackCollectionJson objFromJson = mapper.readValue(response.getResponseBody(), ChargebackCollectionJson.class);
        assertEquals(objFromJson.getChargebacks().size(), 4);
        for (int i = 0; i < objFromJson.getChargebacks().size(); i++) {
            final ChargebackJson chargeBack = objFromJson.getChargebacks().get(i);
            assertTrue(chargeBack.getChargebackAmount().compareTo(input.getChargebackAmount()) == 0);
            assertEquals(chargeBack.getPaymentId(), input.getPaymentId());
        }

        // Find the chargeback by payment
        response = doGet(JaxrsResource.PAYMENTS_PATH  +  "/" + payment.getPaymentId() +  "/" +JaxrsResource.CHARGEBACKS, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), javax.ws.rs.core.Response.Status.OK.getStatusCode());
        objFromJson = mapper.readValue(response.getResponseBody(), ChargebackCollectionJson.class);
        assertEquals(objFromJson.getChargebacks().size(), 4);
    }

    @Test(groups = "slow")
    public void testAddChargebackForDeletedPaymentMethod() throws Exception {
        final PaymentJsonSimple payment = createAccountWithInvoiceAndPayment();

        // Check the payment method exists
        assertEquals(getAccountById(payment.getAccountId()).getPaymentMethodId(), payment.getPaymentMethodId());
        assertEquals(getPaymentMethod(payment.getPaymentMethodId()).getAccountId(), payment.getAccountId());

        // Delete the payment method
        deletePaymentMethod(payment.getPaymentMethodId(), true);

        // Check the payment method was deleted
        assertNull(getAccountById(payment.getAccountId()).getPaymentMethodId());

        createAndVerifyChargeback(payment);
    }

    @Test(groups = "slow")
    public void testInvoicePaymentDoesNotExist() throws Exception {
        final ChargebackJson input = new ChargebackJson(new DateTime(DateTimeZone.UTC), new DateTime(DateTimeZone.UTC),
                                                        BigDecimal.TEN, UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                                                        null);
        final String jsonInput = mapper.writeValueAsString(input);

        // Try to create the chargeback
        final Response response = doPost(JaxrsResource.CHARGEBACKS_PATH, jsonInput, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.NOT_FOUND.getStatusCode(), response.getResponseBody());
    }

    @Test(groups = "slow")
    public void testBadRequest() throws Exception {
        final ChargebackJson input = new ChargebackJson(null, null, null, null, null, null);
        final String jsonInput = mapper.writeValueAsString(input);

        // Try to create the chargeback
        final Response response = doPost(JaxrsResource.CHARGEBACKS_PATH, jsonInput, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.BAD_REQUEST.getStatusCode(), response.getResponseBody());
    }

    @Test(groups = "slow")
    public void testNoChargebackForAccount() throws Exception {
        final String accountId = UUID.randomUUID().toString();
        final Response response = doGet(JaxrsResource.ACCOUNTS_PATH   +  "/" + accountId +  "/" +JaxrsResource.CHARGEBACKS, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.OK.getStatusCode(), response.getResponseBody());

        final ChargebackCollectionJson chargebackCollectionJson = mapper.readValue(response.getResponseBody(), ChargebackCollectionJson.class);
        Assert.assertEquals(chargebackCollectionJson.getAccountId(), accountId);
        Assert.assertEquals(chargebackCollectionJson.getChargebacks().size(), 0);
    }

    @Test(groups = "slow")
    public void testNoChargebackForPayment() throws Exception {
        final String paymentId = UUID.randomUUID().toString();
        final Response response = doGet(JaxrsResource.PAYMENTS_PATH   +  "/" + paymentId +  "/" +JaxrsResource.CHARGEBACKS, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        // STEPH needs to fix that we get 200 instaed of 204 although stepping through code, i see we do return NO_CONTENT. mistery that needs to be solved!!!!
        //assertEquals(response.getStatusCode(),Status.NO_CONTENT.getStatusCode(), response.getResponseBody());
    }

    private void createAndVerifyChargeback(final PaymentJsonSimple payment) throws IOException {
        final ChargebackJson input = new ChargebackJson(null, null, BigDecimal.TEN, payment.getPaymentId(), null, null);
        final String jsonInput = mapper.writeValueAsString(input);

        // Create the chargeback
        Response response = doPost(JaxrsResource.CHARGEBACKS_PATH, jsonInput, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.CREATED.getStatusCode(), response.getResponseBody());

        // Find the chargeback by location
        final String location = response.getHeader("Location");
        assertNotNull(location);
        response = doGetWithUrl(location, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        verifySingleChargebackResponse(response, input);

        // Find the chargeback by account
        response = doGet(JaxrsResource.ACCOUNTS_PATH   +  "/" + payment.getAccountId() +  "/" +JaxrsResource.CHARGEBACKS, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        verifyCollectionChargebackResponse(response, input);

        // Find the chargeback by payment
        response = doGet(JaxrsResource.PAYMENTS_PATH   +  "/" + payment.getPaymentId() +  "/" +JaxrsResource.CHARGEBACKS, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        verifyCollectionChargebackResponse(response, input);
    }

    private void verifyCollectionChargebackResponse(final Response response, final ChargebackJson input) throws IOException {
        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        final ChargebackCollectionJson objFromJson = mapper.readValue(response.getResponseBody(), ChargebackCollectionJson.class);
        assertEquals(objFromJson.getChargebacks().size(), 1);
        final ChargebackJson chargeBack = objFromJson.getChargebacks().get(0);
        assertTrue(chargeBack.getChargebackAmount().compareTo(input.getChargebackAmount()) == 0);
        assertEquals(chargeBack.getPaymentId(), input.getPaymentId());
    }

    private void verifySingleChargebackResponse(final Response response, final ChargebackJson input) throws IOException {
        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        final ChargebackJson objFromJson = mapper.readValue(response.getResponseBody(), ChargebackJson.class);
        assertTrue(objFromJson.getChargebackAmount().compareTo(input.getChargebackAmount()) == 0);
    }

    private PaymentJsonSimple createAccountWithInvoiceAndPayment() throws Exception {
        final InvoiceJsonSimple invoice = createAccountWithInvoice();
        return getPayment(invoice);
    }

    private InvoiceJsonSimple createAccountWithInvoice() throws Exception {
        // Create account
        final AccountJson accountJson = createAccountWithDefaultPaymentMethod(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "nohup@yahoo.com");


        // Create subscription
        final EntitlementJsonNoEvents subscriptionJson = createEntitlement(accountJson.getAccountId(), "6253283", "Shotgun", ProductCategory.BASE.toString(), BillingPeriod.MONTHLY.toString(), true);
        assertNotNull(subscriptionJson);

        // Move after the trial period to trigger an invoice with a non-zero invoice item
        clock.addDays(32);
        crappyWaitForLackOfProperSynchonization();

        // Retrieve the invoice
        final Response response = doGet(JaxrsResource.INVOICES_PATH, ImmutableMap.<String, String>of(JaxrsResource.QUERY_ACCOUNT_ID, accountJson.getAccountId()), DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        final String baseJson = response.getResponseBody();
        final List<InvoiceJsonSimple> objFromJson = mapper.readValue(baseJson, new TypeReference<List<InvoiceJsonSimple>>() {});
        assertNotNull(objFromJson);
        // We should have two invoices, one for the trial (zero dollar amount) and one for the first month
        assertEquals(objFromJson.size(), 2);
        assertTrue(objFromJson.get(1).getAmount().doubleValue() > 0);

        return objFromJson.get(1);
    }

    private PaymentJsonSimple getPayment(final InvoiceJsonSimple invoice) throws IOException {
        final String uri = JaxrsResource.INVOICES_PATH + "/" + invoice.getInvoiceId() + "/" + JaxrsResource.PAYMENTS;
        final Response response = doGet(uri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);

        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        final String baseJson = response.getResponseBody();
        final List<PaymentJsonSimple> objFromJson = mapper.readValue(baseJson, new TypeReference<List<PaymentJsonSimple>>() {});
        assertNotNull(objFromJson);
        assertEquals(objFromJson.size(), 1);

        return objFromJson.get(0);
    }
}
