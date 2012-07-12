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
import com.ning.billing.util.ChangeType;
import com.ning.billing.util.bus.Bus;
import com.ning.billing.util.bus.Bus.EventBusException;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.dao.EntityAudit;
import com.ning.billing.util.dao.EntityHistory;
import com.ning.billing.util.dao.TableName;
import com.ning.billing.util.entity.EntityPersistenceException;

public class AuditedAccountDao implements AccountDao {
    private static final Logger log = LoggerFactory.getLogger(AuditedAccountDao.class);

    private final AccountSqlDao accountSqlDao;
    private final Bus eventBus;

    @Inject
    public AuditedAccountDao(final IDBI dbi, final Bus eventBus) {
        this.eventBus = eventBus;
        this.accountSqlDao = dbi.onDemand(AccountSqlDao.class);
    }

    @Override
    public Account getAccountByKey(final String key) {
        return accountSqlDao.getAccountByKey(key);
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
        return accountSqlDao.getById(id.toString());
    }

    @Override
    public List<Account> get() {
        return accountSqlDao.get();
    }

    @Override
    public void create(final Account account, final CallContext context) throws EntityPersistenceException {
        final String key = account.getExternalKey();
        try {
            accountSqlDao.inTransaction(new Transaction<Void, AccountSqlDao>() {
                @Override
                public Void inTransaction(final AccountSqlDao transactionalDao, final TransactionStatus status) throws AccountApiException, Bus.EventBusException {
                    final Account currentAccount = transactionalDao.getAccountByKey(key);
                    if (currentAccount != null) {
                        throw new AccountApiException(ErrorCode.ACCOUNT_ALREADY_EXISTS, key);
                    }

                    transactionalDao.create(account, context);

                    // Insert history
                    final Long recordId = accountSqlDao.getRecordId(account.getId().toString());
                    final EntityHistory<Account> history = new EntityHistory<Account>(account.getId(), recordId, account, ChangeType.INSERT);
                    accountSqlDao.insertHistoryFromTransaction(history, context);

                    // Insert audit
                    final Long historyRecordId = accountSqlDao.getHistoryRecordId(recordId);
                    final EntityAudit audit = new EntityAudit(TableName.ACCOUNT_HISTORY, historyRecordId, ChangeType.INSERT);
                    accountSqlDao.insertAuditFromTransaction(audit, context);

                    final AccountCreationEvent creationEvent = new DefaultAccountCreationEvent(account, context.getUserToken());
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
    public void update(final Account specifiedAccount, final CallContext context) throws EntityPersistenceException {
        try {
            accountSqlDao.inTransaction(new Transaction<Void, AccountSqlDao>() {
                @Override
                public Void inTransaction(final AccountSqlDao transactional, final TransactionStatus status) throws EntityPersistenceException, Bus.EventBusException {
                    final UUID accountId = specifiedAccount.getId();
                    final Account currentAccount = transactional.getById(accountId.toString());
                    if (currentAccount == null) {
                        throw new EntityPersistenceException(ErrorCode.ACCOUNT_DOES_NOT_EXIST_FOR_ID, accountId);
                    }

                    // Set unspecified (null) fields to their current values
                    final Account account = specifiedAccount.mergeWithDelegate(currentAccount);

                    transactional.update(account, context);

                    final Long recordId = accountSqlDao.getRecordId(accountId.toString());
                    final EntityHistory<Account> history = new EntityHistory<Account>(accountId, recordId, account, ChangeType.UPDATE);
                    accountSqlDao.insertHistoryFromTransaction(history, context);

                    final Long historyRecordId = accountSqlDao.getHistoryRecordId(recordId);
                    final EntityAudit audit = new EntityAudit(TableName.ACCOUNT_HISTORY, historyRecordId, ChangeType.UPDATE);
                    accountSqlDao.insertAuditFromTransaction(audit, context);

                    final AccountChangeEvent changeEvent = new DefaultAccountChangeEvent(accountId, context.getUserToken(), currentAccount, account);
                    if (changeEvent.hasChanges()) {
                        try {
                            eventBus.postFromTransaction(changeEvent, transactional);
                        } catch (EventBusException e) {
                            log.warn("Failed to post account change event for account " + accountId, e);
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
}
