/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

package org.killbill.billing.subscription.glue;

import org.killbill.billing.api.TestApiListener;
import org.killbill.billing.catalog.glue.CatalogModule;
import org.killbill.billing.mock.glue.MockTenantModule;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.subscription.DefaultSubscriptionTestInitializer;
import org.killbill.billing.subscription.SubscriptionTestInitializer;
import org.killbill.billing.subscription.api.user.TestSubscriptionHelper;
import org.killbill.billing.util.glue.CacheModule;
import org.killbill.billing.util.glue.CallContextModule;
import org.killbill.billing.util.glue.ConfigModule;

public class TestDefaultSubscriptionModule extends DefaultSubscriptionModule {

    public TestDefaultSubscriptionModule(final KillbillConfigSource configSource) {
        super(configSource);
    }

    @Override
    protected void configure() {
        super.configure();
        install(new CatalogModule(configSource));
        install(new CallContextModule(configSource));
        install(new CacheModule(configSource));
        install(new ConfigModule(configSource));
        install(new MockTenantModule(configSource));

        bind(TestSubscriptionHelper.class).asEagerSingleton();
        bind(TestApiListener.class).asEagerSingleton();
        bind(SubscriptionTestInitializer.class).to(DefaultSubscriptionTestInitializer.class).asEagerSingleton();
    }
}
