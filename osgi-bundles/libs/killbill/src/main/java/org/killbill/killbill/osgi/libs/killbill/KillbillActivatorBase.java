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

package org.killbill.killbill.osgi.libs.killbill;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

import org.killbill.killbill.osgi.libs.killbill.OSGIKillbillEventDispatcher.OSGIKillbillEventHandler;

public abstract class KillbillActivatorBase implements BundleActivator {


    protected OSGIKillbillAPI killbillAPI;
    protected OSGIKillbillLogService logService;
    protected OSGIKillbillRegistrar registrar;
    protected OSGIKillbillDataSource dataSource;
    protected OSGIKillbillEventDispatcher dispatcher;

    @Override
    public void start(final BundleContext context) throws Exception {

        // Tracked resource
        killbillAPI = new OSGIKillbillAPI(context);
        logService = new OSGIKillbillLogService(context);
        dataSource = new OSGIKillbillDataSource(context);
        dispatcher = new OSGIKillbillEventDispatcher(context);

        // Registrar for bundle
        registrar = new OSGIKillbillRegistrar();

        // Killbill events
        final OSGIKillbillEventHandler handler = getOSGIKillbillEventHandler();
        if (handler != null) {
            dispatcher.registerEventHandler(handler);
        }
    }

    @Override
    public void stop(final BundleContext context) throws Exception {

        // Close trackers
        if (killbillAPI != null) {
            killbillAPI.close();
            killbillAPI = null;
        }
        if (dispatcher != null) {
            dispatcher.close();
            dispatcher = null;
        }
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
        if (logService != null) {
            logService.close();
            logService = null;
        }

        try {
            // Remove Killbill event handler
            final OSGIKillbillEventHandler handler = getOSGIKillbillEventHandler();
            if (handler != null && dispatcher != null) {
                dispatcher.unregisterEventHandler(handler);
                dispatcher = null;
            }
        } catch (OSGIServiceNotAvailable ignore) {
            // If the system bundle shut down prior to that bundle, we can' unregister our Observer, which is fine.
        }

        // Unregister all servies from that bundle
        if (registrar != null) {
            registrar.unregisterAll();
            registrar = null;
        }
    }


    public abstract OSGIKillbillEventHandler getOSGIKillbillEventHandler();
}
