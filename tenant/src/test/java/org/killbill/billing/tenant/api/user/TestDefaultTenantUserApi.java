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

package org.killbill.billing.tenant.api.user;

import java.util.List;
import java.util.UUID;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.tenant.TenantTestSuiteWithEmbeddedDb;
import org.killbill.billing.tenant.api.DefaultTenant;
import org.killbill.billing.tenant.api.Tenant;
import org.killbill.billing.tenant.api.TenantApiException;
import org.killbill.billing.tenant.api.TenantData;
import org.killbill.billing.tenant.api.TenantKV.TenantKey;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestDefaultTenantUserApi extends TenantTestSuiteWithEmbeddedDb {


    @Test(groups = "slow")
    public void testTenant() throws Exception {
        final TenantData tenantdata = new DefaultTenant(UUID.randomUUID(), clock.getUTCNow(), clock.getUTCNow(), "er44TT-yy4r", "TTR445ee2", "dskjhfs^^54R");
        tenantUserApi.createTenant(tenantdata, callContext);

        final Tenant tenant = tenantUserApi.getTenantByApiKey(tenantdata.getApiKey());

        Assert.assertEquals(tenant.getApiKey(), tenantdata.getApiKey());
        Assert.assertEquals(tenant.getExternalKey(), tenantdata.getExternalKey());


        // The second time, the value is already in the cache so the TenantCacheLoader is not invoked
        final Tenant tenant2 = tenantUserApi.getTenantByApiKey(tenantdata.getApiKey());

        Assert.assertEquals(tenant2.getApiKey(), tenantdata.getApiKey());
        Assert.assertEquals(tenant2.getExternalKey(), tenantdata.getExternalKey());
    }

    @Test(groups = "slow")
    public void testUserKey() throws Exception {
        tenantUserApi.addTenantKeyValue("THE_KEY", "TheValue", callContext);

        List<String> value = tenantUserApi.getTenantValuesForKey("THE_KEY", callContext);
        Assert.assertEquals(value.size(), 1);
        Assert.assertEquals(value.get(0), "TheValue");

        tenantUserApi.addTenantKeyValue("THE_KEY", "TheSecondValue", callContext);
        value = tenantUserApi.getTenantValuesForKey("THE_KEY", callContext);
        Assert.assertEquals(value.size(), 2);

        value = tenantUserApi.getTenantValuesForKey("THE_KEY", callContext);
        Assert.assertEquals(value.size(), 2);

        tenantUserApi.deleteTenantKey("THE_KEY", callContext);
        value = tenantUserApi.getTenantValuesForKey("THE_KEY", callContext);
        Assert.assertEquals(value.size(), 0);
    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/297")
    public void testVerifyCacheOnAbsentValues() throws Exception {
        final String tenantKey = TenantKey.PLUGIN_CONFIG_.toString() + "MyPluginName";

        // Warm the cache with the empty value
        List<String> value = tenantUserApi.getTenantValuesForKey(tenantKey, callContext);
        Assert.assertEquals(value.size(), 0);

        // Update the DAO directly (caching is done at the API layer)
        tenantDao.addTenantKeyValue(tenantKey, "TheValue-hidden!", true, internalCallContext);

        // Verify we still hit the cache
        value = tenantUserApi.getTenantValuesForKey(tenantKey, callContext);
        Assert.assertEquals(value.size(), 0);

        // Update the cache
        tenantUserApi.addTenantKeyValue(tenantKey, "TheValue", callContext);

        // Verify the cache now has the right value
        value = tenantUserApi.getTenantValuesForKey(tenantKey, callContext);
        Assert.assertEquals(value.size(), 1);
        Assert.assertEquals(value.get(0), "TheValue");
    }

    @Test(groups = "slow")
    public void testSystemKeySingleValue() throws Exception {
        final String tenantKey = TenantKey.PLUGIN_CONFIG_.toString() + "MyPluginName";

        tenantUserApi.addTenantKeyValue(tenantKey, "TheValue", callContext);

        List<String> value = tenantUserApi.getTenantValuesForKey(tenantKey, callContext);
        Assert.assertEquals(value.size(), 1);
        Assert.assertEquals(value.get(0), "TheValue");

        // Warm cache
        value = tenantUserApi.getTenantValuesForKey(tenantKey, callContext);
        Assert.assertEquals(value.size(), 1);
        Assert.assertEquals(value.get(0), "TheValue");

        tenantUserApi.addTenantKeyValue(tenantKey, "TheSecondValue", callContext);
        value = tenantUserApi.getTenantValuesForKey(tenantKey, callContext);
        Assert.assertEquals(value.size(), 1);
        Assert.assertEquals(value.get(0), "TheSecondValue");

        // Warm cache
        value = tenantUserApi.getTenantValuesForKey(tenantKey, callContext);
        Assert.assertEquals(value.size(), 1);
        Assert.assertEquals(value.get(0), "TheSecondValue");

        tenantUserApi.deleteTenantKey(tenantKey, callContext);
        value = tenantUserApi.getTenantValuesForKey(tenantKey, callContext);
        Assert.assertEquals(value.size(), 0);
    }

    @Test(groups = "slow")
    public void testSystemKeyMultipleValue() throws Exception {
        final String tenantKey = TenantKey.CATALOG.toString();

        tenantUserApi.addTenantKeyValue(tenantKey, "TheValue", callContext);

        List<String> value = tenantUserApi.getTenantValuesForKey(tenantKey, callContext);
        Assert.assertEquals(value.size(), 1);
        Assert.assertEquals(value.get(0), "TheValue");

        tenantUserApi.addTenantKeyValue(tenantKey, "TheSecondValue", callContext);
        value = tenantUserApi.getTenantValuesForKey(tenantKey, callContext);
        Assert.assertEquals(value.size(), 2);

        tenantUserApi.deleteTenantKey(tenantKey, callContext);
        value = tenantUserApi.getTenantValuesForKey(tenantKey, callContext);
        Assert.assertEquals(value.size(), 0);
    }

    @Test(groups = "slow", description = "Test Tenant creation with External Key over limit")
    public void testCreateTenantWithExternalKeyOverLimit() throws Exception {
        final TenantData tenantdata = new DefaultTenant(UUID.randomUUID(),
                                                        clock.getUTCNow(),
                                                        clock.getUTCNow(),
                                                        "Lorem ipsum dolor sit amet, consectetuer adipiscing elit. Aenean commodo ligula eget dolor. Aenean massa. Cum sociis natoque penatibus et magnis dis parturient montes, nascetur ridiculus mus. Donec quam felis, ultricies nec, pellentesque eu, pretium quis,.",
                                                        "TTR445ee2", "dskjhfs^^54R");
        try {
            tenantUserApi.createTenant(tenantdata, callContext);
            Assert.fail();
        } catch (final TenantApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.EXTERNAL_KEY_LIMIT_EXCEEDED.getCode());
        }
    }
}
