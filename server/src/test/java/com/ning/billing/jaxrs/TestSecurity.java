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

import java.util.HashSet;
import java.util.List;

import javax.ws.rs.core.Response.Status;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.jaxrs.resources.JaxrsResource;
import com.ning.billing.security.Permission;
import com.ning.http.client.Response;

import com.google.common.collect.ImmutableSet;

public class TestSecurity extends TestJaxrsBase {

    @Test(groups = "slow")
    public void testPermissions() throws Exception {
        logout();

        final Response anonResponse = doGet(JaxrsResource.SECURITY_PATH + "/permissions", DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(anonResponse.getStatusCode(), Status.UNAUTHORIZED.getStatusCode());

        // See src/test/resources/shiro.ini

        final List<String> pierresPermissions = getPermissions("pierre", "password");
        Assert.assertEquals(pierresPermissions.size(), 2);
        Assert.assertEquals(new HashSet<String>(pierresPermissions), ImmutableSet.<String>of(Permission.INVOICE_CAN_CREDIT.toString(), Permission.INVOICE_CAN_ITEM_ADJUST.toString()));

        final List<String> stephanesPermissions = getPermissions("stephane", "password");
        Assert.assertEquals(stephanesPermissions.size(), 1);
        Assert.assertEquals(new HashSet<String>(stephanesPermissions), ImmutableSet.<String>of(Permission.PAYMENT_CAN_REFUND.toString()));
    }
}
