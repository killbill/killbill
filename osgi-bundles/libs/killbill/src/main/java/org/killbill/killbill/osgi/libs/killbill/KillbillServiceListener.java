/*
 * Copyright 2014 The Billing Project, LLC
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

package org.killbill.killbill.osgi.libs.killbill;

import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;

public class KillbillServiceListener implements ServiceListener {

    private final BundleContext context;
    private final KillbillServiceListenerCallback killbillServiceListenerCallback;

    public static KillbillServiceListener listenForService(final BundleContext context, final String serviceClass, final KillbillServiceListenerCallback listenerCallback) throws InvalidSyntaxException {
        final String filter = "(objectclass=" + serviceClass + ")";

        final KillbillServiceListener killbillServiceListener = new KillbillServiceListener(context, listenerCallback);
        context.addServiceListener(killbillServiceListener, filter);

        // If the service was already registered, manually construct a REGISTERED ServiceEvent
        final ServiceReference[] serviceReferences = context.getServiceReferences((String) null, filter);
        for (int i = 0; serviceReferences != null && i < serviceReferences.length; i++) {
            killbillServiceListener.serviceChanged(new ServiceEvent(ServiceEvent.REGISTERED, serviceReferences[i]));
        }

        return killbillServiceListener;
    }

    public KillbillServiceListener(final BundleContext context, final KillbillServiceListenerCallback killbillServiceListenerCallback) {
        this.context = context;
        this.killbillServiceListenerCallback = killbillServiceListenerCallback;
    }

    @Override
    public void serviceChanged(final ServiceEvent event) {
        final ServiceReference serviceReference = event.getServiceReference();
        if (serviceReference == null || serviceReference.getBundle() == null) {
            return;
        }

        final BundleContext bundleContext = serviceReference.getBundle().getBundleContext();
        if (bundleContext == null) {
            return;
        }

        final Object service = bundleContext.getService(serviceReference);
        if (service != null) {
            if (event.getType() == ServiceEvent.REGISTERED) {
                killbillServiceListenerCallback.isRegistered(context);
            } else if (event.getType() == ServiceEvent.UNREGISTERING) {
                killbillServiceListenerCallback.isUnRegistering(context);
            }
        }
    }
}
