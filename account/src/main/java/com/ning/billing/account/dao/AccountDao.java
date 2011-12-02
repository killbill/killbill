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
import com.ning.billing.account.api.user.AccountChangeEvent;
import com.ning.billing.account.api.user.AccountCreationEvent;
import com.ning.billing.util.eventbus.IEventBus;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.TransactionCallback;
import org.skife.jdbi.v2.TransactionStatus;

import java.util.List;

public class AccountDao implements IAccountDao {
    private final IAccountDao accountDao;
    private final IFieldStoreDao fieldStoreDao;
    private final IDBI dbi; // needed for transaction support
    private final IEventBus eventBus;

    @Inject
    public AccountDao(IDBI dbi, IEventBus eventBus) {
        this.dbi = dbi;
        this.eventBus = eventBus;
        this.accountDao = dbi.onDemand(IAccountDao.class);
        this.fieldStoreDao = dbi.onDemand(IFieldStoreDao.class);
    }

    @Override
    public IAccount getAccountByKey(String key) {
        IAccount account = accountDao.getAccountByKey(key);
        if (account != null) {
            loadFields(account);
        }
        return account;
    }

    @Override
    public IAccount getById(String id) {
        IAccount account = accountDao.getById(id);
        if (account != null) {
            loadFields(account);
        }
        return account;
    }

    private void loadFields(IAccount account) {
        List<ICustomField> fields = fieldStoreDao.load(account.getId().toString(), Account.OBJECT_TYPE);
        account.getFields().clear();
        if (fields != null) {
            for (ICustomField field : fields) {
                account.getFields().setValue(field.getName(), field.getValue());
            }
        }
    }

    @Override
    public List<IAccount> get() {
        return accountDao.get();
    }

    @Override
    public void test() {
        accountDao.test();
    }

    @Override
    public void save(final IAccount account) {
        final String accountId = account.getId().toString();
        final String objectType = Account.OBJECT_TYPE;

        dbi.inTransaction(new TransactionCallback<Void>() {
            @Override
            public Void inTransaction(Handle conn, TransactionStatus status) throws Exception {
                try {
                    conn.begin();

                    IAccountDao accountDao = conn.attach(IAccountDao.class);
                    IAccount currentAccount = accountDao.getById(accountId);
                    accountDao.save(account);

                    IFieldStore fieldStore = account.getFields();
                    IFieldStoreDao fieldStoreDao = conn.attach(IFieldStoreDao.class);
                    fieldStoreDao.save(accountId, objectType, fieldStore.getFieldList());

                    if (currentAccount == null) {
                        IAccountCreationEvent creationEvent = new AccountCreationEvent(account);
                        eventBus.post(creationEvent);
                    } else {
                        IAccountChangeEvent changeEvent = new AccountChangeEvent(account.getId(), currentAccount, account);
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
//
//
//        //accountDao.begin();
//        try {
//            accountDao.save(account);
//
//            IFieldStore fieldStore = account.getFields();
//            fieldStoreDao.save(objectId, objectType, fieldStore.getFieldList());
//            fieldStore.processSaveEvent();
//
//            account.processSaveEvent();
//            //accountDao.commit();
//        }
//        catch (RuntimeException ex) {
//            //accountDao.rollback();
//            throw ex;
//        }
    }
}
