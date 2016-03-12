/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

package org.killbill.billing.tenant.glue;

import org.killbill.billing.GuicyKillbillTestWithEmbeddedDBModule;
import org.killbill.billing.mock.glue.MockAccountModule;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.util.glue.NonEntityDaoModule;
import org.killbill.billing.util.glue.SecurityModule;
import org.killbill.billing.util.glue.TestUtilModuleNoDB.ShiroModuleNoDB;

public class TestTenantModuleWithEmbeddedDB extends TestTenantModule {

    public TestTenantModuleWithEmbeddedDB(final KillbillConfigSource configSource) {
        super(configSource);
    }

    @Override
    public void configure() {
        super.configure();

        install(new GuicyKillbillTestWithEmbeddedDBModule(configSource));
        install(new NonEntityDaoModule(configSource));
        install(new SecurityModule(configSource));
        install(new ShiroModuleNoDB(configSource));
        install(new MockAccountModule(configSource));
    }
}
