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
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;
import com.google.inject.Inject;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountChangeNotification;
import com.ning.billing.account.api.AccountCreationNotification;
import com.ning.billing.account.api.DefaultAccount;
import com.ning.billing.account.api.user.DefaultAccountChangeNotification;
import com.ning.billing.account.api.user.DefaultAccountCreationEvent;
import com.ning.billing.util.customfield.CustomField;
import com.ning.billing.util.customfield.dao.FieldStoreDao;
import com.ning.billing.util.eventbus.EventBus;
import com.ning.billing.util.tag.Tag;
import com.ning.billing.util.tag.dao.TagStoreDao;

public class DefaultAccountDao implements AccountDao {
    private final AccountSqlDao accountDao;
    private final EventBus eventBus;

    @Inject
    public DefaultAccountDao(IDBI dbi, EventBus eventBus) {
        this.eventBus = eventBus;
        this.accountDao = dbi.onDemand(AccountSqlDao.class);
    }

    @Override
    public Account getAccountByKey(final String key) {
        return accountDao.inTransaction(new Transaction<Account, AccountSqlDao>() {
            @Override
            public Account inTransaction(AccountSqlDao accountSqlDao, TransactionStatus status) throws Exception {
                Account account = accountSqlDao.getAccountByKey(key);
                if (account != null) {
                    setCustomFieldsFromWithinTransaction(account, accountSqlDao);
                    setTagsFromWithinTransaction(account, accountSqlDao);
                }
                return account;
            }
        });
    }

    @Override
    public UUID getIdFromKey(final String externalKey) {
        return accountDao.getIdFromKey(externalKey);
    }

    @Override
    public Account getById(final String id) {
        return accountDao.inTransaction(new Transaction<Account, AccountSqlDao>() {
            @Override
            public Account inTransaction(AccountSqlDao accountSqlDao, TransactionStatus status) throws Exception {
                Account account = accountSqlDao.getById(id);
                if (account != null) {
                    setCustomFieldsFromWithinTransaction(account, accountSqlDao);
                    setTagsFromWithinTransaction(account, accountSqlDao);
                }
                return account;
            }
        });
    }


    @Override
    public List<Account> get() {
        return accountDao.get();
    }

    @Override
    public void create(final Account account) {
        final String accountId = account.getId().toString();
        final String objectType = DefaultAccount.OBJECT_TYPE;

        accountDao.inTransaction(new Transaction<Void, AccountSqlDao>() {
            @Override
            public Void inTransaction(AccountSqlDao accountSqlDao, TransactionStatus status) throws Exception {
                accountSqlDao.create(account);

                FieldStoreDao fieldStoreDao = accountSqlDao.become(FieldStoreDao.class);
                fieldStoreDao.save(accountId, objectType, account.getFieldList());

                TagStoreDao tagStoreDao = accountSqlDao.become(TagStoreDao.class);
                tagStoreDao.save(accountId, objectType, account.getTagList());

                AccountCreationNotification creationEvent = new DefaultAccountCreationEvent(account);
                eventBus.post(creationEvent);
                return null;
            }
        });
    }

    @Override
    public void update(final Account account) {
        final String accountId = account.getId().toString();
        final String objectType = DefaultAccount.OBJECT_TYPE;

        accountDao.inTransaction(new Transaction<Void, AccountSqlDao>() {
            @Override
            public Void inTransaction(AccountSqlDao accountSqlDao, TransactionStatus status) throws Exception {
                Account currentAccount = accountSqlDao.getById(accountId);

                accountSqlDao.update(account);

                FieldStoreDao fieldStoreDao = accountSqlDao.become(FieldStoreDao.class);
                fieldStoreDao.clear(accountId, objectType);
                fieldStoreDao.save(accountId, objectType, account.getFieldList());

                TagStoreDao tagStoreDao = fieldStoreDao.become(TagStoreDao.class);
                tagStoreDao.clear(accountId, objectType);
                tagStoreDao.save(accountId, objectType, account.getTagList());

                AccountChangeNotification changeEvent = new DefaultAccountChangeNotification(account.getId(), currentAccount, account);
                if (changeEvent.hasChanges()) {
                    eventBus.post(changeEvent);
                }
                return null;
            }
        });
    }

    @Override
    public void test() {
        accountDao.test();
    }

    private void setCustomFieldsFromWithinTransaction(final Account account, final AccountSqlDao transactionalDao) {
        FieldStoreDao fieldStoreDao = transactionalDao.become(FieldStoreDao.class);
        List<CustomField> fields = fieldStoreDao.load(account.getId().toString(), account.getObjectName());

        account.clearFields();
        if (fields != null) {
            for (CustomField field : fields) {
                account.setFieldValue(field.getName(), field.getValue());
            }
        }
    }

    private void setTagsFromWithinTransaction(final Account account, final AccountSqlDao transactionalDao) {
        TagStoreDao tagStoreDao = transactionalDao.become(TagStoreDao.class);
        List<Tag> tags = tagStoreDao.load(account.getId().toString(), account.getObjectName());
        account.clearTags();

        if (tags != null) {
            account.addTags(tags);
        }
    }
}
