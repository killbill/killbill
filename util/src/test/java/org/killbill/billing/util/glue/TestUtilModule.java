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

package org.killbill.billing.util.glue;

import org.killbill.billing.mock.glue.MockAccountModule;
import org.killbill.billing.mock.glue.MockTenantModule;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.subscription.api.timeline.SubscriptionBaseTimelineApi;
import org.mockito.Mockito;

public class TestUtilModule extends KillBillModule {

    public TestUtilModule(final KillbillConfigSource configSource) {
        super(configSource);
    }

    // TODO this is bad!
    public void installHacks() {
        // DefaultAuditUserApi is using SubscriptionBaseTimeline API
        bind(SubscriptionBaseTimelineApi.class).toInstance(Mockito.mock(SubscriptionBaseTimelineApi.class));
        // InternalCallContextFactory is using AccountInternalApi
        install(new MockAccountModule(configSource));
    }

    @Override
    protected void configure() {
        //install(new CallContextModule());
        install(new CacheModule(configSource));
        install(new ConfigModule(configSource));
        install(new MockTenantModule(configSource));
        installHacks();
    }
}
