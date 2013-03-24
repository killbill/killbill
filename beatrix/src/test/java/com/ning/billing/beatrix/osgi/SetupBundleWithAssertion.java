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
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.util.zip.GZIPInputStream;

import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.testng.Assert;

import com.ning.billing.osgi.api.config.PluginConfig.PluginLanguage;
import com.ning.billing.osgi.api.config.PluginConfig.PluginType;
import com.ning.billing.osgi.api.config.PluginJavaConfig;
import com.ning.billing.util.config.OSGIConfig;

import com.google.common.io.ByteStreams;
import com.google.common.io.Resources;

public class SetupBundleWithAssertion {

    private final String JRUBY_BUNDLE_RESOURCE = "killbill-osgi-bundles-jruby";

    private final String bundleName;
    private final OSGIConfig config;
    private final String killbillVersion;

    private final File rootInstallDir;

    public SetupBundleWithAssertion(final String bundleName, final OSGIConfig config, final String killbillVersion) {
        this.bundleName = bundleName;
        this.config = config;
        this.killbillVersion = killbillVersion;
        this.rootInstallDir = new File(config.getRootInstallationDir());
    }


    public void setupJrubyBundle() {

        try {

            installJrubyJar();

            final URL resourceUrl = Resources.getResource(bundleName);
            final File unzippedRubyPlugin = unGzip(new File(resourceUrl.getFile()), rootInstallDir);


            final StringBuilder tmp = new StringBuilder(rootInstallDir.getAbsolutePath());
            tmp.append("/plugins/")
               .append(PluginLanguage.RUBY.toString().toLowerCase());

            final File destination = new File(tmp.toString());
            if (!destination.exists()) {
                destination.mkdir();
            }

            unTar(unzippedRubyPlugin, destination);

        } catch (IOException e) {
            Assert.fail(e.getMessage());
        } catch (ArchiveException e) {
            Assert.fail(e.getMessage());
        }
    }

    public void setupJavaBundle() {

        try {
            // Retrieve PluginConfig info from classpath
            // test bundle should have been exported under Beatrix resource by the maven maven-dependency-plugin
            final PluginJavaConfig pluginConfig = extractJavaBundleTestResource();
            Assert.assertNotNull(pluginConfig);

            // Create OSGI install bundle directory
            setupDirectoryStructure(pluginConfig);

            // Copy the jar
            ByteStreams.copy(new FileInputStream(new File(pluginConfig.getBundleJarPath())), new FileOutputStream(new File(pluginConfig.getPluginVersionRoot().getAbsolutePath(), pluginConfig.getPluginVersionnedName() + ".jar")));

            // Create the osgiConfig file
            createConfigFile(pluginConfig);

        } catch (IOException e) {
            Assert.fail(e.getMessage());
        }
    }

    public void cleanBundleInstallDir() {
        if (rootInstallDir.exists()) {
            deleteDirectory(rootInstallDir, false);
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


    private void installJrubyJar() throws IOException {

        final String resourceName = JRUBY_BUNDLE_RESOURCE + "-" + killbillVersion + "-jar-with-dependencies.jar";
        final URL resourceUrl = Resources.getResource(resourceName);
        final File rubyJarInput = new File(resourceUrl.getFile());

        final File platform = new File(rootInstallDir, "platform");
        if (!platform.exists()) {
            platform.mkdir();
        }

        final File rubyJarDestination = new File(platform, "jruby.jar");
        ByteStreams.copy(new FileInputStream(rubyJarInput), new FileOutputStream(rubyJarDestination));
    }


    private PluginJavaConfig extractJavaBundleTestResource() {

        final String resourceName = bundleName + "-" + killbillVersion + "-jar-with-dependencies.jar";
        final URL resourceUrl = Resources.getResource(resourceName);
        if (resourceUrl != null) {
            final String[] parts = resourceUrl.getPath().split("/");
            final String lastPart = parts[parts.length - 1];
            if (lastPart.startsWith(bundleName)) {
                return createPluginJavaConfig(resourceUrl.getPath());
            }
        }
        return null;

    }

    private PluginJavaConfig createPluginJavaConfig(final String bundleTestResourcePath) {

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
                final StringBuilder tmp = new StringBuilder(rootInstallDir.getAbsolutePath());
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


    private static void unTar(final File inputFile, final File outputDir) throws IOException, ArchiveException {

        InputStream is = null;
        TarArchiveInputStream archiveInputStream = null;
        TarArchiveEntry entry = null;

        try {
            is = new FileInputStream(inputFile);
            archiveInputStream = (TarArchiveInputStream) new ArchiveStreamFactory().createArchiveInputStream("tar", is);
            while ((entry = (TarArchiveEntry) archiveInputStream.getNextEntry()) != null) {
                final File outputFile = new File(outputDir, entry.getName());
                if (entry.isDirectory()) {
                    if (!outputFile.exists()) {
                        if (!outputFile.mkdirs()) {
                            throw new IllegalStateException(String.format("Couldn't create directory %s.", outputFile.getAbsolutePath()));
                        }
                    }
                } else {
                    final OutputStream outputFileStream = new FileOutputStream(outputFile);
                    ByteStreams.copy(archiveInputStream, outputFileStream);
                    outputFileStream.close();
                }
            }
        } finally {
            if (archiveInputStream != null) {
                archiveInputStream.close();
            }
            if (is != null) {
                is.close();
            }
        }
    }

    private static File unGzip(final File inputFile, final File outputDir) throws IOException {

        GZIPInputStream in = null;
        FileOutputStream out = null;

        try {
            final File outputFile = new File(outputDir, inputFile.getName().substring(0, inputFile.getName().length() - 3));

            in = new GZIPInputStream(new FileInputStream(inputFile));
            out = new FileOutputStream(outputFile);

            for (int c = in.read(); c != -1; c = in.read()) {
                out.write(c);
            }
            return outputFile;

        } finally {
            if (in != null) {
                in.close();
            }
            if (out != null) {
                out.close();
            }
        }
    }
}
