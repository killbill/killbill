/*
 * Copyright 2020-2025 Equinix, Inc
 * Copyright 2014-2025 The Billing Project, LLC
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
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import javax.inject.Singleton;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;

import org.glassfish.jersey.server.ContainerException;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ContainerResponse;
import org.glassfish.jersey.server.spi.ContainerResponseWriter;
import org.killbill.commons.utils.annotation.VisibleForTesting;

@Singleton
public class EventStreamResponseFilter implements ContainerResponseFilter {

    @Override
    public void filter(final ContainerRequestContext requestContext, final ContainerResponseContext responseContext) {
        final boolean eventStreamRequest = isEventStreamRequest(responseContext);
        final ContainerRequest containerRequest = (ContainerRequest) requestContext;
        containerRequest.setWriter(new Adapter(containerRequest.getResponseWriter(), eventStreamRequest));
    }

    @VisibleForTesting
    public static boolean isEventStreamRequest(final ContainerResponseContext responseContext) {
        final MediaType mediaType = responseContext.getMediaType();
        if (mediaType != null && mediaType.isCompatible(MediaType.SERVER_SENT_EVENTS_TYPE)) {
            return true;
        }
        final String contentType = responseContext.getHeaderString(HttpHeaders.CONTENT_TYPE);
        return contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("text/event-stream");
    }

    private static class Adapter implements ContainerResponseWriter {

        private final ContainerResponseWriter delegate;
        private final boolean eventStreamRequest;

        public Adapter(final ContainerResponseWriter delegate, final boolean eventStreamRequest) {
            this.delegate = delegate;
            this.eventStreamRequest = eventStreamRequest;
        }

        @Override
        public OutputStream writeResponseStatusAndHeaders(final long l, final ContainerResponse containerResponse) throws ContainerException {
            return this.delegate.writeResponseStatusAndHeaders(l, containerResponse);
        }

        @Override
        public boolean suspend(final long l, final TimeUnit timeUnit, final TimeoutHandler timeoutHandler) {
            return this.delegate.suspend(l, timeUnit, timeoutHandler);
        }

        @Override
        public void setSuspendTimeout(final long l, final TimeUnit timeUnit) throws IllegalStateException {
            this.delegate.setSuspendTimeout(l, timeUnit);
        }

        @Override
        public void commit() {
            this.delegate.commit();
        }

        @Override
        public void failure(final Throwable throwable) {
            this.delegate.failure(throwable);
        }

        @Override
        public boolean enableResponseBuffering() {
            if (this.eventStreamRequest) {
                return false;
            }
            return this.delegate.enableResponseBuffering();
        }
    }
}
