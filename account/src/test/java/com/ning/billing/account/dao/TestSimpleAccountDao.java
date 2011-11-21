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
import com.ning.billing.catalog.api.Currency;
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
    //private InjectorMagic injectorMagic;

    @BeforeClass(alwaysRun = true)
    private void setup() throws IOException {
        AccountModuleMock module = new AccountModuleMock();
        final String ddl = IOUtils.toString(IAccountDaoSql.class.getResourceAsStream("/com/ning/billing/account/ddl.sql"));
        module.createDb(ddl);

        // Healthcheck test to make sure MySQL is setup properly
        try {
            final Injector injector = Guice.createInjector(Stage.DEVELOPMENT, module);

            InjectorMagic injectorMagic = injector.getInstance(InjectorMagic.class);
            dao = injector.getInstance(IAccountDao.class);
            dao.test();
        }
        catch (Throwable t) {
            fail(t.toString());
        }
    }

    private final String key = "test1234";
    private final String name = "Wesley";
    private final String email = "dreadpirateroberts@therevenge.com";

    private Account createTestAccount() {
        Account account = Account.create();
        String thisKey = key + UUID.randomUUID().toString();
        String thisName = name + UUID.randomUUID().toString();
        account.externalKey(thisKey).name(thisName).email(email).currency(Currency.USD);
        return account;
    }

    @Test(enabled=true, groups={"Account-DAO"})
    public void testBasic() {

        IAccount a = createTestAccount();
        dao.saveAccount(a);
        String key = a.getExternalKey();

        IAccount r = dao.getAccountByKey(key);
        assertNotNull(r);
        assertEquals(r.getExternalKey(), a.getExternalKey());

        r = dao.getAccountById(r.getId());
        assertNotNull(r);
        assertEquals(r.getExternalKey(), a.getExternalKey());

        List<IAccount> all = dao.getAccounts();
        assertNotNull(all);
        assertTrue(all.size() >= 1);
    }

    @Test
    public void testGetById() {
        Account account = createTestAccount();
        UUID id = account.getId();
        String key = account.getExternalKey();
        String name = account.getName();
        account.save();

        account = Account.loadAccount(id);
        assertNotNull(account);
        assertEquals(account.getId(), id);
        assertEquals(account.getExternalKey(), key);
        assertEquals(account.getName(), name);

    }

    @Test
    public void testCustomFields() {
        Account account = createTestAccount();
        String fieldName = "testField1";
        String fieldValue = "testField1_value";
        account.setFieldValue(fieldName, fieldValue);

        account.save();

        Account thisAccount = Account.loadAccount(account.getExternalKey());
        assertNotNull(thisAccount);
        assertEquals(thisAccount.getExternalKey(), account.getExternalKey());
        assertEquals(thisAccount.getFieldValue(fieldName), fieldValue);
    }


}
