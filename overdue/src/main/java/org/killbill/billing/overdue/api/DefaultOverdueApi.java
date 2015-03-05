/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

package org.killbill.billing.overdue.api;

import javax.inject.Inject;

import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.overdue.caching.OverdueConfigCache;
import org.killbill.billing.tenant.api.TenantApiException;
import org.killbill.billing.tenant.api.TenantKV.TenantKey;
import org.killbill.billing.tenant.api.TenantUserApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;

public class DefaultOverdueApi implements OverdueApi {

    private final OverdueConfigCache overdueConfigCache;
    private final InternalCallContextFactory internalCallContextFactory;
    private final TenantUserApi tenantApi;

    @Inject
    public DefaultOverdueApi(final OverdueConfigCache overdueConfigCache,
                             final TenantUserApi tenantApi,
                             final InternalCallContextFactory internalCallContextFactory) {
        this.overdueConfigCache = overdueConfigCache;
        this.tenantApi = tenantApi;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Override
    public OverdueConfig getOverdueConfig(final TenantContext tenantContext) throws OverdueApiException {
        final InternalTenantContext internalTenantContext = createInternalTenantContext(tenantContext);
        return overdueConfigCache.getOverdueConfig(internalTenantContext);
    }

    @Override
    public void uploadOverdueConfig(final String overdueXML, final CallContext callContext) throws OverdueApiException {
        try {
            final InternalTenantContext internalTenantContext = createInternalTenantContext(callContext);
            final String tenantKey = TenantKey.OVERDUE_CONFIG.toString();
            if (!tenantApi.getTenantValuesForKey(tenantKey, callContext).isEmpty()) {
                tenantApi.deleteTenantKey(tenantKey, callContext);
            }
            tenantApi.addTenantKeyValue(tenantKey, overdueXML, callContext);
            overdueConfigCache.clearOverdueConfig(internalTenantContext);
        } catch (final TenantApiException e) {
            throw new OverdueApiException(e);
        }
    }

    private InternalTenantContext createInternalTenantContext(final TenantContext tenantContext) {
        // Only tenantRecordId will be populated-- this is important to always create the (ehcache) key the same way
        return internalCallContextFactory.createInternalTenantContext(tenantContext);
    }
}
