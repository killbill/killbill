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

package org.killbill.billing.payment.caching;

import javax.inject.Inject;

import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.tenant.api.TenantInternalApi.CacheInvalidationCallback;
import org.killbill.billing.tenant.api.TenantKV.TenantKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Similar to TenantCacheInvalidationCallback
public class StateMachineConfigCacheInvalidationCallback implements CacheInvalidationCallback {

    private final Logger log = LoggerFactory.getLogger(StateMachineConfigCacheInvalidationCallback.class);

    private final StateMachineConfigCache stateMachineConfigCache;

    @Inject
    public StateMachineConfigCacheInvalidationCallback(final StateMachineConfigCache stateMachineConfigCache) {
        this.stateMachineConfigCache = stateMachineConfigCache;
    }

    @Override
    public void invalidateCache(final TenantKey tenantKey, final Object cookie, final InternalTenantContext tenantContext) {
        if (cookie == null) {
            return;
        }

        log.info("Invalidate payment state machine config cache for pluginName='{}', tenantRecordId='{}'", cookie, tenantContext.getTenantRecordId());
        stateMachineConfigCache.clearPaymentStateMachineConfig(cookie.toString(), tenantContext);
    }
}
