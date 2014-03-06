/*
 * Copyright 2010-2013 Ning, Inc.
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

package com.ning.billing.entitlement.glue;

import org.skife.config.ConfigSource;

import com.ning.billing.GuicyKillbillTestWithEmbeddedDBModule;
import com.ning.billing.account.glue.DefaultAccountModule;
import com.ning.billing.api.TestApiListener;
import com.ning.billing.catalog.glue.CatalogModule;
import com.ning.billing.subscription.glue.DefaultSubscriptionModule;
import com.ning.billing.util.glue.AuditModule;
import com.ning.billing.util.glue.BusModule;
import com.ning.billing.util.glue.MetricsModule;
import com.ning.billing.util.glue.NonEntityDaoModule;
import com.ning.billing.util.glue.NotificationQueueModule;
import com.ning.billing.util.glue.TagStoreModule;

public class TestEntitlementModuleWithEmbeddedDB extends TestEntitlementModule {

    public TestEntitlementModuleWithEmbeddedDB(final ConfigSource configSource) {
        super(configSource);
    }

    @Override
    protected void configure() {
        super.configure();
        install(new DefaultAccountModule(configSource));
        install(new GuicyKillbillTestWithEmbeddedDBModule());
        install(new NonEntityDaoModule());
        install(new MetricsModule());
        install(new BusModule(configSource));
        install(new TagStoreModule());
        install(new CatalogModule(configSource));
        install(new NotificationQueueModule(configSource));
        install(new DefaultSubscriptionModule(configSource));
        install(new AuditModule());

        bind(TestApiListener.class).asEagerSingleton();
    }
}
