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

import org.killbill.billing.tenant.TenantTestSuiteWithEmbeddedDb;
import org.killbill.billing.tenant.api.TenantKV.TenantKey;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestDefaultTenantUserApi extends TenantTestSuiteWithEmbeddedDb {

    @Test(groups = "slow")
    public void testUserKey() throws Exception {
        tenantUserApi.addTenantKeyValue("THE_KEY", "TheValue", callContext);

        List<String> value = tenantUserApi.getTenantValueForKey("THE_KEY", callContext);
        Assert.assertEquals(value.size(), 1);
        Assert.assertEquals(value.get(0), "TheValue");

        tenantUserApi.addTenantKeyValue("THE_KEY", "TheSecondValue", callContext);
        value = tenantUserApi.getTenantValueForKey("THE_KEY", callContext);
        Assert.assertEquals(value.size(), 2);

        value = tenantUserApi.getTenantValueForKey("THE_KEY", callContext);
        Assert.assertEquals(value.size(), 2);

        tenantUserApi.deleteTenantKey("THE_KEY", callContext);
        value = tenantUserApi.getTenantValueForKey("THE_KEY", callContext);
        Assert.assertEquals(value.size(), 0);
    }

    @Test(groups = "slow")
    public void testSystemKeySingleValue() throws Exception {

        final String tenantKey = TenantKey.PLUGIN_CONFIG_.toString() + "MyPluginName";

        tenantUserApi.addTenantKeyValue(tenantKey, "TheValue", callContext);

        List<String> value = tenantUserApi.getTenantValueForKey(tenantKey, callContext);
        Assert.assertEquals(value.size(), 1);
        Assert.assertEquals(value.get(0), "TheValue");

        // Warm cache
        value = tenantUserApi.getTenantValueForKey(tenantKey, callContext);
        Assert.assertEquals(value.size(), 1);
        Assert.assertEquals(value.get(0), "TheValue");

        tenantUserApi.addTenantKeyValue(tenantKey, "TheSecondValue", callContext);
        value = tenantUserApi.getTenantValueForKey(tenantKey, callContext);
        Assert.assertEquals(value.size(), 1);
        Assert.assertEquals(value.get(0), "TheSecondValue");

        // Warm cache
        value = tenantUserApi.getTenantValueForKey(tenantKey, callContext);
        Assert.assertEquals(value.size(), 1);
        Assert.assertEquals(value.get(0), "TheSecondValue");

        tenantUserApi.deleteTenantKey(tenantKey, callContext);
        value = tenantUserApi.getTenantValueForKey(tenantKey, callContext);
        Assert.assertEquals(value.size(), 0);
    }

    @Test(groups = "slow")
    public void testSystemKeyMultipleValue() throws Exception {

        final String tenantKey = TenantKey.CATALOG.toString();

        tenantUserApi.addTenantKeyValue(tenantKey, "TheValue", callContext);

        List<String> value = tenantUserApi.getTenantValueForKey(tenantKey, callContext);
        Assert.assertEquals(value.size(), 1);
        Assert.assertEquals(value.get(0), "TheValue");

        tenantUserApi.addTenantKeyValue(tenantKey, "TheSecondValue", callContext);
        value = tenantUserApi.getTenantValueForKey(tenantKey, callContext);
        Assert.assertEquals(value.size(), 2);

        tenantUserApi.deleteTenantKey(tenantKey, callContext);
        value = tenantUserApi.getTenantValueForKey(tenantKey, callContext);
        Assert.assertEquals(value.size(), 0);
    }

}
