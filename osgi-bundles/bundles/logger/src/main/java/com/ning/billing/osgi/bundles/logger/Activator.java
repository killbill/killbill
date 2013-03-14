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

package com.ning.billing.osgi.bundles.logger;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.service.log.LogListener;
import org.osgi.service.log.LogReaderService;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Activator implements BundleActivator {

    private static final Logger logger = LoggerFactory.getLogger(Activator.class);

    private final LogListener killbillLogListener = new KillbillLogWriter();
    private final List<LogReaderService> logReaderServices = new LinkedList<LogReaderService>();

    private final ServiceListener logReaderServiceListener = new ServiceListener() {
        public void serviceChanged(final ServiceEvent event) {
            final ServiceReference serviceReference = event.getServiceReference();
            if (serviceReference == null || serviceReference.getBundle() == null) {
                return;
            }

            final BundleContext bundleContext = serviceReference.getBundle().getBundleContext();
            if (bundleContext == null) {
                return;
            }

            final LogReaderService logReaderService = (LogReaderService) bundleContext.getService(serviceReference);
            if (logReaderService != null) {
                if (event.getType() == ServiceEvent.REGISTERED) {
                    registerLogReaderService(logReaderService);
                } else if (event.getType() == ServiceEvent.UNREGISTERING) {
                    logReaderService.removeLogListener(killbillLogListener);
                    logReaderServices.remove(logReaderService);
                }
            }
        }
    };

    @Override
    public void start(final BundleContext context) throws Exception {
        // Get a list of all the registered LogReaderService, and add the killbill listener
        final ServiceTracker logReaderTracker = new ServiceTracker(context, LogReaderService.class.getName(), null);
        logReaderTracker.open();
        final Object[] readers = logReaderTracker.getServices();
        if (readers != null) {
            for (final Object reader : readers) {
                final LogReaderService service = (LogReaderService) reader;
                registerLogReaderService(service);
            }
        }
        logReaderTracker.close();

        // Add the ServiceListener
        final String filter = "(objectclass=" + LogReaderService.class.getName() + ")";
        try {
            context.addServiceListener(logReaderServiceListener, filter);
        } catch (InvalidSyntaxException e) {
            logger.warn("Unable to register the killbill LogReaderService listener", e);
        }
    }

    @Override
    public void stop(final BundleContext context) throws Exception {
        for (final Iterator<LogReaderService> iterator = logReaderServices.iterator(); iterator.hasNext(); ) {
            final LogReaderService service = iterator.next();
            service.removeLogListener(killbillLogListener);
            iterator.remove();
        }
    }

    private void registerLogReaderService(final LogReaderService service) {
        logReaderServices.add(service);
        service.addLogListener(killbillLogListener);
    }
}
