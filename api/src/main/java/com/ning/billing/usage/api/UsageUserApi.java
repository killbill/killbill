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

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.TenantContext;

public interface UsageUserApi {

    /**
     * Bulk usage API when the external system (or the meter module) rolls-up usage data.
     * <p/>
     * This is used to record e.g. "X has used 12 minutes of his data plan between 2012/02/04 and 2012/02/06".
     *
     * @param subscriptionId subscription id source
     * @param unitType       unit type for this usage
     * @param startTime      start date of the usage period
     * @param endTime        end date of the usage period
     * @param amount         value to record
     * @param context        tenant context
     */
    public void recordRolledUpUsage(UUID subscriptionId, String unitType, DateTime startTime, DateTime endTime,
                                    BigDecimal amount, CallContext context);

    /**
     * Get usage information for a given subscription.
     *
     * @param subscriptionId subscription id
     * @param context        tenant context
     * @return usage data (rolled-up)
     */
    public RolledUpUsage getUsageForSubscription(UUID subscriptionId, TenantContext context);
}
