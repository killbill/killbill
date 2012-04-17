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

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response.Status;

import org.codehaus.jackson.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.jaxrs.json.AccountJson;
import com.ning.billing.jaxrs.json.BundleJson;
import com.ning.billing.jaxrs.resources.BaseJaxrsResource;
import com.ning.http.client.Response;

public class TestBundle extends TestJaxrsBase {

	private static final Logger log = LoggerFactory.getLogger(TestBundle.class);


	
	@Test(groups="slow", enabled=true)
	public void testBundleOk() throws Exception {

		AccountJson accountJson = createAccount("xlxl", "shdgfhkkl", "xlxl@yahoo.com");
		BundleJson bundleJson = createBundle(accountJson.getAcountId(), "12345");
		
		// Retrieves by external key
		Map<String, String> queryParams = new HashMap<String, String>();
		queryParams.put(BaseJaxrsResource.QUERY_EXTERNAL_KEY, "12345");
		Response response = doGet(BaseJaxrsResource.BUNDLES_PATH, queryParams, DEFAULT_HTTP_TIMEOUT_SEC);
		Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
		String baseJson = response.getResponseBody();
		BundleJson objFromJson = mapper.readValue(baseJson, BundleJson.class);
		Assert.assertTrue(objFromJson.equals(bundleJson));
	}
	
	
	@Test(groups="slow", enabled=true)
	public void testBundleFromAccount() throws Exception {

		AccountJson accountJson = createAccount("xaxa", "saagfhkkl", "xaxa@yahoo.com");
		BundleJson bundleJson1 = createBundle(accountJson.getAcountId(), "156567");
		BundleJson bundleJson2 = createBundle(accountJson.getAcountId(), "265658");

		String uri = BaseJaxrsResource.ACCOUNTS_PATH + "/" + accountJson.getAcountId().toString() + "/" + BaseJaxrsResource.BUNDLES;
		Response response = doGet(uri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
		Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
		String baseJson = response.getResponseBody();
		List<BundleJson> objFromJson = mapper.readValue(baseJson, new TypeReference<List<BundleJson>>() {});
		
		Collections.sort(objFromJson, new Comparator<BundleJson>() {
			@Override
			public int compare(BundleJson o1, BundleJson o2) {
				return o1.getExternalKey().compareTo(o2.getExternalKey());
			}
		});
		Assert.assertEquals(objFromJson.get(0), bundleJson1);
		Assert.assertEquals(objFromJson.get(1), bundleJson2);		
	}
	
	@Test(groups="slow", enabled=true)
	public void testBundleNonExistent() throws Exception {
		AccountJson accountJson = createAccount("dfdf", "dfdfgfhkkl", "dfdf@yahoo.com");	
		
		String uri = BaseJaxrsResource.BUNDLES_PATH + "/99999999-b103-42f3-8b6e-dd244f1d0747";
		Response response = doGet(uri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
		Assert.assertEquals(response.getStatusCode(), Status.NO_CONTENT.getStatusCode());
		
		
		// Retrieves by external key
		Map<String, String> queryParams = new HashMap<String, String>();
		queryParams.put(BaseJaxrsResource.QUERY_EXTERNAL_KEY, "56566");
		response = doGet(BaseJaxrsResource.BUNDLES_PATH, queryParams, DEFAULT_HTTP_TIMEOUT_SEC);
		Assert.assertEquals(response.getStatusCode(), Status.NO_CONTENT.getStatusCode());
		
		
		uri = BaseJaxrsResource.ACCOUNTS_PATH + "/" + accountJson.getAcountId().toString() + "/" + BaseJaxrsResource.BUNDLES;
		response = doGet(uri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
		Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
		String baseJson = response.getResponseBody();
		List<BundleJson> objFromJson = mapper.readValue(baseJson, new TypeReference<List<BundleJson>>() {});
		Assert.assertNotNull(objFromJson);
		Assert.assertEquals(objFromJson.size(), 0);
	}

	@Test(groups="slow", enabled=true)
	public void testAppNonExistent() throws Exception {
		String uri = BaseJaxrsResource.ACCOUNTS_PATH + "/99999999-b103-42f3-8b6e-dd244f1d0747/" + BaseJaxrsResource.BUNDLES;
		Response response = doGet(uri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
		Assert.assertEquals(response.getStatusCode(), Status.NO_CONTENT.getStatusCode());	
	}
	
	
}
