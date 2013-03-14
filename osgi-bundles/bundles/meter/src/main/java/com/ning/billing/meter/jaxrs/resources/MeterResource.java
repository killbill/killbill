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

package com.ning.billing.meter.jaxrs.resources;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.servlet.ServletRequest;
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
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import com.ning.billing.meter.api.TimeAggregationMode;
import com.ning.billing.meter.api.user.MeterUserApi;
import com.ning.billing.tenant.api.Tenant;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallContextFactory;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.TenantContext;
import com.ning.billing.util.callcontext.UserType;
import com.ning.billing.util.clock.Clock;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
@Path(MeterResource.METER_PATH)
public class MeterResource {

    public static final String METER_PATH = "/1.0/kb/plugins/meter";

    private static final String HDR_CREATED_BY = "X-Killbill-CreatedBy";
    private static final String HDR_REASON = "X-Killbill-Reason";
    private static final String HDR_COMMENT = "X-Killbill-Comment";
    private static final String STRING_PATTERN = "[\\w-]+";
    private static final String QUERY_METER_WITH_CATEGORY_AGGREGATE = "withCategoryAggregate";
    private static final String QUERY_METER_TIME_AGGREGATION_MODE = "timeAggregationMode";
    private static final String QUERY_METER_TIMESTAMP = "timestamp";
    private static final String QUERY_METER_FROM = "from";
    private static final String QUERY_METER_TO = "to";
    private static final String QUERY_METER_CATEGORY = "category";
    private static final String QUERY_METER_CATEGORY_AND_METRIC = "category_and_metric";

    private final DateTimeFormatter DATE_TIME_FORMATTER = ISODateTimeFormat.dateTimeParser();

    private final MeterUserApi meterApi;
    private final Clock clock;
    private final CallContextFactory contextFactory;

    @Inject
    public MeterResource(final MeterUserApi meterApi,
                         final Clock clock,
                         final CallContextFactory factory) {
        this.meterApi = meterApi;
        this.clock = clock;
        this.contextFactory = factory;
    }

    @GET
    @Path("/{source:" + STRING_PATTERN + "}")
    @Produces(MediaType.APPLICATION_JSON)
    public StreamingOutput getUsage(@PathParam("source") final String source,
                                    // Aggregates per category
                                    @QueryParam(QUERY_METER_CATEGORY) final List<String> categories,
                                    // Format: category,metric
                                    @QueryParam(QUERY_METER_CATEGORY_AND_METRIC) final List<String> categoriesAndMetrics,
                                    @QueryParam(QUERY_METER_FROM) final String fromTimestampString,
                                    @QueryParam(QUERY_METER_TO) final String toTimestampString,
                                    @QueryParam(QUERY_METER_TIME_AGGREGATION_MODE) @DefaultValue("") final String timeAggregationModeString,
                                    @javax.ws.rs.core.Context final HttpServletRequest request) {
        final TenantContext tenantContext = createContext(request);

        final DateTime fromTimestamp;
        if (fromTimestampString != null) {
            fromTimestamp = DATE_TIME_FORMATTER.parseDateTime(fromTimestampString);
        } else {
            fromTimestamp = clock.getUTCNow().minusMonths(3);
        }
        final DateTime toTimestamp;
        if (toTimestampString != null) {
            toTimestamp = DATE_TIME_FORMATTER.parseDateTime(toTimestampString);
        } else {
            toTimestamp = clock.getUTCNow();
        }

        return new StreamingOutput() {
            @Override
            public void write(final OutputStream output) throws IOException, WebApplicationException {
                // Look at aggregates per category?
                if (categories != null && categories.size() > 0) {
                    if (Strings.isNullOrEmpty(timeAggregationModeString)) {
                        meterApi.getUsage(output, source, categories, fromTimestamp, toTimestamp, tenantContext);
                    } else {
                        final TimeAggregationMode timeAggregationMode = TimeAggregationMode.valueOf(timeAggregationModeString);
                        meterApi.getUsage(output, timeAggregationMode, source, categories, fromTimestamp, toTimestamp, tenantContext);
                    }
                } else {
                    final Map<String, Collection<String>> metricsPerCategory = retrieveMetricsPerCategory(categoriesAndMetrics);
                    if (Strings.isNullOrEmpty(timeAggregationModeString)) {
                        meterApi.getUsage(output, source, metricsPerCategory, fromTimestamp, toTimestamp, tenantContext);
                    } else {
                        final TimeAggregationMode timeAggregationMode = TimeAggregationMode.valueOf(timeAggregationModeString);
                        meterApi.getUsage(output, timeAggregationMode, source, metricsPerCategory, fromTimestamp, toTimestamp, tenantContext);
                    }
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
    @Path("/{source:" + STRING_PATTERN + "}/{categoryName:" + STRING_PATTERN + "}/{metricName:" + STRING_PATTERN + "}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response recordUsage(@PathParam("source") final String source,
                                @PathParam("categoryName") final String categoryName,
                                @PathParam("metricName") final String metricName,
                                @QueryParam(QUERY_METER_WITH_CATEGORY_AGGREGATE) @DefaultValue("false") final Boolean withAggregate,
                                @QueryParam(QUERY_METER_TIMESTAMP) final String timestampString,
                                @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                @HeaderParam(HDR_REASON) final String reason,
                                @HeaderParam(HDR_COMMENT) final String comment,
                                @javax.ws.rs.core.Context final HttpServletRequest request) {
        final CallContext callContext = createContext(createdBy, reason, comment, request);

        final DateTime timestamp;
        if (timestampString == null) {
            timestamp = clock.getUTCNow();
        } else {
            timestamp = DATE_TIME_FORMATTER.parseDateTime(timestampString);
        }

        if (withAggregate) {
            meterApi.incrementUsageAndAggregate(source, categoryName, metricName, timestamp, callContext);
        } else {
            meterApi.incrementUsage(source, categoryName, metricName, timestamp, callContext);
        }

        return Response.ok().build();
    }

    private CallContext createContext(final String createdBy, final String reason, final String comment, final ServletRequest request)
            throws IllegalArgumentException {
        try {
            Preconditions.checkNotNull(createdBy, String.format("Header %s needs to be set", HDR_CREATED_BY));
            final Tenant tenant = getTenantFromRequest(request);
            return contextFactory.createCallContext(tenant == null ? null : tenant.getId(), createdBy, CallOrigin.EXTERNAL, UserType.CUSTOMER, reason,
                                                    comment, UUID.randomUUID());
        } catch (NullPointerException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    private TenantContext createContext(final ServletRequest request) {
        final Tenant tenant = getTenantFromRequest(request);
        if (tenant == null) {
            // Multi-tenancy may not have been configured - default to "default" tenant (see InternalCallContextFactory)
            return contextFactory.createTenantContext(null);
        } else {
            return contextFactory.createTenantContext(tenant.getId());
        }
    }

    private Tenant getTenantFromRequest(final ServletRequest request) {
        final Object tenantObject = request.getAttribute("killbill_tenant");
        if (tenantObject == null) {
            return null;
        } else {
            return (Tenant) tenantObject;
        }
    }
}
