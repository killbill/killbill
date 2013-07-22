/*
 * Copyright 2010-2013 Ning, Incc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ning.billing.jaxrs;

import javax.ws.rs.core.Response.Status;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.ErrorCode;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.jaxrs.json.BillingExceptionJson;
import com.ning.billing.jaxrs.resources.JaxrsResource;
import com.ning.http.client.Response;

import com.fasterxml.jackson.core.type.TypeReference;

public class TestExceptions extends TestJaxrsBase {

    @Test(groups = "slow")
    public void testExceptionMapping() throws Exception {
        // Non-existent account
        final String uri = JaxrsResource.ACCOUNTS_PATH + "/99999999-b103-42f3-8b6e-dd244f1d0747";
        final Response response = doGet(uri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.NOT_FOUND.getStatusCode());

        final BillingExceptionJson objFromJson = mapper.readValue(response.getResponseBody(), new TypeReference<BillingExceptionJson>() {});
        Assert.assertNotNull(objFromJson);
        Assert.assertEquals(objFromJson.getClassName(), AccountApiException.class.getName());
        Assert.assertEquals(objFromJson.getCode(), (Integer) ErrorCode.ACCOUNT_DOES_NOT_EXIST_FOR_ID.getCode());
        Assert.assertTrue(objFromJson.getStackTrace().size() > 0);
    }
}
