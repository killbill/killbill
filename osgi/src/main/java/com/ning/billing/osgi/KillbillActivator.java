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

import java.util.List;

import javax.inject.Inject;
import javax.servlet.Servlet;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.http.HttpService;

import com.ning.billing.osgi.api.OSGIKillbill;
import com.ning.billing.osgi.api.OSGIPluginProperties;
import com.ning.billing.osgi.api.OSGIServiceRegistration;
import com.ning.billing.payment.plugin.api.PaymentPluginApi;

import com.google.common.collect.ImmutableList;

public class KillbillActivator implements BundleActivator, ServiceListener {

    private final OSGIKillbill osgiKillbill;
    private final HttpService defaultHttpService;
    private final List<OSGIServiceRegistration> allRegistrationHandlers;

    private volatile ServiceRegistration osgiKillbillRegistration;

    private BundleContext context = null;

    @Inject
    public KillbillActivator(final OSGIKillbill osgiKillbill,
                             final HttpService defaultHttpService,
                             final OSGIServiceRegistration<Servlet> servletRouter,
                             final OSGIServiceRegistration<PaymentPluginApi> paymentProviderPluginRegistry) {
        this.osgiKillbill = osgiKillbill;
        this.defaultHttpService = defaultHttpService;
        this.allRegistrationHandlers = ImmutableList.<OSGIServiceRegistration>of(servletRouter, paymentProviderPluginRegistry);
    }

    @Override
    public void start(final BundleContext context) throws Exception {
        this.context = context;

        context.addServiceListener(this);
        registerServices(context);
    }

    @Override
    public void stop(final BundleContext context) throws Exception {
        this.context = null;

        context.removeServiceListener(this);
        unregisterServices();
    }

    @Override
    public void serviceChanged(final ServiceEvent event) {
        if (context == null || (event.getType() != ServiceEvent.REGISTERED && event.getType() != ServiceEvent.UNREGISTERING)) {
            // We are not initialized or uninterested
            return;
        }
        for (OSGIServiceRegistration cur : allRegistrationHandlers) {
            if (listenForServiceType(event, cur.getServiceType(), cur)) {
                break;
            }
        }
    }

    private <T> boolean listenForServiceType(final ServiceEvent event, Class<T> claz, final OSGIServiceRegistration<T> registation) {

        // Is that for us ?
        final String[] objectClass = (String[]) event.getServiceReference().getProperty("objectClass");
        if (objectClass == null || objectClass.length == 0 || !claz.getName().equals(objectClass[0])) {
            return false;
        }

        // Make sure we can retrieve the plugin name
        final ServiceReference serviceReference = event.getServiceReference();
        final String pluginName = (String) serviceReference.getProperty(OSGIPluginProperties.PLUGIN_NAME_PROP);
        if (pluginName == null) {
            // STEPH logger ?
            return true;
        }

        final T theService = (T) context.getService(serviceReference);
        if (theService == null) {
            return true;
        }

        switch (event.getType()) {
            case ServiceEvent.REGISTERED:
                registation.registerService(pluginName, theService);

                break;
            case ServiceEvent.UNREGISTERING:
                registation.unregisterService(pluginName);
                break;

            default:
                break;
        }
        return true;
    }

    private void registerServices(final BundleContext context) {
        osgiKillbillRegistration = context.registerService(OSGIKillbill.class.getName(), osgiKillbill, null);

        context.registerService(HttpService.class.getName(), defaultHttpService, null);
    }

    private void unregisterServices() {
        if (osgiKillbillRegistration != null) {
            osgiKillbillRegistration.unregister();
            osgiKillbillRegistration = null;
        }
    }
}
