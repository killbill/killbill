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

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.ws.rs.core.Response.Status;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.jaxrs.json.AccountJson;
import com.ning.billing.jaxrs.json.AccountTimelineJson;
import com.ning.billing.jaxrs.json.BillCycleDayJson;
import com.ning.billing.jaxrs.json.BundleJsonNoSubscriptions;
import com.ning.billing.jaxrs.json.CustomFieldJson;
import com.ning.billing.jaxrs.json.PaymentJsonSimple;
import com.ning.billing.jaxrs.json.PaymentMethodJson;
import com.ning.billing.jaxrs.json.RefundJson;
import com.ning.billing.jaxrs.json.SubscriptionJsonNoEvents;
import com.ning.billing.jaxrs.json.TagDefinitionJson;
import com.ning.billing.jaxrs.json.TagJson;
import com.ning.billing.jaxrs.resources.JaxrsResource;
import com.ning.http.client.Response;

import com.fasterxml.jackson.core.type.TypeReference;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class TestAccount extends TestJaxrsBase {

    private static final Logger log = LoggerFactory.getLogger(TestAccount.class);

    @Test(groups = "slow")
    public void testAccountOk() throws Exception {

        final AccountJson input = createAccount("xoxo", "shdgfhwe", "xoxo@yahoo.com");

        // Retrieves by external key
        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put(JaxrsResource.QUERY_EXTERNAL_KEY, "shdgfhwe");
        Response response = doGet(JaxrsResource.ACCOUNTS_PATH, queryParams, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        String baseJson = response.getResponseBody();
        AccountJson objFromJson = mapper.readValue(baseJson, AccountJson.class);
        Assert.assertTrue(objFromJson.equals(input));

        // Update Account
        final AccountJson newInput = new AccountJson(objFromJson.getAccountId(),
                                                     "zozo", 4, objFromJson.getExternalKey(), "rr@google.com", new BillCycleDayJson(18, 18),
                                                     "USD", null, "UTC", "bl1", "bh2", "", "", "ca", "San Francisco", "usa", "en", "415-255-2991",
                                                     false, false);
        baseJson = mapper.writeValueAsString(newInput);
        final String uri = JaxrsResource.ACCOUNTS_PATH + "/" + objFromJson.getAccountId();
        response = doPut(uri, baseJson, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        baseJson = response.getResponseBody();
        objFromJson = mapper.readValue(baseJson, AccountJson.class);
        Assert.assertTrue(objFromJson.equals(newInput));
    }

    @Test(groups = "slow")
    public void testUpdateNonExistentAccount() throws Exception {
        final AccountJson input = getAccountJson("xoxo", "shghaahwe", "xoxo@yahoo.com");
        final String baseJson = mapper.writeValueAsString(input);
        final String uri = JaxrsResource.ACCOUNTS_PATH + "/" + input.getAccountId();
        final Response response = doPut(uri, baseJson, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.NOT_FOUND.getStatusCode());
    }

    @Test(groups = "slow")
    public void testAccountNonExistent() throws Exception {
        final String uri = JaxrsResource.ACCOUNTS_PATH + "/99999999-b103-42f3-8b6e-dd244f1d0747";
        final Response response = doGet(uri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.NOT_FOUND.getStatusCode());
    }

    @Test(groups = "slow")
    public void testAccountBadAccountId() throws Exception {
        final String uri = JaxrsResource.ACCOUNTS_PATH + "/yo";
        final Response response = doGet(uri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.NOT_FOUND.getStatusCode());
    }

    @Test(groups = "slow")
    public void testAccountTimeline() throws Exception {

        clock.setTime(new DateTime(2012, 4, 25, 0, 3, 42, 0));

        final AccountJson accountJson = createAccountWithDefaultPaymentMethod("poney", "shdddqgfhwe", "poney@yahoo.com");
        assertNotNull(accountJson);

        final BundleJsonNoSubscriptions bundleJson = createBundle(accountJson.getAccountId(), "996599");
        assertNotNull(bundleJson);

        final SubscriptionJsonNoEvents subscriptionJson = createSubscription(bundleJson.getBundleId(), "Shotgun", ProductCategory.BASE.toString(), BillingPeriod.MONTHLY.toString(), true);
        assertNotNull(subscriptionJson);

        // MOVE AFTER TRIAL
        clock.addDays(31);

        crappyWaitForLackOfProperSynchonization();

        final String uri = JaxrsResource.ACCOUNTS_PATH + "/" + accountJson.getAccountId() + "/" + JaxrsResource.TIMELINE;

        final Response response = doGet(uri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        final String baseJson = response.getResponseBody();
        final AccountTimelineJson objFromJson = mapper.readValue(baseJson, AccountTimelineJson.class);
        assertNotNull(objFromJson);
        log.info(baseJson);

        Assert.assertEquals(objFromJson.getPayments().size(), 1);
        Assert.assertEquals(objFromJson.getInvoices().size(), 2);
        Assert.assertEquals(objFromJson.getBundles().size(), 1);
        Assert.assertEquals(objFromJson.getBundles().get(0).getSubscriptions().size(), 1);
        Assert.assertEquals(objFromJson.getBundles().get(0).getSubscriptions().get(0).getEvents().size(), 2);
    }

    @Test(groups = "slow")
    public void testAccountPaymentMethods() throws Exception {

        final AccountJson accountJson = createAccount("qwerty", "ytrewq", "qwerty@yahoo.com");
        assertNotNull(accountJson);

        String uri = JaxrsResource.ACCOUNTS_PATH + "/" + accountJson.getAccountId() + "/" + JaxrsResource.PAYMENT_METHODS;
        PaymentMethodJson paymentMethodJson = getPaymentMethodJson(accountJson.getAccountId(), getPaymentMethodCCProperties());
        String baseJson = mapper.writeValueAsString(paymentMethodJson);
        Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put(JaxrsResource.QUERY_PAYMENT_METHOD_IS_DEFAULT, "true");

        Response response = doPost(uri, baseJson, queryParams, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.CREATED.getStatusCode());

        final String locationCC = response.getHeader("Location");
        Assert.assertNotNull(locationCC);

        // Retrieves by Id based on Location returned
        response = doGetWithUrl(locationCC, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        baseJson = response.getResponseBody();
        final PaymentMethodJson paymentMethodCC = mapper.readValue(baseJson, PaymentMethodJson.class);
        assertTrue(paymentMethodCC.isDefault());
        //
        // Add another payment method
        //
        uri = JaxrsResource.ACCOUNTS_PATH + "/" + accountJson.getAccountId() + "/" + JaxrsResource.PAYMENT_METHODS;
        paymentMethodJson = getPaymentMethodJson(accountJson.getAccountId(), getPaymentMethodPaypalProperties());
        baseJson = mapper.writeValueAsString(paymentMethodJson);

        response = doPost(uri, baseJson, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.CREATED.getStatusCode());

        final String locationPP = response.getHeader("Location");
        assertNotNull(locationPP);
        response = doGetWithUrl(locationPP, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        baseJson = response.getResponseBody();
        final PaymentMethodJson paymentMethodPP = mapper.readValue(baseJson, PaymentMethodJson.class);
        assertFalse(paymentMethodPP.isDefault());

        //
        // FETCH ALL PAYMENT METHODS
        //
        queryParams = new HashMap<String, String>();
        queryParams.put(JaxrsResource.QUERY_PAYMENT_METHOD_PLUGIN_INFO, "true");
        response = doGet(uri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        baseJson = response.getResponseBody();
        List<PaymentMethodJson> paymentMethods = mapper.readValue(baseJson, new TypeReference<List<PaymentMethodJson>>() {});
        assertEquals(paymentMethods.size(), 2);

        //
        // CHANGE DEFAULT
        //
        uri = JaxrsResource.ACCOUNTS_PATH + "/" + accountJson.getAccountId() + "/" + JaxrsResource.PAYMENT_METHODS + "/" + paymentMethodPP.getPaymentMethodId() + "/" + JaxrsResource.PAYMENT_METHODS_DEFAULT_PATH_POSTFIX;
        response = doPut(uri, "{}", DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());

        response = doGetWithUrl(locationPP, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        baseJson = response.getResponseBody();
        final PaymentMethodJson paymentMethodPPDefault = mapper.readValue(baseJson, PaymentMethodJson.class);
        assertTrue(paymentMethodPPDefault.isDefault());

        //
        // DELETE NON DEFAULT PM
        //
        uri = JaxrsResource.PAYMENT_METHODS_PATH + "/" + paymentMethodCC.getPaymentMethodId();
        response = doDelete(uri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());

        //
        // FETCH ALL PAYMENT METHODS
        //
        uri = JaxrsResource.ACCOUNTS_PATH + "/" + accountJson.getAccountId() + "/" + JaxrsResource.PAYMENT_METHODS;
        queryParams = new HashMap<String, String>();
        queryParams.put(JaxrsResource.QUERY_PAYMENT_METHOD_PLUGIN_INFO, "true");
        response = doGet(uri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        baseJson = response.getResponseBody();
        paymentMethods = mapper.readValue(baseJson, new TypeReference<List<PaymentMethodJson>>() {});
        assertEquals(paymentMethods.size(), 1);


        //
        // DELETE DEFAULT PAYMENT METHOD (without special flag first)
        //
        uri = JaxrsResource.PAYMENT_METHODS_PATH + "/" + paymentMethodPP.getPaymentMethodId() ;
        response = doDelete(uri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.BAD_REQUEST.getStatusCode());

        //
        // RETRY TO DELETE DEFAULT PAYMENT METHOD (with special flag this time)
        //
        uri = JaxrsResource.PAYMENT_METHODS_PATH + "/" + paymentMethodPP.getPaymentMethodId() ;
        queryParams = new HashMap<String, String>();
        queryParams.put(JaxrsResource.QUERY_DELETE_DEFAULT_PM_WITH_AUTO_PAY_OFF, "true");

        response = doDelete(uri, queryParams, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());

        // CHECK ACCOUNT IS NOW AUTO_PAY_OFF

        uri = JaxrsResource.ACCOUNTS_PATH + "/" + accountJson.getAccountId() + "/" + JaxrsResource.TAGS;
        response = doGet(uri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());

        baseJson = response.getResponseBody();
        List<TagJson> tagsJson = mapper.readValue(baseJson, new TypeReference<List<TagJson>>() {});
        Assert.assertEquals(tagsJson.size(), 1);
        TagJson tagJson = tagsJson.get(0);
        Assert.assertEquals(tagJson.getTagDefinitionName(), "AUTO_PAY_OFF");
        Assert.assertEquals(tagJson.getTagDefinitionId(), new UUID(0, 1).toString());

        // FETCH ACCOUNT AGAIN AND CHECK THERE IS NO DEFAULT PAYMENT METHOD SET
        uri = JaxrsResource.ACCOUNTS_PATH + "/" + accountJson.getAccountId();
        response = doGet(uri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);

        AccountJson updatedAccountJson = mapper.readValue(response.getResponseBody(), AccountJson.class);
        Assert.assertEquals(updatedAccountJson.getAccountId(), accountJson.getAccountId());
        Assert.assertNull(updatedAccountJson.getPaymentMethodId());

        //
        // FINALLY TRY TO REMOVE AUTO_PAY_OFF WITH NO DEFAULT PAYMENT METHOD ON ACCOUNT
        //
        uri = JaxrsResource.ACCOUNTS_PATH + "/" + accountJson.getAccountId() + "/" + JaxrsResource.TAGS;
        queryParams = new HashMap<String, String>();
        queryParams.put(JaxrsResource.QUERY_TAGS, new UUID(0, 1).toString());
        response = doDelete(uri, queryParams, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.BAD_REQUEST.getStatusCode());

    }

    @Test(groups = "slow")
    public void testAccountPaymentsWithRefund() throws Exception {

        //clock.setTime(new DateTime(2012, 4, 25, 0, 3, 42, 0));

        final AccountJson accountJson = createAccountWithDefaultPaymentMethod("ermenehildo", "shtyrgfhwe", "ermenehildo@yahoo.com");
        assertNotNull(accountJson);

        final BundleJsonNoSubscriptions bundleJson = createBundle(accountJson.getAccountId(), "396199");
        assertNotNull(bundleJson);

        final SubscriptionJsonNoEvents subscriptionJson = createSubscription(bundleJson.getBundleId(), "Shotgun", ProductCategory.BASE.toString(), BillingPeriod.MONTHLY.toString(), true);
        assertNotNull(subscriptionJson);

        clock.addMonths(1);
        crappyWaitForLackOfProperSynchonization();

        String uri = JaxrsResource.ACCOUNTS_PATH + "/" + accountJson.getAccountId() + "/" + JaxrsResource.PAYMENTS;

        Response response = doGet(uri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        String baseJson = response.getResponseBody();
        List<PaymentJsonSimple> objFromJson = mapper.readValue(baseJson, new TypeReference<List<PaymentJsonSimple>>() {});
        Assert.assertEquals(objFromJson.size(), 1);

        uri = JaxrsResource.ACCOUNTS_PATH + "/" + accountJson.getAccountId() + "/" + JaxrsResource.REFUNDS;
        response = doGet(uri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        baseJson = response.getResponseBody();
        List<RefundJson> objRefundFromJson = mapper.readValue(baseJson, new TypeReference<List<RefundJson>>() {});
        Assert.assertEquals(objRefundFromJson.size(), 0);

    }

    @Test(groups = "slow")
    public void testTags() throws Exception {

        // Use tag definition for AUTO_PAY_OFF
        final TagDefinitionJson input = new TagDefinitionJson(new UUID(0, 1).toString(), "AUTO_PAY_OFF", "nothing more to say");

        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put(JaxrsResource.QUERY_TAGS, input.getId());
        final String uri = JaxrsResource.ACCOUNTS_PATH + "/" + UUID.randomUUID().toString() + "/" + JaxrsResource.TAGS;
        Response response = doPost(uri, null, queryParams, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.CREATED.getStatusCode());

        // Retrieves by Id based on Location returned
        final String url = getUrlFromUri(uri);
        response = doGetWithUrl(url, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
    }

    @Test(groups = "slow")
    public void testCustomFields() throws Exception {

        final AccountJson accountJson = createAccount("yoyoq", "gfgrqe", "yoyoq@yahoo.com");
        assertNotNull(accountJson);

        final List<CustomFieldJson> customFields = new LinkedList<CustomFieldJson>();
        customFields.add(new CustomFieldJson("1", "value1"));
        customFields.add(new CustomFieldJson("2", "value2"));
        customFields.add(new CustomFieldJson("3", "value3"));
        final String baseJson = mapper.writeValueAsString(customFields);

        final String uri = JaxrsResource.ACCOUNTS_PATH + "/" + accountJson.getAccountId() + "/" + JaxrsResource.CUSTOM_FIELDS;
        Response response = doPost(uri, baseJson, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.CREATED.getStatusCode());

        // Retrieves by Id based on Location returned
        final String url = getUrlFromUri(uri);
        response = doGetWithUrl(url, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
    }
}
