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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.ErrorCode;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.user.DefaultAccountChangeEvent;
import com.ning.billing.account.api.user.DefaultAccountCreationEvent;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.entity.EntityPersistenceException;
import com.ning.billing.util.entity.dao.EntitySqlDao;
import com.ning.billing.util.entity.dao.EntitySqlDaoTransactionWrapper;
import com.ning.billing.util.entity.dao.EntitySqlDaoTransactionalJdbiWrapper;
import com.ning.billing.util.entity.dao.EntitySqlDaoWrapperFactory;
import com.ning.billing.util.events.AccountChangeInternalEvent;
import com.ning.billing.util.events.AccountCreationInternalEvent;
import com.ning.billing.util.svcsapi.bus.InternalBus;
import com.ning.billing.util.svcsapi.bus.InternalBus.EventBusException;

import com.google.inject.Inject;

public class AuditedAccountDao implements AccountDao {

    private static final Logger log = LoggerFactory.getLogger(AuditedAccountDao.class);

    private final EntitySqlDaoTransactionalJdbiWrapper transactionalSqlDao;
    private final AccountSqlDao accountSqlDao;
    private final InternalBus eventBus;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public AuditedAccountDao(final IDBI dbi, final InternalBus eventBus, final InternalCallContextFactory internalCallContextFactory) {
        this.eventBus = eventBus;
        this.accountSqlDao = dbi.onDemand(AccountSqlDao.class);
        this.internalCallContextFactory = internalCallContextFactory;
        transactionalSqlDao = new EntitySqlDaoTransactionalJdbiWrapper(dbi);
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
    public Account getByRecordId(final Long recordId, final InternalTenantContext context) {
        return accountSqlDao.getByRecordId(recordId, context);
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
    public void create(final Account account, final InternalCallContext context) throws AccountApiException {
        final String key = account.getExternalKey();

        transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws AccountApiException, InternalBus.EventBusException {
                final AccountSqlDao transactionalDao = entitySqlDaoWrapperFactory.become(AccountSqlDao.class);

                final Account currentAccount = transactionalDao.getAccountByKey(key, context);
                if (currentAccount != null) {
                    throw new AccountApiException(ErrorCode.ACCOUNT_ALREADY_EXISTS, key);
                }

                transactionalDao.create(account, context);

                final Long recordId = accountSqlDao.getRecordId(account.getId().toString(), context);
                // We need to re-hydrate the context with the account record id
                final InternalCallContext rehydratedContext = internalCallContextFactory.createInternalCallContext(recordId, context);
                final AccountCreationInternalEvent creationEvent = new DefaultAccountCreationEvent(account,
                                                                                                   rehydratedContext.getUserToken(),
                                                                                                   context.getAccountRecordId(),
                                                                                                   context.getTenantRecordId());
                try {
                    eventBus.postFromTransaction(creationEvent, entitySqlDaoWrapperFactory, rehydratedContext);
                } catch (final EventBusException e) {
                    log.warn("Failed to post account creation event for account " + account.getId(), e);
                }
                return null;
            }
        });
    }

    @Override
    public void update(final Account specifiedAccount, final InternalCallContext context) {
        transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws EntityPersistenceException, InternalBus.EventBusException {
                final AccountSqlDao transactional = entitySqlDaoWrapperFactory.become(AccountSqlDao.class);

                final UUID accountId = specifiedAccount.getId();
                final Account currentAccount = transactional.getById(accountId.toString(), context);
                if (currentAccount == null) {
                    throw new EntityPersistenceException(ErrorCode.ACCOUNT_DOES_NOT_EXIST_FOR_ID, accountId);
                }

                // Set unspecified (null) fields to their current values
                final Account account = specifiedAccount.mergeWithDelegate(currentAccount);
                transactional.update(account, context);

                final AccountChangeInternalEvent changeEvent = new DefaultAccountChangeEvent(accountId,
                                                                                             context.getUserToken(),
                                                                                             currentAccount,
                                                                                             account,
                                                                                             context.getAccountRecordId(),
                                                                                             context.getTenantRecordId());
                if (changeEvent.hasChanges()) {
                    try {
                        eventBus.postFromTransaction(changeEvent, entitySqlDaoWrapperFactory, context);
                    } catch (final EventBusException e) {
                        log.warn("Failed to post account change event for account " + accountId, e);
                    }
                }

                return null;
            }
        });
    }

    @Override
    public void test(final InternalTenantContext context) {
        accountSqlDao.test(context);
    }

    @Override
    public void updatePaymentMethod(final UUID accountId, final UUID paymentMethodId, final InternalCallContext context) throws EntityPersistenceException {
        try {
            transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<Void>() {
                @Override
                public Void inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws EntityPersistenceException, InternalBus.EventBusException {
                    final AccountSqlDao transactional = entitySqlDaoWrapperFactory.become(AccountSqlDao.class);

                    final Account currentAccount = transactional.getById(accountId.toString(), context);
                    if (currentAccount == null) {
                        throw new EntityPersistenceException(ErrorCode.ACCOUNT_DOES_NOT_EXIST_FOR_ID, accountId);
                    }
                    final String thePaymentMethodId = paymentMethodId != null ? paymentMethodId.toString() : null;
                    transactional.updatePaymentMethod(accountId.toString(), thePaymentMethodId, context);

                    final Account account = transactional.getById(accountId.toString(), context);
                    final AccountChangeInternalEvent changeEvent = new DefaultAccountChangeEvent(accountId, context.getUserToken(), currentAccount, account,
                                                                                                 context.getAccountRecordId(), context.getTenantRecordId());

                    if (changeEvent.hasChanges()) {
                        try {
                            eventBus.postFromTransaction(changeEvent, entitySqlDaoWrapperFactory, context);
                        } catch (final EventBusException e) {
                            log.warn("Failed to post account change event for account " + accountId, e);
                        }
                    }
                    return null;
                }
            });
        } catch (final RuntimeException re) {
            if (re.getCause() instanceof EntityPersistenceException) {
                throw (EntityPersistenceException) re.getCause();
            } else {
                throw re;
            }
        }
    }
}
