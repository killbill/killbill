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
import java.util.Map;

import javax.inject.Inject;

import org.apache.felix.framework.Felix;
import org.apache.felix.framework.util.FelixConstants;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleException;
import org.osgi.framework.launch.Framework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.lifecycle.LifecycleHandlerType;
import com.ning.billing.lifecycle.LifecycleHandlerType.LifecycleLevel;
import com.ning.billing.osgi.api.OSGIService;
import com.ning.billing.osgi.api.config.PluginConfigServiceApi;
import com.ning.billing.osgi.pluginconf.PluginFinder;
import com.ning.billing.util.config.OSGIConfig;

import com.google.common.collect.ImmutableList;

public class DefaultOSGIService implements OSGIService {

    public static final String OSGI_SERVICE_NAME = "osgi-service";

    private static final Logger logger = LoggerFactory.getLogger(DefaultOSGIService.class);

    private final OSGIConfig osgiConfig;
    private final KillbillActivator killbillActivator;
    private final FileInstall fileInstall;

    private Framework framework;

    @Inject
    public DefaultOSGIService(final OSGIConfig osgiConfig, final PureOSGIBundleFinder osgiBundleFinder,
                              final PluginFinder pluginFinder, final PluginConfigServiceApi pluginConfigServiceApi,
                              final KillbillActivator killbillActivator) {
        this.osgiConfig = osgiConfig;
        this.killbillActivator = killbillActivator;
        this.fileInstall = new FileInstall(osgiBundleFinder, pluginFinder, pluginConfigServiceApi);
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
            fileInstall.installAndStartBundles(framework);
        } catch (BundleException e) {
            logger.error("Failed to initialize Killbill OSGIService", e);
        }
    }

    @LifecycleHandlerType(LifecycleHandlerType.LifecycleLevel.REGISTER_EVENTS)
    public void registerForExternalEvents() throws Exception {
    }

    @LifecycleHandlerType(LifecycleHandlerType.LifecycleLevel.UNREGISTER_EVENTS)
    public void unregisterForExternalEvents() {
    }

    @LifecycleHandlerType(LifecycleLevel.START_SERVICE)
    public void startFramework() {
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

        // Install default bundles in the Framework: Killbill bundle only for now
        // Note! Think twice before adding a bundle here as it will run inside the System bundle. This means the bundle
        // context that the bundle will see is the System bundle one, which will break e.g. resources lookup
        felixConfig.put(FelixConstants.SYSTEMBUNDLE_ACTIVATORS_PROP,
                        ImmutableList.<BundleActivator>of(killbillActivator));

        final Framework felix = new Felix(felixConfig);
        felix.init();
        return felix;
    }

    private void pruneOSGICache() {
        final String path = osgiConfig.getOSGIBundleRootDir();
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
                    } else if (!f.delete()) {
                        logger.warn("Unable to delete {}", f.getAbsolutePath());
                    }
                }
            }

            if (deleteParent) {
                if (!path.delete()) {
                    logger.warn("Unable to delete {}", path.getAbsolutePath());
                } else {
                    logger.info("Deleted recursively {}", path.getAbsolutePath());
                }
            }
        }
    }
}
