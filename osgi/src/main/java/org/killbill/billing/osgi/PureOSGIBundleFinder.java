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

package org.killbill.billing.osgi;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.killbill.billing.osgi.pluginconf.PluginConfigException;
import org.killbill.billing.util.config.OSGIConfig;

import com.google.common.collect.ImmutableList;

@Singleton
public class PureOSGIBundleFinder {

    private final Logger logger = LoggerFactory.getLogger(Logger.class);

    private final OSGIConfig osgiConfig;

    @Inject
    public PureOSGIBundleFinder(final OSGIConfig osgiConfig) {
        this.osgiConfig = osgiConfig;
    }

    public List<String> getLatestBundles() throws PluginConfigException {
        final String rootDirPath = getPlatformOSGIBundlesRootDir();
        final File rootDir = new File(rootDirPath);
        if (!rootDir.exists() || !rootDir.isDirectory()) {
            logger.warn("Configuration root dir {} is not a valid directory", rootDirPath);
            return ImmutableList.<String>of();
        }

        final File[] files = rootDir.listFiles();
        if (files == null) {
            return ImmutableList.<String>of();
        }

        final List<String> bundles = new ArrayList<String>();
        for (final File bundleJar : files) {
            if (bundleJar.isFile()) {
                bundles.add(bundleJar.getAbsolutePath());
            }
        }

        return bundles;
    }

    public String getPlatformOSGIBundlesRootDir() {
        return osgiConfig.getRootInstallationDir() + "/platform/";
    }
}
