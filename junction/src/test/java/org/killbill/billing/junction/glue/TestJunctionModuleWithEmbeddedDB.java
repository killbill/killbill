/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

package org.killbill.billing.junction.glue;

import org.killbill.billing.GuicyKillbillTestWithEmbeddedDBModule;
import org.killbill.billing.account.glue.DefaultAccountModule;
import org.killbill.billing.api.TestApiListener;
import org.killbill.billing.catalog.glue.CatalogModule;
import org.killbill.billing.entitlement.glue.DefaultEntitlementModule;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.subscription.glue.DefaultSubscriptionModule;
import org.killbill.billing.util.glue.AuditModule;
import org.killbill.billing.util.glue.NonEntityDaoModule;
import org.killbill.billing.util.glue.TagStoreModule;

public class TestJunctionModuleWithEmbeddedDB extends TestJunctionModule {

    public TestJunctionModuleWithEmbeddedDB(final KillbillConfigSource configSource) {
        super(configSource);
    }

    @Override
    protected void configure() {
        super.configure();

        install(new GuicyKillbillTestWithEmbeddedDBModule(configSource));
        install(new NonEntityDaoModule(configSource));
        install(new CatalogModule(configSource));
        install(new DefaultAccountModule(configSource));
        install(new DefaultEntitlementModule(configSource));
        install(new DefaultSubscriptionModule(configSource));
        install(new TagStoreModule(configSource));
        install(new AuditModule(configSource));

        bind(TestApiListener.class).asEagerSingleton();
    }
}
