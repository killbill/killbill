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

package org.killbill.billing.jaxrs.resources;

import java.util.List;

import org.killbill.billing.jaxrs.glue.TestJaxrsModuleNoDB;
import org.killbill.billing.payment.api.PluginProperty;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

public class TestJaxRsResourceBase extends TestJaxrsModuleNoDB {

    private final class JaxRsResourceBaseTest extends JaxRsResourceBase {

        public JaxRsResourceBaseTest() {
            super(null, null, null, null, null, null, null);
        }
    }

    @Test(groups = "fast")
    public void testPluginPropertyDecoding() throws Exception {
        final JaxRsResourceBase base = new JaxRsResourceBaseTest();
        final List<PluginProperty> properties = ImmutableList.<PluginProperty>copyOf(base.extractPluginProperties(ImmutableList.<String>of("name%3DAcme", "city%3DCarrot%20City", "billing_address%3Daddress%5Bpostcode%5D%3D12345")));
        Assert.assertEquals(properties.size(), 3);
        Assert.assertEquals(properties.get(0).getKey(), "name");
        Assert.assertEquals(properties.get(0).getValue(), "Acme");
        Assert.assertFalse(properties.get(0).getIsUpdatable());
        Assert.assertEquals(properties.get(1).getKey(), "city");
        Assert.assertEquals(properties.get(1).getValue(), "Carrot City");
        Assert.assertFalse(properties.get(1).getIsUpdatable());
        Assert.assertEquals(properties.get(2).getKey(), "billing_address");
        Assert.assertEquals(properties.get(2).getValue(), "address[postcode]=12345");
        Assert.assertFalse(properties.get(2).getIsUpdatable());
    }
}
