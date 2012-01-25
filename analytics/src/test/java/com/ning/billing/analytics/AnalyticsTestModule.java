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

package com.ning.billing.analytics;

import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.IDBI;

import com.ning.billing.account.glue.AccountModule;
import com.ning.billing.analytics.setup.AnalyticsModule;
import com.ning.billing.catalog.glue.CatalogModule;
import com.ning.billing.dbi.MysqlTestingHelper;
import com.ning.billing.entitlement.glue.EntitlementModule;
import com.ning.billing.util.glue.ClockModule;
import com.ning.billing.util.glue.EventBusModule;
import com.ning.billing.util.glue.TagStoreModule;

public class AnalyticsTestModule extends AnalyticsModule
{
    @Override
    protected void configure()
    {
        super.configure();

        // Need to configure a few more things for the EventBus
        install(new AccountModule());
        install(new CatalogModule());
        install(new EventBusModule());
        install(new EntitlementModule());
        install(new ClockModule());
        install(new TagStoreModule());

        // Install the Dao layer
        final MysqlTestingHelper helper = new MysqlTestingHelper();
        bind(MysqlTestingHelper.class).toInstance(helper);
        final DBI dbi = helper.getDBI();
        bind(IDBI.class).toInstance(dbi);
        bind(DBI.class).toInstance(dbi);
    }
}
