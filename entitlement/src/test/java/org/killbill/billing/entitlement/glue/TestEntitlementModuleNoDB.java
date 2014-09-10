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

package org.killbill.billing.entitlement.glue;

import org.killbill.billing.GuicyKillbillTestNoDBModule;
import org.killbill.billing.catalog.MockCatalogModule;
import org.killbill.billing.entitlement.dao.BlockingStateDao;
import org.killbill.billing.entitlement.dao.MockBlockingStateDao;
import org.killbill.billing.mock.glue.MockAccountModule;
import org.killbill.billing.mock.glue.MockNonEntityDaoModule;
import org.killbill.billing.mock.glue.MockSubscriptionModule;
import org.killbill.billing.mock.glue.MockTagModule;
import org.killbill.billing.platform.api.KillbillConfigSource;

public class TestEntitlementModuleNoDB extends TestEntitlementModule {

    public TestEntitlementModuleNoDB(final KillbillConfigSource configSource) {
        super(configSource);
    }

    @Override
    protected void configure() {
        super.configure();
        install(new GuicyKillbillTestNoDBModule(configSource));
        install(new MockNonEntityDaoModule(configSource));
        install(new MockTagModule(configSource));
        install(new MockSubscriptionModule(configSource));
        install(new MockCatalogModule(configSource));
        install(new MockAccountModule(configSource));
    }

    @Override
    public void installBlockingStateDao() {
        bind(BlockingStateDao.class).to(MockBlockingStateDao.class).asEagerSingleton();
    }
}
