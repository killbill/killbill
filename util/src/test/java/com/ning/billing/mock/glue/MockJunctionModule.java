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

import org.mockito.Mockito;

import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.glue.JunctionModule;
import com.ning.billing.junction.api.BillingApi;
import com.ning.billing.junction.api.BlockingApi;

import com.google.inject.AbstractModule;

public class MockJunctionModule extends AbstractModule implements JunctionModule {
    private final BillingApi billingApi = Mockito.mock(BillingApi.class);
    private final BlockingApi blockingApi = Mockito.mock(BlockingApi.class);
    private final AccountUserApi userApi = Mockito.mock(AccountUserApi.class);
    private final EntitlementUserApi entUserApi = Mockito.mock(EntitlementUserApi.class);

    @Override
    protected void configure() {
        installBlockingApi();
        installAccountUserApi();
        installBillingApi();
        installEntitlementUserApi();
    }

    @Override
    public void installBillingApi() {
        bind(BillingApi.class).toInstance(billingApi);
    }

    @Override
    public void installAccountUserApi() {
        bind(AccountUserApi.class).toInstance(userApi);
    }

    @Override
    public void installBlockingApi() {
        bind(BlockingApi.class).toInstance(blockingApi);
    }

    @Override
    public void installEntitlementUserApi() {
        bind(EntitlementUserApi.class).toInstance(entUserApi);
    }
}
