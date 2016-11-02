/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

package org.killbill.billing.jaxrs.glue;

import org.apache.shiro.mgt.SecurityManager;
import org.killbill.billing.GuicyKillbillTestNoDBModule;
import org.killbill.billing.mock.glue.MockAccountModule;
import org.killbill.billing.mock.glue.MockNonEntityDaoModule;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.tenant.api.TenantInternalApi;
import org.killbill.billing.util.glue.CacheModule;
import org.killbill.billing.util.glue.ConfigModule;
import org.mockito.Mockito;

public class TestJaxrsModuleNoDB extends TestJaxrsModule {

    public TestJaxrsModuleNoDB(final KillbillConfigSource configSource) {
        super(configSource);
    }

    @Override
    public void configure() {
        super.configure();
        install(new GuicyKillbillTestNoDBModule(configSource));

        install(new MockNonEntityDaoModule(configSource));
        install(new MockAccountModule(configSource));
        install(new CacheModule(configSource));
        install(new ConfigModule(configSource));
        bind(TenantInternalApi.class).toInstance(Mockito.mock(TenantInternalApi.class));
        bind(SecurityManager.class).toInstance(Mockito.mock(SecurityManager.class));
    }
}
