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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response.Status;

import org.codehaus.jackson.type.TypeReference;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.jaxrs.json.AccountJson;
import com.ning.billing.jaxrs.json.BundleJsonNoSubsciptions;
import com.ning.billing.jaxrs.json.InvoiceJson;
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
        
        BundleJsonNoSubsciptions bundleJson = createBundle(accountJson.getAcountId(), "9967599");
        assertNotNull(bundleJson);
        
        SubscriptionJsonNoEvents subscriptionJson = createSubscription(bundleJson.getBundleId(), "Shotgun", ProductCategory.BASE.toString(), BillingPeriod.MONTHLY.toString(), true);
        assertNotNull(subscriptionJson);
        
        // MOVE AFTER TRIAL
        Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusMonths(3).plusDays(1));
        clock.addDeltaFromReality(it.toDurationMillis());

        crappyWaitForLackOfProperSynchonization();
        
        String uri = BaseJaxrsResource.INVOICES_PATH;
        Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put(BaseJaxrsResource.QUERY_ACCOUNT_ID, accountJson.getAcountId());
        
        Response response = doGet(uri, queryParams, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        String baseJson = response.getResponseBody();
        List<InvoiceJson> objFromJson = mapper.readValue(baseJson, new TypeReference<List<InvoiceJson>>() {});
        assertNotNull(objFromJson);
        log.info(baseJson);
        assertEquals(objFromJson.size(), 4);
        
        // Check we can retrieve an individual invoice
        uri = BaseJaxrsResource.INVOICES_PATH + "/" + objFromJson.get(0).getInvoiceId();
        response = doGet(uri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());        
        baseJson = response.getResponseBody();
        InvoiceJson firstInvoiceJson = mapper.readValue(baseJson, InvoiceJson.class);
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
        InvoiceJson futureInvoice = mapper.readValue(baseJson, InvoiceJson.class);
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
        objFromJson = mapper.readValue(baseJson, new TypeReference<List<InvoiceJson>>() {});
        assertNotNull(objFromJson);
        log.info(baseJson);
        assertEquals(objFromJson.size(), 5);
    }
}
