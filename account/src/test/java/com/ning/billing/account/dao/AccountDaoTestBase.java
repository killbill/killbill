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

import org.apache.commons.io.IOUtils;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.TransactionCallback;
import org.skife.jdbi.v2.TransactionStatus;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

import com.ning.billing.dbi.MysqlTestingHelper;
import com.ning.billing.util.bus.DefaultBusService;
import com.ning.billing.util.bus.BusService;
import com.ning.billing.util.bus.Bus;
import com.ning.billing.util.bus.InMemoryBus;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.UserType;
import com.ning.billing.util.callcontext.DefaultCallContextFactory;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.ClockMock;

public abstract class AccountDaoTestBase {
    private final MysqlTestingHelper helper = new MysqlTestingHelper();

    protected AccountDao accountDao;
    protected AccountEmailDao accountEmailDao;
    protected IDBI dbi;

    protected CallContext context;

    @BeforeClass(alwaysRun = true)
    protected void setup() throws IOException {
        // Health check test to make sure MySQL is setup properly
        try {
            final String accountDdl = IOUtils.toString(AccountSqlDao.class.getResourceAsStream("/com/ning/billing/account/ddl.sql"));
            final String utilDdl = IOUtils.toString(AccountSqlDao.class.getResourceAsStream("/com/ning/billing/util/ddl.sql"));

            helper.startMysql();
            helper.initDb(accountDdl);
            helper.initDb(utilDdl);

            dbi = helper.getDBI();

            Bus bus = new InMemoryBus();
            BusService busService = new DefaultBusService(bus);
            ((DefaultBusService) busService).startBus();

            accountDao = new AuditedAccountDao(dbi, bus);
            accountDao.test();

            accountEmailDao = new AuditedAccountEmailDao(dbi);
            accountEmailDao.test();

            Clock clock = new ClockMock();
            context = new DefaultCallContextFactory(clock).createCallContext("Account Dao Tests", CallOrigin.TEST, UserType.TEST);
        }
        catch (Throwable t) {
            fail(t.toString());
        }
    }

    @AfterClass(alwaysRun = true)
    public void stopMysql()
    {
        helper.stopMysql();
    }

    @BeforeMethod(alwaysRun = true)
    public void cleanupData() {
        dbi.inTransaction(new TransactionCallback<Void>() {
            @Override
            public Void inTransaction(Handle h, TransactionStatus status) throws Exception {
                h.execute("truncate table accounts");
                h.execute("truncate table notifications");
                h.execute("truncate table bus_events");
                h.execute("truncate table claimed_bus_events");                              
                h.execute("truncate table claimed_notifications");
                h.execute("truncate table tag_definitions");
                h.execute("truncate table tags");
                h.execute("truncate table custom_fields");
                return null;
            }
        });
    }
}
