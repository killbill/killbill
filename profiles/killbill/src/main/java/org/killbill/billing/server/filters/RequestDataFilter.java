/*
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.inject.Singleton;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;

import org.glassfish.jersey.server.ContainerException;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ContainerResponse;
import org.glassfish.jersey.server.spi.ContainerResponseWriter;
import org.killbill.billing.util.UUIDs;
import org.killbill.commons.request.Request;
import org.killbill.commons.request.RequestData;

@Singleton
public class RequestDataFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    private static final String LEGACY_REQUEST_ID_HEADER = "X-Killbill-Request-Id-Req";

    @Override
    public void filter(final ContainerRequestContext requestContext) {
        final List<String> requestIdHeaderRequests = getRequestId(requestContext);
        final String requestId = (requestIdHeaderRequests == null || requestIdHeaderRequests.isEmpty()) ? UUIDs.randomUUID().toString() : requestIdHeaderRequests.get(0);
        Request.setPerThreadRequestData(new RequestData(requestId));
    }

    @Override
    public void filter(final ContainerRequestContext requestContext, final ContainerResponseContext responseContext) {
        final ContainerRequest containerRequest = (ContainerRequest) requestContext;
        containerRequest.setWriter(new Adapter(containerRequest.getResponseWriter()));
    }

    private List<String> getRequestId(final ContainerRequestContext requestContext) {
        List<String> requestIds = requestContext.getHeaders().get(REQUEST_ID_HEADER);
        if (requestIds == null || requestIds.isEmpty()) {
            requestIds = requestContext.getHeaders().get(LEGACY_REQUEST_ID_HEADER);
        }
        return requestIds;
    }

    private static final class Adapter implements ContainerResponseWriter {

        private final ContainerResponseWriter crw;

        Adapter(final ContainerResponseWriter containerResponseWriter) {
            this.crw = containerResponseWriter;
        }

        @Override
        public OutputStream writeResponseStatusAndHeaders(final long contentLength, final ContainerResponse responseContext) throws ContainerException {
            return crw.writeResponseStatusAndHeaders(contentLength, responseContext);
        }

        @Override
        public boolean suspend(final long timeOut, final TimeUnit timeUnit, final TimeoutHandler timeoutHandler) {
            return crw.suspend(timeOut, timeUnit, timeoutHandler);
        }

        @Override
        public void setSuspendTimeout(final long timeOut, final TimeUnit timeUnit) throws IllegalStateException {
            crw.setSuspendTimeout(timeOut, timeUnit);
        }

        @Override
        public void commit() {
            crw.commit();

            // Reset the per-thread RequestData last
            Request.resetPerThreadRequestData();
        }

        @Override
        public void failure(final Throwable error) {
            crw.failure(error);
        }

        @Override
        public boolean enableResponseBuffering() {
            return crw.enableResponseBuffering();
        }
    }
}
