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

package org.killbill.billing.util.nodes;

import java.util.Set;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.killbill.billing.osgi.api.DefaultPluginsInfoApi.DefaultPluginInfo;
import org.killbill.billing.osgi.api.DefaultPluginsInfoApi.DefaultPluginServiceInfo;
import org.killbill.billing.osgi.api.PluginInfo;
import org.killbill.billing.osgi.api.PluginServiceInfo;
import org.killbill.billing.osgi.api.PluginState;
import org.killbill.billing.util.nodes.json.NodeInfoModelJson;
import org.killbill.billing.util.nodes.json.PluginInfoModelJson;
import org.killbill.billing.util.nodes.json.PluginServiceInfoModelJson;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

public class DefaultNodeInfo implements NodeInfo {

    private final String nodeName;
    private final DateTime bootTime;
    private final DateTime lastUpdatedDate;
    private final String killbillVersion;
    private final String apiVersion;
    private final String platformVersion;
    private final String commonVersion;
    private final String pluginApiVersion;
    private final Iterable<PluginInfo> pluginInfo;

    public DefaultNodeInfo(final String nodeName,
                           final DateTime bootTime,
                           final DateTime lastUpdatedDate,
                           final String killbillVersion,
                           final String apiVersion,
                           final String platformVersion,
                           final String commonVersion,
                           final String pluginApiVersion,
                           final Iterable<PluginInfo> pluginInfo) {
        this.nodeName = nodeName;
        this.bootTime = bootTime;
        this.lastUpdatedDate = lastUpdatedDate;
        this.killbillVersion = killbillVersion;
        this.apiVersion = apiVersion;
        this.platformVersion = platformVersion;
        this.commonVersion = commonVersion;
        this.pluginApiVersion = pluginApiVersion;
        this.pluginInfo = pluginInfo;
    }

    public DefaultNodeInfo(final NodeInfoModelJson in) {
        this(in.getNodeName(),
             in.getBootTime(),
             in.getBootTime(),
             in.getKillbillVersion(),
             in.getApiVersion(),
             in.getPlatformVersion(),
             in.getCommonVersion(),
             in.getPluginApiVersion(),
             toPluginInfo(in.getPluginInfo()));
    }

    private static Set<PluginServiceInfo> toPluginServiceInfo(final Set<PluginServiceInfoModelJson> services) {
        return ImmutableSet.<PluginServiceInfo>copyOf(Iterables.transform(services, new Function<PluginServiceInfoModelJson, PluginServiceInfo>() {

            @Nullable
            @Override
            public PluginServiceInfo apply(final PluginServiceInfoModelJson input) {
                return new DefaultPluginServiceInfo(input.getServiceTypeName(), input.getRegistrationName());
            }
        }));
    }

    private static Iterable<PluginInfo> toPluginInfo(final Iterable<PluginInfoModelJson> plugins) {
        return Iterables.transform(plugins, new Function<PluginInfoModelJson, PluginInfo>() {
            @Override
            public PluginInfo apply(final PluginInfoModelJson input) {
                return new DefaultPluginInfo(input.getPluginKey(), input.getBundleSymbolicName(), input.getPluginName(), input.getVersion(), input.getState(), input.isSelectedForStart(), toPluginServiceInfo(input.getServices()));
            }
        });
    }

    @Override
    public String getNodeName() {
        return nodeName;
    }

    @Override
    public DateTime getBootTime() {
        return bootTime;
    }

    @Override
    public DateTime getLastUpdatedDate() {
        return lastUpdatedDate;
    }

    @Override
    public String getKillbillVersion() {
        return killbillVersion;
    }

    @Override
    public String getApiVersion() {
        return apiVersion;
    }

    @Override
    public String getPlatformVersion() {
        return platformVersion;
    }

    @Override
    public String getCommonVersion() {
        return commonVersion;
    }

    @Override
    public String getPluginApiVersion() {
        return pluginApiVersion;
    }

    @Override
    public Iterable<PluginInfo> getPluginInfo() {
        return pluginInfo;
    }
}
