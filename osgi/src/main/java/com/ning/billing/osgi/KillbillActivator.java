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

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

import com.ning.billing.osgi.api.OSGIKillbill;
import com.ning.billing.payment.plugin.api.PaymentPluginApi;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;

public class KillbillActivator implements BundleActivator {

    private final OSGIKillbill osgiKillbill;

    private volatile ServiceRegistration osgiKillbillRegistration;
    @Inject
    public KillbillActivator(final OSGIKillbill osgiKillbill) {
        this.osgiKillbill = osgiKillbill;
    }

    @Override
    public void start(final BundleContext context) throws Exception {
        registerServices(context);
    }

    private void registerServices(final BundleContext context) {
        osgiKillbillRegistration = context.registerService(OSGIKillbill.class.getName(), osgiKillbill, null);
    }

    @Override
    public void stop(final BundleContext context) throws Exception {
        if (osgiKillbillRegistration != null) {
            osgiKillbillRegistration.unregister();
            osgiKillbillRegistration = null;
        }
    }

}
