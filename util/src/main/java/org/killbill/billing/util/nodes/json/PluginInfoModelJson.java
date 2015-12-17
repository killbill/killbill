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

package org.killbill.billing.util.nodes.json;

import java.util.Set;

import org.killbill.billing.osgi.api.PluginInfo;
import org.killbill.billing.osgi.api.PluginServiceInfo;
import org.killbill.billing.osgi.api.PluginState;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

public class PluginInfoModelJson {

    private final String bundleSymbolicName;

    private final String pluginKey;

    private final String pluginName;

    private final String version;

    private final PluginState state;

    private final Set<PluginServiceInfoModelJson> services;

    private final Boolean isSelectedForStart;

    @JsonCreator
    public PluginInfoModelJson(@JsonProperty("bundleSymbolicName") final String bundleSymbolicName,
                               @JsonProperty("pluginKey") final String pluginKey,
                               @JsonProperty("pluginName") final String pluginName,
                               @JsonProperty("version") final String version,
                               @JsonProperty("state") final PluginState state,
                               @JsonProperty("isSelectedForStart") final Boolean isSelectedForStart,
                               @JsonProperty("services") final Set<PluginServiceInfoModelJson> services) {
        this.bundleSymbolicName = bundleSymbolicName;
        this.pluginKey = pluginKey;
        this.pluginName = pluginName;
        this.version = version;
        this.state = state;
        this.isSelectedForStart = isSelectedForStart;
        this.services = services;
    }

    public PluginInfoModelJson(final PluginInfo input) {
        this(input.getBundleSymbolicName(),
             input.getPluginKey(),
             input.getPluginName(),
             input.getVersion(),
             input.getPluginState(),
             input.isSelectedForStart(),
             ImmutableSet.copyOf(Iterables.transform(input.getServices(), new Function<PluginServiceInfo, PluginServiceInfoModelJson>() {
                 @Override
                 public PluginServiceInfoModelJson apply(final PluginServiceInfo input) {
                     return new PluginServiceInfoModelJson(input.getServiceTypeName(), input.getRegistrationName());
                 }
             })));
    }

    public String getBundleSymbolicName() {
        return bundleSymbolicName;
    }

    public String getPluginName() {
        return pluginName;
    }

    public String getVersion() {
        return version;
    }

    public PluginState getState() {
        return state;
    }

    public String getPluginKey() {
        return pluginKey;
    }

    @JsonProperty("isSelectedForStart")
    public boolean isSelectedForStart() {
        return isSelectedForStart;
    }

    public Set<PluginServiceInfoModelJson> getServices() {
        return services;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PluginInfoModelJson)) {
            return false;
        }

        final PluginInfoModelJson that = (PluginInfoModelJson) o;

        if (bundleSymbolicName != null ? !bundleSymbolicName.equals(that.bundleSymbolicName) : that.bundleSymbolicName != null) {
            return false;
        }
        if (state != null ? !state.equals(that.state) : that.state != null) {
            return false;
        }
        if (isSelectedForStart != that.isSelectedForStart) {
            return false;
        }
        if (pluginKey != null ? !pluginKey.equals(that.pluginKey) : that.pluginKey != null) {
            return false;
        }
        if (pluginName != null ? !pluginName.equals(that.pluginName) : that.pluginName != null) {
            return false;
        }
        if (version != null ? !version.equals(that.version) : that.version != null) {
            return false;
        }
        return !(services != null ? !services.equals(that.services) : that.services != null);

    }

    @Override
    public int hashCode() {
        int result = bundleSymbolicName != null ? bundleSymbolicName.hashCode() : 0;
        result = 31 * result + (pluginKey != null ? pluginKey.hashCode() : 0);
        result = 31 * result + (pluginName != null ? pluginName.hashCode() : 0);
        result = 31 * result + (version != null ? version.hashCode() : 0);
        result = 31 * result + (state != null ? state.hashCode() : 0);
        result = 31 * result + (services != null ? services.hashCode() : 0);
        return result;
    }
}
