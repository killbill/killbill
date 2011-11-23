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

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.IAccount;
import com.ning.billing.catalog.api.Currency;
import org.testng.annotations.Test;

import java.util.List;
import java.util.UUID;

import static org.testng.Assert.*;

@Test(groups = {"account-dao"})
public class TestSimpleAccountDao extends AccountDaoTestBase {
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

    public void testBasic() {

        IAccount a = createTestAccount();
        accountDao.saveAccount(a);
        String key = a.getExternalKey();

        IAccount r = accountDao.getAccountByKey(key);
        assertNotNull(r);
        assertEquals(r.getExternalKey(), a.getExternalKey());

        r = accountDao.getAccountById(r.getId());
        assertNotNull(r);
        assertEquals(r.getExternalKey(), a.getExternalKey());

        List<IAccount> all = accountDao.getAccounts();
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
