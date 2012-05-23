/* 
 * Copyright 2010-2011 Ning, Inc.
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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response.Status;

import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.jaxrs.json.AccountJson;
import com.ning.billing.jaxrs.json.BundleJsonNoSubsciptions;
import com.ning.billing.jaxrs.json.InvoiceJsonSimple;
import com.ning.billing.jaxrs.json.PaymentJsonSimple;
import com.ning.billing.jaxrs.json.SubscriptionJsonNoEvents;
import com.ning.billing.jaxrs.resources.BaseJaxrsResource;
import com.ning.http.client.Response;

public class TestInvoice extends TestJaxrsBase  {

    private final DateTimeFormatter DATE_TIME_FORMATTER = ISODateTimeFormat.dateTime();

    private static final Logger log = LoggerFactory.getLogger(TestInvoice.class);


    @Test(groups="slow", enabled=true)
    public void testInvoiceOk() throws Exception {

        DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());


        AccountJson accountJson = createAccount("poupou", "qhddffrwe", "poupou@yahoo.com");
        assertNotNull(accountJson);

        BundleJsonNoSubsciptions bundleJson = createBundle(accountJson.getAccountId(), "9967599");
        assertNotNull(bundleJson);

        SubscriptionJsonNoEvents subscriptionJson = createSubscription(bundleJson.getBundleId(), "Shotgun", ProductCategory.BASE.toString(), BillingPeriod.MONTHLY.toString(), true);
        assertNotNull(subscriptionJson);

        // MOVE AFTER TRIAL
        Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusMonths(3).plusDays(1));
        clock.addDeltaFromReality(it.toDurationMillis());

        crappyWaitForLackOfProperSynchonization();

        String uri = BaseJaxrsResource.INVOICES_PATH;
        Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put(BaseJaxrsResource.QUERY_ACCOUNT_ID, accountJson.getAccountId());

        Response response = doGet(uri, queryParams, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        String baseJson = response.getResponseBody();
        List<InvoiceJsonSimple> objFromJson = mapper.readValue(baseJson, new TypeReference<List<InvoiceJsonSimple>>() {});
        assertNotNull(objFromJson);
        log.info(baseJson);
        assertEquals(objFromJson.size(), 4);

        // Check we can retrieve an individual invoice
        uri = BaseJaxrsResource.INVOICES_PATH + "/" + objFromJson.get(0).getInvoiceId();
        response = doGet(uri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());        
        baseJson = response.getResponseBody();
        InvoiceJsonSimple firstInvoiceJson = mapper.readValue(baseJson, InvoiceJsonSimple.class);
        assertNotNull(objFromJson);    
        assertEquals(firstInvoiceJson, objFromJson.get(0));

        // Then create a dryRun Invoice
        DateTime futureDate = clock.getUTCNow().plusMonths(1).plusDays(3);
        uri = BaseJaxrsResource.INVOICES_PATH;
        queryParams.put(BaseJaxrsResource.QUERY_TARGET_DATE, futureDate.toString());
        queryParams.put(BaseJaxrsResource.QUERY_DRY_RUN, "true");        
        response = doPost(uri, null, queryParams, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode()); 
        baseJson = response.getResponseBody();
        InvoiceJsonSimple futureInvoice = mapper.readValue(baseJson, InvoiceJsonSimple.class);
        assertNotNull(futureInvoice);    
        log.info(baseJson);

        // The one more time with no DryRun
        queryParams.remove(BaseJaxrsResource.QUERY_DRY_RUN);
        response = doPost(uri, null, queryParams, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.CREATED.getStatusCode());

        String location = response.getHeader("Location");
        Assert.assertNotNull(location);

        // Check again # invoices, should be 5 this time
        uri = BaseJaxrsResource.INVOICES_PATH;
        response = doGet(uri, queryParams, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        baseJson = response.getResponseBody();
        objFromJson = mapper.readValue(baseJson, new TypeReference<List<InvoiceJsonSimple>>() {});
        assertNotNull(objFromJson);
        log.info(baseJson);
        assertEquals(objFromJson.size(), 5);
    }

    @Test(groups="slow", enabled=true)
    public void testInvoicePayments() throws Exception {

        clock.setTime(new DateTime(2012, 4, 25, 0, 3, 42, 0));

        AccountJson accountJson = createAccount("nohup", "shtergyhwF", "nohup@yahoo.com");
        assertNotNull(accountJson);

        BundleJsonNoSubsciptions bundleJson = createBundle(accountJson.getAccountId(), "391193");
        assertNotNull(bundleJson);

        SubscriptionJsonNoEvents subscriptionJson = createSubscription(bundleJson.getBundleId(), "Shotgun", ProductCategory.BASE.toString(), BillingPeriod.MONTHLY.toString(), true);
        assertNotNull(subscriptionJson);

        // MOVE AFTER TRIAL
        clock.addMonths(3);

        crappyWaitForLackOfProperSynchonization();

        Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put(BaseJaxrsResource.QUERY_ACCOUNT_ID, accountJson.getAccountId());
        String uri = BaseJaxrsResource.INVOICES_PATH;
        Response response = doGet(uri, queryParams, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        String baseJson = response.getResponseBody();
        List<InvoiceJsonSimple> invoices = mapper.readValue(baseJson, new TypeReference<List<InvoiceJsonSimple>>() {});
        assertNotNull(invoices);
        log.info(baseJson);
        assertEquals(invoices.size(), 4);


        for (InvoiceJsonSimple cur : invoices) {

            uri = BaseJaxrsResource.INVOICES_PATH + "/" + cur.getInvoiceId() + "/" + BaseJaxrsResource.PAYMENTS;    
            response = doGet(uri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);

            Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
            baseJson = response.getResponseBody();
            List<PaymentJsonSimple> objFromJson = mapper.readValue(baseJson, new TypeReference<List<PaymentJsonSimple>>() {});
            assertNotNull(objFromJson);
            log.info(cur.getAmount().toString());
            if (cur.getAmount().compareTo(BigDecimal.ZERO) == 0) {
                assertEquals(objFromJson.size(), 0);
            } else {
                assertEquals(objFromJson.size(), 1);
                assertTrue(cur.getAmount().compareTo(objFromJson.get(0).getAmount()) == 0);
            }
        }
    }



    @Test(groups="slow", enabled=true)
    public void testInvoiceCreatePayment() throws Exception {

        clock.setTime(new DateTime(2012, 4, 25, 0, 3, 42, 0));

        AccountJson accountJson = createAccount("nohup", "shtergyhwF", "nohup@yahoo.com");
        assertNotNull(accountJson);


        // STEPH MISSING SET ACCOUNT AUTO_PAY_OFF


        BundleJsonNoSubsciptions bundleJson = createBundle(accountJson.getAccountId(), "391193");
        assertNotNull(bundleJson);

        SubscriptionJsonNoEvents subscriptionJson = createSubscription(bundleJson.getBundleId(), "Shotgun", ProductCategory.BASE.toString(), BillingPeriod.MONTHLY.toString(), true);
        assertNotNull(subscriptionJson);

        // MOVE AFTER TRIAL
        clock.addDays(31);

        crappyWaitForLackOfProperSynchonization();

        Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put(BaseJaxrsResource.QUERY_ACCOUNT_ID, accountJson.getAccountId());
        String uri = BaseJaxrsResource.INVOICES_PATH;
        Response response = doGet(uri, queryParams, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        String baseJson = response.getResponseBody();
        List<InvoiceJsonSimple> invoices = mapper.readValue(baseJson, new TypeReference<List<InvoiceJsonSimple>>() {});
        assertNotNull(invoices);
        log.info(baseJson);
        assertEquals(invoices.size(), 2);


        for (InvoiceJsonSimple cur : invoices) {
            if (cur.getAmount().compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }

            // CREATE INSTA PAYMENT
            PaymentJsonSimple payment = new PaymentJsonSimple(cur.getAmount(), BigDecimal.ZERO, accountJson.getAccountId(), cur.getInvoiceId(), null, null, null, 0, null, null);
            String postJson = mapper.writeValueAsString(payment);
            
            uri = BaseJaxrsResource.INVOICES_PATH + "/" + cur.getInvoiceId() + "/" + BaseJaxrsResource.PAYMENTS;    
            response = doPost(uri, postJson, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);

            response = doGet(uri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);

            Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
            baseJson = response.getResponseBody();
            List<PaymentJsonSimple> objFromJson = mapper.readValue(baseJson, new TypeReference<List<PaymentJsonSimple>>() {});
            assertNotNull(objFromJson);
            log.info(cur.getAmount().toString());
            assertEquals(objFromJson.size(), 1);
            assertTrue(cur.getAmount().compareTo(objFromJson.get(0).getAmount()) == 0);
        }
    }
}
