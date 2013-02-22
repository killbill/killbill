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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jruby.embed.ScriptingContainer;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.log.LogService;

import com.ning.billing.osgi.api.OSGIKillbill;
import com.ning.billing.osgi.api.config.PluginConfig.PluginType;
import com.ning.billing.osgi.api.config.PluginConfigServiceApi;
import com.ning.billing.osgi.api.config.PluginRubyConfig;

public class Activator implements BundleActivator {

    private final List<ServiceReference<?>> serviceReferences = new ArrayList<ServiceReference<?>>();
    private final Logger logger = new Logger();

    private OSGIKillbill osgiKillbill;
    private JRubyPlugin plugin = null;

    public void start(final BundleContext context) throws Exception {
        logger.start(context);

        osgiKillbill = retrieveApi(context, OSGIKillbill.class);
        logger.log(LogService.LOG_INFO, "JRuby bundle activated");

        doMagicToMakeJRubyAndFelixHappy();

        // Retrieve the plugin config
        final PluginRubyConfig rubyConfig = retrievePluginRubyConfig(context);

        // Setup JRuby
        final ScriptingContainer scriptingContainer = setupScriptingContainer(rubyConfig);
        if (PluginType.NOTIFICATION.equals(rubyConfig.getPluginType())) {
            plugin = new JRubyNotificationPlugin(rubyConfig, scriptingContainer, context, logger);
        } else if (PluginType.PAYMENT.equals(rubyConfig.getPluginType())) {
            plugin = new JRubyPaymentPlugin(rubyConfig, scriptingContainer, context, logger);
        }

        // Validate and instantiate the plugin

        final Map<String, Object> killbillServices = retrieveKillbillApis(context);
        killbillServices.put("root", rubyConfig.getPluginVersionRoot().getAbsolutePath());
        killbillServices.put("logger", logger);
        plugin.instantiatePlugin(killbillServices);

        logger.log(LogService.LOG_INFO, "Starting JRuby plugin " + plugin.getPluginMainClass());
        plugin.startPlugin(context);
    }

    private PluginRubyConfig retrievePluginRubyConfig(final BundleContext context) {
        final PluginConfigServiceApi pluginConfigServiceApi = osgiKillbill.getPluginConfigServiceApi();
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
        logger.log(LogService.LOG_INFO, "Stopping JRuby plugin " + plugin.getPluginMainClass());
        plugin.stopPlugin(context);

        for (final ServiceReference apiReference : serviceReferences) {
            context.ungetService(apiReference);
        }

        logger.close();
    }

    private Map<String, Object> retrieveKillbillApis(final BundleContext context) {
        final Map<String, Object> killbillUserApis = new HashMap<String, Object>();

        // See killbill/plugin.rb for the naming convention magic
        killbillUserApis.put("account_user_api", osgiKillbill.getAccountUserApi());
        killbillUserApis.put("analytics_sanity_api", osgiKillbill.getAnalyticsSanityApi());
        killbillUserApis.put("analytics_user_api", osgiKillbill.getAnalyticsUserApi());
        killbillUserApis.put("catalog_user_api", osgiKillbill.getCatalogUserApi());
        killbillUserApis.put("entitlement_migration_api", osgiKillbill.getEntitlementMigrationApi());
        killbillUserApis.put("entitlement_timeline_api", osgiKillbill.getEntitlementMigrationApi());
        killbillUserApis.put("entitlement_transfer_api", osgiKillbill.getEntitlementTransferApi());
        killbillUserApis.put("entitlement_user_api", osgiKillbill.getEntitlementUserApi());
        killbillUserApis.put("invoice_migration_api", osgiKillbill.getInvoiceMigrationApi());
        killbillUserApis.put("invoice_payment_api", osgiKillbill.getInvoicePaymentApi());
        killbillUserApis.put("invoice_user_api", osgiKillbill.getInvoiceUserApi());
        killbillUserApis.put("overdue_user_api", osgiKillbill.getOverdueUserApi());
        killbillUserApis.put("payment_api", osgiKillbill.getPaymentApi());
        killbillUserApis.put("tenant_user_api", osgiKillbill.getTagUserApi());
        killbillUserApis.put("usage_user_api", osgiKillbill.getUsageUserApi());
        killbillUserApis.put("audit_user_api", osgiKillbill.getAuditUserApi());
        killbillUserApis.put("custom_field_user_api", osgiKillbill.getCustomFieldUserApi());
        killbillUserApis.put("export_user_api", osgiKillbill.getExportUserApi());
        killbillUserApis.put("tag_user_api", osgiKillbill.getTagUserApi());

        return killbillUserApis;
    }

    /**
     * Retrieve a service class (e.g. Killbill API)
     *
     * @param context OSGI Bundle context
     * @param clazz   service class to retrieve
     * @param <T>     class type to retrieve
     * @return instance of the service class
     */
    private <T> T retrieveApi(final BundleContext context, final Class<T> clazz) {
        final ServiceReference<T> apiReference = context.getServiceReference(clazz);
        if (apiReference != null) {
            // Keep references to stop the bundle properly
            serviceReferences.add(apiReference);

            return context.getService(apiReference);
        } else {
            return null;
        }
    }
}
