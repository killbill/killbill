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

package org.killbill.billing.util.glue;

import org.killbill.billing.GuicyKillbillTestNoDBModule;
import org.killbill.billing.mock.glue.MockGlobalLockerModule;
import org.killbill.billing.mock.glue.MockNonEntityDaoModule;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.util.api.AuditUserApi;
import org.killbill.billing.util.audit.api.DefaultAuditUserApi;
import org.killbill.billing.util.audit.dao.AuditDao;
import org.killbill.billing.util.audit.dao.MockAuditDao;

public class TestUtilModuleNoDB extends TestUtilModule {

    public TestUtilModuleNoDB(final KillbillConfigSource configSource) {
        super(configSource);
    }

    private void installAuditMock() {
        bind(AuditDao.class).toInstance(new MockAuditDao());
        bind(AuditUserApi.class).to(DefaultAuditUserApi.class).asEagerSingleton();
    }

    @Override
    protected void configure() {
        super.configure();
        install(new GuicyKillbillTestNoDBModule(configSource));

        install(new MockNonEntityDaoModule(configSource));
        install(new MockGlobalLockerModule(configSource));

        installAuditMock();

        install(new ShiroModuleNoDB(configSource));
        install(new KillBillShiroAopModule());
        install(new SecurityModule(configSource));
    }

    public static class ShiroModuleNoDB extends KillBillShiroModule {

        public ShiroModuleNoDB(final KillbillConfigSource configSource) {
            super(configSource);
        }

        protected void configureJDBCRealm() {
        }

        protected void configureLDAPRealm() {
        }
    }
}
