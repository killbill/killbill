/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

import java.util.List;

import org.killbill.billing.util.UUIDs;
import org.killbill.commons.request.Request;
import org.killbill.commons.request.RequestData;

import com.google.inject.Singleton;
import com.sun.jersey.spi.container.ContainerRequest;
import com.sun.jersey.spi.container.ContainerRequestFilter;
import com.sun.jersey.spi.container.ContainerResponse;
import com.sun.jersey.spi.container.ContainerResponseFilter;

@Singleton
public class RequestIdFilter implements ContainerRequestFilter, ContainerResponseFilter {


    private static final String REQUEST_ID_HEADER_REQ = "X-Killbill-Request-Id-Req";

    @Override
    public ContainerRequest filter(final ContainerRequest request) {
        final List<String> requestIdHeaderRequests = request.getRequestHeader(REQUEST_ID_HEADER_REQ);
        final String requestId = (requestIdHeaderRequests == null || requestIdHeaderRequests.isEmpty()) ? UUIDs.randomUUID().toString() : requestIdHeaderRequests.get(0);
        Request.setPerThreadRequestData(new RequestData(requestId));
        return request;
    }

    @Override
    public ContainerResponse filter(final ContainerRequest request, final ContainerResponse response) {
        Request.resetPerThreadRequestData();
        return response;
    }
}
