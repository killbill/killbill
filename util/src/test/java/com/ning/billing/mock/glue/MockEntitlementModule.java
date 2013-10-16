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

import org.mockito.Mockito;

import com.ning.billing.entitlement.api.EntitlementApi;
import com.ning.billing.entitlement.api.SubscriptionApi;
import com.ning.billing.glue.EntitlementModule;
import com.ning.billing.junction.BlockingInternalApi;

import com.google.inject.AbstractModule;

public class MockEntitlementModule extends AbstractModule implements EntitlementModule {

    private final BlockingInternalApi blockingApi = Mockito.mock(BlockingInternalApi.class);
    private final EntitlementApi entitlementApi = Mockito.mock(EntitlementApi.class);
    private final SubscriptionApi subscriptionApi = Mockito.mock(SubscriptionApi.class);

    @Override
    protected void configure() {
        installBlockingStateDao();
        installBlockingApi();
        installEntitlementApi();
        installBlockingChecker();
    }

    @Override
    public void installBlockingStateDao() {
    }

    @Override
    public void installBlockingApi() {
        //bind(BlockingInternalApi.class).toInstance(blockingApi);
    }

    @Override
    public void installEntitlementApi() {
        bind(EntitlementApi.class).toInstance(entitlementApi);
    }

    @Override
    public void installSubscriptionApi() {
        bind(SubscriptionApi.class).toInstance(subscriptionApi);
    }

    @Override
    public void installBlockingChecker() {
    }

}
