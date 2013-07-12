/*
 * Copyright 2010-2012 Ning, Inc.
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

import com.ning.billing.entitlement.api.SubscriptionService;
import com.ning.billing.entitlement.api.migration.EntitlementMigrationApi;
import com.ning.billing.subscription.api.timeline.SubscriptionTimelineApi;
import com.ning.billing.subscription.api.transfer.SubscriptionTransferApi;
import com.ning.billing.subscription.api.user.SubscriptionUserApi;
import com.ning.billing.glue.EntitlementModule;
import com.ning.billing.util.glue.RealImplementation;
import com.ning.billing.util.svcapi.entitlement.EntitlementInternalApi;

import com.google.inject.AbstractModule;

public class MockEntitlementModule extends AbstractModule implements EntitlementModule {
    @Override
    public void installSubscriptionService() {
        bind(SubscriptionService.class).toInstance(Mockito.mock(SubscriptionService.class));
    }

    @Override
    public void installSubscriptionUserApi() {
        bind(SubscriptionUserApi.class).annotatedWith(RealImplementation.class).toInstance(Mockito.mock(SubscriptionUserApi.class));
    }

    @Override
    public void installSubscriptionMigrationApi() {
        bind(EntitlementMigrationApi.class).toInstance(Mockito.mock(EntitlementMigrationApi.class));
    }

    @Override
    public void installSubscriptionInternalApi() {
        bind(EntitlementInternalApi.class).toInstance(Mockito.mock(EntitlementInternalApi.class));
    }

    @Override
    protected void configure() {
        installSubscriptionService();
        installSubscriptionUserApi();
        installSubscriptionMigrationApi();
        installSubscriptionInternalApi();
        installSubscriptionTimelineApi();
    }

    @Override
    public void installSubscriptionTimelineApi() {
        bind(SubscriptionTimelineApi.class).toInstance(Mockito.mock(SubscriptionTimelineApi.class));
    }

    @Override
    public void installSubscriptionTransferApi() {
        bind(SubscriptionTransferApi.class).toInstance(Mockito.mock(SubscriptionTransferApi.class));

    }
}
