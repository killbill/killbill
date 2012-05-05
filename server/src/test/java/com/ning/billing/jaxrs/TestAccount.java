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
import java.util.Map;


import javax.ws.rs.core.Response.Status;


import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;


import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.jaxrs.json.AccountJson;
import com.ning.billing.jaxrs.json.AccountTimelineJson;
import com.ning.billing.jaxrs.json.BundleJsonNoSubsciptions;
import com.ning.billing.jaxrs.json.SubscriptionJsonNoEvents;
import com.ning.billing.jaxrs.json.TagDefinitionJson;
import com.ning.billing.jaxrs.resources.BaseJaxrsResource;
import com.ning.http.client.Response;


public class TestAccount extends TestJaxrsBase {

	private static final Logger log = LoggerFactory.getLogger(TestAccount.class);


	@Test(groups="slow", enabled=true)
	public void testAccountOk() throws Exception {
		
		AccountJson input = createAccount("xoxo", "shdgfhwe", "xoxo@yahoo.com");
		
		// Retrieves by external key
		Map<String, String> queryParams = new HashMap<String, String>();
		queryParams.put(BaseJaxrsResource.QUERY_EXTERNAL_KEY, "shdgfhwe");
		Response response = doGet(BaseJaxrsResource.ACCOUNTS_PATH, queryParams, DEFAULT_HTTP_TIMEOUT_SEC);
		Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
		String baseJson = response.getResponseBody();
		AccountJson objFromJson = mapper.readValue(baseJson, AccountJson.class);
		Assert.assertTrue(objFromJson.equals(input));
		
		// Update Account
		AccountJson newInput = new AccountJson(objFromJson.getAcountId(),
				"zozo", 4, objFromJson.getExternalKey(), "rr@google.com", 18, "EUR", "none", "UTC", "bl1", "bh2", "", "ca", "usa", "415-255-2991");
		baseJson = mapper.writeValueAsString(newInput);
		final String uri = BaseJaxrsResource.ACCOUNTS_PATH + "/" + objFromJson.getAcountId();
		response = doPut(uri, baseJson, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
		Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
		baseJson = response.getResponseBody();
		objFromJson = mapper.readValue(baseJson, AccountJson.class);
		Assert.assertTrue(objFromJson.equals(newInput));
	}


	@Test(groups="slow", enabled=true)
	public void testUpdateNonExistentAccount() throws Exception {
		AccountJson input = getAccountJson("xoxo", "shghaahwe", "xoxo@yahoo.com");
		String baseJson = mapper.writeValueAsString(input);
		final String uri = BaseJaxrsResource.ACCOUNTS_PATH + "/" + input.getAcountId();
		Response response = doPut(uri, baseJson, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
		Assert.assertEquals(response.getStatusCode(), Status.NO_CONTENT.getStatusCode());
		String body = response.getResponseBody();
		Assert.assertEquals(body, "");
	}
	
	
	@Test(groups="slow", enabled=true)
	public void testAccountNonExistent() throws Exception {
		final String uri = BaseJaxrsResource.ACCOUNTS_PATH + "/99999999-b103-42f3-8b6e-dd244f1d0747";
		Response response = doGet(uri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
		Assert.assertEquals(response.getStatusCode(), Status.NO_CONTENT.getStatusCode());
	}
	
	@Test(groups="slow", enabled=true)
	public void testAccountBadAccountId() throws Exception {
		final String uri = BaseJaxrsResource.ACCOUNTS_PATH + "/yo";
		Response response = doGet(uri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
		Assert.assertEquals(response.getStatusCode(), Status.NOT_FOUND.getStatusCode());
	}
	
	@Test(groups="slow", enabled=true)
	public void testAccountTimeline() throws Exception {
	    
	    DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());
        
        
	    AccountJson accountJson = createAccount("poney", "shdddqgfhwe", "poney@yahoo.com");
	    assertNotNull(accountJson);
	    
	    BundleJsonNoSubsciptions bundleJson = createBundle(accountJson.getAcountId(), "996599");
	    assertNotNull(bundleJson);
	    
        SubscriptionJsonNoEvents subscriptionJson = createSubscription(bundleJson.getBundleId(), "Shotgun", ProductCategory.BASE.toString(), BillingPeriod.MONTHLY.toString(), true);
        assertNotNull(subscriptionJson);
        
        // MOVE AFTER TRIAL
        Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusMonths(3).plusDays(1));
        clock.addDeltaFromReality(it.toDurationMillis());

        crappyWaitForLackOfProperSynchonization();
        
        
        final String uri = BaseJaxrsResource.ACCOUNTS_PATH + "/" + accountJson.getAcountId() + "/" + BaseJaxrsResource.TIMELINE;
        
        Response response = doGet(uri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        String baseJson = response.getResponseBody();
        AccountTimelineJson objFromJson = mapper.readValue(baseJson, AccountTimelineJson.class);
        assertNotNull(objFromJson);
        log.info(baseJson);
        
        Assert.assertEquals(objFromJson.getPayments().size(), 3);
        Assert.assertEquals(objFromJson.getInvoices().size(), 4);   
        Assert.assertEquals(objFromJson.getBundles().size(), 1); 
        Assert.assertEquals(objFromJson.getBundles().get(0).getSubscriptions().size(), 1);
        Assert.assertEquals(objFromJson.getBundles().get(0).getSubscriptions().get(0).getEvents().size(), 2);        
 	}

	@Test(groups="slow", enabled=true)
	public void testAccountWithTags() throws Exception {
	    //Create Tag definition
	    TagDefinitionJson input = new TagDefinitionJson("yoyo", "nothing more to say");
	    String baseJson = mapper.writeValueAsString(input);
	    Response response = doPost(BaseJaxrsResource.TAG_DEFINITIONS_PATH, baseJson, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
	    assertEquals(response.getStatusCode(), Status.CREATED.getStatusCode());
	    
	    AccountJson accountJson = createAccount("couroucoucou", "shdwdsqgfhwe", "couroucoucou@yahoo.com");
	    assertNotNull(accountJson);
	        
	    Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put(BaseJaxrsResource.QUERY_TAGS, input.getName());
        String uri = BaseJaxrsResource.ACCOUNTS_PATH + "/" + BaseJaxrsResource.TAGS + "/" + accountJson.getAcountId() ;
	    response = doPost(uri, null, queryParams, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.CREATED.getStatusCode());
        
        /*
         * STEPH Some how Location returns the ID twice (confused) :
         * Location: http://127.0.0.1:8080/1.0/kb/accounts/tags/ebb5f830-6f0a-4521-9553-521d173169be/ebb5f830-6f0a-4521-9553-521d173169be
         * 
        String location = response.getHeader("Location");
        Assert.assertNotNull(location);

        // Retrieves by Id based on Location returned
        response = doGetWithUrl(location, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        */

	}


}
