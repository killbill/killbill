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

import com.ning.billing.util.ChangeType;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.customfield.dao.CustomFieldDao;
import com.ning.billing.util.dao.EntityAudit;
import com.ning.billing.util.dao.EntityHistory;
import com.ning.billing.util.dao.ObjectType;
import com.ning.billing.util.dao.TableName;
import com.ning.billing.util.entity.EntityPersistenceException;
import com.ning.billing.util.tag.dao.TagDao;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.ning.billing.ErrorCode;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountChangeEvent;
import com.ning.billing.account.api.AccountCreationEvent;
import com.ning.billing.account.api.user.DefaultAccountChangeEvent;
import com.ning.billing.account.api.user.DefaultAccountCreationEvent;
import com.ning.billing.util.customfield.CustomField;
import com.ning.billing.util.customfield.dao.CustomFieldSqlDao;
import com.ning.billing.util.bus.Bus;
import com.ning.billing.util.bus.Bus.EventBusException;
import com.ning.billing.util.tag.Tag;

public class AuditedAccountDao implements AccountDao {
    private final static Logger log = LoggerFactory.getLogger(AuditedAccountDao.class);
    
    private final AccountSqlDao accountSqlDao;
    private final TagDao tagDao;
    private final CustomFieldDao customFieldDao;
    private final Bus eventBus;

