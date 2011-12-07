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

import com.google.inject.Inject;
import com.ning.billing.account.api.*;
import com.ning.billing.account.api.user.AccountChangeEventDefault;
import com.ning.billing.account.api.user.AccountCreationEventDefault;
import com.ning.billing.util.eventbus.EventBus;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.TransactionCallback;
import org.skife.jdbi.v2.TransactionStatus;

import java.util.List;

public class AccountDaoWrapper implements AccountDao {
    private final AccountDao accountDao;
    private final FieldStoreDao fieldStoreDao;
    private final IDBI dbi; // needed for transaction support
    private final EventBus eventBus;

    @Inject
    public AccountDaoWrapper(IDBI dbi, EventBus eventBus) {
        this.dbi = dbi;
        this.eventBus = eventBus;
        this.accountDao = dbi.onDemand(AccountDao.class);
        this.fieldStoreDao = dbi.onDemand(FieldStoreDao.class);
    }

    @Override
    public Account getAccountByKey(String key) {
        Account account = accountDao.getAccountByKey(key);
        if (account != null) {
            loadFields(account);
        }
        return account;
    }

    @Override
    public Account getById(String id) {
        Account account = accountDao.getById(id);
        if (account != null) {
            loadFields(account);
        }
        return account;
    }

    private void loadFields(Account account) {
        List<CustomField> fields = fieldStoreDao.load(account.getId().toString(), DefaultAccount.OBJECT_TYPE);
        account.getFields().clear();
        if (fields != null) {
            for (CustomField field : fields) {
                account.getFields().setValue(field.getName(), field.getValue());
            }
        }
    }

    @Override
    public List<Account> get() {
        return accountDao.get();
    }

    @Override
    public void test() {
        accountDao.test();
    }

    @Override
    public void save(final Account account) {
        final String accountId = account.getId().toString();
        final String objectType = DefaultAccount.OBJECT_TYPE;

        dbi.inTransaction(new TransactionCallback<Void>() {
            @Override
            public Void inTransaction(Handle conn, TransactionStatus status) throws Exception {
                try {
                    conn.begin();

                    AccountDao accountDao = conn.attach(AccountDao.class);
                    Account currentAccount = accountDao.getById(accountId);
                    accountDao.save(account);

                    FieldStore fieldStore = account.getFields();
                    FieldStoreDao fieldStoreDao = conn.attach(FieldStoreDao.class);
                    fieldStoreDao.save(accountId, objectType, fieldStore.getFieldList());

                    if (currentAccount == null) {
                        AccountCreationNotification creationEvent = new AccountCreationEventDefault(account);
                        eventBus.post(creationEvent);
                    } else {
                        AccountChangeNotification changeEvent = new AccountChangeEventDefault(account.getId(), currentAccount, account);
                        if (changeEvent.hasChanges()) {
                            eventBus.post(changeEvent);
                        }
                    }

                    conn.commit();
                } catch (Exception e) {
                    conn.rollback();
                    throw e;
                }

                return null;
            }
        });
    }
}
