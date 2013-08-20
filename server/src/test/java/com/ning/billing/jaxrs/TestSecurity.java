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

import javax.annotation.Nullable;
import javax.ws.rs.core.Response.Status;

import org.apache.shiro.web.servlet.ShiroHttpSession;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.jaxrs.resources.JaxrsResource;
import com.ning.billing.security.Permission;
import com.ning.http.client.AsyncHttpClient.BoundRequestBuilder;
import com.ning.http.client.Cookie;
import com.ning.http.client.Response;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

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

    @Test(groups = "slow")
    public void testSession() throws Exception {
        loginAs("pierre", "password");

        final Response firstResponse = doGet(JaxrsResource.SECURITY_PATH + "/permissions", DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(firstResponse.getStatusCode(), Status.OK.getStatusCode());
        Assert.assertEquals(((List) mapper.readValue(firstResponse.getResponseBody(), new TypeReference<List<String>>() {})).size(), 2);

        // Retrieve the session id
        final Cookie session = Iterables.find(firstResponse.getCookies(),
                                              new Predicate<Cookie>() {
                                                  @Override
                                                  public boolean apply(@Nullable final Cookie cookie) {
                                                      return ShiroHttpSession.DEFAULT_SESSION_ID_NAME.equals(cookie.getName());
                                                  }
                                              });

        // Make sure we don't use the credentials anymore
        logout();

        // Re-issue the query with the cookie
        final String url = String.format("http://%s:%d%s", config.getServerHost(), config.getServerPort(), JaxrsResource.SECURITY_PATH + "/permissions");
        final BoundRequestBuilder builder = getBuilderWithHeaderAndQuery("GET", url, DEFAULT_EMPTY_QUERY);
        builder.addCookie(session);
        final Response secondResponse = executeAndWait(builder, DEFAULT_HTTP_TIMEOUT_SEC, false);
        Assert.assertEquals(secondResponse.getStatusCode(), Status.OK.getStatusCode());
        Assert.assertEquals(((List) mapper.readValue(firstResponse.getResponseBody(), new TypeReference<List<String>>() {})).size(), 2);
    }
}
