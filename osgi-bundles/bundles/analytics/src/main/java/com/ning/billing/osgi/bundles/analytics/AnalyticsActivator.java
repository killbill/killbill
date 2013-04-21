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

package com.ning.billing.osgi.bundles.analytics;

import java.util.Hashtable;

import javax.servlet.Servlet;
import javax.servlet.http.HttpServlet;

import org.osgi.framework.BundleContext;

import com.ning.billing.osgi.api.OSGIPluginProperties;
import com.ning.billing.osgi.bundles.analytics.api.user.AnalyticsUserApi;
import com.ning.billing.osgi.bundles.analytics.http.AnalyticsServlet;
import com.ning.killbill.osgi.libs.killbill.KillbillActivatorBase;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillEventDispatcher.OSGIKillbillEventHandler;

public class AnalyticsActivator extends KillbillActivatorBase {

    public static final String PLUGIN_NAME = "killbill-analytics";

    private OSGIKillbillEventHandler analyticsListener;

    @Override
    public void start(final BundleContext context) throws Exception {
        super.start(context);

        analyticsListener = new AnalyticsListener(logService, killbillAPI, dataSource);
        dispatcher.registerEventHandler(analyticsListener);

        final AnalyticsUserApi analyticsUserApi = new AnalyticsUserApi(logService, killbillAPI, dataSource);
        final AnalyticsServlet analyticsServlet = new AnalyticsServlet(analyticsUserApi, logService);
        registerServlet(context, analyticsServlet);
    }

    @Override
    public OSGIKillbillEventHandler getOSGIKillbillEventHandler() {
        return analyticsListener;
    }

    private void registerServlet(final BundleContext context, final HttpServlet servlet) {
        final Hashtable<String, String> props = new Hashtable<String, String>();
        props.put(OSGIPluginProperties.PLUGIN_NAME_PROP, PLUGIN_NAME);
        registrar.registerService(context, Servlet.class, servlet, props);
    }
}
