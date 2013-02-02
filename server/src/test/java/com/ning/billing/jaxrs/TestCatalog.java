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

import javax.ws.rs.core.Response.Status;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.jaxrs.json.CatalogJsonSimple;
import com.ning.billing.jaxrs.resources.JaxrsResource;
import com.ning.http.client.Response;

public class TestCatalog extends TestJaxrsBase {

    private static final Logger log = LoggerFactory.getLogger(TestAccount.class);

    @Test(groups = "slow", enabled = true)
    public void testCatalogSimple() throws Exception {
        Response response = doGet(JaxrsResource.CATALOG_PATH + "/simpleCatalog", DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        String body = response.getResponseBody();
        CatalogJsonSimple objFromJson = mapper.readValue(body, CatalogJsonSimple.class);
        log.info("Yeaahh...");
    }
}
