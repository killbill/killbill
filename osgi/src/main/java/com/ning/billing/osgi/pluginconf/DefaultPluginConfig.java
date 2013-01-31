/*
 * Copyright 2010-2013 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
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

package com.ning.billing.osgi.pluginconf;

import java.util.Properties;

import com.ning.billing.osgi.api.config.PluginConfig;

public abstract class DefaultPluginConfig implements PluginConfig {

    private static final String PROP_PLUGIN_TYPE_NAME = "pluginType";

    private final String pluginName;
    private final PluginType pluginType;
    private final String version;

    public DefaultPluginConfig(final String pluginName, final String version, final Properties props) {
        this.pluginName = pluginName;
        this.version = version;
        this.pluginType = PluginType.valueOf(props.getProperty(PROP_PLUGIN_TYPE_NAME, PluginType.__UNKNOWN__.toString()));
    }

    @Override
    public String getPluginName() {
        return pluginName;
    }

    @Override
    public PluginType getPluginType() {
        return pluginType;
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public String getPluginVersionnedName() {
        return pluginName + "-" + version;
    }

    @Override
    public abstract PluginLanguage getPluginLanguage();

    protected abstract void validate() throws PluginConfigException;

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final DefaultPluginConfig that = (DefaultPluginConfig) o;

        if (!pluginName.equals(that.pluginName)) {
            return false;
        }
        if (pluginType != that.pluginType) {
            return false;
        }
        if (!version.equals(that.version)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = pluginName.hashCode();
        result = 31 * result + pluginType.hashCode();
        result = 31 * result + version.hashCode();
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("DefaultPluginConfig");
        sb.append("{pluginName='").append(pluginName).append('\'');
        sb.append(", pluginType=").append(pluginType);
        sb.append(", version='").append(version).append('\'');
        sb.append('}');
        return sb.toString();
    }

}
