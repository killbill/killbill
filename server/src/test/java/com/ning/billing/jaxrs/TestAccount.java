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


import java.util.HashMap;
import java.util.Map;


import javax.ws.rs.core.Response.Status;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;


import com.ning.billing.jaxrs.json.AccountJson;
import com.ning.billing.jaxrs.resources.BaseJaxrsResource;
import com.ning.http.client.Response;


public class TestAccount extends TestJaxrsBase {

	private static final Logger log = LoggerFactory.getLogger(TestAccount.class);


	@Test(groups="slow", enabled=true)
	public void testAccountOk() throws Exception {
		
		AccountJson input = getAccountJson("xoxo", "shdgfhwe", "xoxo@yahoo.com");
		String baseJson = mapper.writeValueAsString(input);
		Response response = doPost(BaseJaxrsResource.ACCOUNTS_PATH, baseJson, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
		Assert.assertEquals(response.getStatusCode(), Status.CREATED.getStatusCode());

		String location = response.getHeader("Location");
		Assert.assertNotNull(location);

		// Retrieves by Id based on Location returned
		response = doGetWithUrl(location, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
		Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());

		baseJson = response.getResponseBody();
		AccountJson objFromJson = mapper.readValue(baseJson, AccountJson.class);
		Assert.assertTrue(objFromJson.equalsNoId(input));

		// Retrieves by external key
		Map<String, String> queryParams = new HashMap<String, String>();
		queryParams.put(BaseJaxrsResource.QUERY_EXTERNAL_KEY, "shdgfhwe");
		response = doGet(BaseJaxrsResource.ACCOUNTS_PATH, queryParams, DEFAULT_HTTP_TIMEOUT_SEC);
		Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
		baseJson = response.getResponseBody();
		objFromJson = mapper.readValue(baseJson, AccountJson.class);
		Assert.assertTrue(objFromJson.equalsNoId(input));
		
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
		AccountJson input = getAccountJson("xoxo", "shdgfhwe", "xoxo@yahoo.com");
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
}
