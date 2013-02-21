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

package com.ning.billing.beatrix.osgi;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.nio.channels.FileChannel;

import org.testng.Assert;

import com.ning.billing.osgi.api.config.PluginConfig.PluginType;
import com.ning.billing.osgi.api.config.PluginJavaConfig;
import com.ning.billing.util.config.OSGIConfig;

import com.google.common.io.Resources;

public class SetupBundleWithAssertion {

    private final String bundleName;
    private final OSGIConfig config;
    private final String killbillVersion;

    public SetupBundleWithAssertion(final String bundleName, final OSGIConfig config, final String killbillVersion) {
        this.bundleName = bundleName;
        this.config = config;
        this.killbillVersion = killbillVersion;
    }

    public void setupBundle() {

        try {
            // Retrieve PluginConfig info from classpath
            // test bundle should have been exported under Beatrix resource by the maven maven-dependency-plugin
            final PluginJavaConfig pluginConfig = extractBundleTestResource();
            Assert.assertNotNull(pluginConfig);

            // Create OSGI install bundle directory
            setupDirectoryStructure(pluginConfig);

            // Copy the jar
            copyFile(new File(pluginConfig.getBundleJarPath()), new File(pluginConfig.getPluginVersionRoot().getAbsolutePath(), pluginConfig.getPluginVersionnedName() + ".jar"));

            // Create the osgiConfig file
            createConfigFile(pluginConfig);

        } catch (IOException e) {
            Assert.fail(e.getMessage());
        }
    }

    public void cleanBundleInstallDir() {
        final File rootDir  = new File(config.getRootInstallationDir());
        if (rootDir.exists()) {
            deleteDirectory(rootDir, false);
        }
    }

    private void createConfigFile(final PluginJavaConfig pluginConfig) throws IOException {

        PrintStream printStream = null;
        try {
            final File configFile = new File(pluginConfig.getPluginVersionRoot(), config.getOSGIKillbillPropertyName());
            configFile.createNewFile();
            printStream = new PrintStream(new FileOutputStream(configFile));
            printStream.print("pluginType=" + PluginType.NOTIFICATION);
        } finally {
            if (printStream != null) {
                printStream.close();
            }
        }
    }

    private void setupDirectoryStructure(final PluginJavaConfig pluginConfig) {
        cleanBundleInstallDir();
        pluginConfig.getPluginVersionRoot().mkdirs();
    }

    private static void deleteDirectory(final File path, final boolean deleteParent) {
        if (path == null) {
            return;
        }

        if (path.exists()) {
            final File[] files = path.listFiles();
            if (files != null) {
                for (final File f : files) {
                    if (f.isDirectory()) {
                        deleteDirectory(f, true);
                    }
                    f.delete();
                }
            }

            if (deleteParent) {
                path.delete();
            }
        }
    }



    private PluginJavaConfig extractBundleTestResource() {

        final String resourceName = bundleName + "-" + killbillVersion + "-jar-with-dependencies.jar";
        final URL resourceUrl = Resources.getResource(resourceName);
        if (resourceUrl != null) {
            final String[] parts = resourceUrl.getPath().split("/");
            final String lastPart = parts[parts.length - 1];
            if (lastPart.startsWith(bundleName)) {
                return createPluginConfig(resourceUrl.getPath(), lastPart);
            }
        }
        return null;

    }

    private PluginJavaConfig createPluginConfig(final String bundleTestResourcePath, final String fileName) {

        return new PluginJavaConfig() {
            @Override
            public String getBundleJarPath() {
                return bundleTestResourcePath;
            }

            @Override
            public String getPluginName() {
                return bundleName;
            }

            @Override
            public PluginType getPluginType() {
                return PluginType.PAYMENT;
            }

            @Override
            public String getVersion() {
                return killbillVersion;
            }

            @Override
            public String getPluginVersionnedName() {
                return bundleName + "-" + killbillVersion;
            }

            @Override
            public File getPluginVersionRoot() {
                final StringBuilder tmp = new StringBuilder(config.getRootInstallationDir());
                tmp.append("/plugins/")
                   .append(PluginLanguage.JAVA.toString().toLowerCase())
                   .append("/")
                   .append(bundleName)
                   .append("/")
                   .append(killbillVersion);
                final File result = new File(tmp.toString());
                return result;
            }

            @Override
            public PluginLanguage getPluginLanguage() {
                return PluginLanguage.JAVA;
            }
        };
    }

    public static void copyFile(File sourceFile, File destFile) throws IOException {
        if (!destFile.exists()) {
            destFile.createNewFile();
        }

        FileChannel source = null;
        FileChannel destination = null;

        try {
            source = new FileInputStream(sourceFile).getChannel();
            destination = new FileOutputStream(destFile).getChannel();
            destination.transferFrom(source, 0, source.size());
        } finally {
            if (source != null) {
                source.close();
            }
            if (destination != null) {
                destination.close();
            }
        }
    }
}
