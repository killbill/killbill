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

import static org.testng.Assert.assertTrue;

import javax.ws.rs.core.Response.Status;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.beatrix.integration.TestBusHandler.NextEvent;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Duration;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.catalog.api.TimeUnit;
import com.ning.billing.jaxrs.json.AccountJson;
import com.ning.billing.jaxrs.json.BundleJson;
import com.ning.billing.jaxrs.json.SubscriptionJson;
import com.ning.billing.jaxrs.resources.BaseJaxrsResource;
import com.ning.http.client.Response;

public class TestSubscription extends TestJaxrsBase {
	
	private static final Logger log = LoggerFactory.getLogger(TestSubscription.class);

	private static final long DELAY = 5000;

	@Test(groups="slow", enabled=true)
	public void testSubscriptionOk() throws Exception {

		AccountJson accountJson = createAccount("xil", "shdxilhkkl", "xil@yahoo.com");
		BundleJson bundleJson = createBundle(accountJson.getAcountId(), "99999");
		
        String productName = "Shotgun";
        BillingPeriod term = BillingPeriod.MONTHLY;

        //busHandler.pushExpectedEvent(NextEvent.CREATE);
        //busHandler.pushExpectedEvent(NextEvent.INVOICE);
		SubscriptionJson subscriptionJson = createSubscription(bundleJson.getBundleId(), productName, ProductCategory.BASE.toString(), term.toString());
		//assertTrue(busHandler.isCompleted(DELAY));
		
		// Change plan IMM
		String newProductName = "Assault-Rifle";
	
		SubscriptionJson newInput = new SubscriptionJson(subscriptionJson.getSubscriptionId(),
				subscriptionJson.getBundleId(),
				newProductName,
				subscriptionJson.getProductCategory(), 
				subscriptionJson.getBillingPeriod(), 
				subscriptionJson.getPriceList(), null, null, null);
		String baseJson = mapper.writeValueAsString(newInput);
		
		String uri = BaseJaxrsResource.SUBSCRIPTIONS_PATH + "/" + subscriptionJson.getSubscriptionId().toString();
		Response response = doPut(uri, baseJson, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
		Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
		baseJson = response.getResponseBody();
		SubscriptionJson objFromJson = mapper.readValue(baseJson, SubscriptionJson.class);
		Assert.assertTrue(objFromJson.equals(newInput));
		
		// MOVE after TRIAL
	    //busHandler.pushExpectedEvent(NextEvent.PHASE);
	    //busHandler.pushExpectedEvent(NextEvent.INVOICE);
	    //busHandler.pushExpectedEvent(NextEvent.PAYMENT);
		clock.setDeltaFromReality(new Duration() {
			@Override
			public TimeUnit getUnit() {
				return TimeUnit.MONTHS;
			}
			@Override
			public int getNumber() {
				return 1;
			}
			@Override
			public DateTime addToDateTime(DateTime dateTime) {
				return null;
			}
		}, 1000);
		//assertTrue(busHandler.isCompleted(DELAY));		
		
		
		Thread.sleep(5000);
		
		// Cancel EOT
		uri = BaseJaxrsResource.SUBSCRIPTIONS_PATH + "/" + subscriptionJson.getSubscriptionId().toString();
		response = doDelete(uri, DEFAULT_EMPTY_QUERY, 10000 /* DEFAULT_HTTP_TIMEOUT_SEC */);
		Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
		
		
		// Uncancel
		uri = BaseJaxrsResource.SUBSCRIPTIONS_PATH + "/" + subscriptionJson.getSubscriptionId().toString() + "/uncancel";
		response = doPut(uri, baseJson, DEFAULT_EMPTY_QUERY, 10000 /* DEFAULT_HTTP_TIMEOUT_SEC */);
		Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
	}

}
