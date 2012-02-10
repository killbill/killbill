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

package com.ning.billing.invoice.glue;

import java.io.IOException;

import com.ning.billing.invoice.api.test.InvoiceTestApi;
import com.ning.billing.invoice.api.test.DefaultInvoiceTestApi;
import com.ning.billing.invoice.dao.InvoicePaymentSqlDao;
import com.ning.billing.invoice.dao.RecurringInvoiceItemSqlDao;
import org.skife.jdbi.v2.IDBI;
import com.ning.billing.account.glue.AccountModule;
import com.ning.billing.catalog.glue.CatalogModule;
import com.ning.billing.dbi.MysqlTestingHelper;
import com.ning.billing.entitlement.glue.EntitlementModule;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.DefaultClock;
import com.ning.billing.util.glue.BusModule;
import com.ning.billing.util.notificationq.MockNotificationQueueService;
import com.ning.billing.util.notificationq.NotificationQueueService;

public class InvoiceModuleWithEmbeddedDb extends InvoiceModule {
    private final MysqlTestingHelper helper = new MysqlTestingHelper();
    private IDBI dbi;

    public void startDb() throws IOException {
        helper.startMysql();
    }

    public void initDb(final String ddl) throws IOException {
        helper.initDb(ddl);
    }

    public void stopDb() {
        helper.stopMysql();
    }

    public RecurringInvoiceItemSqlDao getInvoiceItemSqlDao() {
        return dbi.onDemand(RecurringInvoiceItemSqlDao.class);
    }

    public InvoicePaymentSqlDao getInvoicePaymentSqlDao() {
        return dbi.onDemand(InvoicePaymentSqlDao.class);
    }

    private void installNotificationQueue() {
        bind(NotificationQueueService.class).to(MockNotificationQueueService.class).asEagerSingleton();
    }

    @Override
    public void configure() {
        dbi = helper.getDBI();
        bind(IDBI.class).toInstance(dbi);

        bind(Clock.class).to(DefaultClock.class).asEagerSingleton();
        installNotificationQueue();
        install(new AccountModule());
        install(new CatalogModule());
        install(new EntitlementModule());

        super.configure();

        bind(InvoiceTestApi.class).to(DefaultInvoiceTestApi.class).asEagerSingleton();

        install(new BusModule());
    }
}
