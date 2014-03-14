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

package org.killbill.billing.util.glue;

import org.skife.config.ConfigSource;

import org.killbill.billing.GuicyKillbillTestNoDBModule;
import org.killbill.billing.mock.glue.MockGlobalLockerModule;
import org.killbill.billing.mock.glue.MockNonEntityDaoModule;
import org.killbill.billing.mock.glue.MockNotificationQueueModule;
import org.killbill.billing.util.api.AuditUserApi;
import org.killbill.billing.util.audit.api.DefaultAuditUserApi;
import org.killbill.billing.util.audit.dao.AuditDao;
import org.killbill.billing.util.audit.dao.MockAuditDao;
import org.killbill.billing.util.bus.InMemoryBusModule;

public class TestUtilModuleNoDB extends TestUtilModule {

    public TestUtilModuleNoDB(final ConfigSource configSource) {
        super(configSource);
    }

    private void installAuditMock() {
        bind(AuditDao.class).toInstance(new MockAuditDao());
        bind(AuditUserApi.class).to(DefaultAuditUserApi.class).asEagerSingleton();
    }

    @Override
    protected void configure() {
        super.configure();
        install(new GuicyKillbillTestNoDBModule());

        install(new MockNonEntityDaoModule());
        install(new MockGlobalLockerModule());
        install(new InMemoryBusModule(configSource));
        install(new MockNotificationQueueModule(configSource));

        installAuditMock();

        install(new KillBillShiroModule(configSource));
        install(new KillBillShiroAopModule());
        install(new SecurityModule(configSource));
    }
}
