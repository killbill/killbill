/*
 * Copyright 2016 Groupon, Inc
 * Copyright 2016 The Billing Project, LLC
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

package org.killbill.billing.util;

import java.util.List;
import java.util.Map;

import org.killbill.billing.payment.api.PluginProperty;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Ordering;

public class TestPluginProperties extends UtilTestSuiteNoDB {

    private final List<PluginProperty> pluginProperties1 = PluginProperties.buildPluginProperties(ImmutableMap.<String, Object>of("foo", "bar",
                                                                                                                                  "baz", 12L));
    private final List<PluginProperty> pluginProperties2 = PluginProperties.buildPluginProperties(ImmutableMap.<String, Object>of("foo", "override",
                                                                                                                                  "baz2", "something else"));

    @Test(groups = "fast")
    public void testMerge() throws Exception {
        final List<PluginProperty> pluginPropertiesRaw = ImmutableList.<PluginProperty>copyOf(PluginProperties.merge(pluginProperties1, pluginProperties2));

        final List<PluginProperty> pluginProperties = sort(pluginPropertiesRaw);

        Assert.assertEquals(pluginProperties.size(), 3);
        Assert.assertEquals(pluginProperties.get(0).getKey(), "baz");
        Assert.assertEquals(pluginProperties.get(0).getValue(), (Long) 12L);
        Assert.assertFalse(pluginProperties.get(0).getIsUpdatable());
        Assert.assertEquals(pluginProperties.get(1).getKey(), "baz2");
        Assert.assertEquals(pluginProperties.get(1).getValue(), "something else");
        Assert.assertFalse(pluginProperties.get(1).getIsUpdatable());
        Assert.assertEquals(pluginProperties.get(2).getKey(), "foo");
        Assert.assertEquals(pluginProperties.get(2).getValue(), "override");
        Assert.assertFalse(pluginProperties.get(2).getIsUpdatable());
    }

    @Test(groups = "fast")
    public void testToMap() throws Exception {
        final Map<String, Object> properties = PluginProperties.toMap(pluginProperties1, pluginProperties2);
        Assert.assertEquals(properties.get("baz"), (Long) 12L);
        Assert.assertEquals(properties.get("baz2"), "something else");
        Assert.assertEquals(properties.get("foo"), "override");
    }

    @Test(groups = "fast")
    public void testBuildPluginProperties() throws Exception {
        Assert.assertEquals(pluginProperties1.size(), 2);
        Assert.assertEquals(pluginProperties1.get(0).getKey(), "foo");
        Assert.assertEquals(pluginProperties1.get(0).getValue(), "bar");
        Assert.assertFalse(pluginProperties1.get(0).getIsUpdatable());
        Assert.assertEquals(pluginProperties1.get(1).getKey(), "baz");
        Assert.assertEquals(pluginProperties1.get(1).getValue(), (Long) 12L);
        Assert.assertFalse(pluginProperties1.get(1).getIsUpdatable());
    }

    private List<PluginProperty> sort(final Iterable<PluginProperty> pluginProperties) {
        return Ordering.natural()
                       .onResultOf(new Function<PluginProperty, String>() {
                           @Override
                           public String apply(final PluginProperty pluginProperty) {
                               return pluginProperty.getKey();
                           }
                       })
                       .immutableSortedCopy(pluginProperties);
    }
}
