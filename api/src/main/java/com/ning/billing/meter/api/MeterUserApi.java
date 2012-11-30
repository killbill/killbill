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

package com.ning.billing.meter.api;

import java.util.Map;
import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.util.callcontext.CallContext;

public interface MeterUserApi {

    /**
     * Shortcut API to record a usage value of "1" for a given category and metric.
     *
     * @param bundleId     bundle id source
     * @param categoryName category name for this usage
     * @param metricName   metric name associated with this category
     * @param context      call context
     */
    public void incrementUsage(UUID bundleId, String categoryName, String metricName, DateTime timestamp, CallContext context);

    /**
     * Shortcut API to record a usage value of "1" for a given category and metric.
     * <p/>
     * This will also store an aggregation of all usage data across all metrics for that category.
     * This is useful if one wants to store fine grained usage information (e.g. number of minutes used per cell phone number),
     * while keeping a fast access path to the aggregate (e.g. number of minutes used across all cell phone numbers).
     *
     * @param bundleId     bundle id source
     * @param categoryName category name for this usage
     * @param metricName   metric name associated with this category
     * @param context      call context
     */
    public void incrementUsageAndAggregate(UUID bundleId, String categoryName, String metricName, DateTime timestamp, CallContext context);

    /**
     * Fine grained usage API. This is used to record e.g. "X has used 2 credits at 2012/02/04 4:12pm".
     *
     * @param bundleId                       bundle id source
     * @param samplesForCategoriesAndMetrics samples per metric and category
     * @param timestamp                      timestamp of this usage
     * @param context                        tenant context
     */
    public void incrementUsage(UUID bundleId, Map<String, Map<String, Object>> samplesForCategoriesAndMetrics, DateTime timestamp, CallContext context);
}
