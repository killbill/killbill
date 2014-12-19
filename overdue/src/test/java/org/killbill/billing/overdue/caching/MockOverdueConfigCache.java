/*
 * Copyright 2014 Groupon, Inc
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

package org.killbill.billing.overdue.caching;

import javax.inject.Inject;

import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.overdue.api.OverdueApiException;
import org.killbill.billing.overdue.api.OverdueConfig;
import org.killbill.billing.util.cache.CacheControllerDispatcher;

public class MockOverdueConfigCache extends EhCacheOverdueConfigCache implements OverdueConfigCache
{

    private OverdueConfig overwriteDefaultOverdueConfig;

    @Inject
    public MockOverdueConfigCache(final CacheControllerDispatcher cacheControllerDispatcher) {
        super(cacheControllerDispatcher);
    }

    @Override
    public void loadDefaultOverdueConfig(final String url) throws OverdueApiException {
        super.loadDefaultOverdueConfig(url);
    }

    public void loadOverwriteDefaultOverdueConfig(final OverdueConfig overdueConfig) throws OverdueApiException {
        this.overwriteDefaultOverdueConfig = overdueConfig;
    }

    public void clearOverwriteDefaultOverdueConfig() {
        this.overwriteDefaultOverdueConfig = null;
    }

    @Override
    public OverdueConfig getOverdueConfig(final InternalTenantContext tenantContext) throws OverdueApiException {
        if (overwriteDefaultOverdueConfig != null) {
            return overwriteDefaultOverdueConfig;
        }
        return super.getOverdueConfig(tenantContext);
    }
}
