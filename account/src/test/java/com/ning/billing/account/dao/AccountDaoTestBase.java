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
import com.ning.billing.util.eventbus.EventBusService;
import com.ning.billing.util.eventbus.IEventBusService;
import org.apache.commons.io.IOUtils;
import org.testng.annotations.BeforeClass;

import java.io.IOException;

import static org.testng.Assert.fail;

public abstract class AccountDaoTestBase {
    protected IFieldStoreDao fieldStoreDao;
    protected IAccountDao accountDao;

    @BeforeClass(alwaysRun = true)
    protected void setup() throws IOException {
        // Healthcheck test to make sure MySQL is setup properly
        try {
            AccountModuleMock module = new AccountModuleMock();
            final String ddl = IOUtils.toString(IAccountDaoSql.class.getResourceAsStream("/com/ning/billing/account/ddl.sql"));
            module.createDb(ddl);

            final Injector injector = Guice.createInjector(Stage.DEVELOPMENT, module);

            fieldStoreDao = injector.getInstance(IFieldStoreDao.class);
            fieldStoreDao.test();

            accountDao = injector.getInstance(IAccountDao.class);
            accountDao.test();

            IEventBusService busService = injector.getInstance(IEventBusService.class);
            ((EventBusService) busService).startBus();
        }
        catch (Throwable t) {
            fail(t.toString());
        }
    }
}
