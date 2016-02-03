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

package org.killbill.billing.mock.glue;

import org.killbill.billing.entitlement.EntitlementInternalApi;
import org.killbill.billing.entitlement.api.EntitlementApi;
import org.killbill.billing.entitlement.api.SubscriptionApi;
import org.killbill.billing.glue.EntitlementModule;
import org.killbill.billing.junction.BlockingInternalApi;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.util.glue.KillBillModule;
import org.mockito.Mockito;

public class MockEntitlementModule extends KillBillModule implements EntitlementModule {

    private final EntitlementApi entitlementApi = Mockito.mock(EntitlementApi.class);
    private final EntitlementInternalApi entitlementInternalApi = Mockito.mock(EntitlementInternalApi.class);
    private final SubscriptionApi subscriptionApi = Mockito.mock(SubscriptionApi.class);

    protected final BlockingInternalApi blockingApi;

    public MockEntitlementModule(final KillbillConfigSource configSource) {
        this(configSource, Mockito.mock(BlockingInternalApi.class));
    }

    public MockEntitlementModule(final KillbillConfigSource configSource, final BlockingInternalApi blockingApi) {
        super(configSource);
        this.blockingApi = blockingApi;
    }

    @Override
    protected void configure() {
        installBlockingStateDao();
        installBlockingApi();
        installEntitlementApi();
        installEntitlementInternalApi();
        installBlockingChecker();
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
        bind(EntitlementApi.class).toInstance(entitlementApi);
    }

    @Override
    public void installEntitlementInternalApi() {
        bind(EntitlementInternalApi.class).toInstance(entitlementInternalApi);
    }

    @Override
    public void installSubscriptionApi() {
        bind(SubscriptionApi.class).toInstance(subscriptionApi);
    }

    @Override
    public void installBlockingChecker() {
    }
}
