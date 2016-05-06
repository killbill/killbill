/*
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

import org.killbill.billing.jaxrs.json.ProfilingDataJson;
import org.killbill.billing.util.jackson.ObjectMapper;
import org.killbill.commons.profiling.Profiling;
import org.killbill.commons.profiling.ProfilingData;
import org.killbill.commons.profiling.ProfilingFeature.ProfilingFeatureType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.inject.Singleton;
import com.sun.jersey.spi.container.ContainerRequest;
import com.sun.jersey.spi.container.ContainerRequestFilter;
import com.sun.jersey.spi.container.ContainerResponse;
import com.sun.jersey.spi.container.ContainerResponseFilter;

@Singleton
public class ProfilingContainerResponseFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final Logger log = LoggerFactory.getLogger(ProfilingContainerResponseFilter.class);

    private static final String PROFILING_HEADER_REQ = "X-Killbill-Profiling-Req";
    private static final String PROFILING_HEADER_RESP = "X-Killbill-Profiling-Resp";

    private static final ObjectMapper mapper = new ObjectMapper();

    static {
        mapper.configure(SerializationFeature.WRITE_EMPTY_JSON_ARRAYS, false);
    }

    @Override
    public ContainerRequest filter(final ContainerRequest request) {

        final List<String> profilingHeaderRequests = request.getRequestHeader(PROFILING_HEADER_REQ);
        final String profilingHeaderRequest = (profilingHeaderRequests == null || profilingHeaderRequests.isEmpty()) ? null : profilingHeaderRequests.get(0);
        if (profilingHeaderRequest != null) {
            try {
                Profiling.setPerThreadProfilingData(profilingHeaderRequest);
                // If we need to profile JAXRS let's do it...
                final ProfilingData profilingData = Profiling.getPerThreadProfilingData();
                if (profilingData.getProfileFeature().isProfilingJAXRS()) {
                    profilingData.addStart(ProfilingFeatureType.JAXRS, request.getPath());
                }
            } catch (IllegalArgumentException e) {
                log.info("Profiling data output {} is not supported, profiling NOT enabled", profilingHeaderRequest);
            }
        }
        return request;
    }

    @Override
    public ContainerResponse filter(final ContainerRequest request, final ContainerResponse response) {
        try {
            final ProfilingData rawData = Profiling.getPerThreadProfilingData();
            if (rawData != null) {
                if (rawData.getProfileFeature().isProfilingJAXRS()) {
                    rawData.addEnd(ProfilingFeatureType.JAXRS, request.getPath());
                }
                final ProfilingDataJson profilingData = new ProfilingDataJson(rawData);

                final String value;
                try {
                    value = mapper.writeValueAsString(profilingData);
                    response.getHttpHeaders().add(PROFILING_HEADER_RESP, value);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            }
        } finally {
            Profiling.resetPerThreadProfilingData();
        }
        return response;
    }
}
