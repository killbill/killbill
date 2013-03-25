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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.jruby.embed.ScriptingContainer;
import org.osgi.framework.BundleContext;
import org.osgi.service.log.LogService;

import com.ning.billing.osgi.api.config.PluginConfig.PluginType;
import com.ning.billing.osgi.api.config.PluginConfigServiceApi;
import com.ning.billing.osgi.api.config.PluginRubyConfig;
import com.ning.killbill.osgi.libs.killbill.KillbillActivatorBase;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillEventDispatcher.OSGIKillbillEventHandler;

public class JRubyActivator extends KillbillActivatorBase {

    private JRubyPlugin plugin = null;

    public void start(final BundleContext context) throws Exception {

        super.start(context);

        withContextClassLoader(new PluginCall() {
            @Override
            public void doCall() {

                logService.log(LogService.LOG_INFO, "JRuby bundle activated");

                // Retrieve the plugin config
                final PluginRubyConfig rubyConfig = retrievePluginRubyConfig(context);

                // Setup JRuby
                final ScriptingContainer scriptingContainer = setupScriptingContainer(rubyConfig);
                if (PluginType.NOTIFICATION.equals(rubyConfig.getPluginType())) {
                    plugin = new JRubyNotificationPlugin(rubyConfig, scriptingContainer, context, logService);
                    dispatcher.registerEventHandler((OSGIKillbillEventHandler) plugin);
                } else if (PluginType.PAYMENT.equals(rubyConfig.getPluginType())) {
                    plugin = new JRubyPaymentPlugin(rubyConfig, scriptingContainer, context, logService);
                }

                // Validate and instantiate the plugin

                final Map<String, Object> killbillServices = retrieveKillbillApis(context);
                killbillServices.put("root", rubyConfig.getPluginVersionRoot().getAbsolutePath());
                killbillServices.put("logger", logService);
                plugin.instantiatePlugin(killbillServices);

                logService.log(LogService.LOG_INFO, "Starting JRuby plugin " + plugin.getPluginMainClass());
                plugin.startPlugin(context);

            }
        }, this.getClass().getClassLoader());
    }

    private PluginRubyConfig retrievePluginRubyConfig(final BundleContext context) {
        final PluginConfigServiceApi pluginConfigServiceApi = killbillAPI.getPluginConfigServiceApi();
        return pluginConfigServiceApi.getPluginRubyConfig(context.getBundle().getBundleId());
    }

    private ScriptingContainer setupScriptingContainer(final PluginRubyConfig rubyConfig) {
        final ScriptingContainer scriptingContainer = new ScriptingContainer();

        // Set the load paths instead of adding, to avoid looking at the filesystem
        scriptingContainer.setLoadPaths(Collections.<String>singletonList(rubyConfig.getRubyLoadDir()));

        return scriptingContainer;
    }

    public void stop(final BundleContext context) throws Exception {

        withContextClassLoader(new PluginCall() {
            @Override
            public void doCall() {
                plugin.stopPlugin(context);
                killbillAPI.close();
                logService.close();
            }
        }, this.getClass().getClassLoader());
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
