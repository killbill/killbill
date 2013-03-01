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

package com.ning.billing.osgi.bundles.test;

import java.util.Dictionary;
import java.util.Hashtable;

import org.osgi.framework.BundleContext;

import com.ning.billing.osgi.api.OSGIPluginProperties;
import com.ning.billing.payment.plugin.api.PaymentPluginApi;
import com.ning.billing.payment.plugin.api.PaymentPluginApiWithTestControl;
import com.ning.killbill.osgi.libs.killbill.KillbillActivatorBase;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillEventDispatcher.OSGIKillbillEventHandler;

/**
 * Test class used by Payment tests-- to test fake OSGI payment bundle
 */
public class PaymentActivator extends KillbillActivatorBase {

    @Override
    public void start(final BundleContext context) throws Exception {

        final String bundleName = context.getBundle().getSymbolicName();
        System.out.println("PaymentActivator starting bundle = " + bundleName);

        super.start(context);
        registerPaymentApi(context);
    }

    @Override
    public void stop(final BundleContext context) throws Exception {
        super.stop(context);
        System.out.println("Good bye world from PaymentActivator!");
    }

    @Override
    public OSGIKillbillEventHandler getOSGIKillbillEventHandler() {
        return null;
    }

    private void registerPaymentApi(final BundleContext context) {

        final Dictionary props = new Hashtable();
        // Same name the beatrix tests expect when using that payment plugin
        props.put(OSGIPluginProperties.PLUGIN_NAME_PROP, "osgiPaymentPlugin");
        registrar.registerService(context, PaymentPluginApiWithTestControl.class, new TestPaymentPluginApi("test"), props);
    }
}
