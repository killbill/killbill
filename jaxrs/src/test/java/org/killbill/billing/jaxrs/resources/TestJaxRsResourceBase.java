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

package org.killbill.billing.jaxrs.resources;

import java.util.List;

import org.killbill.billing.jaxrs.JaxrsTestSuiteNoDB;
import org.killbill.billing.payment.api.PluginProperty;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

public class TestJaxRsResourceBase extends JaxrsTestSuiteNoDB {

    private final JaxRsResourceBaseTest base = new JaxRsResourceBaseTest();

    @Test(groups = "fast")
    public void testExtractPluginProperties() throws Exception {
        final List<String> pluginPropertiesString = ImmutableList.<String>of("payment_cryptogram=EHuWW9PiBkWvqE5juRwDzAUFBAk=",
                                                                             "cc_number=4111111111111111",
                                                                             "cc_type=visa",
                                                                             "cc_expiration_month=09",
                                                                             "cc_expiration_year=2020");
        final List<PluginProperty> pluginProperties = ImmutableList.<PluginProperty>copyOf(base.extractPluginProperties(pluginPropertiesString));
        Assert.assertEquals(pluginProperties.size(), 5);
        Assert.assertEquals(pluginProperties.get(0).getKey(), "payment_cryptogram");
        Assert.assertEquals(pluginProperties.get(0).getValue(), "EHuWW9PiBkWvqE5juRwDzAUFBAk=");
        Assert.assertEquals(pluginProperties.get(1).getKey(), "cc_number");
        Assert.assertEquals(pluginProperties.get(1).getValue(), "4111111111111111");
        Assert.assertEquals(pluginProperties.get(2).getKey(), "cc_type");
        Assert.assertEquals(pluginProperties.get(2).getValue(), "visa");
        Assert.assertEquals(pluginProperties.get(3).getKey(), "cc_expiration_month");
        Assert.assertEquals(pluginProperties.get(3).getValue(), "09");
        Assert.assertEquals(pluginProperties.get(4).getKey(), "cc_expiration_year");
        Assert.assertEquals(pluginProperties.get(4).getValue(), "2020");
    }

    @Test(groups = "fast")
    public void testExtractPluginPropertiesWithNullProperty() throws Exception {
        final List<String> pluginPropertiesString = ImmutableList.<String>of("foo=",
                                                                             "bar=ttt");
        final List<PluginProperty> pluginProperties = ImmutableList.<PluginProperty>copyOf(base.extractPluginProperties(pluginPropertiesString));
        Assert.assertEquals(pluginProperties.size(), 1);
        Assert.assertEquals(pluginProperties.get(0).getKey(), "bar");
        Assert.assertEquals(pluginProperties.get(0).getValue(), "ttt");
    }

        private static final class JaxRsResourceBaseTest extends JaxRsResourceBase {

        public JaxRsResourceBaseTest() {
            super(null, null, null, null, null, null, null, null, null);
        }
    }
}
