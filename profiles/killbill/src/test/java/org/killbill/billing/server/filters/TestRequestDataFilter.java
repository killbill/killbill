/*
 * Copyright 2020-2022 Equinix, Inc
 * Copyright 2014-2022 The Billing Project, LLC
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

package org.killbill.billing.server.filters;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;

import org.killbill.billing.GuicyKillbillTestSuiteNoDB;
import org.killbill.billing.jaxrs.resources.JaxrsResource;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.killbill.billing.server.filters.RequestDataFilter.KILL_BILL_ENCODING_HEADER;

public class TestRequestDataFilter extends GuicyKillbillTestSuiteNoDB {

    private MultivaluedMap<String, String> applyFilterAndGetHeaders(final MultivaluedMap<String, String> headers) {
        final ContainerRequestContext requestContext = Mockito.spy(ContainerRequestContext.class);
        Mockito.doReturn(headers).when(requestContext).getHeaders();

        final RequestDataFilter requestDataFilter = new RequestDataFilter();
        requestDataFilter.filter(requestContext);

        return requestContext.getHeaders();
    }

    @Test(groups = "fast")
    public void testFilterWithBase64() {
        final MultivaluedMap<String, String> fromRequest = new MultivaluedHashMap<>();
        fromRequest.putSingle(KILL_BILL_ENCODING_HEADER, "base64");
        fromRequest.putSingle(JaxrsResource.HDR_COMMENT, "VGhpcyBpcyBhIGNvbW1lbnQ="); // "This is a comment"
        fromRequest.putSingle(JaxrsResource.HDR_REASON, "SG9vYmFzdGFuayAtIHRoZSByZWFzb24="); // "Hoobastank - the reason"
        fromRequest.putSingle(JaxrsResource.HDR_CREATED_BY, "Should be decoded, but not base64"); // Throw ILE, thus will return as is

        final MultivaluedMap<String, String> encodedHeaders = applyFilterAndGetHeaders(fromRequest);

        Assert.assertEquals(encodedHeaders.getFirst(JaxrsResource.HDR_COMMENT), "This is a comment");
        Assert.assertEquals(encodedHeaders.getFirst(JaxrsResource.HDR_REASON), "Hoobastank - the reason");
        Assert.assertEquals(encodedHeaders.getFirst(JaxrsResource.HDR_CREATED_BY), "Should be decoded, but not base64");
    }

    @Test(groups = "fast", description = "no base64 request header")
    public void testFilterWithoutBase64() {
        final MultivaluedMap<String, String> fromRequest = new MultivaluedHashMap<>();
        fromRequest.putSingle(JaxrsResource.HDR_COMMENT, "VGhpcyBpcyBhIGNvbW1lbnQ=");
        fromRequest.putSingle(JaxrsResource.HDR_REASON, "SG9vYmFzdGFuayAtIHRoZSByZWFzb24=");

        final MultivaluedMap<String, String> encodedHeaders = applyFilterAndGetHeaders(fromRequest);

        Assert.assertEquals(encodedHeaders.getFirst(JaxrsResource.HDR_COMMENT), "VGhpcyBpcyBhIGNvbW1lbnQ=");
        Assert.assertEquals(encodedHeaders.getFirst(JaxrsResource.HDR_REASON), "SG9vYmFzdGFuayAtIHRoZSByZWFzb24=");
    }

    @Test(groups = "fast", description = "base64 exist, but contains no headers that need to decoded")
    public void testFilterWithBase64WithoutFilteredHeaders() {
        final MultivaluedMap<String, String> fromRequest = new MultivaluedHashMap<>();
        fromRequest.putSingle(KILL_BILL_ENCODING_HEADER, "base64");
        fromRequest.putSingle(JaxrsResource.HDR_API_KEY, "VGhpcyBpcyBhIGNvbW1lbnQ=");
        fromRequest.putSingle(JaxrsResource.HDR_API_SECRET, "SG9vYmFzdGFuayAtIHRoZSByZWFzb24=");

        final MultivaluedMap<String, String> encodedHeaders = applyFilterAndGetHeaders(fromRequest);

        Assert.assertNull(encodedHeaders.getFirst(JaxrsResource.HDR_COMMENT));
        Assert.assertNull(encodedHeaders.getFirst(JaxrsResource.HDR_REASON));

        // returned as is
        Assert.assertEquals(encodedHeaders.getFirst(JaxrsResource.HDR_API_KEY), "VGhpcyBpcyBhIGNvbW1lbnQ=");
        Assert.assertEquals(encodedHeaders.getFirst(JaxrsResource.HDR_API_SECRET), "SG9vYmFzdGFuayAtIHRoZSByZWFzb24=");
    }
}
