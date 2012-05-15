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
import com.ning.billing.entitlement.api.EntitlementService;
import com.ning.billing.entitlement.api.billing.ChargeThruApi;
import com.ning.billing.entitlement.api.migration.EntitlementMigrationApi;
import com.ning.billing.entitlement.api.timeline.EntitlementTimelineApi;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.glue.EntitlementModule;
import com.ning.billing.mock.BrainDeadProxyFactory;
import com.ning.billing.util.glue.RealImplementation;

public class MockEntitlementModule extends AbstractModule implements EntitlementModule {
    
    /* (non-Javadoc)
     * @see com.ning.billing.mock.glue.EntitlementModule#installEntitlementService()
     */
    @Override
    public void installEntitlementService() {
        bind(EntitlementService.class).toInstance(BrainDeadProxyFactory.createBrainDeadProxyFor(EntitlementService.class));
    }
    
    /* (non-Javadoc)
     * @see com.ning.billing.mock.glue.EntitlementModule#installEntitlementUserApi()
     */
    @Override
    public void installEntitlementUserApi() {
        bind(EntitlementUserApi.class).annotatedWith(RealImplementation.class).toInstance(BrainDeadProxyFactory.createBrainDeadProxyFor(EntitlementUserApi.class));
    }
    
    /* (non-Javadoc)
     * @see com.ning.billing.mock.glue.EntitlementModule#installEntitlementMigrationApi()
     */
    @Override
    public void installEntitlementMigrationApi() {
        bind(EntitlementMigrationApi.class).toInstance(BrainDeadProxyFactory.createBrainDeadProxyFor(EntitlementMigrationApi.class));
    }
    
    /* (non-Javadoc)
     * @see com.ning.billing.mock.glue.EntitlementModule#installChargeThruApi()
     */
    @Override
    public void installChargeThruApi() {
        bind(ChargeThruApi.class).toInstance(BrainDeadProxyFactory.createBrainDeadProxyFor(ChargeThruApi.class));
    }

    @Override
    protected void configure() {
        installEntitlementService();
        installEntitlementUserApi();
        installEntitlementMigrationApi();
        installChargeThruApi();
        installEntitlementTimelineApi();
    }

    @Override
    public void installEntitlementTimelineApi() {
        bind(EntitlementTimelineApi.class).toInstance(BrainDeadProxyFactory.createBrainDeadProxyFor(EntitlementTimelineApi.class));
    }
}
