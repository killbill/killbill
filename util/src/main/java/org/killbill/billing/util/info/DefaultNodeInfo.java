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

package org.killbill.billing.util.info;

import org.joda.time.DateTime;
import org.killbill.billing.osgi.api.PluginInfo;

public class DefaultNodeInfo implements NodeInfo {

    private final String nodeName;
    private final DateTime bootTime;
    private final DateTime updatedDate;
    private final String killbillVersion;
    private final String apiVersion;
    private final String pluginApiVersion;
    private final String commonVersion;
    private final String platformVersion;
    private final Iterable<PluginInfo> pluginInfo;

    public DefaultNodeInfo(final String nodeName,
                           final DateTime bootTime,
                           final DateTime updatedDate,
                           final String killbillVersion,
                           final String apiVersion,
                           final String pluginApiVersion,
                           final String commonVersion,
                           final String platformVersion,
                           final Iterable<PluginInfo> pluginInfo) {
        this.nodeName = nodeName;
        this.bootTime = bootTime;
        this.updatedDate = updatedDate;
        this.killbillVersion = killbillVersion;
        this.apiVersion = apiVersion;
        this.pluginApiVersion = pluginApiVersion;
        this.commonVersion = commonVersion;
        this.platformVersion = platformVersion;
        this.pluginInfo = pluginInfo;
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
        return updatedDate;
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
