/*
 * Copyright 2016 Groupon, Inc
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

package org.killbill.billing.server.filters;

import java.io.IOException;
import java.io.OutputStream;

import org.killbill.commons.request.Request;
import org.killbill.commons.request.RequestData;
import org.slf4j.MDC;

import com.google.inject.Singleton;
import com.sun.jersey.spi.container.ContainerRequest;
import com.sun.jersey.spi.container.ContainerRequestFilter;
import com.sun.jersey.spi.container.ContainerResponse;
import com.sun.jersey.spi.container.ContainerResponseFilter;
import com.sun.jersey.spi.container.ContainerResponseWriter;

import static org.killbill.billing.util.callcontext.InternalCallContextFactory.MDC_KB_ACCOUNT_RECORD_ID;
import static org.killbill.billing.util.callcontext.InternalCallContextFactory.MDC_KB_TENANT_RECORD_ID;

@Singleton
public class KillbillMDCInsertingServletFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final String MDC_REQUEST_ID = "req.requestId";

    @Override
    public ContainerRequest filter(final ContainerRequest request) {
        final RequestData perThreadRequestData = Request.getPerThreadRequestData();
        if (perThreadRequestData != null) {
            MDC.put(MDC_REQUEST_ID, perThreadRequestData.getRequestId());
        }

        return request;
    }

    @Override
    public ContainerResponse filter(final ContainerRequest request, final ContainerResponse response) {
        response.setContainerResponseWriter(new Adapter(response.getContainerResponseWriter()));
        return response;
    }

    private static final class Adapter implements ContainerResponseWriter {

        private final ContainerResponseWriter crw;

        Adapter(final ContainerResponseWriter containerResponseWriter) {
            this.crw = containerResponseWriter;
        }

        @Override
        public OutputStream writeStatusAndHeaders(final long contentLength, final ContainerResponse response) throws IOException {
            return crw.writeStatusAndHeaders(contentLength, response);
        }

        @Override
        public void finish() throws IOException {
            crw.finish();

            // Removing possibly inexistent item is OK
            MDC.remove(MDC_REQUEST_ID);

            // Cleanup
            MDC.remove(MDC_KB_ACCOUNT_RECORD_ID);
            MDC.remove(MDC_KB_TENANT_RECORD_ID);
        }
    }
}
