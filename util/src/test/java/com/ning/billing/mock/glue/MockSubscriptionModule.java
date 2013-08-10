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

import com.ning.billing.glue.SubscriptionModule;
import com.ning.billing.subscription.api.SubscriptionBaseService;
import com.ning.billing.subscription.api.migration.SubscriptionBaseMigrationApi;
import com.ning.billing.subscription.api.timeline.SubscriptionBaseTimelineApi;
import com.ning.billing.subscription.api.transfer.SubscriptionBaseTransferApi;
import com.ning.billing.util.svcapi.subscription.SubscriptionBaseInternalApi;

import com.google.inject.AbstractModule;

public class MockSubscriptionModule extends AbstractModule implements SubscriptionModule {

    @Override
    public void installSubscriptionService() {
        bind(SubscriptionBaseService.class).toInstance(Mockito.mock(SubscriptionBaseService.class));
    }


    @Override
    public void installSubscriptionMigrationApi() {
        bind(SubscriptionBaseMigrationApi.class).toInstance(Mockito.mock(SubscriptionBaseMigrationApi.class));
    }

    @Override
    public void installSubscriptionInternalApi() {
        bind(SubscriptionBaseInternalApi.class).toInstance(Mockito.mock(SubscriptionBaseInternalApi.class));
    }

    @Override
    protected void configure() {
        installSubscriptionService();
        installSubscriptionMigrationApi();
        installSubscriptionInternalApi();
        installSubscriptionTimelineApi();
    }

    @Override
    public void installSubscriptionTimelineApi() {
        bind(SubscriptionBaseTimelineApi.class).toInstance(Mockito.mock(SubscriptionBaseTimelineApi.class));
    }

    @Override
    public void installSubscriptionTransferApi() {
        bind(SubscriptionBaseTransferApi.class).toInstance(Mockito.mock(SubscriptionBaseTransferApi.class));

    }
}
