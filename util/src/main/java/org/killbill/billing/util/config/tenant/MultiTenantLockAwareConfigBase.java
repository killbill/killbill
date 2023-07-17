/*
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

package org.killbill.billing.util.config.tenant;

import java.util.List;

import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.util.config.definition.LockAwareConfig;
import org.skife.config.TimeSpan;

public abstract class MultiTenantLockAwareConfigBase extends MultiTenantConfigBase implements LockAwareConfig {


    public MultiTenantLockAwareConfigBase(final LockAwareConfig staticConfig, final CacheConfig cacheConfig) {
        super(staticConfig, cacheConfig);
    }


    @Override
    public List<TimeSpan> getRescheduleIntervalOnLock() {
        return ((LockAwareConfig) staticConfig).getRescheduleIntervalOnLock();
    }

    @Override
    public List<TimeSpan> getRescheduleIntervalOnLock(final InternalTenantContext tenantContext) {

        final String result = getStringTenantConfig("getRescheduleIntervalOnLock", tenantContext);
        if (result != null) {
            return convertToListTimeSpan(result, "getRescheduleIntervalOnLock");
        }
        return getRescheduleIntervalOnLock();
    }

}
