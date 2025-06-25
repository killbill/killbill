/*
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

package org.killbill.billing.server.log;

import org.killbill.billing.GuicyKillbillTestNoDBModule;
import org.killbill.billing.mock.glue.MockAccountModule;
import org.killbill.billing.mock.glue.MockNonEntityDaoModule;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.config.definition.EventConfig;
import org.killbill.billing.util.glue.KillBillModule;
import org.killbill.clock.ClockMock;
import org.killbill.commons.metrics.api.MetricRegistry;
import org.killbill.commons.metrics.impl.NoOpMetricRegistry;
import org.skife.config.AugmentedConfigurationObjectFactory;

import com.google.inject.util.Providers;

public class TestServerModuleNoDB extends KillBillModule {

    private final ClockMock clock;

    public TestServerModuleNoDB(final KillbillConfigSource configSource, final ClockMock clock) {
        super(configSource);
        this.clock = clock;
    }

    @Override
    public void configure() {
        install(new GuicyKillbillTestNoDBModule(configSource, clock));

        install(new MockNonEntityDaoModule(configSource));
        install(new MockAccountModule(configSource));
        bind(CacheControllerDispatcher.class).toProvider(Providers.<CacheControllerDispatcher>of(null));
        final EventConfig eventConfig = new AugmentedConfigurationObjectFactory(skifeConfigSource).build(EventConfig.class);
        bind(EventConfig.class).toInstance(eventConfig);

        bind(MetricRegistry.class).to(NoOpMetricRegistry.class).asEagerSingleton();
    }
}
