/*
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

package org.killbill.billing.usage.api.user;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.usage.api.RolledUpUsage;
import org.killbill.billing.usage.api.UsageUserApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.TenantContext;


public class MockUsageUserApi implements UsageUserApi {

    private List<RolledUpUsage> result;

    @Override
    public void recordRolledUpUsage(final UUID uuid, final String s, final DateTime dateTime, final DateTime dateTime2, final BigDecimal bigDecimal, final CallContext callContext) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RolledUpUsage getUsageForSubscription(final UUID uuid, final String s, final DateTime dateTime, final DateTime dateTime2, final TenantContext tenantContext) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<RolledUpUsage> getAllUsageForSubscription(final UUID uuid, final Set<String> strings, final List<DateTime> dateTimes, final TenantContext tenantContext) {
        return result;
    }

    public void setAllUsageForSubscription(final List<RolledUpUsage> result) {
        this.result = result;
    }
}
