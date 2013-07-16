/*
 * Copyright 2010-2013 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
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
import com.ning.billing.glue.EntitlementModule;
import com.ning.billing.junction.api.JunctionApi;
import com.ning.billing.subscription.api.user.SubscriptionUserApi;
import com.ning.billing.util.svcapi.junction.BlockingInternalApi;
import org.mockito.Mockito;

public class MockEntitlementModule extends AbstractModule implements EntitlementModule {

    private final AccountUserApi userApi = Mockito.mock(AccountUserApi.class);
    private final SubscriptionUserApi entUserApi = Mockito.mock(SubscriptionUserApi.class);
    private final BlockingInternalApi blockingApi = Mockito.mock(BlockingInternalApi.class);
    private final JunctionApi junctionApi = Mockito.mock(JunctionApi.class);

    @Override
    protected void configure() {
        installAccountUserApi();
        installSubscriptionUserApi();
        installBlockingStateDao();
        installBlockingApi();
        installEntitlementApi();
    }

    @Override
    public void installAccountUserApi() {
        bind(AccountUserApi.class).toInstance(userApi);
    }

    @Override
    public void installSubscriptionUserApi() {
        bind(SubscriptionUserApi.class).toInstance(entUserApi);
    }

    @Override
    public void installBlockingStateDao() {
    }

    @Override
    public void installBlockingApi() {
        bind(BlockingInternalApi.class).toInstance(blockingApi);
    }

    @Override
    public void installEntitlementApi() {
        bind(JunctionApi.class).toInstance(junctionApi);
    }

    @Override
    public void installBlockingChecker() {
    }

}
