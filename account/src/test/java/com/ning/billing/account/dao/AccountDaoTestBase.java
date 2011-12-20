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

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import com.ning.billing.account.glue.AccountModuleMock;
import com.ning.billing.util.eventbus.DefaultEventBusService;
import com.ning.billing.util.eventbus.EventBusService;
import org.apache.commons.io.IOUtils;
import org.skife.jdbi.v2.IDBI;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;

import java.io.IOException;

import static org.testng.Assert.fail;

public abstract class AccountDaoTestBase {
    protected AccountModuleMock module;
    protected AccountDao accountDao;
    protected IDBI dbi;

    @BeforeClass(alwaysRun = true)
    protected void setup() throws IOException {
        // Healthcheck test to make sure MySQL is setup properly
        try {
            module = new AccountModuleMock();
            final String accountDdl = IOUtils.toString(AccountSqlDao.class.getResourceAsStream("/com/ning/billing/account/ddl.sql"));
            final String invoiceDdl = IOUtils.toString(AccountSqlDao.class.getResourceAsStream("/com/ning/billing/invoice/ddl.sql"));
            module.startDb();
            module.initDb(accountDdl);
            module.initDb(invoiceDdl);

            final Injector injector = Guice.createInjector(Stage.DEVELOPMENT, module);
            dbi = injector.getInstance(IDBI.class);

            accountDao = injector.getInstance(AccountDao.class);
            accountDao.test();

            EventBusService busService = injector.getInstance(EventBusService.class);
            ((DefaultEventBusService) busService).startBus();
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
}
