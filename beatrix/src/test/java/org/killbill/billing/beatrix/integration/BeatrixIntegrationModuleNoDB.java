/*
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

package org.killbill.billing.beatrix.integration;

import org.killbill.billing.GuicyKillbillTestNoDBModule;
import org.killbill.billing.mock.glue.MockAccountModule;
import org.killbill.billing.mock.glue.MockNonEntityDaoModule;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.glue.KillBillModule;

import com.google.inject.util.Providers;

public class BeatrixIntegrationModuleNoDB extends KillBillModule {

    public BeatrixIntegrationModuleNoDB(final KillbillConfigSource configSource) {
        super(configSource);
    }

    @Override
    protected void configure() {
        install(new GuicyKillbillTestNoDBModule(configSource));

        install(new MockNonEntityDaoModule(configSource));
        install(new MockAccountModule(configSource));
        bind(CacheControllerDispatcher.class).toProvider(Providers.<CacheControllerDispatcher>of(null));
    }
}
