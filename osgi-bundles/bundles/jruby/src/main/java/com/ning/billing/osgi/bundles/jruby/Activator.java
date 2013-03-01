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
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.service.log.LogService;

import com.ning.billing.osgi.api.config.PluginConfig.PluginType;
import com.ning.billing.osgi.api.config.PluginConfigServiceApi;
import com.ning.billing.osgi.api.config.PluginRubyConfig;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillAPI;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillLogService;

public class Activator implements BundleActivator {

    private OSGIKillbillAPI kb;
    private OSGIKillbillLogService logService;

    private JRubyPlugin plugin = null;

    public void start(final BundleContext context) throws Exception {

        kb = new OSGIKillbillAPI(context);
        logService = new OSGIKillbillLogService(context);

        logService.log(LogService.LOG_INFO, "JRuby bundle activated");

        doMagicToMakeJRubyAndFelixHappy();

        // Retrieve the plugin config
        final PluginRubyConfig rubyConfig = retrievePluginRubyConfig(context);

        // Setup JRuby
        final ScriptingContainer scriptingContainer = setupScriptingContainer(rubyConfig);
        if (PluginType.NOTIFICATION.equals(rubyConfig.getPluginType())) {
            plugin = new JRubyNotificationPlugin(rubyConfig, scriptingContainer, context, logService);
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

    private PluginRubyConfig retrievePluginRubyConfig(final BundleContext context) {
        final PluginConfigServiceApi pluginConfigServiceApi = kb.getPluginConfigServiceApi();
        return pluginConfigServiceApi.getPluginRubyConfig(context.getBundle().getBundleId());
    }

    // JRuby/Felix specifics, it works out of the box on Equinox.
    // Other OSGI frameworks are untested.
    private void doMagicToMakeJRubyAndFelixHappy() {
        // Tell JRuby to use the correct class loader
        Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
    }

    private ScriptingContainer setupScriptingContainer(final PluginRubyConfig rubyConfig) {
        final ScriptingContainer scriptingContainer = new ScriptingContainer();

        // Set the load paths instead of adding, to avoid looking at the filesystem
        scriptingContainer.setLoadPaths(Collections.<String>singletonList(rubyConfig.getRubyLoadDir()));

        return scriptingContainer;
    }

    public void stop(final BundleContext context) throws Exception {
        plugin.stopPlugin(context);
        kb.close();
        logService.close();
    }

    private Map<String, Object> retrieveKillbillApis(final BundleContext context) {
        final Map<String, Object> killbillUserApis = new HashMap<String, Object>();

        // See killbill/plugin.rb for the naming convention magic
        killbillUserApis.put("account_user_api", kb.getAccountUserApi());
        killbillUserApis.put("analytics_sanity_api", kb.getAnalyticsSanityApi());
        killbillUserApis.put("analytics_user_api", kb.getAnalyticsUserApi());
        killbillUserApis.put("catalog_user_api", kb.getCatalogUserApi());
        killbillUserApis.put("entitlement_migration_api", kb.getEntitlementMigrationApi());
        killbillUserApis.put("entitlement_timeline_api", kb.getEntitlementMigrationApi());
        killbillUserApis.put("entitlement_transfer_api", kb.getEntitlementTransferApi());
        killbillUserApis.put("entitlement_user_api", kb.getEntitlementUserApi());
        killbillUserApis.put("invoice_migration_api", kb.getInvoiceMigrationApi());
        killbillUserApis.put("invoice_payment_api", kb.getInvoicePaymentApi());
        killbillUserApis.put("invoice_user_api", kb.getInvoiceUserApi());
        killbillUserApis.put("overdue_user_api", kb.getOverdueUserApi());
        killbillUserApis.put("payment_api", kb.getPaymentApi());
        killbillUserApis.put("tenant_user_api", kb.getTagUserApi());
        killbillUserApis.put("usage_user_api", kb.getUsageUserApi());
        killbillUserApis.put("audit_user_api", kb.getAuditUserApi());
        killbillUserApis.put("custom_field_user_api", kb.getCustomFieldUserApi());
        killbillUserApis.put("export_user_api", kb.getExportUserApi());
        killbillUserApis.put("tag_user_api", kb.getTagUserApi());

        return killbillUserApis;
    }
}
