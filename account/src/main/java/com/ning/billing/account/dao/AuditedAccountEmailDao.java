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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;

import com.ning.billing.account.api.AccountEmail;
import com.ning.billing.account.api.DefaultAccountEmail;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.dao.AuditedCollectionDaoBase;
import com.ning.billing.util.dao.ObjectType;
import com.ning.billing.util.dao.TableName;
import com.ning.billing.util.entity.EntityPersistenceException;
import com.ning.billing.util.entity.collection.dao.UpdatableEntityCollectionSqlDao;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

public class AuditedAccountEmailDao extends AuditedCollectionDaoBase<AccountEmail, AccountEmail> implements AccountEmailDao {

    private final AccountEmailSqlDao accountEmailSqlDao;

    @Inject
    public AuditedAccountEmailDao(final IDBI dbi) {
        this.accountEmailSqlDao = dbi.onDemand(AccountEmailSqlDao.class);
    }

    @Override
    public void create(final AccountEmail entity, final InternalCallContext context) throws EntityPersistenceException {
        saveEmails(entity.getAccountId(), ImmutableList.<AccountEmail>of(entity), context);
    }

    @Override
    public Long getRecordId(final UUID id, final InternalTenantContext context) {
        return accountEmailSqlDao.getRecordId(id.toString(), context);
    }

    @Override
    public AccountEmail getById(final UUID id, final InternalTenantContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<AccountEmail> get(final InternalTenantContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected AccountEmail getEquivalenceObjectFor(final AccountEmail obj) {
        return obj;
    }

    @Override
    public List<AccountEmail> getEmails(final UUID accountId, final InternalTenantContext context) {
        return new ArrayList<AccountEmail>(super.loadEntities(accountId, ObjectType.ACCOUNT_EMAIL, context).values());
    }

    @Override
    public void saveEmails(final UUID accountId, final List<AccountEmail> emails, final InternalCallContext context) {
        super.saveEntities(accountId, ObjectType.ACCOUNT_EMAIL, emails, context);
    }

    @Override
    public void addEmail(final UUID accountId, final AccountEmail email, final InternalCallContext context) {
        accountEmailSqlDao.inTransaction(new Transaction<Object, AccountEmailSqlDao>() {
            @Override
            public Object inTransaction(final AccountEmailSqlDao transactional, final TransactionStatus status) throws Exception {
                // Compute the final list of emails by looking up the current ones and adding the new one
                // We can use a simple set here as the supplied email may not have its id field populated
                final List<AccountEmail> currentEmails = accountEmailSqlDao.load(accountId.toString(), ObjectType.ACCOUNT_EMAIL, context);
                final Map<String, AccountEmail> newEmails = new HashMap<String, AccountEmail>();
                for (final AccountEmail currentEmail : currentEmails) {
                    newEmails.put(currentEmail.getEmail(), currentEmail);
                }
                // TODO Note the hack here... saveEntitiesFromTransaction() works by comparing objects in sets, so we
                // have to use DefaultAccountEmail instances (email is likely to be an inner class from AccountEmailJson
                // here and will confuse AuditedCollectionDaoBase).
                newEmails.put(email.getEmail(), new DefaultAccountEmail(email.getAccountId(), email.getEmail()));

                saveEntitiesFromTransaction(getSqlDao(context), accountId, ObjectType.ACCOUNT_EMAIL,
                                            ImmutableList.<AccountEmail>copyOf(newEmails.values()), context);

                return null;
            }
        });
    }

    @Override
    public void removeEmail(final UUID accountId, final AccountEmail email, final InternalCallContext context) {
        accountEmailSqlDao.inTransaction(new Transaction<Object, AccountEmailSqlDao>() {
            @Override
            public Object inTransaction(final AccountEmailSqlDao transactional, final TransactionStatus status) throws Exception {
                // Compute the final list of emails by looking up the current ones and removing the new one
                // We can use a simple set here as the supplied email may not have its id field populated
                final List<AccountEmail> currentEmails = accountEmailSqlDao.load(accountId.toString(), ObjectType.ACCOUNT_EMAIL, context);
                final Map<String, AccountEmail> newEmails = new HashMap<String, AccountEmail>();
                for (final AccountEmail currentEmail : currentEmails) {
                    newEmails.put(currentEmail.getEmail(), currentEmail);
                }
                newEmails.remove(email.getEmail());

                saveEntitiesFromTransaction(getSqlDao(context), accountId, ObjectType.ACCOUNT_EMAIL,
                                            ImmutableList.<AccountEmail>copyOf(newEmails.values()), context);

                return null;
            }
        });
    }

    @Override
    public String getKey(final AccountEmail entity, final InternalTenantContext context) {
        return entity.getEmail();
    }

    @Override
    public void test(final InternalTenantContext context) {
        accountEmailSqlDao.test(context);
    }

    @Override
    protected TableName getTableName(final InternalTenantContext context) {
        return TableName.ACCOUNT_EMAIL_HISTORY;
    }

    @Override
    protected UpdatableEntityCollectionSqlDao<AccountEmail> transmogrifyDao(final Transmogrifier transactionalDao, final InternalTenantContext context) {
        return transactionalDao.become(AccountEmailSqlDao.class);
    }

    @Override
    protected UpdatableEntityCollectionSqlDao<AccountEmail> getSqlDao(final InternalTenantContext context) {
        return accountEmailSqlDao;
    }
}
