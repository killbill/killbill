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

package com.ning.billing.entitlement.glue;

import org.mockito.Mockito;

import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.api.TestApiListener;
import com.ning.billing.api.TestListenerStatus;
import com.ning.billing.catalog.glue.CatalogModule;
import com.ning.billing.entitlement.DefaultEntitlementTestInitializer;
import com.ning.billing.entitlement.EntitlementTestInitializer;
import com.ning.billing.entitlement.EntitlementTestListenerStatus;
import com.ning.billing.entitlement.api.user.DefaultEntitlementUserApi;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.entitlement.api.user.TestUtil;
import com.ning.billing.mock.glue.MockClockModule;
import com.ning.billing.util.glue.CacheModule;
import com.ning.billing.util.glue.CallContextModule;
import com.ning.billing.util.glue.NonEntityDaoModule;
import com.ning.billing.util.glue.RealImplementation;

public class MockEngineModule extends DefaultEntitlementModule {


    @Override
    public void installEntitlementUserApi() {
        bind(EntitlementUserApi.class).to(DefaultEntitlementUserApi.class).asEagerSingleton();
    }

    @Override
    protected void configure() {
        super.configure();
        install(new CatalogModule());
        install(new CallContextModule());
        install(new CacheModule());

        bind(AccountUserApi.class).toInstance(Mockito.mock(AccountUserApi.class));

        bind(TestUtil.class).asEagerSingleton();
        bind(TestListenerStatus.class).to(EntitlementTestListenerStatus.class).asEagerSingleton();
        bind(TestApiListener.class).asEagerSingleton();
        bind(EntitlementTestInitializer.class).to(DefaultEntitlementTestInitializer.class).asEagerSingleton();
    }
}
