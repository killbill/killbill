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

package com.ning.billing.mock.glue;

import com.google.inject.AbstractModule;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.junction.api.BillingApi;
import com.ning.billing.junction.api.BlockingApi;
import com.ning.billing.mock.BrainDeadProxyFactory;

public class MockJunctionModule extends AbstractModule {
    private BillingApi billingApi = BrainDeadProxyFactory.createBrainDeadProxyFor(BillingApi.class);
    private BlockingApi blockingApi = BrainDeadProxyFactory.createBrainDeadProxyFor(BlockingApi.class);
    private AccountUserApi userApi = BrainDeadProxyFactory.createBrainDeadProxyFor(AccountUserApi.class);
    private EntitlementUserApi entUserApi = BrainDeadProxyFactory.createBrainDeadProxyFor(EntitlementUserApi.class);
    
    @Override
    protected void configure() {
        installBlockingApi();
        installAccountUserApi();
        installBillingApi();
        installEntitlementUserApi();
    }

    protected void installBillingApi() {
        bind(BillingApi.class).toInstance(billingApi);
    }
    
    
    protected void installAccountUserApi() {
        bind(AccountUserApi.class).toInstance(userApi);
    }
    
    protected void installBlockingApi() {
        bind(BlockingApi.class).toInstance(blockingApi);
    }
    
    protected void installEntitlementUserApi() {
        bind(EntitlementUserApi.class).toInstance(entUserApi);
    }
}
