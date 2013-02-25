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

package com.ning.killbill.osgi.libs.killbill;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

import com.ning.killbill.osgi.libs.killbill.OSGIKillbillEventDispatcher.OSGIKillbillEventHandler;

public abstract class KillbillActivatorBase implements BundleActivator, OSGIKillbillEventHandler {


    protected OSGIKillbillAPI api;
    protected OSGIKillbillLogService logService;
    protected OSGIKillbillRegistrar registrar;
    protected OSGIKillbillDataSource dataSource;
    protected OSGIKillbillEventDispatcher dispatcher;

    @Override
    public void start(final BundleContext context) {

        // Tracked resource
        api = new OSGIKillbillAPI(context);
        logService = new OSGIKillbillLogService(context);
        dataSource = new OSGIKillbillDataSource(context);
        dispatcher = new OSGIKillbillEventDispatcher(context);

        // Registrar for bundle
        registrar = new OSGIKillbillRegistrar();

        // Killbill events
        final OSGIKillbillEventHandler handler = getOSGIKillbillEventHandler();
        if (handler != null) {
            dispatcher.registerEventHandler(this);
        }
    }

    @Override
    public void stop(final BundleContext context) {

        // Close trackers
        api.close();
        dispatcher.close();
        dataSource.close();
        logService.close();

        try {
            // Remove Killbill event handler
            final OSGIKillbillEventHandler handler = getOSGIKillbillEventHandler();
            if (handler != null) {
                dispatcher.unregisterEventHandler(handler);
            }
        } catch (OSGIServiceNotAvailable ignore) {
            // If the system bundle shut down prior to that bundle, we can' unregister our Observer, which is fine.
        }

        // Unregistaer all servies from that bundle
        registrar.unregisterAll();
        System.out.println("Good bye world from TestActivator!");
    }


    public abstract OSGIKillbillEventHandler getOSGIKillbillEventHandler();
}