    @Inject
    public AuditedAccountDao(IDBI dbi, Bus eventBus, TagDao tagDao, CustomFieldDao customFieldDao) {
        this.eventBus = eventBus;
        this.accountSqlDao = dbi.onDemand(AccountSqlDao.class);
        this.tagDao = tagDao;
        this.customFieldDao = customFieldDao;
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
    public Account getById(final UUID id) {
        return accountSqlDao.inTransaction(new Transaction<Account, AccountSqlDao>() {
            @Override
            public Account inTransaction(final AccountSqlDao accountSqlDao, final TransactionStatus status) throws Exception {
                Account account = accountSqlDao.getById(id.toString());
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
        return accountSqlDao.inTransaction(new Transaction<List<Account>, AccountSqlDao>() {
            @Override
            public List<Account> inTransaction(final AccountSqlDao accountSqlDao, final TransactionStatus status) throws Exception {
                List<Account> accounts = accountSqlDao.get();
                for (Account account : accounts) {
                    setCustomFieldsFromWithinTransaction(account, accountSqlDao);
                    setTagsFromWithinTransaction(account, accountSqlDao);
                }

                return accounts;
            }
        });
    }

    @Override
    public void create(final Account account, final CallContext context) throws EntityPersistenceException {
        final String key = account.getExternalKey();
        try {
            accountSqlDao.inTransaction(new Transaction<Void, AccountSqlDao>() {
                @Override
                public Void inTransaction(final AccountSqlDao transactionalDao, final TransactionStatus status) throws AccountApiException, Bus.EventBusException {
                    Account currentAccount = transactionalDao.getAccountByKey(key);
                    if (currentAccount != null) {
                        throw new AccountApiException(ErrorCode.ACCOUNT_ALREADY_EXISTS, key);
                    }
                    transactionalDao.create(account, context);

                    // insert history
                    Long recordId = accountSqlDao.getRecordId(account.getId().toString());
                    EntityHistory<Account> history = new EntityHistory<Account>(account.getId(), recordId, account, ChangeType.INSERT);
                    accountSqlDao.insertHistoryFromTransaction(history, context);

                    // insert audit
                    Long historyRecordId = accountSqlDao.getHistoryRecordId(recordId);
                    EntityAudit audit = new EntityAudit(TableName.ACCOUNT_HISTORY, historyRecordId, ChangeType.INSERT);
                    accountSqlDao.insertAuditFromTransaction(audit, context);

                    saveTagsFromWithinTransaction(account, transactionalDao, context);
                    saveCustomFieldsFromWithinTransaction(account, transactionalDao, context);
                    AccountCreationEvent creationEvent = new DefaultAccountCreationEvent(account, context.getUserToken());
                    try {
                        eventBus.postFromTransaction(creationEvent, transactionalDao);
                    } catch (EventBusException e) {
                        log.warn("Failed to post account creation event for account " + account.getId(), e);
                    }
                    return null;
                }
            });
        } catch (RuntimeException re) {
            if (re.getCause() instanceof EntityPersistenceException) {
                throw (EntityPersistenceException) re.getCause();
            } else if (re.getCause() instanceof DataTruncation) {
                throw new EntityPersistenceException(ErrorCode.DATA_TRUNCATION, re.getCause().getMessage());
            } else {
                throw re;
            }
        }
    }

    @Override
    public void update(final Account account, final CallContext context) throws EntityPersistenceException {
        try {
            accountSqlDao.inTransaction(new Transaction<Void, AccountSqlDao>() {
                @Override
                public Void inTransaction(final AccountSqlDao transactional, final TransactionStatus status) throws EntityPersistenceException, Bus.EventBusException {
                    String accountId = account.getId().toString();
                    Account currentAccount = transactional.getById(accountId);
                    if (currentAccount == null) {
                        throw new EntityPersistenceException(ErrorCode.ACCOUNT_DOES_NOT_EXIST_FOR_ID, accountId);
                    }

                    String currentKey = currentAccount.getExternalKey();
                    if (!currentKey.equals(account.getExternalKey())) {
                        throw new EntityPersistenceException(ErrorCode.ACCOUNT_CANNOT_CHANGE_EXTERNAL_KEY, currentKey);
                    }

                    transactional.update(account, context);

                    Long recordId = accountSqlDao.getRecordId(account.getId().toString());
                    EntityHistory<Account> history = new EntityHistory<Account>(account.getId(), recordId, account, ChangeType.INSERT);
                    accountSqlDao.insertHistoryFromTransaction(history, context);

                    Long historyRecordId = accountSqlDao.getHistoryRecordId(recordId);
                    EntityAudit audit = new EntityAudit(TableName.ACCOUNT_HISTORY, historyRecordId, ChangeType.INSERT);
                    accountSqlDao.insertAuditFromTransaction(audit, context);

                    saveTagsFromWithinTransaction(account, transactional, context);
                    saveCustomFieldsFromWithinTransaction(account, transactional, context);

                    AccountChangeEvent changeEvent = new DefaultAccountChangeEvent(account.getId(), context.getUserToken(), currentAccount, account);
                    if (changeEvent.hasChanges()) {
                        try {
                            eventBus.postFromTransaction(changeEvent, transactional);
                        } catch (EventBusException e) {
                            log.warn("Failed to post account change event for account " + account.getId(), e);
                        }
                    }
                    return null;
                }
            });
        } catch (RuntimeException re) {
            if (re.getCause() instanceof EntityPersistenceException) {
                throw (EntityPersistenceException) re.getCause();
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
        CustomFieldSqlDao customFieldSqlDao = transactionalDao.become(CustomFieldSqlDao.class);
        List<CustomField> fields = customFieldSqlDao.load(account.getId().toString(), account.getObjectType());

        account.clearFields();
        if (fields != null) {
            account.setFields(fields);
        }
    }

    private void setTagsFromWithinTransaction(final Account account, final AccountSqlDao transactionalDao) {
        List<Tag> tags = tagDao.loadEntitiesFromTransaction(transactionalDao, account.getId(), ObjectType.ACCOUNT);
        account.clearTags();

        if (tags != null) {
            account.addTags(tags);
        }
    }

    private void saveTagsFromWithinTransaction(final Account account, final AccountSqlDao transactionalDao,
                                               final CallContext context) {
        tagDao.saveEntitiesFromTransaction(transactionalDao, account.getId(), account.getObjectType(), account.getTagList(), context);
    }

    private void saveCustomFieldsFromWithinTransaction(final Account account, final AccountSqlDao transactionalDao,
                                                       final CallContext context) {
        customFieldDao.saveEntitiesFromTransaction(transactionalDao, account.getId(), account.getObjectType(), account.getFieldList(), context);
    }
}
