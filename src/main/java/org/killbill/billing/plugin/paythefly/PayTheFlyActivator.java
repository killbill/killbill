/*
 * Copyright 2024 PayTheFly
 * Copyright 2024 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.plugin.paythefly;

import java.util.Hashtable;

import javax.servlet.Servlet;
import javax.servlet.http.HttpServlet;

import org.killbill.billing.osgi.api.Healthcheck;
import org.killbill.billing.osgi.api.OSGIPluginProperties;
import org.killbill.billing.osgi.libs.killbill.KillbillActivatorBase;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.plugin.api.notification.PluginConfigurationEventHandler;
import org.killbill.billing.plugin.core.config.PluginEnvironmentConfig;
import org.killbill.billing.plugin.core.resources.jooby.PluginApp;
import org.killbill.billing.plugin.core.resources.jooby.PluginAppBuilder;
import org.killbill.billing.plugin.paythefly.dao.PayTheFlyDao;
import org.osgi.framework.BundleContext;

/**
 * OSGi activator for the PayTheFly Web3 payment plugin.
 *
 * <p>Registers the {@link PayTheFlyPaymentPluginApi} as a {@link PaymentPluginApi} service,
 * exposes the webhook notification servlet, and wires up health-check + configuration handling.</p>
 */
public class PayTheFlyActivator extends KillbillActivatorBase {

    public static final String PLUGIN_NAME = "killbill-paythefly";

    private PayTheFlyConfigPropertiesConfigurationHandler configHandler;

    @Override
    public void start(final BundleContext context) throws Exception {
        super.start(context);

        final PayTheFlyDao dao = new PayTheFlyDao(dataSource.getDataSource());

        final String region = PluginEnvironmentConfig.getRegion(configProperties.getProperties());
        configHandler = new PayTheFlyConfigPropertiesConfigurationHandler(PLUGIN_NAME, killbillAPI, region);

        final PayTheFlyConfigProperties payTheFlyConfig = configHandler.createConfigurable(configProperties.getProperties());
        configHandler.setDefaultConfigurable(payTheFlyConfig);

        // Health check
        final PayTheFlyHealthcheck healthcheck = new PayTheFlyHealthcheck(configHandler);
        registerHealthcheck(context, healthcheck);

        // Payment plugin API
        final PayTheFlyPaymentPluginApi pluginApi = new PayTheFlyPaymentPluginApi(
                configHandler,
                killbillAPI,
                configProperties,
                clock.getClock(),
                dao
        );
        registerPaymentPluginApi(context, pluginApi);

        // Servlet (webhook endpoint + healthcheck)
        final PluginApp pluginApp = new PluginAppBuilder(PLUGIN_NAME,
                                                         killbillAPI,
                                                         dataSource,
                                                         super.clock,
                                                         configProperties)
                .withRouteClass(PayTheFlyHealthcheckServlet.class)
                .withRouteClass(PayTheFlyWebhookServlet.class)
                .withService(healthcheck)
                .withService(pluginApi)
                .withService(clock)
                .withService(dao)
                .withService(configHandler)
                .build();
        final HttpServlet servlet = PluginApp.createServlet(pluginApp);
        registerServlet(context, servlet);

        registerHandlers();
    }

    public void registerHandlers() {
        final PluginConfigurationEventHandler handler = new PluginConfigurationEventHandler(configHandler);
        dispatcher.registerEventHandlers(handler);
    }

    private void registerServlet(final BundleContext context, final HttpServlet servlet) {
        final Hashtable<String, String> props = new Hashtable<>();
        props.put(OSGIPluginProperties.PLUGIN_NAME_PROP, PLUGIN_NAME);
        registrar.registerService(context, Servlet.class, servlet, props);
    }

    private void registerPaymentPluginApi(final BundleContext context, final PaymentPluginApi api) {
        final Hashtable<String, String> props = new Hashtable<>();
        props.put(OSGIPluginProperties.PLUGIN_NAME_PROP, PLUGIN_NAME);
        registrar.registerService(context, PaymentPluginApi.class, api, props);
    }

    private void registerHealthcheck(final BundleContext context, final PayTheFlyHealthcheck healthcheck) {
        final Hashtable<String, String> props = new Hashtable<>();
        props.put(OSGIPluginProperties.PLUGIN_NAME_PROP, PLUGIN_NAME);
        registrar.registerService(context, Healthcheck.class, healthcheck, props);
    }
}
