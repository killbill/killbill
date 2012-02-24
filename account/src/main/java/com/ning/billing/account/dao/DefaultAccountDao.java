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

import java.sql.DataTruncation;
import java.util.List;
import java.util.UUID;

import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;
import com.google.inject.Inject;
import com.ning.billing.ErrorCode;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountChangeNotification;
import com.ning.billing.account.api.AccountCreationNotification;
import com.ning.billing.account.api.user.DefaultAccountChangeNotification;
import com.ning.billing.account.api.user.DefaultAccountCreationEvent;
import com.ning.billing.util.customfield.CustomField;
import com.ning.billing.util.customfield.dao.FieldStoreDao;
import com.ning.billing.util.bus.Bus;
import com.ning.billing.util.tag.Tag;
import com.ning.billing.util.tag.dao.TagStoreSqlDao;

public class DefaultAccountDao implements AccountDao {
    private final AccountSqlDao accountSqlDao;
    private final Bus eventBus;

    @Inject
    public DefaultAccountDao(IDBI dbi, Bus eventBus) {
        this.eventBus = eventBus;
        this.accountSqlDao = dbi.onDemand(AccountSqlDao.class);
    }

    @Override
    public Account getAccountByKey(final String key) {
        return accountSqlDao.inTransaction(new Transaction<Account, AccountSqlDao>() {
            @Override
            public Account inTransaction(final AccountSqlDao accountSqlDao, final TransactionStatus status) throws Exception {
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
    public UUID getIdFromKey(final String externalKey) throws AccountApiException {
        if (externalKey == null) {
            throw new AccountApiException(ErrorCode.ACCOUNT_CANNOT_MAP_NULL_KEY, "");
        }
        return accountSqlDao.getIdFromKey(externalKey);
    }

    @Override
    public Account getById(final String id) {
        Account account = accountSqlDao.getById(id);
        if (account != null) {
            setCustomFieldsFromWithinTransaction(account, accountSqlDao);
            setTagsFromWithinTransaction(account, accountSqlDao);
        }
        return account;
    }


    @Override
    public List<Account> get() {
        return accountSqlDao.get();
    }

    @Override
    public void create(final Account account) throws AccountApiException {
        final String key = account.getExternalKey();
        try {
            accountSqlDao.inTransaction(new Transaction<Void, AccountSqlDao>() {
                @Override
                public Void inTransaction(final AccountSqlDao transactionalDao, final TransactionStatus status) throws AccountApiException, Bus.EventBusException {
                    Account currentAccount = transactionalDao.getAccountByKey(key);
                    if (currentAccount != null) {
                        throw new AccountApiException(ErrorCode.ACCOUNT_ALREADY_EXISTS, key);
                    }
                    transactionalDao.create(account);

                    saveTagsFromWithinTransaction(account, transactionalDao, true);
                    saveCustomFieldsFromWithinTransaction(account, transactionalDao, true);
                    AccountCreationNotification creationEvent = new DefaultAccountCreationEvent(account);
                    eventBus.post(creationEvent);
                    return null;
                }
            });
        } catch (RuntimeException re) {
            if (re.getCause() instanceof AccountApiException) {
                throw (AccountApiException) re.getCause();
            } else if (re.getCause() instanceof DataTruncation) {
                throw new AccountApiException(ErrorCode.DATA_TRUNCATION, re.getCause().getMessage());
            } else {
                throw re;
            }
        }
    }

    @Override
    public void update(final Account account) throws AccountApiException {
        try {
            accountSqlDao.inTransaction(new Transaction<Void, AccountSqlDao>() {
                @Override
                public Void inTransaction(final AccountSqlDao accountSqlDao, final TransactionStatus status) throws AccountApiException, Bus.EventBusException {
                    String accountId = account.getId().toString();
                    Account currentAccount = accountSqlDao.getById(accountId);
                    if (currentAccount == null) {
                        throw new AccountApiException(ErrorCode.ACCOUNT_DOES_NOT_EXIST_FOR_ID, accountId);
                    }

                    String currentKey = currentAccount.getExternalKey();
                    if (!currentKey.equals(account.getExternalKey())) {
                        throw new AccountApiException(ErrorCode.ACCOUNT_CANNOT_CHANGE_EXTERNAL_KEY, currentKey);
                    }

                    accountSqlDao.update(account);

                    saveTagsFromWithinTransaction(account, accountSqlDao, false);
                    saveCustomFieldsFromWithinTransaction(account, accountSqlDao, false);

                    AccountChangeNotification changeEvent = new DefaultAccountChangeNotification(account.getId(), currentAccount, account);
                    if (changeEvent.hasChanges()) {
                        eventBus.post(changeEvent);
                    }
                    return null;
                }
            });
        } catch (RuntimeException re) {
            if (re.getCause() instanceof AccountApiException) {
                throw (AccountApiException) re.getCause();
            } else {
                throw re;
            }
        }
    }

    @Override
	public void deleteByKey(final String externalKey) throws AccountApiException {
    	try {
            accountSqlDao.inTransaction(new Transaction<Void, AccountSqlDao>() {
                @Override
                public Void inTransaction(final AccountSqlDao accountSqlDao, final TransactionStatus status) throws AccountApiException, Bus.EventBusException {

                    accountSqlDao.deleteByKey(externalKey);

                    return null;
                }
            });
        } catch (RuntimeException re) {
            if (re.getCause() instanceof AccountApiException) {
                throw (AccountApiException) re.getCause();
            } else {
                throw re;
            }
        }
	}

    @Override
    public void test() {
        accountSqlDao.test();
    }

    private void setCustomFieldsFromWithinTransaction(final Account account, final AccountSqlDao transactionalDao) {
        FieldStoreDao fieldStoreDao = transactionalDao.become(FieldStoreDao.class);
        List<CustomField> fields = fieldStoreDao.load(account.getId().toString(), account.getObjectName());

        account.clearFields();
        if (fields != null) {
            account.addFields(fields);
        }
    }

    private void setTagsFromWithinTransaction(final Account account, final AccountSqlDao transactionalDao) {
        TagStoreSqlDao tagStoreDao = transactionalDao.become(TagStoreSqlDao.class);
        List<Tag> tags = tagStoreDao.load(account.getId().toString(), account.getObjectName());
        account.clearTags();

        if (tags != null) {
            account.addTags(tags);
        }
    }

    private void saveTagsFromWithinTransaction(final Account account, final AccountSqlDao transactionalDao, final boolean isCreation) {
        String accountId = account.getId().toString();
        String objectType = account.getObjectName();

        TagStoreSqlDao tagStoreDao = transactionalDao.become(TagStoreSqlDao.class);
        if (!isCreation) {
            tagStoreDao.clear(accountId, objectType);
        }

        List<Tag> tagList = account.getTagList();
        if (tagList != null) {
            tagStoreDao.batchSaveFromTransaction(accountId, objectType, tagList);
        }
    }

    private void saveCustomFieldsFromWithinTransaction(final Account account, final AccountSqlDao transactionalDao, final boolean isCreation) {
        String accountId = account.getId().toString();
        String objectType = account.getObjectName();

        FieldStoreDao fieldStoreDao = transactionalDao.become(FieldStoreDao.class);
        if (!isCreation) {
            fieldStoreDao.clear(accountId, objectType);
        }

        List<CustomField> fieldList = account.getFieldList();
        if (fieldList != null) {
            fieldStoreDao.batchSaveFromTransaction(accountId, objectType, fieldList);
        }
    }


}
