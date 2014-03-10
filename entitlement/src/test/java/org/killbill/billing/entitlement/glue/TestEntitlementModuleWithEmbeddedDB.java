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

package org.killbill.billing.entitlement.glue;

import org.skife.config.ConfigSource;

import org.killbill.billing.GuicyKillbillTestWithEmbeddedDBModule;
import org.killbill.billing.account.glue.DefaultAccountModule;
import org.killbill.billing.api.TestApiListener;
import org.killbill.billing.catalog.glue.CatalogModule;
import org.killbill.billing.subscription.glue.DefaultSubscriptionModule;
import org.killbill.billing.util.glue.AuditModule;
import org.killbill.billing.util.glue.BusModule;
import org.killbill.billing.util.glue.MetricsModule;
import org.killbill.billing.util.glue.NonEntityDaoModule;
import org.killbill.billing.util.glue.NotificationQueueModule;
import org.killbill.billing.util.glue.TagStoreModule;

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
