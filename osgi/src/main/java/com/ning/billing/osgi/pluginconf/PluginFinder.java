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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.osgi.api.config.PluginConfig;
import com.ning.billing.osgi.api.config.PluginConfig.PluginLanguage;
import com.ning.billing.osgi.api.config.PluginJavaConfig;
import com.ning.billing.osgi.api.config.PluginRubyConfig;
import com.ning.billing.util.config.OSGIConfig;

public class PluginFinder {

    private static final String INSTALATION_PROPERTIES = "killbill.properties";

    private final Logger logger = LoggerFactory.getLogger(PluginFinder.class);

    private final OSGIConfig osgiConfig;
    private final Map<String, List<? extends PluginConfig>> allPlugins;

    @Inject
    public PluginFinder(final OSGIConfig osgiConfig) {
        this.osgiConfig = osgiConfig;
        this.allPlugins = new HashMap<String, List<? extends PluginConfig>>();
    }

    public List<PluginJavaConfig> getLatestJavaPlugins() throws PluginConfigException {
        return getLatestPluginForLanguage(PluginLanguage.JAVA);
    }

    public List<PluginRubyConfig> getLatestRubyPlugins() throws PluginConfigException {
        return getLatestPluginForLanguage(PluginLanguage.RUBY);
    }

    public <T extends PluginConfig> List<T> getVersionsForPlugin(final String lookupName) throws PluginConfigException {
        loadPluginsIfRequired();

        final List<T> result = new LinkedList<T>();
        for (final String pluginName : allPlugins.keySet()) {
            if (pluginName.equals(lookupName)) {
                for (final PluginConfig cur : allPlugins.get(pluginName)) {
                    result.add((T) cur);
                }
            }
        }
        return result;
    }

    private <T extends PluginConfig> List<T> getLatestPluginForLanguage(final PluginLanguage pluginLanguage) throws PluginConfigException {
        loadPluginsIfRequired();

        final List<T> result = new LinkedList<T>();
        for (final String pluginName : allPlugins.keySet()) {
            final T plugin = (T) allPlugins.get(pluginName).get(0);
            if (pluginLanguage != plugin.getPluginLanguage()) {
                continue;
            }
            result.add(plugin);
        }

        return result;
    }

    private void loadPluginsIfRequired() throws PluginConfigException {
        synchronized (allPlugins) {

            if (allPlugins.size() > 0) {
                return;
            }

            loadPluginsForLanguage(PluginLanguage.RUBY);
            loadPluginsForLanguage(PluginLanguage.JAVA);

            // Order for each plugin by versions starting from highest version
            for (final String pluginName : allPlugins.keySet()) {
                final List<? extends PluginConfig> value = allPlugins.get(pluginName);
                Collections.sort(value, new Comparator<PluginConfig>() {
                    @Override
                    public int compare(final PluginConfig o1, final PluginConfig o2) {
                        return -(o1.getVersion().compareTo(o2.getVersion()));
                    }
                });
            }
        }
    }

    private <T extends PluginConfig> void loadPluginsForLanguage(final PluginLanguage pluginLanguage) throws PluginConfigException {
        final String rootDirPath = osgiConfig.getRootInstallationDir() + "/" + pluginLanguage.toString().toLowerCase();
        final File rootDir = new File(rootDirPath);
        if (!rootDir.exists() || !rootDir.isDirectory()) {
            logger.warn("Configuration root dir {} is not a valid directory", rootDirPath);
            return;
        }

        final File[] files = rootDir.listFiles();
        if (files == null) {
            return;
        }
        for (final File curPlugin : files) {
            // Skip any non directory entry
            if (!curPlugin.isDirectory()) {
                logger.warn("Skipping entry {} in directory {}", curPlugin.getName(), rootDir.getAbsolutePath());
                continue;
            }
            final String pluginName = curPlugin.getName();

            final File[] filesInDir = curPlugin.listFiles();
            if (filesInDir == null) {
                continue;
            }
            for (final File curVersion : filesInDir) {
                // Skip any non directory entry
                if (!curVersion.isDirectory()) {
                    logger.warn("Skipping entry {} in directory {}", curPlugin.getName(), rootDir.getAbsolutePath());
                    continue;
                }
                final String version = curVersion.getName();

                final T plugin = extractPluginConfig(pluginLanguage, pluginName, version, curVersion);
                List<T> curPluginVersionlist = (List<T>) allPlugins.get(plugin.getPluginName());
                if (curPluginVersionlist == null) {
                    curPluginVersionlist = new LinkedList<T>();
                    allPlugins.put(plugin.getPluginName(), curPluginVersionlist);
                }
                curPluginVersionlist.add(plugin);
                logger.info("Adding plugin {} ", plugin.getPluginVersionnedName());
            }
        }
    }

    private <T extends PluginConfig> T extractPluginConfig(final PluginLanguage pluginLanguage, final String pluginName, final String pluginVersion, final File pluginVersionDir) throws PluginConfigException {
        T result;
        Properties props = null;
        try {
            final File[] files = pluginVersionDir.listFiles();
            if (files == null) {
                throw new PluginConfigException("Unable to list files in " + pluginVersionDir.getAbsolutePath());
            }

            for (final File cur : files) {
                if (cur.isFile() && cur.getName().equals(INSTALATION_PROPERTIES)) {
                    props = readPluginConfigurationFile(cur);
                }
                if (props != null) {
                    break;
                }
            }

            if (pluginLanguage == PluginLanguage.RUBY && props == null) {
                throw new PluginConfigException("Invalid plugin configuration file for " + pluginName + "-" + pluginVersion);
            }

        } catch (IOException e) {
            throw new PluginConfigException("Failed to read property file for " + pluginName + "-" + pluginVersion, e);
        }
        switch (pluginLanguage) {
            case RUBY:
                result = (T) new DefaultPluginRubyConfig(pluginName, pluginVersion, pluginVersionDir, props);
                break;
            case JAVA:
                result = (T) new DefaultPluginJavaConfig(pluginName, pluginVersion, pluginVersionDir, (props == null) ? new Properties() : props);
                break;
            default:
                throw new RuntimeException("Unknown plugin language " + pluginLanguage);
        }
        return result;
    }

    private Properties readPluginConfigurationFile(final File config) throws IOException {
        final Properties props = new Properties();
        final BufferedReader br = new BufferedReader(new FileReader(config));
        String line;
        while ((line = br.readLine()) != null) {
            final String[] parts = line.split("\\s*=\\s*");
            final String key = parts[0];
            final String value = parts[1];
            props.put(key, value);
        }
        br.close();
        return props;
    }
}
