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

package com.ning.billing.util.glue;

import org.skife.config.ConfigSource;

import com.ning.billing.GuicyKillbillTestNoDBModule;
import com.ning.billing.mock.glue.MockGlobalLockerModule;
import com.ning.billing.mock.glue.MockNonEntityDaoModule;
import com.ning.billing.mock.glue.MockNotificationQueueModule;
import com.ning.billing.util.api.AuditUserApi;
import com.ning.billing.util.audit.api.DefaultAuditUserApi;
import com.ning.billing.util.audit.dao.AuditDao;
import com.ning.billing.util.audit.dao.MockAuditDao;
import com.ning.billing.util.bus.InMemoryBusModule;

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
    }
}
