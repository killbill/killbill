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

import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.catalog.glue.CatalogModule;
import com.ning.billing.mock.BrainDeadProxyFactory;
import com.ning.billing.util.clock.MockClockModule;
import com.ning.billing.util.glue.BusModule;
import com.ning.billing.util.glue.CallContextModule;

public class MockEngineModule extends DefaultEntitlementModule {

   
    @Override
    protected void configure() {
        super.configure();
        install(new CatalogModule());
        bind(AccountUserApi.class).toInstance(BrainDeadProxyFactory.createBrainDeadProxyFor(AccountUserApi.class));
        install(new MockClockModule());
        install(new CallContextModule());
    }
}
