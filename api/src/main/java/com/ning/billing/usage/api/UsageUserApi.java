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

package com.ning.billing.usage.api;

import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.util.callcontext.CallContext;

public interface UsageUserApi {

    /**
     * Shortcut API to record a usage value of "1" for a given metric.
     *
     * @param bundleId   bundle id source
     * @param metricName metric name for this usage
     * @param context    call context
     */
    public void incrementUsage(UUID bundleId, String metricName, CallContext context) throws EntitlementUserApiException;

    /**
     * Fine grained usage API if the external system doesn't roll its usage data. This is used to record e.g. "X has used
     * 2 credits from his plan at 2012/02/04 4:12pm".
     *
     * @param bundleId   bundle id source
     * @param metricName metric name for this usage
     * @param timestamp  timestamp of this usage
     * @param value      value to record
     * @param context    tenant context
     */
    public void recordUsage(UUID bundleId, String metricName, DateTime timestamp, long value, CallContext context) throws EntitlementUserApiException;

    /**
     * Bulk usage API if the external system rolls-up usage data. This is used to record e.g. "X has used 12 minutes
     * of his data plan between 2012/02/04 and 2012/02/06".
     *
     * @param bundleId   bundle id source
     * @param metricName metric name for this usage
     * @param startDate  start date of the usage period
     * @param endDate    end date of the usage period
     * @param value      value to record
     * @param context    tenant context
     */
    public void recordRolledUpUsage(UUID bundleId, String metricName, DateTime startDate, DateTime endDate, long value, CallContext context) throws EntitlementUserApiException;
}
