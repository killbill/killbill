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

import com.ning.billing.osgi.api.config.PluginJavaConfig;

public class DefaultPluginJavaConfig extends DefaultPluginConfig implements PluginJavaConfig {

    private final String bundleJarPath;

    public DefaultPluginJavaConfig(final String pluginName, final String version, final File pluginVersionRoot, final Properties props) throws PluginConfigException {
        super(pluginName, version, props);
        this.bundleJarPath = extractJarPath(pluginVersionRoot);
        validate();
    }

    private String extractJarPath(final File pluginVersionRoot) {
        final File[] files = pluginVersionRoot.listFiles();
        if (files == null) {
            return null;
        }

        for (final File f : files) {
            if (f.isFile() && f.getName().endsWith(".jar")) {
                return f.getAbsolutePath();
            }
        }
        return null;
    }

    @Override
    public String getBundleJarPath() {
        return bundleJarPath;
    }

    @Override
    public PluginLanguage getPluginLanguage() {
        return PluginLanguage.JAVA;
    }

    @Override
    protected void validate() throws PluginConfigException {
        if (bundleJarPath == null) {
            throw new PluginConfigException("Invalid plugin " + getPluginVersionnedName() + ": cannot find jar file");
        }
    }
}
