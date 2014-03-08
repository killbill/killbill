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

package org.killbill.billing.osgi.bundles.test;

import java.util.Dictionary;
import java.util.Hashtable;

import org.osgi.framework.BundleContext;

import org.killbill.billing.osgi.api.OSGIPluginProperties;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.payment.plugin.api.PaymentPluginApiWithTestControl;
import org.killbill.killbill.osgi.libs.killbill.KillbillActivatorBase;
import org.killbill.killbill.osgi.libs.killbill.OSGIKillbillEventDispatcher.OSGIKillbillEventHandler;

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
        props.put(OSGIPluginProperties.PLUGIN_NAME_PROP, "osgi-payment-plugin");
        registrar.registerService(context, PaymentPluginApiWithTestControl.class, new TestPaymentPluginApi("test"), props);
    }
}
