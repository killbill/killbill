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

import java.io.File;
import java.util.Properties;

import com.ning.billing.osgi.api.config.PluginRubyConfig;

public class DefaultPluginRubyConfig extends DefaultPluginConfig implements PluginRubyConfig {

    private static final String INSTALLATION_GEM_NAME = "gems";

    private static final String PROP_RUBY_MAIN_CLASS_NAME = "mainClass";

    private final String rubyMainClass;
    private final File rubyLoadDir;

    public DefaultPluginRubyConfig(final String pluginName, final String version, final File pluginVersionRoot, final Properties props) throws PluginConfigException {
        super(pluginName, version, props);
        this.rubyMainClass = props.getProperty(PROP_RUBY_MAIN_CLASS_NAME);
        this.rubyLoadDir = new File(pluginVersionRoot.getAbsolutePath() + "/" + INSTALLATION_GEM_NAME);
        validate();
    }

    @Override
    protected void validate() throws PluginConfigException {
        if (rubyMainClass == null) {
            throw new PluginConfigException("Missing property " + PROP_RUBY_MAIN_CLASS_NAME + " for plugin " + getPluginVersionnedName());
        }
        if (rubyLoadDir == null || !rubyLoadDir.exists() || !rubyLoadDir.isDirectory()) {
            throw new PluginConfigException("Missing gem installation directory " + rubyLoadDir.getAbsolutePath() + " for plugin " + getPluginVersionnedName());
        }
    }

    @Override
    public String getRubyMainClass() {
        return rubyMainClass;
    }

    @Override
    public String getRubyLoadDir() {
        return rubyLoadDir.getAbsolutePath();
    }

    @Override
    public PluginLanguage getPluginLanguage() {
        return PluginLanguage.RUBY;
    }
}
