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

import java.util.List;

import org.joda.time.DateTime;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;

@ApiModel(value="NodeInfo")
public class NodeInfoJson {

    private final String nodeName;
    private final DateTime bootTime;
    private final DateTime lastUpdatedDate;
    private final String kbVersion;
    private final String apiVersion;
    private final String pluginApiVersion;
    private final String commonVersion;
    private final String platformVersion;
    private final List<PluginInfoJson> pluginsInfo;

    @JsonCreator
    public NodeInfoJson(@JsonProperty("nodeName") final String nodeName,
                        @JsonProperty("bootTime") final DateTime bootTime,
                        @JsonProperty("lastUpdatedDate") final DateTime lastUpdatedDate,
                        @JsonProperty("kbVersion") final String kbVersion,
                        @JsonProperty("apiVersion") final String apiVersion,
                        @JsonProperty("pluginApiVersion") final String pluginApiVersion,
                        @JsonProperty("commonVersion") final String commonVersion,
                        @JsonProperty("platformVersion") final String platformVersion,
                        @JsonProperty("pluginsInfo") final List<PluginInfoJson> pluginsInfo) {
        this.nodeName = nodeName;
        this.bootTime = bootTime;
        this.lastUpdatedDate = lastUpdatedDate;
        this.kbVersion = kbVersion;
        this.apiVersion = apiVersion;
        this.pluginApiVersion = pluginApiVersion;
        this.commonVersion = commonVersion;
        this.platformVersion = platformVersion;
        this.pluginsInfo = pluginsInfo;
    }

    public String getNodeName() {
        return nodeName;
    }

    public DateTime getBootTime() {
        return bootTime;
    }

    public DateTime getLastUpdatedDate() {
        return lastUpdatedDate;
    }

    public String getKbVersion() {
        return kbVersion;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public String getPluginApiVersion() {
        return pluginApiVersion;
    }

    public String getCommonVersion() {
        return commonVersion;
    }

    public String getPlatformVersion() {
        return platformVersion;
    }

    public List<PluginInfoJson> getPluginsInfo() {
        return pluginsInfo;
    }
}
