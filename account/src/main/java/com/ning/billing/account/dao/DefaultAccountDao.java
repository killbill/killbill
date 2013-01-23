/*
 * Copyright 2010-2012 Ning, Inc.
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

import com.ning.billing.BillingExceptionBase;
import com.ning.billing.ErrorCode;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.user.DefaultAccountChangeEvent;
import com.ning.billing.account.api.user.DefaultAccountCreationEvent;
import com.ning.billing.util.audit.ChangeType;
import com.ning.billing.util.cache.CacheControllerDispatcher;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.dao.NonEntityDao;
import com.ning.billing.util.entity.EntityPersistenceException;
import com.ning.billing.util.entity.dao.EntityDaoBase;
import com.ning.billing.util.entity.dao.EntitySqlDao;
import com.ning.billing.util.entity.dao.EntitySqlDaoTransactionWrapper;
import com.ning.billing.util.entity.dao.EntitySqlDaoTransactionalJdbiWrapper;
import com.ning.billing.util.entity.dao.EntitySqlDaoWrapperFactory;
import com.ning.billing.util.events.AccountChangeInternalEvent;
import com.ning.billing.util.events.AccountCreationInternalEvent;
import com.ning.billing.util.svcsapi.bus.InternalBus;
import com.ning.billing.util.svcsapi.bus.InternalBus.EventBusException;

import com.google.inject.Inject;

public class DefaultAccountDao extends EntityDaoBase<AccountModelDao, Account, AccountApiException> implements AccountDao {

    private static final Logger log = LoggerFactory.getLogger(DefaultAccountDao.class);

    private final InternalBus eventBus;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public DefaultAccountDao(final IDBI dbi, final InternalBus eventBus, final Clock clock, final CacheControllerDispatcher cacheControllerDispatcher,
                             final InternalCallContextFactory internalCallContextFactory, final NonEntityDao nonEntityDao) {
        super(new EntitySqlDaoTransactionalJdbiWrapper(dbi, clock, cacheControllerDispatcher, nonEntityDao), AccountSqlDao.class);
        this.eventBus = eventBus;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Override
    protected AccountApiException generateAlreadyExistsException(final AccountModelDao account, final InternalCallContext context) {
        return new AccountApiException(ErrorCode.ACCOUNT_ALREADY_EXISTS, account.getExternalKey());
    }

    @Override
    protected void postBusEventFromTransaction(final AccountModelDao account, final AccountModelDao savedAccount, final ChangeType changeType,
                                               final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory, final InternalCallContext context) throws BillingExceptionBase {
        // This is only called for the create call (see update below)
        switch (changeType) {
            case INSERT:
                break;
            default:
                return;
        }

        final Long recordId = entitySqlDaoWrapperFactory.become(AccountSqlDao.class).getRecordId(savedAccount.getId().toString(), context);
        // We need to re-hydrate the context with the account record id
        final InternalCallContext rehydratedContext = internalCallContextFactory.createInternalCallContext(recordId, context);
        final AccountCreationInternalEvent creationEvent = new DefaultAccountCreationEvent(savedAccount,
                                                                                           rehydratedContext.getUserToken(),
                                                                                           context.getAccountRecordId(),
                                                                                           context.getTenantRecordId());
        try {
            eventBus.postFromTransaction(creationEvent, entitySqlDaoWrapperFactory, rehydratedContext);
        } catch (final EventBusException e) {
            log.warn("Failed to post account creation event for account " + savedAccount.getId(), e);
        }
    }

    @Override
    public AccountModelDao getAccountByKey(final String key, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<AccountModelDao>() {
            @Override
            public AccountModelDao inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(AccountSqlDao.class).getAccountByKey(key, context);
            }
        });
    }

    @Override
    public UUID getIdFromKey(final String externalKey, final InternalTenantContext context) throws AccountApiException {
        if (externalKey == null) {
            throw new AccountApiException(ErrorCode.ACCOUNT_CANNOT_MAP_NULL_KEY, "");
        }

        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<UUID>() {
            @Override
            public UUID inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(AccountSqlDao.class).getIdFromKey(externalKey, context);
            }
        });
    }

    @Override
    public void update(final AccountModelDao specifiedAccount, final InternalCallContext context) throws AccountApiException {
        transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws EventBusException, AccountApiException {
                final AccountSqlDao transactional = entitySqlDaoWrapperFactory.become(AccountSqlDao.class);

                final UUID accountId = specifiedAccount.getId();
                final AccountModelDao currentAccount = transactional.getById(accountId.toString(), context);
                if (currentAccount == null) {
                    throw new AccountApiException(ErrorCode.ACCOUNT_DOES_NOT_EXIST_FOR_ID, accountId);
                }

                transactional.update(specifiedAccount, context);

                final AccountChangeInternalEvent changeEvent = new DefaultAccountChangeEvent(accountId,
                                                                                             context.getUserToken(),
                                                                                             currentAccount,
                                                                                             specifiedAccount,
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
    public void updatePaymentMethod(final UUID accountId, final UUID paymentMethodId, final InternalCallContext context) throws AccountApiException {
        transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws EntityPersistenceException, EventBusException {
                final AccountSqlDao transactional = entitySqlDaoWrapperFactory.become(AccountSqlDao.class);

                final AccountModelDao currentAccount = transactional.getById(accountId.toString(), context);
                if (currentAccount == null) {
                    throw new EntityPersistenceException(ErrorCode.ACCOUNT_DOES_NOT_EXIST_FOR_ID, accountId);
                }
                final String thePaymentMethodId = paymentMethodId != null ? paymentMethodId.toString() : null;
                transactional.updatePaymentMethod(accountId.toString(), thePaymentMethodId, context);

                final AccountModelDao account = transactional.getById(accountId.toString(), context);
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
    }

    @Override
    public void addEmail(final AccountEmailModelDao email, final InternalCallContext context) throws AccountApiException {
        transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final AccountEmailSqlDao transactional = entitySqlDaoWrapperFactory.become(AccountEmailSqlDao.class);

                if (transactional.getById(email.getId().toString(), context) != null) {
                    throw new AccountApiException(ErrorCode.ACCOUNT_EMAIL_ALREADY_EXISTS, email.getId());
                }

                transactional.create(email, context);
                return null;
            }
        });
    }

    @Override
    public void removeEmail(final AccountEmailModelDao email, final InternalCallContext context) {
        transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                entitySqlDaoWrapperFactory.become(AccountEmailSqlDao.class).markEmailAsDeleted(email, context);
                return null;
            }
        });
    }

    @Override
    public List<AccountEmailModelDao> getEmailsByAccountId(final UUID accountId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<List<AccountEmailModelDao>>() {
            @Override
            public List<AccountEmailModelDao> inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(AccountEmailSqlDao.class).getEmailByAccountId(accountId, context);
            }
        });
    }

    @Override
    public AccountModelDao getByRecordId(final Long recordId, final InternalCallContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<AccountModelDao>() {
            @Override
            public AccountModelDao inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(AccountSqlDao.class).getByRecordId(recordId, context);
            }
        });
    }
}
