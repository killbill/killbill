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

import com.ning.billing.ErrorCode;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountEmail;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.entity.dao.EntityDaoBase;
import com.ning.billing.util.entity.dao.EntitySqlDao;
import com.ning.billing.util.entity.dao.EntitySqlDaoTransactionWrapper;
import com.ning.billing.util.entity.dao.EntitySqlDaoTransactionalJdbiWrapper;
import com.ning.billing.util.entity.dao.EntitySqlDaoWrapperFactory;

import com.google.inject.Inject;

public class DefaultAccountEmailDao extends EntityDaoBase<AccountEmail, AccountApiException> implements AccountEmailDao {

    @Inject
    public DefaultAccountEmailDao(final IDBI dbi) {
        super(new EntitySqlDaoTransactionalJdbiWrapper(dbi), AccountEmailSqlDao.class);
    }

    @Override
    public void delete(final AccountEmail email, final InternalCallContext context) {
        transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                entitySqlDaoWrapperFactory.become(AccountEmailSqlDao.class).delete(email, context);
                return null;
            }
        });
    }

    @Override
    public List<AccountEmail> getByAccountId(final UUID accountId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<List<AccountEmail>>() {
            @Override
            public List<AccountEmail> inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(AccountEmailSqlDao.class).getEmailByAccountId(accountId, context);
            }
        });
    }

    @Override
    protected AccountApiException generateAlreadyExistsException(final AccountEmail entity, final InternalCallContext context) {
        return new AccountApiException(ErrorCode.ACCOUNT_EMAIL_ALREADY_EXISTS, entity.getId());
    }
}
