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

package com.ning.billing.junction.glue;

import org.skife.config.ConfigSource;

import com.ning.billing.entitlement.api.svcs.DefaultInternalBlockingApi;
import com.ning.billing.entitlement.block.BlockingChecker;
import com.ning.billing.entitlement.block.MockBlockingChecker;
import com.ning.billing.entitlement.dao.BlockingStateDao;
import com.ning.billing.entitlement.dao.MockBlockingStateDao;
import com.ning.billing.junction.BlockingInternalApi;
import com.ning.billing.mock.glue.MockEntitlementModule;
import com.ning.billing.util.glue.CacheModule;
import com.ning.billing.util.glue.CallContextModule;
import com.ning.billing.util.glue.MetricsModule;

public class TestJunctionModule extends DefaultJunctionModule {

    public TestJunctionModule(final ConfigSource configSource) {
        super(configSource);
    }

    @Override
    protected void configure() {
        super.configure();

        install(new MetricsModule());
        install(new CacheModule(configSource));
        install(new CallContextModule());
    }

    public class MockEntitlementModuleForJunction extends MockEntitlementModule {

        @Override
        public void installBlockingApi() {
            bind(BlockingInternalApi.class).to(DefaultInternalBlockingApi.class).asEagerSingleton();
        }

        @Override
        public void installBlockingStateDao() {
            bind(BlockingStateDao.class).to(MockBlockingStateDao.class).asEagerSingleton();
        }

        @Override
        public void installBlockingChecker() {
            bind(BlockingChecker.class).to(MockBlockingChecker.class).asEagerSingleton();
        }
    }
}
