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

package org.killbill.billing.osgi;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.Observable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.Servlet;
import javax.sql.DataSource;

import org.killbill.billing.invoice.plugin.api.InvoicePluginApi;
import org.killbill.billing.osgi.api.OSGIConfigProperties;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.service.http.HttpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.killbill.billing.currency.plugin.api.CurrencyPluginApi;
import org.killbill.billing.osgi.api.OSGIKillbill;
import org.killbill.billing.osgi.api.OSGIPluginProperties;
import org.killbill.billing.osgi.api.OSGIServiceDescriptor;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.osgi.glue.DefaultOSGIModule;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.killbill.osgi.libs.killbill.OSGIKillbillRegistrar;

import com.google.common.collect.ImmutableList;

public class KillbillActivator implements BundleActivator, ServiceListener {

    final static int PLUGIN_NAME_MAX_LENGTH = 40;
    final static Pattern PLUGIN_NAME_PATTERN = Pattern.compile("\\p{Lower}(?:\\p{Lower}|\\d|-|_)*");

    private final static Logger logger = LoggerFactory.getLogger(KillbillActivator.class);

    private final OSGIKillbill osgiKillbill;
    private final HttpService defaultHttpService;
    private final DataSource dataSource;
    private final KillbillEventObservable observable;
    private final OSGIKillbillRegistrar registrar;
    private final OSGIConfigProperties configProperties;

    private final List<OSGIServiceRegistration> allRegistrationHandlers;


    private BundleContext context = null;

    @Inject
    public KillbillActivator(@Named(DefaultOSGIModule.OSGI_NAMED) final DataSource dataSource,
                             final OSGIKillbill osgiKillbill,
                             final HttpService defaultHttpService,
                             final KillbillEventObservable observable,
                             final OSGIConfigProperties configProperties,
                             final OSGIServiceRegistration<Servlet> servletRouter,
                             final OSGIServiceRegistration<PaymentPluginApi> paymentProviderPluginRegistry,
                             final OSGIServiceRegistration<InvoicePluginApi> invoiceProviderPluginRegistry,
                             final OSGIServiceRegistration<CurrencyPluginApi> currencyProviderPluginRegistry) {
        this.osgiKillbill = osgiKillbill;
        this.defaultHttpService = defaultHttpService;
        this.dataSource = dataSource;
        this.observable = observable;
        this.configProperties = configProperties;
        this.registrar = new OSGIKillbillRegistrar();
        this.allRegistrationHandlers = ImmutableList.<OSGIServiceRegistration>of(servletRouter, paymentProviderPluginRegistry, invoiceProviderPluginRegistry, currencyProviderPluginRegistry);
    }

    @Override
    public void start(final BundleContext context) throws Exception {

        this.context = context;
        final Dictionary props = new Hashtable();
        props.put(OSGIPluginProperties.PLUGIN_NAME_PROP, "killbill");

        observable.register();

        registrar.registerService(context, OSGIKillbill.class, osgiKillbill, props);
        registrar.registerService(context, HttpService.class, defaultHttpService, props);
        registrar.registerService(context, Observable.class, observable, props);
        registrar.registerService(context, DataSource.class, dataSource, props);
        registrar.registerService(context, OSGIConfigProperties.class, configProperties, props);

        context.addServiceListener(this);
    }

    @Override
    public void stop(final BundleContext context) throws Exception {
        this.context = null;
        context.removeServiceListener(this);
        observable.unregister();
        registrar.unregisterAll();
    }

    @Override
    public void serviceChanged(final ServiceEvent event) {
        if (context == null || (event.getType() != ServiceEvent.REGISTERED && event.getType() != ServiceEvent.UNREGISTERING)) {
            // We are not initialized or uninterested
            return;
        }

        final ServiceReference serviceReference = event.getServiceReference();
        for (OSGIServiceRegistration cur : allRegistrationHandlers) {
            if (listenForServiceType(serviceReference, event.getType(), cur.getServiceType(), cur)) {
                break;
            }
        }
    }

    private <T> boolean listenForServiceType(final ServiceReference serviceReference, final int eventType, final Class<T> claz, final OSGIServiceRegistration<T> registration) {
        // Make sure we can retrieve the plugin name
        final String serviceName = (String) serviceReference.getProperty(OSGIPluginProperties.PLUGIN_NAME_PROP);
        if (serviceName == null || !checkSanityPluginRegistrationName(serviceName)) {
            // Quite common for non Killbill bundles
            logger.debug("Ignoring registered OSGI service {} with no {} property", claz.getName(), OSGIPluginProperties.PLUGIN_NAME_PROP);
            return true;
        }

        final Object theServiceObject = context.getService(serviceReference);
        // Is that for us? We look for a subclass here for greater flexibility (e.g. HttpServlet for a Servlet service)
        if (theServiceObject == null || !claz.isAssignableFrom(theServiceObject.getClass())) {
            return false;
        }
        final T theService = (T) theServiceObject;

        final OSGIServiceDescriptor desc = new DefaultOSGIServiceDescriptor(serviceReference.getBundle().getSymbolicName(), serviceName);
        switch (eventType) {
            case ServiceEvent.REGISTERED:
                final T wrappedService = ContextClassLoaderHelper.getWrappedServiceWithCorrectContextClassLoader(theService);
                registration.registerService(desc, wrappedService);
                break;
            case ServiceEvent.UNREGISTERING:
                registration.unregisterService(desc.getRegistrationName());
                break;
            default:
                break;
        }
        return true;
    }


    private final boolean checkSanityPluginRegistrationName(final String pluginName) {
        final Matcher m = PLUGIN_NAME_PATTERN.matcher(pluginName);
        if (!m.matches()) {
            logger.warn("Invalid plugin name {} : should be of the form {}", pluginName, PLUGIN_NAME_PATTERN.toString());
            return false;
        }
        if (pluginName.length() > PLUGIN_NAME_MAX_LENGTH) {
            logger.warn("Invalid plugin name {} : too long, should be less than {}", pluginName, PLUGIN_NAME_MAX_LENGTH);
            return false;
        }
        return true;
    }
}
