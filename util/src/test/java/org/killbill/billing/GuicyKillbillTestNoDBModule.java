/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing;

import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.platform.test.glue.TestPlatformModuleNoDB;
import org.killbill.billing.util.glue.IDBISetup;
import org.killbill.billing.util.glue.MemoryGlobalLockerModule;
import org.killbill.billing.util.optimizer.BusDispatcherOptimizer;
import org.killbill.billing.util.optimizer.BusDispatcherOptimizerNoop;
import org.killbill.billing.util.optimizer.BusDispatcherOptimizerOn;
import org.killbill.billing.util.optimizer.BusOptimizer;
import org.killbill.billing.util.optimizer.BusOptimizerNoop;
import org.killbill.billing.util.optimizer.BusOptimizerOn;
import org.killbill.clock.ClockMock;
import org.skife.jdbi.v2.IDBI;

import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

public class GuicyKillbillTestNoDBModule extends GuicyKillbillTestModule {

    public GuicyKillbillTestNoDBModule(final KillbillConfigSource configSource, final ClockMock clock) {
        super(configSource, clock);
    }

    public class KillbillTestPlatformModuleNoDB extends TestPlatformModuleNoDB {

        public KillbillTestPlatformModuleNoDB(final KillbillConfigSource configSource) {
            super(configSource);
        }

        @Override
        protected void configureBus() {
            super.configureBus();
            if (killbillFeatures.isBusOptimizationOn()) {
                this.bind(BusOptimizer.class).to(BusOptimizerOn.class).asEagerSingleton();
                this.bind(BusDispatcherOptimizer.class).to(BusDispatcherOptimizerOn.class).asEagerSingleton();
            } else {
                this.bind(BusOptimizer.class).to(BusOptimizerNoop.class).asEagerSingleton();
                this.bind(BusDispatcherOptimizer.class).to(BusDispatcherOptimizerNoop.class).asEagerSingleton();
            }
        }
    }

    @Override
    protected void configure() {
        super.configure();

        install(new KillbillTestPlatformModuleNoDB(configSource));
        install(new MemoryGlobalLockerModule(configSource));
    }

    @Provides
    @Singleton
    @Named(IDBISetup.MAIN_RO_IDBI_NAMED)
    protected IDBI provideRoIDBI(final IDBI idbi) {
        return idbi;
    }
}
