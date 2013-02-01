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

package com.ning.billing.meter.api.user;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Map;

import javax.annotation.Nullable;

import org.joda.time.DateTime;

import com.ning.billing.meter.api.DecimationMode;
import com.ning.billing.meter.api.TimeAggregationMode;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.TenantContext;

public interface MeterUserApi {

    /**
     * Get usage data for all metrics (given a set of categories) as Json using an accumulating filter.
     * <p/>
     * Emulate: select sum(value) from chunks group by source, category, date_trunc('timeAggregationMode', timestamp);
     *
     * @param outputStream        stream to write the data to
     * @param timeAggregationMode granularity of the accumulator
     * @param source              source
     * @param categories          categories
     * @param fromTimestamp       earliest timestamp to consider (inclusive)
     * @param toTimestamp         latest timestamp to consider (inclusive)
     * @param context             call context
     * @throws IOException
     */
    void getUsage(OutputStream outputStream, TimeAggregationMode timeAggregationMode,
                  String source, Collection<String> categories,
                  DateTime fromTimestamp, DateTime toTimestamp, TenantContext context) throws IOException;

    /**
     * Get usage data as Json using an accumulating filter.
     * <p/>
     * Emulate: select sum(value) from chunks group by source, category, metric, date_trunc('timeAggregationMode', timestamp);
     *
     * @param outputStream        stream to write the data to
     * @param timeAggregationMode granularity of the accumulator
     * @param source              source
     * @param metricsPerCategory  mapping of metrics per category
     * @param fromTimestamp       earliest timestamp to consider (inclusive)
     * @param toTimestamp         latest timestamp to consider (inclusive)
     * @param context             call context
     * @throws IOException generic I/O exception
     */
    void getUsage(OutputStream outputStream, TimeAggregationMode timeAggregationMode,
                  String source, Map<String, Collection<String>> metricsPerCategory,
                  DateTime fromTimestamp, DateTime toTimestamp, TenantContext context) throws IOException;

    /**
     * Get usage data as Json using a decimating filter to reduce the number of points
     *
     * @param outputStream       stream to write the data to
     * @param decimationMode     decimation mode for the decimating filter
     * @param outputCount        number of data point the decimating filter should output, can be null
     * @param source             source
     * @param metricsPerCategory mapping of metrics per category
     * @param fromTimestamp      earliest timestamp to consider (inclusive)
     * @param toTimestamp        latest timestamp to consider (inclusive)
     * @param context            call context
     * @throws IOException generic I/O exception
     */
    void getUsage(OutputStream outputStream, DecimationMode decimationMode, @Nullable Integer outputCount,
                  String source, Map<String, Collection<String>> metricsPerCategory,
                  DateTime fromTimestamp, DateTime toTimestamp, TenantContext context) throws IOException;

    /**
     * Get usage data for all metrics (given a set of categories) as Json.
     *
     * @param outputStream  stream to write the data to
     * @param source        source
     * @param categories    categories
     * @param fromTimestamp earliest timestamp to consider (inclusive)
     * @param toTimestamp   latest timestamp to consider (inclusive)
     * @param context       call context
     * @throws IOException generic I/O exception
     */
    void getUsage(OutputStream outputStream, String source, Collection<String> categories,
                  DateTime fromTimestamp, DateTime toTimestamp, TenantContext context) throws IOException;

    /**
     * Get usage data as Json.
     *
     * @param outputStream       stream to write the data to
     * @param source             source
     * @param metricsPerCategory mapping of metrics per category
     * @param fromTimestamp      earliest timestamp to consider (inclusive)
     * @param toTimestamp        latest timestamp to consider (inclusive)
     * @param context            call context
     * @throws IOException generic I/O exception
     */
    void getUsage(OutputStream outputStream, String source, Map<String, Collection<String>> metricsPerCategory,
                  DateTime fromTimestamp, DateTime toTimestamp, TenantContext context) throws IOException;

    /**
     * Shortcut API to record a usage value of "1" for a given category and metric.
     *
     * @param source       source name for this usage
     * @param categoryName category name for this usage
     * @param metricName   metric name associated with this category
     * @param context      call context
     */
    public void incrementUsage(String source, String categoryName, String metricName, DateTime timestamp, CallContext context);

    /**
     * Shortcut API to record a usage value of "1" for a given category and metric.
     * <p/>
     * This will also store an aggregation of all usage data across all metrics for that category.
     * This is useful if one wants to store fine grained usage information (e.g. number of minutes used per cell phone number),
     * while keeping a fast access path to the aggregate (e.g. number of minutes used across all cell phone numbers).
     *
     * @param source       source name for this usage
     * @param categoryName category name for this usage
     * @param metricName   metric name associated with this category
     * @param context      call context
     */
    public void incrementUsageAndAggregate(String source, String categoryName, String metricName, DateTime timestamp, CallContext context);

    /**
     * Fine grained usage API. This is used to record e.g. "X has used 2 credits at 2012/02/04 4:12pm".
     *
     * @param source                         source name for this usage
     * @param samplesForCategoriesAndMetrics samples per metric and category
     * @param timestamp                      timestamp of this usage
     * @param context                        tenant context
     */
    public void recordUsage(String source, Map<String, Map<String, Object>> samplesForCategoriesAndMetrics, DateTime timestamp, CallContext context);
}
