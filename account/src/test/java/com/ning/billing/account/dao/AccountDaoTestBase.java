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

package com.ning.billing.account.dao;

import static org.testng.Assert.fail;

import java.io.IOException;

import com.ning.billing.util.CallContext;
import com.ning.billing.util.CallOrigin;
import com.ning.billing.util.UserType;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.entity.CallContextFactory;
import org.apache.commons.io.IOUtils;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.TransactionCallback;
import org.skife.jdbi.v2.TransactionStatus;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import com.ning.billing.account.glue.AccountModuleWithEmbeddedDb;
import com.ning.billing.util.bus.DefaultBusService;
import com.ning.billing.util.bus.BusService;
import org.testng.annotations.BeforeMethod;

public abstract class AccountDaoTestBase {
    protected AccountModuleWithEmbeddedDb module;
    protected AccountDao accountDao;
    protected IDBI dbi;

    protected CallContext context;

    @BeforeClass(alwaysRun = true)
    protected void setup() throws IOException {
        // Health check test to make sure MySQL is setup properly
        try {
            module = new AccountModuleWithEmbeddedDb();
            final String accountDdl = IOUtils.toString(AccountSqlDao.class.getResourceAsStream("/com/ning/billing/account/ddl.sql"));
            final String utilDdl = IOUtils.toString(AccountSqlDao.class.getResourceAsStream("/com/ning/billing/util/ddl.sql"));

            module.startDb();
            module.initDb(accountDdl);
            module.initDb(utilDdl);

            final Injector injector = Guice.createInjector(Stage.DEVELOPMENT, module);
            dbi = injector.getInstance(IDBI.class);

            accountDao = injector.getInstance(AccountDao.class);
            accountDao.test();

            Clock clock = injector.getInstance(Clock.class);
            context = new CallContextFactory(clock).createCallContext("Vizzini", CallOrigin.TEST, UserType.TEST);


            BusService busService = injector.getInstance(BusService.class);
            ((DefaultBusService) busService).startBus();
        }
        catch (Throwable t) {
            fail(t.toString());
        }
    }

    @AfterClass(alwaysRun = true)
    public void stopMysql()
    {
        module.stopDb();
    }

    @BeforeMethod(alwaysRun = true)
    public void cleanupData() {
        dbi.inTransaction(new TransactionCallback<Void>() {
            @Override
            public Void inTransaction(Handle h, TransactionStatus status) throws Exception {
                h.execute("truncate table accounts");
                h.execute("truncate table entitlement_events");
                h.execute("truncate table subscriptions");
                h.execute("truncate table bundles");
                h.execute("truncate table notifications");
                h.execute("truncate table claimed_notifications");
                h.execute("truncate table invoices");
                h.execute("truncate table fixed_invoice_items");
                h.execute("truncate table recurring_invoice_items");
                h.execute("truncate table tag_definitions");
                h.execute("truncate table tags");
                h.execute("truncate table custom_fields");
                h.execute("truncate table invoice_payments");
                h.execute("truncate table payment_attempts");
                h.execute("truncate table payments");
                return null;
            }
        });
    }
}
