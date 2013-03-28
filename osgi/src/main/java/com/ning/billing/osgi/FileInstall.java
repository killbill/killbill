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

package com.ning.billing.osgi;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import javax.annotation.Nullable;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.wiring.BundleRevision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.osgi.api.config.PluginConfigServiceApi;
import com.ning.billing.osgi.api.config.PluginJavaConfig;
import com.ning.billing.osgi.api.config.PluginRubyConfig;
import com.ning.billing.osgi.pluginconf.DefaultPluginConfigServiceApi;
import com.ning.billing.osgi.pluginconf.PluginConfigException;
import com.ning.billing.osgi.pluginconf.PluginFinder;

import com.google.common.io.ByteStreams;

// TODO Pierre Should we leverage org.apache.felix.fileinstall.internal.FileInstall?
public class FileInstall {

    private static final Logger logger = LoggerFactory.getLogger(FileInstall.class);

    private final PureOSGIBundleFinder osgiBundleFinder;
    private final PluginFinder pluginFinder;
    private final PluginConfigServiceApi pluginConfigServiceApi;

    public FileInstall(final PureOSGIBundleFinder osgiBundleFinder, final PluginFinder pluginFinder, final PluginConfigServiceApi pluginConfigServiceApi) {
        this.osgiBundleFinder = osgiBundleFinder;
        this.pluginFinder = pluginFinder;
        this.pluginConfigServiceApi = pluginConfigServiceApi;
    }

    public void installAndStartBundles(final Framework framework) {
        try {
            final BundleContext context = framework.getBundleContext();

            final String jrubyBundlePath = findJrubyBundlePath();

            // Install all bundles and create service mapping
            final List<Bundle> installedBundles = new LinkedList<Bundle>();
            installAllJavaBundles(context, installedBundles, jrubyBundlePath);
            installAllJavaPluginBundles(context, installedBundles);
            installAllJRubyPluginBundles(context, installedBundles, jrubyBundlePath);

            // Start all the bundles
            for (final Bundle bundle : installedBundles) {
                startBundle(bundle);
            }
        } catch (PluginConfigException e) {
            logger.error("Error while parsing plugin configurations", e);
        } catch (BundleException e) {
            logger.error("Error while parsing plugin configurations", e);
        }
    }

    private void installAllJavaBundles(final BundleContext context, final List<Bundle> installedBundles, @Nullable final String jrubyBundlePath) throws PluginConfigException, BundleException {
        final List<String> bundleJarPaths = osgiBundleFinder.getLatestBundles();
        for (final String cur : bundleJarPaths) {
            // Don't install the jruby.jar bundle
            if (jrubyBundlePath != null && jrubyBundlePath.equals(cur)) {
                continue;
            }

            logger.info("Installing Java OSGI bundle from {}", cur);
            final Bundle bundle = context.installBundle("file:" + cur);
            installedBundles.add(bundle);
        }
    }

    private void installAllJavaPluginBundles(final BundleContext context, final List<Bundle> installedBundles) throws PluginConfigException, BundleException {
        final List<PluginJavaConfig> pluginJavaConfigs = pluginFinder.getLatestJavaPlugins();
        for (final PluginJavaConfig cur : pluginJavaConfigs) {
            logger.info("Installing Java bundle for plugin {} from {}", cur.getPluginName(), cur.getBundleJarPath());
            final Bundle bundle = context.installBundle("file:" + cur.getBundleJarPath());
            ((DefaultPluginConfigServiceApi) pluginConfigServiceApi).registerBundle(bundle.getBundleId(), cur);
            installedBundles.add(bundle);
        }
    }

    private void installAllJRubyPluginBundles(final BundleContext context, final List<Bundle> installedBundles, @Nullable final String jrubyBundlePath) throws PluginConfigException, BundleException {
        if (jrubyBundlePath == null) {
            return;
        }

        final List<PluginRubyConfig> pluginRubyConfigs = pluginFinder.getLatestRubyPlugins();
        int i = 0;
        for (final PluginRubyConfig cur : pluginRubyConfigs) {

            final String uniqueJrubyBundlePath = "jruby-" + cur.getPluginName();

            InputStream tweakedInputStream = null;
            try {
                logger.info("Installing JRuby bundle for plugin {} ", uniqueJrubyBundlePath);
                tweakedInputStream = tweakRubyManifestToBeUnique(jrubyBundlePath, ++i);
                final Bundle bundle = context.installBundle(uniqueJrubyBundlePath, tweakedInputStream);
                ((DefaultPluginConfigServiceApi) pluginConfigServiceApi).registerBundle(bundle.getBundleId(), cur);
                installedBundles.add(bundle);
            } catch (IOException e) {
                logger.warn("Failed to open file {}", jrubyBundlePath);
            } finally {
                if (tweakedInputStream != null) {
                    try {
                        tweakedInputStream.close();
                    } catch (IOException ignore) {
                    }
                }
            }
        }
    }


    private InputStream tweakRubyManifestToBeUnique(final String rubyJar, int index) throws IOException {

        final Attributes.Name attrName = new Attributes.Name(Constants.BUNDLE_SYMBOLICNAME);
        final JarInputStream in = new JarInputStream(new FileInputStream(new File(rubyJar)));
        final Manifest manifest = in.getManifest();


        final Object currentValue = manifest.getMainAttributes().get(attrName);
        manifest.getMainAttributes().put(attrName, currentValue.toString() + "-" + index);

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final JarOutputStream jarOut = new JarOutputStream(out, manifest);
        try {
            JarEntry e = in.getNextJarEntry();
            while (e != null) {
                if (!e.getName().equals(JarFile.MANIFEST_NAME)) {
                    jarOut.putNextEntry(e);
                    ByteStreams.copy(in, jarOut);
                }
                e = in.getNextJarEntry();
            }

        } finally {
            if (jarOut != null) {
                jarOut.close();
            }
        }

        return new ByteArrayInputStream(out.toByteArray());
    }

    private String findJrubyBundlePath() {
        final String expectedPath = osgiBundleFinder.getPlatformOSGIBundlesRootDir() + "jruby.jar";
        if (new File(expectedPath).isFile()) {
            return expectedPath;
        } else {
            return null;
        }
    }

    private boolean startBundle(final Bundle bundle) {
        if (bundle.getState() == Bundle.UNINSTALLED) {
            logger.info("Skipping uninstalled bundle {}", bundle.getLocation());
        } else if (isFragment(bundle)) {
            // Fragments can never be started.
            logger.info("Skipping fragment bundle {}", bundle.getLocation());
        } else {
            logger.info("Starting bundle {}", bundle.getLocation());
            try {
                bundle.start();
                return true;
            } catch (BundleException e) {
                logger.warn("Unable to start bundle", e);
            }
        }

        return false;
    }

    /**
     * Check if a bundle is a fragment.
     *
     * @param bundle bundle to check
     * @return true iff the bundle is a fragment
     */
    private boolean isFragment(final Bundle bundle) {
        // Necessary cast on jdk7
        final BundleRevision bundleRevision = (BundleRevision) bundle.adapt(BundleRevision.class);
        return bundleRevision != null && (bundleRevision.getTypes() & BundleRevision.TYPE_FRAGMENT) != 0;
    }
}
