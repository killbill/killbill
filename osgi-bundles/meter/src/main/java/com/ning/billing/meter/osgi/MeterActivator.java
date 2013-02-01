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

package com.ning.billing.meter.osgi;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

import com.ning.billing.meter.DefaultMeterService;
import com.ning.billing.meter.MeterService;
import com.ning.billing.meter.glue.MeterModule;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;

public class MeterActivator implements BundleActivator {

    private DefaultMeterService meterService = null;

    @Override
    public void start(final BundleContext context) throws Exception {
        final Injector injector = Guice.createInjector(Stage.PRODUCTION, new MeterModule());
        meterService = (DefaultMeterService) injector.getInstance(MeterService.class);
        meterService.start();
    }

    @Override
    public void stop(final BundleContext context) throws Exception {
        if (meterService != null) {
            meterService.stop();
        }
    }
}
