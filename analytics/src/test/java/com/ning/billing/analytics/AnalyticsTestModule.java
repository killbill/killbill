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

import com.ning.billing.util.email.EmailModule;
import com.ning.billing.util.glue.GlobalLockerModule;
import org.skife.jdbi.v2.IDBI;

import com.ning.billing.account.glue.AccountModule;
import com.ning.billing.analytics.setup.AnalyticsModule;
import com.ning.billing.dbi.MysqlTestingHelper;
import com.ning.billing.entitlement.glue.EntitlementModule;
import com.ning.billing.invoice.glue.InvoiceModule;
import com.ning.billing.junction.MockBlockingModule;
import com.ning.billing.junction.glue.JunctionModule;
import com.ning.billing.payment.setup.PaymentModule;
import com.ning.billing.util.glue.BusModule;
import com.ning.billing.util.glue.CallContextModule;
import com.ning.billing.util.glue.ClockModule;
import com.ning.billing.util.glue.FieldStoreModule;
import com.ning.billing.util.glue.NotificationQueueModule;
import com.ning.billing.util.glue.TagStoreModule;
import com.ning.billing.util.tag.dao.TagDefinitionSqlDao;

public class AnalyticsTestModule extends AnalyticsModule
{
    @Override
    protected void configure()
    {
        super.configure();

        // Need to configure a few more things for the EventBus
        install(new EmailModule());
        install(new GlobalLockerModule());
        install(new ClockModule());
        install(new CallContextModule());
        install(new FieldStoreModule());
        install(new TagStoreModule());
        install(new AccountModule());
        install(new BusModule());
        install(new EntitlementModule());
        install(new InvoiceModule());
        install(new PaymentModule());
        install(new TagStoreModule());
        install(new NotificationQueueModule());
        install(new JunctionModule());

        // Install the Dao layer
        final MysqlTestingHelper helper = new MysqlTestingHelper();
        bind(MysqlTestingHelper.class).toInstance(helper);
        final IDBI dbi = helper.getDBI();
        bind(IDBI.class).toInstance(dbi);

        bind(TagDefinitionSqlDao.class).toInstance(dbi.onDemand(TagDefinitionSqlDao.class));
    }
}
