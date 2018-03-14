/*
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.account.api.svcs;

import java.util.UUID;

import javax.inject.Named;

import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.DefaultAccount;
import org.killbill.billing.account.api.DefaultImmutableAccountData;
import org.killbill.billing.account.api.ImmutableAccountData;
import org.killbill.billing.account.api.ImmutableAccountInternalApi;
import org.killbill.billing.account.dao.AccountModelDao;
import org.killbill.billing.account.dao.AccountSqlDao;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.util.cache.Cachable.CacheType;
import org.killbill.billing.util.cache.CacheController;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.cache.CacheLoaderArgument;
import org.killbill.billing.util.cache.ImmutableAccountCacheLoader.LoaderCallback;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.billing.util.entity.dao.EntitySqlDao;
import org.killbill.billing.util.entity.dao.EntitySqlDaoTransactionWrapper;
import org.killbill.billing.util.entity.dao.EntitySqlDaoTransactionalJdbiWrapper;
import org.killbill.billing.util.entity.dao.EntitySqlDaoWrapperFactory;
import org.killbill.clock.Clock;
import org.skife.jdbi.v2.IDBI;

import com.google.inject.Inject;

import static org.killbill.billing.util.glue.IDBISetup.MAIN_RO_IDBI_NAMED;

public class DefaultImmutableAccountInternalApi implements ImmutableAccountInternalApi {

    private final EntitySqlDaoTransactionalJdbiWrapper transactionalSqlDao;
    private final NonEntityDao nonEntityDao;
    private final CacheController<Long, ImmutableAccountData> accountCacheController;
    private final CacheController<String, Long> recordIdCacheController;

    @Inject
    public DefaultImmutableAccountInternalApi(final IDBI dbi,
                                              @Named(MAIN_RO_IDBI_NAMED) final IDBI roDbi,
                                              final Clock clock,
                                              final NonEntityDao nonEntityDao,
                                              final CacheControllerDispatcher cacheControllerDispatcher) {
        // This API will directly issue queries instead of relying on the DAO (introduced to avoid Guice circular dependencies with InternalCallContextFactory)
        this.transactionalSqlDao = new EntitySqlDaoTransactionalJdbiWrapper(dbi, roDbi, clock, cacheControllerDispatcher, nonEntityDao, null);
        this.nonEntityDao = nonEntityDao;
        this.accountCacheController = cacheControllerDispatcher.getCacheController(CacheType.ACCOUNT_IMMUTABLE);
        this.recordIdCacheController = cacheControllerDispatcher.getCacheController(CacheType.RECORD_ID);
    }

    @Override
    public ImmutableAccountData getImmutableAccountDataById(final UUID accountId, final InternalTenantContext context) throws AccountApiException {
        final Long recordId = nonEntityDao.retrieveRecordIdFromObject(accountId, ObjectType.ACCOUNT, recordIdCacheController);
        return getImmutableAccountDataByRecordId(recordId, context);
    }

    @Override
    public ImmutableAccountData getImmutableAccountDataByRecordId(final Long recordId, final InternalTenantContext context) throws AccountApiException {
        final CacheLoaderArgument arg = createImmutableAccountCacheLoaderArgument(context);
        return accountCacheController.get(recordId, arg);
    }

    private CacheLoaderArgument createImmutableAccountCacheLoaderArgument(final InternalTenantContext context) {
        final LoaderCallback loaderCallback = new LoaderCallback() {
            @Override
            public ImmutableAccountData loadAccount(final Long recordId, final InternalTenantContext context) {
                final Account account = getAccountByRecordIdInternal(recordId, context);
                return account != null ? new DefaultImmutableAccountData(account) : null;
            }
        };

        final Object[] args = {loaderCallback};
        return new CacheLoaderArgument(null, args, context);
    }

    private Account getAccountByRecordIdInternal(final Long recordId, final InternalTenantContext context) {
        final AccountModelDao accountModelDao = transactionalSqlDao.execute(true, new EntitySqlDaoTransactionWrapper<AccountModelDao>() {

            @Override
            public AccountModelDao inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final EntitySqlDao<AccountModelDao, Account> transactional = entitySqlDaoWrapperFactory.become(AccountSqlDao.class);
                return transactional.getByRecordId(recordId, context);
            }
        });

        return accountModelDao != null ? new DefaultAccount(accountModelDao) : null;
    }
}
