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

import com.ning.billing.ErrorCode;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.user.DefaultAccountChangeEvent;
import com.ning.billing.account.api.user.DefaultAccountCreationEvent;
import com.ning.billing.util.ChangeType;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.dao.EntityAudit;
import com.ning.billing.util.dao.EntityHistory;
import com.ning.billing.util.dao.TableName;
import com.ning.billing.util.entity.EntityPersistenceException;
import com.ning.billing.util.events.AccountChangeInternalEvent;
import com.ning.billing.util.events.AccountCreationInternalEvent;
import com.ning.billing.util.svcsapi.bus.Bus;
import com.ning.billing.util.svcsapi.bus.Bus.EventBusException;

import com.google.inject.Inject;

public class AuditedAccountDao implements AccountDao {

    private static final Logger log = LoggerFactory.getLogger(AuditedAccountDao.class);

    private final AccountSqlDao accountSqlDao;
    private final Bus eventBus;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public AuditedAccountDao(final IDBI dbi, final Bus eventBus, final InternalCallContextFactory internalCallContextFactory) {
        this.eventBus = eventBus;
        this.accountSqlDao = dbi.onDemand(AccountSqlDao.class);
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Override
    public Account getAccountByKey(final String key, final InternalTenantContext context) {
        return accountSqlDao.getAccountByKey(key, context);
    }

    @Override
    public UUID getIdFromKey(final String externalKey, final InternalTenantContext context) throws AccountApiException {
        if (externalKey == null) {
            throw new AccountApiException(ErrorCode.ACCOUNT_CANNOT_MAP_NULL_KEY, "");
        }
        return accountSqlDao.getIdFromKey(externalKey, context);
    }

    @Override
    public Account getById(final UUID id, final InternalTenantContext context) {
        return accountSqlDao.getById(id.toString(), context);
    }

    @Override
    public List<Account> get(final InternalTenantContext context) {
        return accountSqlDao.get(context);
    }

    @Override
    public Long getRecordId(final UUID id, final InternalTenantContext context) {
        return accountSqlDao.getRecordId(id.toString(), context);
    }

    @Override
    public void create(final Account account, final InternalCallContext context) throws EntityPersistenceException {
        final String key = account.getExternalKey();
        try {
            accountSqlDao.inTransaction(new Transaction<Void, AccountSqlDao>() {
                @Override
                public Void inTransaction(final AccountSqlDao transactionalDao, final TransactionStatus status) throws AccountApiException, Bus.EventBusException {
                    final Account currentAccount = transactionalDao.getAccountByKey(key, context);
                    if (currentAccount != null) {
                        throw new AccountApiException(ErrorCode.ACCOUNT_ALREADY_EXISTS, key);
                    }

                    transactionalDao.create(account, context);

                    final Long recordId = accountSqlDao.getRecordId(account.getId().toString(), context);
                    // We need to re-hydrate the context with the account record id
                    final InternalCallContext rehydratedContext = internalCallContextFactory.createInternalCallContext(recordId, context);

                    // Insert history
                    final EntityHistory<Account> history = new EntityHistory<Account>(account.getId(), recordId, account, ChangeType.INSERT);
                    accountSqlDao.insertHistoryFromTransaction(history, rehydratedContext);

                    // Insert audit
                    final Long historyRecordId = accountSqlDao.getHistoryRecordId(recordId, rehydratedContext);
                    final EntityAudit audit = new EntityAudit(TableName.ACCOUNT_HISTORY, historyRecordId, ChangeType.INSERT);
                    accountSqlDao.insertAuditFromTransaction(audit, rehydratedContext);

                    final AccountCreationInternalEvent creationEvent = new DefaultAccountCreationEvent(account,
                            rehydratedContext.getUserToken(),
                            context.getAccountRecordId(),
                            context.getTenantRecordId());
                    try {
                        eventBus.postFromTransaction(creationEvent, transactionalDao, rehydratedContext);
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
    public void update(final Account specifiedAccount, final InternalCallContext context) throws EntityPersistenceException {
        try {
            accountSqlDao.inTransaction(new Transaction<Void, AccountSqlDao>() {
                @Override
                public Void inTransaction(final AccountSqlDao transactional, final TransactionStatus status) throws EntityPersistenceException, Bus.EventBusException {
                    final UUID accountId = specifiedAccount.getId();
                    final Account currentAccount = transactional.getById(accountId.toString(), context);
                    if (currentAccount == null) {
                        throw new EntityPersistenceException(ErrorCode.ACCOUNT_DOES_NOT_EXIST_FOR_ID, accountId);
                    }

                    // Set unspecified (null) fields to their current values
                    final Account account = specifiedAccount.mergeWithDelegate(currentAccount);

                    transactional.update(account, context);

                    final Long recordId = accountSqlDao.getRecordId(accountId.toString(), context);
                    final EntityHistory<Account> history = new EntityHistory<Account>(accountId, recordId, account, ChangeType.UPDATE);
                    accountSqlDao.insertHistoryFromTransaction(history, context);

                    final Long historyRecordId = accountSqlDao.getHistoryRecordId(recordId, context);
                    final EntityAudit audit = new EntityAudit(TableName.ACCOUNT_HISTORY, historyRecordId, ChangeType.UPDATE);
                    accountSqlDao.insertAuditFromTransaction(audit, context);

                    final AccountChangeInternalEvent changeEvent = new DefaultAccountChangeEvent(accountId,
                            context.getUserToken(),
                            currentAccount,
                            account,
                            context.getAccountRecordId(),
                            context.getTenantRecordId());
                    if (changeEvent.hasChanges()) {
                        try {
                            eventBus.postFromTransaction(changeEvent, transactional, context);
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
    public void test(final InternalTenantContext context) {
        accountSqlDao.test(context);
    }

    @Override
    public void updatePaymentMethod(final UUID accountId, final UUID paymentMethodId, final InternalCallContext context) throws EntityPersistenceException {
        try {
            accountSqlDao.inTransaction(new Transaction<Void, AccountSqlDao>() {
                @Override
                public Void inTransaction(final AccountSqlDao transactional, final TransactionStatus status) throws EntityPersistenceException, Bus.EventBusException {

                    final Account currentAccount = transactional.getById(accountId.toString(), context);
                    if (currentAccount == null) {
                        throw new EntityPersistenceException(ErrorCode.ACCOUNT_DOES_NOT_EXIST_FOR_ID, accountId);
                    }
                    final String thePaymentMethodId = paymentMethodId != null ? paymentMethodId.toString() : null;
                    transactional.updatePaymentMethod(accountId.toString(), thePaymentMethodId, context);

                    final Account account = transactional.getById(accountId.toString(), context);

                    final Long recordId = accountSqlDao.getRecordId(accountId.toString(), context);
                    final EntityHistory<Account> history = new EntityHistory<Account>(accountId, recordId, account, ChangeType.UPDATE);
                    accountSqlDao.insertHistoryFromTransaction(history, context);

                    final Long historyRecordId = accountSqlDao.getHistoryRecordId(recordId, context);
                    final EntityAudit audit = new EntityAudit(TableName.ACCOUNT_HISTORY, historyRecordId, ChangeType.UPDATE);
                    accountSqlDao.insertAuditFromTransaction(audit, context);

                    final AccountChangeInternalEvent changeEvent = new DefaultAccountChangeEvent(accountId, context.getUserToken(), currentAccount, account,
                            context.getAccountRecordId(), context.getTenantRecordId());

                    if (changeEvent.hasChanges()) {
                        try {
                            eventBus.postFromTransaction(changeEvent, transactional, context);
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
}
