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

import java.util.List;
import java.util.UUID;

import org.skife.jdbi.v2.IDBI;

import com.google.inject.Inject;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.IAccount;
import com.ning.billing.account.api.IAccountData;
import com.ning.billing.account.api.ICustomField;

public class AccountDao implements IAccountDao {

    private final IAccountDaoSql dao;

    @Inject
    public AccountDao(IDBI dbi) {
        this.dao = dbi.onDemand(IAccountDaoSql.class);
    }

    @Override
    public IAccount createAccount(IAccountData input) {
        IAccount result = new Account().withKey(input.getKey());
        dao.insertAccount(result);
        return result;
    }

    @Override
    public IAccount getAccountByKey(String key) {
        IAccount account = dao.getAccountByKey(key);
        if (account != null) {
            loadFields(account);
        }
        return account;
    }

    @Override
    public IAccount getAccountById(UUID uid) {
        IAccount account = dao.getAccountFromId(uid.toString());
        if (account != null) {
            loadFields(account);
        }
        return account;
    }

    private void loadFields(IAccount account) {
        List<ICustomField> fields = dao.getFields(account.getId().toString(), Account.OBJECT_TYPE);
        account.getFields().clear();
        if (fields != null) {
            for (ICustomField field : fields) {
                account.getFields().setValue(field.getName(), field.getValue());
            }
        }
    }

    @Override
    public List<IAccount> getAccounts() {
        return dao.getAccounts();
    }

    @Override
    public void test() {
        dao.test();
    }

    @Override
    public void save(IAccount account) {
        final String objectId = account.getId().toString();
        final String objectType = Account.OBJECT_TYPE;

        dao.begin();
        try {
            dao.insertAccount(account);
            List<ICustomField> newFields = account.getFields().getNewFields();
            dao.createFields(objectId, objectType, newFields);
            for (ICustomField field : newFields) {
                field.setAsSaved();
            }

            dao.saveFields(objectId, objectType, account.getFields().getUpdatedFields());
            dao.commit();
        }
        catch (RuntimeException ex) {
            dao.rollback();
            throw ex;
        }
    }
}
