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

import java.io.File;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.apache.felix.framework.Felix;
import org.apache.felix.framework.util.FelixConstants;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.launch.Framework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.osgi.logservice.impl.Activator;

import com.ning.billing.lifecycle.LifecycleHandlerType;
import com.ning.billing.lifecycle.LifecycleHandlerType.LifecycleLevel;
import com.ning.billing.osgi.api.OSGIService;
import com.ning.billing.osgi.api.config.PluginConfigServiceApi;
import com.ning.billing.osgi.api.config.PluginJavaConfig;
import com.ning.billing.osgi.api.config.PluginRubyConfig;
import com.ning.billing.osgi.pluginconf.DefaultPluginConfigServiceApi;
import com.ning.billing.osgi.pluginconf.PluginConfigException;
import com.ning.billing.osgi.pluginconf.PluginFinder;
import com.ning.billing.util.config.OSGIConfig;

import com.google.common.collect.ImmutableList;

public class DefaultOSGIService implements OSGIService {

    public static final String OSGI_SERVICE_NAME = "osgi-service";

    private static final Logger logger = LoggerFactory.getLogger(DefaultOSGIService.class);

    private Framework framework;

    private final OSGIConfig osgiConfig;
    private final PluginFinder pluginFinder;
    private final PluginConfigServiceApi pluginConfigServiceApi;
    private final KillbillActivator killbillActivator;

    @Inject
    public DefaultOSGIService(final OSGIConfig osgiConfig, final PluginFinder pluginFinder,
                              final PluginConfigServiceApi pluginConfigServiceApi, final KillbillActivator killbillActivator) {
        this.osgiConfig = osgiConfig;
        this.pluginFinder = pluginFinder;
        this.pluginConfigServiceApi = pluginConfigServiceApi;
        this.killbillActivator = killbillActivator;
        this.framework = null;
    }

    @Override
    public String getName() {
        return OSGI_SERVICE_NAME;
    }

    @LifecycleHandlerType(LifecycleLevel.INIT_SERVICE)
    public void initialize() {
        try {
            // We start by deleting existing osi cache; we might optimize later keeping the cache
            pruneOSGICache();

            // Create the system bundle for killbill and start the framework
            this.framework = createAndInitFramework();
            framework.start();

            // This will call the start() method for the bundles
            installAndStartBundles(framework);
        } catch (BundleException e) {
            logger.error("Failed to initialize Killbill OSGIService", e);
        }
    }

    @LifecycleHandlerType(LifecycleLevel.START_SERVICE)
    public void startFramework() {
    }

    @LifecycleHandlerType(LifecycleHandlerType.LifecycleLevel.REGISTER_EVENTS)
    public void registerForExternalEvents() {
    }

    @LifecycleHandlerType(LifecycleHandlerType.LifecycleLevel.UNREGISTER_EVENTS)
    public void unregisterForExternalEvents() {
    }

    @LifecycleHandlerType(LifecycleLevel.STOP_SERVICE)
    public void stop() {
        try {
            framework.stop();
            framework.waitForStop(0);
        } catch (BundleException e) {
            logger.error("Failed to Stop Killbill OSGIService " + e.getMessage());
        } catch (InterruptedException e) {
            logger.error("Failed to Stop Killbill OSGIService " + e.getMessage());
        }
    }

    private void installAndStartBundles(final Framework framework) throws BundleException {
        try {
            final BundleContext context = framework.getBundleContext();

            // Install all bundles and create service mapping
            final List<Bundle> installedBundles = new LinkedList<Bundle>();
            installAllJavaBundles(context, installedBundles);
            installAllJRubyBundles(context, installedBundles);

            // Start all the bundles
            for (final Bundle bundle : installedBundles) {
                logger.info("Starting bundle {}", bundle.getLocation());
                bundle.start();
            }
        } catch (PluginConfigException e) {
            logger.error("Error while parsing plugin configurations", e);
        }
    }

    private void installAllJavaBundles(final BundleContext context, final List<Bundle> installedBundles) throws PluginConfigException, BundleException {
        final List<PluginJavaConfig> pluginJavaConfigs = pluginFinder.getLatestJavaPlugins();
        for (final PluginJavaConfig cur : pluginJavaConfigs) {
            logger.info("Installing Java bundle for plugin {} in {}", cur.getPluginName(), cur.getBundleJarPath());
            final Bundle bundle = context.installBundle("file:" + cur.getBundleJarPath());
            ((DefaultPluginConfigServiceApi) pluginConfigServiceApi).registerBundle(bundle.getBundleId(), cur);
            installedBundles.add(bundle);
        }
    }

    private void installAllJRubyBundles(final BundleContext context, final List<Bundle> installedBundles) throws PluginConfigException, BundleException {
        final List<PluginRubyConfig> pluginRubyConfigs = pluginFinder.getLatestRubyPlugins();
        for (final PluginRubyConfig cur : pluginRubyConfigs) {
            logger.info("Installing JRuby bundle for plugin {} in {}", cur.getPluginName(), cur.getRubyLoadDir());
            final Bundle bundle = context.installBundle(osgiConfig.getJrubyBundlePath());
            ((DefaultPluginConfigServiceApi) pluginConfigServiceApi).registerBundle(bundle.getBundleId(), cur);
            installedBundles.add(bundle);
        }
    }

    private Framework createAndInitFramework() throws BundleException {
        final Map<String, String> config = new HashMap<String, String>();
        config.put("org.osgi.framework.system.packages.extra", osgiConfig.getSystemBundleExportPackages());
        config.put("felix.cache.rootdir", osgiConfig.getOSGIBundleRootDir());
        config.put("org.osgi.framework.storage", osgiConfig.getOSGIBundleCacheName());
        return createAndInitFelixFrameworkWithSystemBundle(config);
    }

    private Framework createAndInitFelixFrameworkWithSystemBundle(final Map<String, String> config) throws BundleException {
        // From standard properties add Felix specific property to add a System bundle activator
        final Map<Object, Object> felixConfig = new HashMap<Object, Object>();
        felixConfig.putAll(config);

        // Install default bundles: killbill and slf4j ones
        felixConfig.put(FelixConstants.SYSTEMBUNDLE_ACTIVATORS_PROP, ImmutableList.<BundleActivator>of(killbillActivator, new Activator()));

        final Framework felix = new Felix(felixConfig);
        felix.init();
        return felix;
    }

    private void pruneOSGICache() {
        final String path = osgiConfig.getOSGIBundleRootDir() + "/" + osgiConfig.getOSGIBundleCacheName();
        deleteUnderDirectory(new File(path));
    }

    private static void deleteUnderDirectory(final File path) {
        deleteDirectory(path, false);
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
                    if (!f.delete()) {
                        logger.warn("Unable to delete {}", f.getAbsolutePath());
                    }
                }
            }

            if (deleteParent) {
                if (!path.delete()) {
                    logger.warn("Unable to delete {}", path.getAbsolutePath());
                }
            }
        }
    }
}
