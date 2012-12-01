/*
 * Copyright 2010-2012 Ning, Inc.
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

package com.ning.billing.jaxrs.resources;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import org.joda.time.DateTime;

import com.ning.billing.jaxrs.util.Context;
import com.ning.billing.jaxrs.util.JaxrsUriBuilder;
import com.ning.billing.meter.api.MeterUserApi;
import com.ning.billing.util.api.AuditUserApi;
import com.ning.billing.util.api.CustomFieldUserApi;
import com.ning.billing.util.api.TagUserApi;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.TenantContext;
import com.ning.billing.util.clock.Clock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Singleton
@Path(JaxrsResource.METER_PATH)
public class MeterResource extends JaxRsResourceBase {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final MeterUserApi meterApi;
    private final Clock clock;

    @Inject
    public MeterResource(final MeterUserApi meterApi,
                         final Clock clock,
                         final JaxrsUriBuilder uriBuilder,
                         final TagUserApi tagUserApi,
                         final CustomFieldUserApi customFieldUserApi,
                         final AuditUserApi auditUserApi,
                         final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, context);
        this.meterApi = meterApi;
        this.clock = clock;
    }

    @GET
    @Path("/{bundleId:" + UUID_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    public StreamingOutput getUsage(@PathParam("bundleId") final String bundleIdString,
                                    @QueryParam(QUERY_METER_CATEGORY) final List<String> categories,
                                    // Format: category,metric
                                    @QueryParam(QUERY_METER_CATEGORY_AND_METRIC) final List<String> categoriesAndMetrics,
                                    @QueryParam(QUERY_METER_FROM) final String fromTimestampString,
                                    @QueryParam(QUERY_METER_TO) final String toTimestampString,
                                    @QueryParam(QUERY_METER_WITH_AGGREGATE) @DefaultValue("false") final Boolean withAggregate,
                                    @javax.ws.rs.core.Context final HttpServletRequest request) {
        final UUID bundleId = UUID.fromString(bundleIdString);
        final DateTime fromTimestamp = DATE_TIME_FORMATTER.parseDateTime(fromTimestampString);
        final DateTime toTimestamp = DATE_TIME_FORMATTER.parseDateTime(toTimestampString);
        final TenantContext tenantContext = context.createContext(request);

        return new StreamingOutput() {
            @Override
            public void write(final OutputStream output) throws IOException, WebApplicationException {
                if (withAggregate) {
                    meterApi.getAggregateUsage(output, bundleId, categories, fromTimestamp, toTimestamp, tenantContext);
                } else {
                    final Map<String, Collection<String>> metricsPerCategory = retrieveMetricsPerCategory(categoriesAndMetrics);
                    meterApi.getUsage(output, bundleId, metricsPerCategory, fromTimestamp, toTimestamp, tenantContext);
                }
            }
        };
    }

    private Map<String, Collection<String>> retrieveMetricsPerCategory(final List<String> categoriesAndMetrics) {
        final Map<String, Collection<String>> metricsPerCategory = new HashMap<String, Collection<String>>();
        for (final String categoryAndSampleKind : categoriesAndMetrics) {
            final String[] categoryAndMetric = getCategoryAndMetricFromQueryParameter(categoryAndSampleKind);
            if (metricsPerCategory.get(categoryAndMetric[0]) == null) {
                metricsPerCategory.put(categoryAndMetric[0], new ArrayList<String>());
            }

            metricsPerCategory.get(categoryAndMetric[0]).add(categoryAndMetric[1]);
        }

        return metricsPerCategory;
    }

    private String[] getCategoryAndMetricFromQueryParameter(final String categoryAndMetric) {
        final String[] parts = categoryAndMetric.split(",");
        if (parts.length != 2) {
            throw new WebApplicationException(Response.Status.BAD_REQUEST);
        }
        return parts;
    }

    @POST
    @Path("/{bundleId:" + UUID_PATTERN + "}/{categoryName:" + STRING_PATTERN + "}/{metricName:" + STRING_PATTERN + "}")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response recordUsage(@PathParam("bundleId") final String bundleIdString,
                                @PathParam("categoryName") final String categoryName,
                                @PathParam("metricName") final String metricName,
                                @QueryParam(QUERY_METER_WITH_AGGREGATE) @DefaultValue("false") final Boolean withAggregate,
                                @QueryParam(QUERY_METER_TIMESTAMP) final String timestampString,
                                @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                @HeaderParam(HDR_REASON) final String reason,
                                @HeaderParam(HDR_COMMENT) final String comment,
                                @javax.ws.rs.core.Context final HttpServletRequest request) {
        final UUID bundleId = UUID.fromString(bundleIdString);
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);

        final DateTime timestamp;
        if (timestampString == null) {
            timestamp = clock.getUTCNow();
        } else {
            timestamp = DATE_TIME_FORMATTER.parseDateTime(timestampString);
        }

        if (withAggregate) {
            meterApi.incrementUsageAndAggregate(bundleId, categoryName, metricName, timestamp, callContext);
        } else {
            meterApi.incrementUsage(bundleId, categoryName, metricName, timestamp, callContext);
        }

        return Response.ok().build();
    }
}
