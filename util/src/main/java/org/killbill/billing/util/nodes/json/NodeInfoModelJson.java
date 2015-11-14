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

import java.util.List;

import org.joda.time.DateTime;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class NodeInfoModelJson {

    private String nodeName;
    private DateTime bootTime;
    private DateTime updatedDate;
    private String killbillVersion;
    private String apiVersion;
    private String pluginApiVersion;
    private String commonVersion;
    private String platformVersion;
    private List<PluginInfoModelJson> pluginInfo;

    @JsonCreator
    public NodeInfoModelJson(@JsonProperty("nodeName") final String nodeName,
                             @JsonProperty("bootTime") final DateTime bootTime,
                             @JsonProperty("updatedDate") final DateTime updatedDate,
                             @JsonProperty("killbillVersion") final String killbillVersion,
                             @JsonProperty("apiVersion") final String apiVersion,
                             @JsonProperty("pluginApiVersion") final String pluginApiVersion,
                             @JsonProperty("commonVersion") final String commonVersion,
                             @JsonProperty("platformVersion") final String platformVersion,
                             @JsonProperty("pluginInfo") final List<PluginInfoModelJson> pluginInfo) {
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

    public String getNodeName() {
        return nodeName;
    }

    public DateTime getBootTime() {
        return bootTime;
    }

    public String getKillbillVersion() {
        return killbillVersion;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public String getPlatformVersion() {
        return platformVersion;
    }

    public String getCommonVersion() {
        return commonVersion;
    }

    public String getPluginApiVersion() {
        return pluginApiVersion;
    }

    public Iterable<PluginInfoModelJson> getPluginInfo() {
        return pluginInfo;
    }

    public DateTime getUpdatedDate() {
        return updatedDate;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NodeInfoModelJson)) {
            return false;
        }

        final NodeInfoModelJson that = (NodeInfoModelJson) o;

        if (nodeName != null ? !nodeName.equals(that.nodeName) : that.nodeName != null) {
            return false;
        }
        if (bootTime != null ? bootTime.compareTo(that.bootTime) != 0 : that.bootTime != null) {
            return false;
        }
        if (updatedDate != null ? updatedDate.compareTo(that.updatedDate) != 0 : that.updatedDate != null) {
            return false;
        }
        if (killbillVersion != null ? !killbillVersion.equals(that.killbillVersion) : that.killbillVersion != null) {
            return false;
        }
        if (apiVersion != null ? !apiVersion.equals(that.apiVersion) : that.apiVersion != null) {
            return false;
        }
        if (pluginApiVersion != null ? !pluginApiVersion.equals(that.pluginApiVersion) : that.pluginApiVersion != null) {
            return false;
        }
        if (commonVersion != null ? !commonVersion.equals(that.commonVersion) : that.commonVersion != null) {
            return false;
        }
        if (platformVersion != null ? !platformVersion.equals(that.platformVersion) : that.platformVersion != null) {
            return false;
        }
        return !(pluginInfo != null ? !pluginInfo.equals(that.pluginInfo) : that.pluginInfo != null);

    }

    @Override
    public int hashCode() {
        int result = nodeName != null ? nodeName.hashCode() : 0;
        result = 31 * result + (bootTime != null ? bootTime.hashCode() : 0);
        result = 31 * result + (updatedDate != null ? updatedDate.hashCode() : 0);
        result = 31 * result + (killbillVersion != null ? killbillVersion.hashCode() : 0);
        result = 31 * result + (apiVersion != null ? apiVersion.hashCode() : 0);
        result = 31 * result + (pluginApiVersion != null ? pluginApiVersion.hashCode() : 0);
        result = 31 * result + (commonVersion != null ? commonVersion.hashCode() : 0);
        result = 31 * result + (platformVersion != null ? platformVersion.hashCode() : 0);
        result = 31 * result + (pluginInfo != null ? pluginInfo.hashCode() : 0);
        return result;
    }
}
