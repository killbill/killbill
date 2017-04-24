/*
 * Copyright 2016 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.jaxrs;

import java.net.URI;
import java.util.UUID;

import javax.servlet.ServletRequest;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.killbill.billing.jaxrs.resources.AccountResource;
import org.killbill.billing.jaxrs.util.JaxrsUriBuilder;
import org.killbill.billing.server.log.ServerTestSuiteNoDB;
import org.killbill.billing.util.config.definition.JaxrsConfig;
import org.testng.annotations.Test;

import static javax.ws.rs.core.Response.Status.CREATED;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

public class TestBuildResponse extends ServerTestSuiteNoDB {

    @Test(groups = "fast", description = "Tests Uri Builder with Path Like URL and root Location")
    public void testUriBuilderWithPathLikeUrlAndRoot() throws Exception {
        UUID objectId = UUID.randomUUID();

        final UriInfo uriInfo = mock(UriInfo.class);
        URI uri = URI.create("http://localhost:8080");
        when(uriInfo.getBaseUri()).thenReturn(uri);

        JaxrsConfig jaxrsConfig = mock(JaxrsConfig.class);
        when(jaxrsConfig.isJaxrsLocationFullUrl()).thenReturn(false);
        JaxrsUriBuilder uriBuilder = new JaxrsUriBuilder(jaxrsConfig);
        Response response = uriBuilder.buildResponse(uriInfo, AccountResource.class, "getAccount", objectId, mockRequest(uriInfo));

        assertEquals(response.getStatus(), CREATED.getStatusCode());
        assertEquals(response.getMetadata().get("Location").get(0), "/1.0/kb/accounts/" + objectId.toString());
    }

    @Test(groups = "fast", description = "Tests Uri Builder with Path Like URL and non root Location")
    public void testUriBuilderWithPathLikeUrlAndNonRoot() throws Exception {
        UUID objectId = UUID.randomUUID();

        final UriInfo uriInfo = mock(UriInfo.class);
        URI uri = URI.create("http://localhost:8080/killbill");
        when(uriInfo.getBaseUri()).thenReturn(uri);

        JaxrsConfig jaxrsConfig = mock(JaxrsConfig.class);
        when(jaxrsConfig.isJaxrsLocationFullUrl()).thenReturn(false);
        JaxrsUriBuilder uriBuilder = new JaxrsUriBuilder(jaxrsConfig);
        Response response = uriBuilder.buildResponse(uriInfo, AccountResource.class, "getAccount", objectId, mockRequest(uriInfo));

        assertEquals(response.getStatus(), CREATED.getStatusCode());
        assertEquals(response.getMetadata().get("Location").get(0), "/killbill/1.0/kb/accounts/" + objectId.toString());
    }

    @Test(groups = "fast", description = "Tests Uri Builder with Full URL and root Location")
    public void testUriBuilderWithoutPathLikeUrlAndRoot() throws Exception {
        UUID objectId = UUID.randomUUID();

        final UriInfo uriInfo = mock(UriInfo.class);
        URI uri = URI.create("http://localhost:8080");
        when(uriInfo.getBaseUri()).thenReturn(uri);
        when(uriInfo.getAbsolutePath()).thenReturn(uri);

        JaxrsConfig jaxrsConfig = mock(JaxrsConfig.class);
        when(jaxrsConfig.isJaxrsLocationFullUrl()).thenReturn(true);
        JaxrsUriBuilder uriBuilder = new JaxrsUriBuilder(jaxrsConfig);
        Response response = uriBuilder.buildResponse(uriInfo, AccountResource.class, "getAccount", objectId, mockRequest(uriInfo));

        assertEquals(response.getStatus(), CREATED.getStatusCode());
        assertEquals(response.getMetadata().get("Location").get(0).toString(), uri.toString() + "/1.0/kb/accounts/" + objectId.toString());
    }

    @Test(groups = "fast", description = "Tests Uri Builder with Full URL and non root Location")
    public void testUriBuilderWithoutPathLikeUrlAndNonRoot() throws Exception {
        UUID objectId = UUID.randomUUID();

        final UriInfo uriInfo = mock(UriInfo.class);
        URI uri = URI.create("http://localhost:8080/killbill");
        when(uriInfo.getBaseUri()).thenReturn(uri);
        when(uriInfo.getAbsolutePath()).thenReturn(uri);

        JaxrsConfig jaxrsConfig = mock(JaxrsConfig.class);
        when(jaxrsConfig.isJaxrsLocationFullUrl()).thenReturn(true);
        JaxrsUriBuilder uriBuilder = new JaxrsUriBuilder(jaxrsConfig);
        Response response = uriBuilder.buildResponse(uriInfo, AccountResource.class, "getAccount", objectId, mockRequest(uriInfo));

        assertEquals(response.getStatus(), CREATED.getStatusCode());
        assertEquals(response.getMetadata().get("Location").get(0).toString(), uri.toString() + "/1.0/kb/accounts/" + objectId.toString());
    }

    private ServletRequest mockRequest(final UriInfo uriInfo) throws Exception {
        final ServletRequest request = mock(ServletRequest.class);
        final URI absolutePath = uriInfo.getAbsolutePath();
        if (absolutePath != null) {
            final String scheme = absolutePath.getScheme();
            when(request.getScheme()).thenReturn(scheme);
            final int port = absolutePath.getPort();
            when(request.getServerPort()).thenReturn(port);
        }
        return request;
    }
}
