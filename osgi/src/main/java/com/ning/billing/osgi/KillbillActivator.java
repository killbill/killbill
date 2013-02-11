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

import javax.inject.Inject;
import javax.servlet.http.HttpServlet;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

import com.ning.billing.osgi.api.OSGIKillbill;
import com.ning.billing.osgi.api.http.ServletRouter;

public class KillbillActivator implements BundleActivator, ServiceListener {

    private final OSGIKillbill osgiKillbill;
    private final ServletRouter servletRouter;

    private volatile ServiceRegistration osgiKillbillRegistration;

    private BundleContext context = null;

    @Inject
    public KillbillActivator(final OSGIKillbill osgiKillbill,
                             final ServletRouter servletRouter) {
        this.osgiKillbill = osgiKillbill;
        this.servletRouter = servletRouter;
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
        listenForServlets(event);
    }

    private void listenForServlets(final ServiceEvent event) {
        if (event.getType() != ServiceEvent.REGISTERED && event.getType() != ServiceEvent.UNREGISTERING) {
            // Servlets can only be added or removed, not modified
            return;
        }
        final ServiceReference serviceReference = event.getServiceReference();

        // Make sure we can retrieve the plugin name
        final String pluginName = (String) serviceReference.getProperty("killbill.pluginName");
        if (pluginName == null) {
            return;
        }

        // Make sure this event is for a servlet
        HttpServlet httpServlet = null;
        final String[] objectClass = (String[]) event.getServiceReference().getProperty("objectClass");
        if (context != null && objectClass != null && objectClass.length > 0 && HttpServlet.class.getName().equals(objectClass[0])) {
            final Object service = context.getService(serviceReference);
            httpServlet = (HttpServlet) service;
        }

        if (httpServlet == null) {
            return;
        }

        if (event.getType() == ServiceEvent.REGISTERED) {
            servletRouter.registerServlet(pluginName, httpServlet);
        } else if (event.getType() == ServiceEvent.UNREGISTERING) {
            servletRouter.unregisterServlet(pluginName);
        }
    }

    private void registerServices(final BundleContext context) {
        osgiKillbillRegistration = context.registerService(OSGIKillbill.class.getName(), osgiKillbill, null);
    }

    private void unregisterServices() {
        if (osgiKillbillRegistration != null) {
            osgiKillbillRegistration.unregister();
            osgiKillbillRegistration = null;
        }
    }
}
