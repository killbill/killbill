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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.killbill.billing.payment.api.PluginProperty;

import com.google.common.collect.ImmutableList;

public abstract class PluginProperties {

    // Last one has precedence
    public static Iterable<PluginProperty> merge(final Iterable<PluginProperty>... propertiesLists) {
        return buildPluginProperties(toMap(propertiesLists));
    }

    // Last one has precedence
    public static Map<String, Object> toMap(final Iterable<PluginProperty>... propertiesLists) {
        final Map<String, Object> mergedProperties = new HashMap<String, Object>();
        for (final Iterable<PluginProperty> propertiesList : propertiesLists) {
            for (final PluginProperty pluginProperty : propertiesList) {
                if (pluginProperty.getKey() != null) {
                    mergedProperties.put(pluginProperty.getKey(), pluginProperty.getValue());
                }
            }
        }
        return mergedProperties;
    }

    public static List<PluginProperty> buildPluginProperties(@Nullable final Map<String, Object> data) {
        final ImmutableList.Builder<PluginProperty> propertiesBuilder = ImmutableList.<PluginProperty>builder();
        if (data != null) {
            for (final String key : data.keySet()) {
                final PluginProperty property = new PluginProperty(key, data.get(key), false);
                propertiesBuilder.add(property);
            }
        }
        return propertiesBuilder.build();
    }
}
