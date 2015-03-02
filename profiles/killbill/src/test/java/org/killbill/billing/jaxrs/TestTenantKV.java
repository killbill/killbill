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

package org.killbill.billing.jaxrs;

import org.killbill.billing.client.model.TenantKey;
import org.killbill.billing.tenant.api.TenantKV;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.io.Resources;

public class TestTenantKV extends TestJaxrsBase {

    @Test(groups = "slow", description = "Upload and retrieve a per plugin config")
    public void testPerTenantPluginConfig() throws Exception {

        final String pluginName = "PLUGIN_FOO";

        final String pluginPath = Resources.getResource("plugin.yml").getPath();
        final TenantKey tenantKey0 = killBillClient.registerPluginConfigurationForTenant(pluginName, pluginPath, createdBy, reason, comment);
        Assert.assertEquals(tenantKey0.getKey(), TenantKV.TenantKey.PLUGIN_CONFIG_.toString() + pluginName);

        final TenantKey tenantKey1  = killBillClient.getPluginConfigurationForTenant(pluginName);
        Assert.assertEquals(tenantKey1.getKey(), TenantKV.TenantKey.PLUGIN_CONFIG_.toString() + pluginName);
        Assert.assertEquals(tenantKey1.getValues().size(), 1);

        killBillClient.unregisterPluginConfigurationForTenant(pluginName, createdBy, reason, comment);
        final TenantKey tenantKey2  = killBillClient.getPluginConfigurationForTenant(pluginName);
        Assert.assertEquals(tenantKey2.getKey(), TenantKV.TenantKey.PLUGIN_CONFIG_.toString() + pluginName);
        Assert.assertEquals(tenantKey2.getValues().size(), 0);
    }

}
