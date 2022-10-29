/*
 * Copyright 2020-2022 Equinix, Inc
 * Copyright 2014-2022 The Billing Project, LLC
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

package org.killbill.billing.tenant.api;

import org.killbill.billing.tenant.TenantTestSuiteNoDB;
import org.killbill.billing.tenant.api.TenantInternalApi.CacheInvalidationCallback;
import org.killbill.billing.tenant.api.TenantKV.TenantKey;
import org.testng.annotations.Test;

import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

public class TestTenantCacheInvalidation extends TenantTestSuiteNoDB {

    private static final TenantKey KEY1 = TenantKey.CATALOG;
    private static final TenantKey KEY2 = TenantKey.PER_TENANT_CONFIG;
    private static final TenantKey KEY3 = TenantKey.INVOICE_TEMPLATE;

    private static final CacheInvalidationCallback CALLBACK1 = mock(CacheInvalidationCallback.class);
    private static final CacheInvalidationCallback CALLBACK2 = mock(CacheInvalidationCallback.class);
    private static final CacheInvalidationCallback CALLBACK3 = mock(CacheInvalidationCallback.class);
    private static final CacheInvalidationCallback CALLBACK4 = mock(CacheInvalidationCallback.class);


    private TenantCacheInvalidation newTenantCacheInvalidation() {
        return new TenantCacheInvalidation(null, null , null, null);
    }

    @Test(groups = "fast")
    public void registerCallback() {
        final TenantCacheInvalidation tenantCache = newTenantCacheInvalidation();
        tenantCache.registerCallback(KEY1, CALLBACK1);
        tenantCache.registerCallback(KEY1, CALLBACK2);
        tenantCache.registerCallback(KEY2, CALLBACK3);
        tenantCache.registerCallback(KEY3, CALLBACK3);
        tenantCache.registerCallback(KEY1, CALLBACK4);
        tenantCache.registerCallback(KEY3, CALLBACK4);

        assertFalse(tenantCache.getCacheInvalidations(KEY1).isEmpty());
        assertEquals(tenantCache.getCacheInvalidations(KEY1).size(), 3);
        assertEquals(tenantCache.getCacheInvalidations(KEY2).size(), 1);
        assertEquals(tenantCache.getCacheInvalidations(KEY3).size(), 2);
    }
}
