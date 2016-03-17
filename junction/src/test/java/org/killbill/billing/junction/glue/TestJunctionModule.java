/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

package org.killbill.billing.junction.glue;

import org.killbill.billing.entitlement.api.svcs.DefaultInternalBlockingApi;
import org.killbill.billing.entitlement.block.BlockingChecker;
import org.killbill.billing.entitlement.block.MockBlockingChecker;
import org.killbill.billing.entitlement.dao.BlockingStateDao;
import org.killbill.billing.entitlement.dao.MockBlockingStateDao;
import org.killbill.billing.junction.BlockingInternalApi;
import org.killbill.billing.mock.glue.MockEntitlementModule;
import org.killbill.billing.mock.glue.MockTenantModule;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.util.glue.CacheModule;
import org.killbill.billing.util.glue.CallContextModule;
import org.killbill.billing.util.glue.ConfigModule;
import org.killbill.billing.util.glue.KillBillShiroAopModule;
import org.killbill.billing.util.glue.KillBillShiroModule;
import org.killbill.billing.util.glue.SecurityModule;

public class TestJunctionModule extends DefaultJunctionModule {

    public TestJunctionModule(final KillbillConfigSource configSource) {
        super(configSource);
    }

    @Override
    protected void configure() {
        super.configure();

        install(new CacheModule(configSource));
        install(new ConfigModule(configSource));
        install(new CallContextModule(configSource));
        install(new MockTenantModule(configSource));
        // Needed because Entitlement depends on Security
        install(new KillBillShiroModuleOnlyIniRealm(configSource));
        install(new SecurityModule(configSource));
    }

    public class MockEntitlementModuleForJunction extends MockEntitlementModule {

        public MockEntitlementModuleForJunction(final KillbillConfigSource configSource) {
            super(configSource);
        }

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

    private static class KillBillShiroModuleOnlyIniRealm extends KillBillShiroModule {

        public KillBillShiroModuleOnlyIniRealm(final KillbillConfigSource configSource) {
            super(configSource);
        }

        protected void configureJDBCRealm() {
        }

        protected void configureLDAPRealm() {
        }
    }
}
