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

package org.killbill.billing.entitlement.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.killbill.billing.entitlement.EntitlementTestSuiteNoDB;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.util.jackson.ObjectMapper;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestDefaultEntitlementApiFast extends EntitlementTestSuiteNoDB {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test(groups = "fast", description = "Test bundle transfer property deserialization")
    public void testBundleTransferRegex() throws Exception {
        final List<PluginProperty> props = new ArrayList<PluginProperty>();
        props.add(new PluginProperty("foo", "bar", false));
        props.add(new PluginProperty("177be083-e56f-4505-87ba-e5d306c5e8bf", "bar", false));
        props.add(new PluginProperty("KB_SUB_ID_fca6f8b9-eee3-433e-a5ef-5653afce4594", "value1", false));
        props.add(new PluginProperty("KB_SUB_ID_6b00a69b-27e7-4675-bca2-8ad1cf5e533e", "value2", false));

        final Map<UUID, String> res = DefaultEntitlementApi.toSubExtKeysMap(props);
        Assert.assertEquals(res.size(), 2);
        Assert.assertEquals(res.get(UUID.fromString("fca6f8b9-eee3-433e-a5ef-5653afce4594")), "value1");
        Assert.assertEquals(res.get(UUID.fromString("6b00a69b-27e7-4675-bca2-8ad1cf5e533e")), "value2");
    }

}
