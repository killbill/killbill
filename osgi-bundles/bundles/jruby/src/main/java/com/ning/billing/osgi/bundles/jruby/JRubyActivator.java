/*
 * Copyright 2010-2012 Ning, Inc.
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

package com.ning.billing.osgi.bundles.jruby;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.osgi.framework.BundleContext;
import org.osgi.service.log.LogService;

import com.ning.billing.osgi.api.config.PluginConfig.PluginType;
import com.ning.billing.osgi.api.config.PluginConfigServiceApi;
import com.ning.billing.osgi.api.config.PluginRubyConfig;
import com.ning.killbill.osgi.libs.killbill.KillbillActivatorBase;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillEventDispatcher.OSGIKillbillEventHandler;

import com.google.common.base.Objects;

public class JRubyActivator extends KillbillActivatorBase {

    private static final String JRUBY_PLUGINS_CONF_DIR = System.getProperty("com.ning.billing.osgi.bundles.jruby.conf.dir");
    private static final int JRUBY_PLUGINS_RESTART_DELAY_SECS = Integer.parseInt(System.getProperty("com.ning.billing.osgi.bundles.jruby.restart.delay.secs", "5"));

    private static final String TMP_DIR_NAME = "tmp";
    private static final String RESTART_FILE_NAME = "restart.txt";

    private JRubyPlugin plugin = null;
    private ScheduledFuture<?> restartFuture = null;

    private static final String KILLBILL_PLUGIN_JPAYMENT = "Killbill::Plugin::JPayment";
    private static final String KILLBILL_PLUGIN_JNOTIFICATION = "Killbill::Plugin::JNotification";

    public void start(final BundleContext context) throws Exception {
        super.start(context);

        withContextClassLoader(new PluginCall() {
            @Override
            public void doCall() {
                logService.log(LogService.LOG_INFO, "JRuby bundle activated");

                // Retrieve the plugin config
                final PluginRubyConfig rubyConfig = retrievePluginRubyConfig(context);

                // Setup JRuby
                final String pluginMain;
                if (PluginType.NOTIFICATION.equals(rubyConfig.getPluginType())) {
                    plugin = new JRubyNotificationPlugin(rubyConfig, context, logService);
                    dispatcher.registerEventHandler((OSGIKillbillEventHandler) plugin);
                    pluginMain = KILLBILL_PLUGIN_JNOTIFICATION;
                } else if (PluginType.PAYMENT.equals(rubyConfig.getPluginType())) {
                    plugin = new JRubyPaymentPlugin(rubyConfig, context, logService);
                    pluginMain = KILLBILL_PLUGIN_JPAYMENT;
                } else {
                    throw new IllegalStateException("Unsupported plugin type " + rubyConfig.getPluginType());
                }

                // Validate and instantiate the plugin
                startPlugin(rubyConfig, pluginMain, context);
            }
        }, this.getClass().getClassLoader());
    }

    private void startPlugin(final PluginRubyConfig rubyConfig, final String pluginMain, final BundleContext context) {
        final Map<String, Object> killbillServices = retrieveKillbillApis(context);
        killbillServices.put("root", rubyConfig.getPluginVersionRoot().getAbsolutePath());
        killbillServices.put("logger", logService);
        // Default to the plugin root dir if no jruby plugins specific configuration directory was specified
        killbillServices.put("conf_dir", Objects.firstNonNull(JRUBY_PLUGINS_CONF_DIR, rubyConfig.getPluginVersionRoot().getAbsolutePath()));

        // Setup the restart mechanism. This is useful for hotswapping plugin code
        // The principle is similar to the one in Phusion Passenger:
        // http://www.modrails.com/documentation/Users%20guide%20Apache.html#_redeploying_restarting_the_ruby_on_rails_application
        final File tmpDirPath = new File(rubyConfig.getPluginVersionRoot().getAbsolutePath() + "/" + TMP_DIR_NAME);
        if (!tmpDirPath.exists()) {
            if (!tmpDirPath.mkdir()) {
                logService.log(LogService.LOG_WARNING, "Unable to create directory " + tmpDirPath + ", the restart mechanism is disabled");
                return;
            }
        }
        if (!tmpDirPath.isDirectory()) {
            logService.log(LogService.LOG_WARNING, tmpDirPath + " is not a directory, the restart mechanism is disabled");
            return;
        }

        final AtomicBoolean firstStart = new AtomicBoolean(true);
        // TODO Switch to failsafe once in killbill-commons
        restartFuture = Executors.newSingleThreadScheduledExecutor().scheduleWithFixedDelay(new Runnable() {
            long lastRestartMillis = System.currentTimeMillis();

            @Override
            public void run() {
                if (firstStart.get()) {
                    // Initial start
                    logService.log(LogService.LOG_INFO, "Starting JRuby plugin " + rubyConfig.getRubyMainClass());
                    doStartPlugin(pluginMain, context, killbillServices);
                    firstStart.set(false);
                    return;
                }

                final File restartFile = new File(tmpDirPath + "/" + RESTART_FILE_NAME);
                if (!restartFile.isFile()) {
                    return;
                }

                if (restartFile.lastModified() > lastRestartMillis) {
                    logService.log(LogService.LOG_INFO, "Restarting JRuby plugin " + rubyConfig.getRubyMainClass());

                    doStopPlugin(context);
                    doStartPlugin(pluginMain, context, killbillServices);

                    lastRestartMillis = restartFile.lastModified();
                }
            }
        }, 0, JRUBY_PLUGINS_RESTART_DELAY_SECS, TimeUnit.SECONDS);
    }

    private PluginRubyConfig retrievePluginRubyConfig(final BundleContext context) {
        final PluginConfigServiceApi pluginConfigServiceApi = killbillAPI.getPluginConfigServiceApi();
        return pluginConfigServiceApi.getPluginRubyConfig(context.getBundle().getBundleId());
    }

    public void stop(final BundleContext context) throws Exception {

        withContextClassLoader(new PluginCall() {
            @Override
            public void doCall() {
                restartFuture.cancel(true);
                doStopPlugin(context);
                killbillAPI.close();
                logService.close();
            }
        }, this.getClass().getClassLoader());
    }

    private void doStartPlugin(final String pluginMain, final BundleContext context, final Map<String, Object> killbillServices) {
        plugin.instantiatePlugin(killbillServices, pluginMain);
        plugin.startPlugin(context);
    }

    private void doStopPlugin(final BundleContext context) {
        plugin.stopPlugin(context);
        plugin.unInstantiatePlugin();
    }

    // We make the explicit registration in the start method by hand as this would be called too early
    // (see OSGIKillbillEventDispatcher)
    @Override
    public OSGIKillbillEventHandler getOSGIKillbillEventHandler() {
        return null;
    }

    private Map<String, Object> retrieveKillbillApis(final BundleContext context) {
        final Map<String, Object> killbillUserApis = new HashMap<String, Object>();

        // See killbill/plugin.rb for the naming convention magic
        killbillUserApis.put("account_user_api", killbillAPI.getAccountUserApi());
        killbillUserApis.put("analytics_sanity_api", killbillAPI.getAnalyticsSanityApi());
        killbillUserApis.put("analytics_user_api", killbillAPI.getAnalyticsUserApi());
        killbillUserApis.put("catalog_user_api", killbillAPI.getCatalogUserApi());
        killbillUserApis.put("entitlement_migration_api", killbillAPI.getEntitlementMigrationApi());
        killbillUserApis.put("entitlement_timeline_api", killbillAPI.getEntitlementTimelineApi());
        killbillUserApis.put("entitlement_transfer_api", killbillAPI.getEntitlementTransferApi());
        killbillUserApis.put("entitlement_user_api", killbillAPI.getEntitlementUserApi());
        killbillUserApis.put("invoice_migration_api", killbillAPI.getInvoiceMigrationApi());
        killbillUserApis.put("invoice_payment_api", killbillAPI.getInvoicePaymentApi());
        killbillUserApis.put("invoice_user_api", killbillAPI.getInvoiceUserApi());
        killbillUserApis.put("overdue_user_api", killbillAPI.getOverdueUserApi());
        killbillUserApis.put("payment_api", killbillAPI.getPaymentApi());
        killbillUserApis.put("tenant_user_api", killbillAPI.getTenantUserApi());
        killbillUserApis.put("usage_user_api", killbillAPI.getUsageUserApi());
        killbillUserApis.put("audit_user_api", killbillAPI.getAuditUserApi());
        killbillUserApis.put("custom_field_user_api", killbillAPI.getCustomFieldUserApi());
        killbillUserApis.put("export_user_api", killbillAPI.getExportUserApi());
        killbillUserApis.put("tag_user_api", killbillAPI.getTagUserApi());
        return killbillUserApis;
    }


    private static interface PluginCall {

        public void doCall();
    }

    // JRuby/Felix specifics, it works out of the box on Equinox.
    // Other OSGI frameworks are untested.
    private void withContextClassLoader(final PluginCall call, final ClassLoader pluginClassLoader) {
        final ClassLoader enteringContextClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(pluginClassLoader);
            call.doCall();
        } finally {
            // We want to make sure that calling thread gets back its original context class loader when it returns
            Thread.currentThread().setContextClassLoader(enteringContextClassLoader);
        }
    }
}
