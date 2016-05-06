/*
 * Copyright 2015 Groupon, Inc
 * Copyright 2015 The Billing Project, LLC
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

package org.killbill.billing.overdue.caching;

import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.tenant.api.TenantInternalApi.CacheInvalidationCallback;
import org.killbill.billing.tenant.api.TenantKV.TenantKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;

public class OverdueCacheInvalidationCallback implements CacheInvalidationCallback {

    private static final Logger log = LoggerFactory.getLogger(OverdueCacheInvalidationCallback.class);

    private final OverdueConfigCache overdueConfigCache;

    @Inject
    public OverdueCacheInvalidationCallback(final OverdueConfigCache overdueConfigCache) {
        this.overdueConfigCache = overdueConfigCache;
    }

    @Override
    public void invalidateCache(TenantKey key, final Object cookie, final InternalTenantContext tenantContext) {
        log.info("Invalidate overdue cache for tenantRecordId='{}'", tenantContext.getTenantRecordId());
        overdueConfigCache.clearOverdueConfig(tenantContext);
    }
}
