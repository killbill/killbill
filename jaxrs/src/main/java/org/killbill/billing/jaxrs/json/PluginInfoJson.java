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

package org.killbill.billing.jaxrs.json;

import java.util.Set;

import org.killbill.billing.osgi.api.PluginInfo;
import org.killbill.billing.osgi.api.PluginServiceInfo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import io.swagger.annotations.ApiModel;

@ApiModel(value="PluginInfo")
public class PluginInfoJson {

    private final String bundleSymbolicName;

    private final String pluginKey;

    private final String pluginName;

    private final String version;

    private final String state;

    private final Set<PluginServiceInfoJson> services;

    private final Boolean isSelectedForStart;

    @JsonCreator
    public PluginInfoJson(@JsonProperty("bundleSymbolicName") final String bundleSymbolicName,
                          @JsonProperty("pluginKey") final String pluginKey,
                          @JsonProperty("pluginName") final String pluginName,
                          @JsonProperty("version") final String version,
                          @JsonProperty("state") final String state,
                          @JsonProperty("isSelectedForStart") final Boolean isSelectedForStart,
                          @JsonProperty("services") final Set<PluginServiceInfoJson> services) {
        this.bundleSymbolicName = bundleSymbolicName;
        this.pluginKey = pluginKey;
        this.pluginName = pluginName;
        this.version = version;
        this.state = state;
        this.isSelectedForStart = isSelectedForStart;
        this.services = services;
    }

    public PluginInfoJson(final PluginInfo input) {
        this(input.getBundleSymbolicName(),
             input.getPluginKey(),
             input.getPluginName(),
             input.getVersion(),
             input.getPluginState().name(),
             input.isSelectedForStart(),
             ImmutableSet.copyOf(Iterables.transform(input.getServices(), new Function<PluginServiceInfo, PluginServiceInfoJson>() {
                 @Override
                 public PluginServiceInfoJson apply(final PluginServiceInfo input) {
                     return new PluginServiceInfoJson(input.getServiceTypeName(), input.getRegistrationName());
                 }
             })));
    }

    public String getBundleSymbolicName() {
        return bundleSymbolicName;
    }

    public String getPluginName() {
        return pluginName;
    }

    public String getPluginKey() {
        return pluginKey;
    }

    public String getVersion() {
        return version;
    }

    public String getState() {
        return state;
    }

    @JsonProperty("isSelectedForStart")
    public Boolean isSelectedForStart() {
        return isSelectedForStart;
    }

    public Set<PluginServiceInfoJson> getServices() {
        return services;
    }

    @ApiModel(value="PluginServiceInfo")
    public static class PluginServiceInfoJson {

        private final String serviceTypeName;
        private final String registrationName;

        @JsonCreator
        public PluginServiceInfoJson(@JsonProperty("serviceTypeName") final String serviceTypeName,
                                     @JsonProperty("registrationName") final String registrationName) {
            this.serviceTypeName = serviceTypeName;
            this.registrationName = registrationName;
        }

        public String getServiceTypeName() {
            return serviceTypeName;
        }

        public String getRegistrationName() {
            return registrationName;
        }
    }
}
