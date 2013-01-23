/* 
 * Copyright 2010-2011 Ning, Inc.
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

package com.ning.billing.util.bus;

import org.skife.config.ConfigurationObjectFactory;
import org.skife.jdbi.v2.IDBI;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import com.ning.billing.KillbillTestSuiteWithEmbeddedDB;
import com.ning.billing.dbi.DBIProvider;
import com.ning.billing.dbi.DBTestingHelper;
import com.ning.billing.dbi.DbiConfig;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.ClockMock;
import com.ning.billing.util.glue.BusModule;
import com.ning.billing.util.glue.BusModule.BusType;
import com.ning.billing.util.glue.CacheModule;
import com.ning.billing.util.glue.NonEntityDaoModule;

import com.google.inject.AbstractModule;

@Guice(modules = TestPersistentEventBus.PersistentBusModuleTest.class)
public class TestPersistentEventBus extends TestEventBusBase {

    public static class PersistentBusModuleTest extends AbstractModule {

        @Override
        protected void configure() {
            //System.setProperty("com.ning.billing.dbi.test.useLocalDb", "true");

            bind(Clock.class).to(ClockMock.class).asEagerSingleton();
            bind(ClockMock.class).asEagerSingleton();

            final DBTestingHelper helper = KillbillTestSuiteWithEmbeddedDB.getDBTestingHelper();
            if (helper.isUsingLocalInstance()) {
                bind(IDBI.class).toProvider(DBIProvider.class).asEagerSingleton();
                final DbiConfig config = new ConfigurationObjectFactory(System.getProperties()).build(DbiConfig.class);
                bind(DbiConfig.class).toInstance(config);
            } else {
                final IDBI dbi = helper.getDBI();
                bind(IDBI.class).toInstance(dbi);
            }
            install(new BusModule(BusType.PERSISTENT));
            install(new NonEntityDaoModule());
            install(new CacheModule());
        }
    }

    @Test(groups = "slow")
    public void testSimple() {
        super.testSimple();
    }

    // Until Guava fixes exception handling, r13?
    @Test(groups = "slow", enabled = false)
    public void testSimpleWithException() {
        super.testSimpleWithException();
    }
}
