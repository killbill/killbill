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

import com.ning.billing.entitlement.api.EntitlementService;
import com.ning.billing.entitlement.api.migration.EntitlementMigrationApi;
import com.ning.billing.entitlement.api.timeline.EntitlementTimelineApi;
import com.ning.billing.entitlement.api.transfer.EntitlementTransferApi;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.glue.EntitlementModule;
import com.ning.billing.util.glue.RealImplementation;
import com.ning.billing.util.svcapi.entitlement.EntitlementInternalApi;

import com.google.inject.AbstractModule;

public class MockEntitlementModule extends AbstractModule implements EntitlementModule {
    @Override
    public void installEntitlementService() {
        bind(EntitlementService.class).toInstance(Mockito.mock(EntitlementService.class));
    }

    @Override
    public void installEntitlementUserApi() {
        bind(EntitlementUserApi.class).annotatedWith(RealImplementation.class).toInstance(Mockito.mock(EntitlementUserApi.class));
    }

    @Override
    public void installEntitlementMigrationApi() {
        bind(EntitlementMigrationApi.class).toInstance(Mockito.mock(EntitlementMigrationApi.class));
    }

    @Override
    public void installEntitlementInternalApi() {
        bind(EntitlementInternalApi.class).toInstance(Mockito.mock(EntitlementInternalApi.class));
    }

    @Override
    protected void configure() {
        installEntitlementService();
        installEntitlementUserApi();
        installEntitlementMigrationApi();
        installEntitlementInternalApi();
        installEntitlementTimelineApi();
    }

    @Override
    public void installEntitlementTimelineApi() {
        bind(EntitlementTimelineApi.class).toInstance(Mockito.mock(EntitlementTimelineApi.class));
    }

    @Override
    public void installEntitlementTransferApi() {
        bind(EntitlementTransferApi.class).toInstance(Mockito.mock(EntitlementTransferApi.class));

    }
}
