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

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;

import org.glassfish.jersey.internal.MapPropertiesDelegate;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ContainerResponse;
import org.glassfish.jersey.server.spi.ContainerResponseWriter;
import org.killbill.billing.GuicyKillbillTestSuiteNoDB;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestEventStreamResponseFilter extends GuicyKillbillTestSuiteNoDB {

    private static final URI BASE_URI = URI.create("http://localhost:8080/1.0/");
    private static final URI REQUEST_URI = BASE_URI.resolve("plugins/logger");

    @Test(groups = "fast")
    public void testFilterDisablesBufferingForEventStream() {
        final ContainerRequest request = new ContainerRequest(BASE_URI, REQUEST_URI, "GET", null, new MapPropertiesDelegate());
        final TrackingResponseWriter originalWriter = new TrackingResponseWriter(true);
        request.setWriter(originalWriter);

        final ContainerResponseContext responseContext = Mockito.mock(ContainerResponseContext.class);
        Mockito.when(responseContext.getMediaType()).thenReturn(null);
        Mockito.when(responseContext.getHeaderString(HttpHeaders.CONTENT_TYPE)).thenReturn("text/event-stream;charset=utf-8");

        final EventStreamResponseFilter filter = new EventStreamResponseFilter();
        filter.filter(request, responseContext);

        final ContainerResponseWriter wrappedWriter = request.getResponseWriter();
        Assert.assertNotNull(wrappedWriter);

        Assert.assertFalse(wrappedWriter.enableResponseBuffering());

        // Make sure delegation still works
        wrappedWriter.commit();
        Assert.assertTrue(originalWriter.commitCalled);

        wrappedWriter.failure(new RuntimeException("boom"));
        Assert.assertTrue(originalWriter.failureCalled);
    }

    @Test(groups = "fast")
    public void testFilterDelegatesForNonEventStream() {
        final ContainerRequest request = new ContainerRequest(BASE_URI, REQUEST_URI, "GET", null, new MapPropertiesDelegate());
        final TrackingResponseWriter originalWriter = new TrackingResponseWriter(true);
        request.setWriter(originalWriter);

        final ContainerResponseContext responseContext = Mockito.mock(ContainerResponseContext.class);
        Mockito.when(responseContext.getMediaType()).thenReturn(MediaType.APPLICATION_JSON_TYPE);
        Mockito.when(responseContext.getHeaderString(HttpHeaders.CONTENT_TYPE)).thenReturn(MediaType.APPLICATION_JSON);

        final EventStreamResponseFilter filter = new EventStreamResponseFilter();
        filter.filter(request, responseContext);

        final ContainerResponseWriter wrappedWriter = request.getResponseWriter();
        Assert.assertNotNull(wrappedWriter);

        Assert.assertTrue(wrappedWriter.enableResponseBuffering());
        Assert.assertEquals(originalWriter.enableResponseBufferingInvocations, 1);
    }

    @Test(groups = "fast")
    public void testIsEventStreamRequestWithMediaType() {
        final ContainerResponseContext responseContext = Mockito.mock(ContainerResponseContext.class);
        Mockito.when(responseContext.getMediaType()).thenReturn(MediaType.SERVER_SENT_EVENTS_TYPE);

        Assert.assertTrue(EventStreamResponseFilter.isEventStreamRequest(responseContext));
    }

    @Test(groups = "fast")
    public void testIsEventStreamRequestWithHeaderFallback() {
        final ContainerResponseContext responseContext = Mockito.mock(ContainerResponseContext.class);
        Mockito.when(responseContext.getMediaType()).thenReturn(null);
        Mockito.when(responseContext.getHeaderString(HttpHeaders.CONTENT_TYPE)).thenReturn("TEXT/EVENT-STREAM; charset=UTF-8");

        Assert.assertTrue(EventStreamResponseFilter.isEventStreamRequest(responseContext));
    }

    @Test(groups = "fast")
    public void testIsEventStreamRequestNegative() {
        final ContainerResponseContext responseContext = Mockito.mock(ContainerResponseContext.class);
        Mockito.when(responseContext.getMediaType()).thenReturn(MediaType.APPLICATION_JSON_TYPE);
        Mockito.when(responseContext.getHeaderString(HttpHeaders.CONTENT_TYPE)).thenReturn(MediaType.APPLICATION_JSON);

        Assert.assertFalse(EventStreamResponseFilter.isEventStreamRequest(responseContext));
    }

    /**
     * Simple test double that records how the wrapped response writer is invoked so we can assert delegation behavior
     * without spinning up Jersey internals.
     */
    private static final class TrackingResponseWriter implements ContainerResponseWriter {

        private final boolean bufferingEnabled;
        private boolean commitCalled;
        private boolean failureCalled;
        private int enableResponseBufferingInvocations;

        private TrackingResponseWriter(final boolean bufferingEnabled) {
            this.bufferingEnabled = bufferingEnabled;
        }

        @Override
        public OutputStream writeResponseStatusAndHeaders(final long contentLength, final ContainerResponse responseContext) {
            return new ByteArrayOutputStream();
        }

        @Override
        public boolean suspend(final long timeOut, final TimeUnit timeUnit, final TimeoutHandler timeoutHandler) {
            return false;
        }

        @Override
        public void setSuspendTimeout(final long timeOut, final TimeUnit timeUnit) throws IllegalStateException {
        }

        @Override
        public void commit() {
            this.commitCalled = true;
        }

        @Override
        public void failure(final Throwable error) {
            this.failureCalled = true;
        }

        @Override
        public boolean enableResponseBuffering() {
            enableResponseBufferingInvocations++;
            return bufferingEnabled;
        }
    }
}
