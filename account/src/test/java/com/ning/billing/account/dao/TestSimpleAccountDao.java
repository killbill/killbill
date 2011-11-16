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
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.IAccount;
import com.ning.billing.account.glue.AccountModuleMock;
import com.ning.billing.account.glue.InjectorMagic;
import org.apache.commons.io.IOUtils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.testng.Assert.*;

@Test(groups = {"Account", "Account-DAO"})
public class TestSimpleAccountDao {
    private IAccountDao dao;
    private InjectorMagic injectorMagic;

    @BeforeClass(alwaysRun = true)
    private void setup() throws IOException {
        AccountModuleMock module = new AccountModuleMock();
        final String ddl = IOUtils.toString(IAccountDaoSql.class.getResourceAsStream("/com/ning/billing/account/ddl.sql"));
        module.createDb(ddl);

        // Healthcheck test to make sure MySQL is setup properly
        try {
            final Injector injector = Guice.createInjector(Stage.DEVELOPMENT, module);

            injectorMagic = injector.getInstance(InjectorMagic.class);
            dao = injector.getInstance(IAccountDao.class);
            dao.test();
        }
        catch (Throwable t) {
            fail(t.toString());
        }
    }

    @Test(enabled=true, groups={"Account-DAO"})
    public void testBasic() {

        IAccount a = new Account().withKey("foo");
        dao.createAccount(a);

        IAccount r = dao.getAccountByKey("foo");
        assertNotNull(r);
        assertEquals(r.getKey(), a.getKey());

        r = dao.getAccountById(r.getId());
        assertNotNull(r);
        assertEquals(r.getKey(), a.getKey());

        List<IAccount> all = dao.getAccounts();
        assertNotNull(all);
        assertTrue(all.size() >= 1);
    }

    @Test
    public void testGetById() {
        String key = "test1234";

        IAccount account = Account.create().withKey(key);
        UUID id = account.getId();

        account.save();

        account = Account.loadAccount(id);
        assertNotNull(account);
        assertEquals(account.getId(), id);
        assertEquals(account.getKey(), key);
    }

    @Test
    public void testCustomFields() {
        String key = "test45678";
        IAccount account = Account.create().withKey(key);

        String fieldName = "testField1";
        String fieldValue = "testField1_value";
        account.setFieldValue(fieldName, fieldValue);

        account.save();

        account = Account.loadAccount(key);
        assertNotNull(account);
        assertEquals(account.getKey(), key);
        assertEquals(account.getFieldValue(fieldName), fieldValue);
    }
}
