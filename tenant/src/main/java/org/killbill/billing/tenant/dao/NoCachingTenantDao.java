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

package org.killbill.billing.tenant.dao;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.inject.Named;

import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.tenant.api.Tenant;
import org.killbill.billing.tenant.api.TenantApiException;
import org.killbill.billing.tenant.glue.DefaultTenantModule;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.billing.util.entity.dao.EntityDaoBase;
import org.killbill.billing.util.entity.dao.EntitySqlDaoTransactionWrapper;
import org.killbill.billing.util.entity.dao.EntitySqlDaoTransactionalJdbiWrapper;
import org.killbill.billing.util.entity.dao.EntitySqlDaoWrapperFactory;
import org.killbill.clock.Clock;
import org.skife.jdbi.v2.IDBI;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.inject.Inject;

import static org.killbill.billing.util.glue.IDBISetup.MAIN_RO_IDBI_NAMED;

/**
 * This is a special implementation of the TenantDao that does not rely on caching (CacheControllerDispatcher is not injected and passed
 * to the EntitySqlDaoWrapperInvocationHandler. It only implements the set of operations that does not require caching:
 * - Only getXXX methods where there is no Cache annotation on the SqlDao method
 * - In addition excludes getById method, which are cached by EntitySqlDaoWrapperInvocationHandler
 * <p/>
 * <p/>
 * It is used from the TenantInternalApi so that caching of catalog, overdue, ... can be done at a higher level (catalog module, overdue module)
 * without causing guice dependency issues.
 */
public class NoCachingTenantDao extends EntityDaoBase<TenantModelDao, Tenant, TenantApiException> implements TenantDao {

    @Inject
    public NoCachingTenantDao(final IDBI dbi, @Named(MAIN_RO_IDBI_NAMED) final IDBI roDbi, final Clock clock, @Named(DefaultTenantModule.NO_CACHING_TENANT) final InternalCallContextFactory internalCallContextFactory) {
        super(new EntitySqlDaoTransactionalJdbiWrapper(dbi, roDbi, clock, null, null, internalCallContextFactory), TenantSqlDao.class);
    }

    @Override
    public TenantModelDao getTenantByApiKey(final String apiKey) {
        return transactionalSqlDao.execute(true, new EntitySqlDaoTransactionWrapper<TenantModelDao>() {
            @Override
            public TenantModelDao inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(TenantSqlDao.class).getByApiKey(apiKey);
            }
        });
    }

    @Override
    public List<String> getTenantValueForKey(final String key, final InternalTenantContext context) {
        return transactionalSqlDao.execute(true, new EntitySqlDaoTransactionWrapper<List<String>>() {
            @Override
            public List<String> inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final List<TenantKVModelDao> tenantKV = entitySqlDaoWrapperFactory.become(TenantKVSqlDao.class).getTenantValueForKey(key, context);
                return new ArrayList(Collections2.transform(tenantKV, new Function<TenantKVModelDao, String>() {
                    @Override
                    public String apply(final TenantKVModelDao in) {
                        return in.getTenantValue();
                    }
                }));
            }
        });
    }

    @Override
    public TenantKVModelDao getKeyByRecordId(final Long recordId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(true, new EntitySqlDaoTransactionWrapper<TenantKVModelDao>() {
            @Override
            public TenantKVModelDao inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(TenantKVSqlDao.class).getByRecordId(recordId, context);
            }
        });
    }

    @Override
    public List<TenantKVModelDao> searchTenantKeyValues(final String searchKey, final InternalTenantContext context) {
        throw new IllegalStateException("Not implemented by NoCachingTenantDao");
    }

    @Override
    public TenantModelDao getByRecordId(final Long recordId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(true, new EntitySqlDaoTransactionWrapper<TenantModelDao>() {
            @Override
            public TenantModelDao inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(TenantSqlDao.class).getByRecordId(recordId, context);
            }
        });
    }

    @Override
    public void addTenantKeyValue(final String key, final String value, final boolean uniqueKey, final InternalCallContext context) {
        throw new IllegalStateException("Not implemented by NoCachingTenantDao");
    }

    @Override
    public void updateTenantLastKeyValue(final String key, final String value, final InternalCallContext context) {
        throw new IllegalStateException("Not implemented by NoCachingTenantDao");
    }

    @Override
    public void deleteTenantKey(final String key, final InternalCallContext context) {
        throw new IllegalStateException("Not implemented by NoCachingTenantDao");
    }

    @Override
    public void create(final TenantModelDao entity, final InternalCallContext context) throws TenantApiException {
        throw new IllegalStateException("Not implemented by NoCachingTenantDao");
    }

    @Override
    protected TenantApiException generateAlreadyExistsException(final TenantModelDao entity, final InternalCallContext context) {
        throw new IllegalStateException("Not implemented by NoCachingTenantDao");
    }

    @Override
    public Long getRecordId(final UUID id, final InternalTenantContext context) {
        throw new IllegalStateException("Not implemented by NoCachingTenantDao");
    }

    @Override
    public TenantModelDao getById(final UUID id, final InternalTenantContext context) {
        throw new IllegalStateException("Not implemented by NoCachingTenantDao");
    }

    @Override
    public Pagination<TenantModelDao> getAll(final InternalTenantContext context) {
        throw new IllegalStateException("Not implemented by NoCachingTenantDao");
    }

    @Override
    public Pagination<TenantModelDao> get(final Long offset, final Long limit, final InternalTenantContext context) {
        throw new IllegalStateException("Not implemented by NoCachingTenantDao");
    }

    @Override
    public Long getCount(final InternalTenantContext context) {
        throw new IllegalStateException("Not implemented by NoCachingTenantDao");
    }

    @Override
    public void test(final InternalTenantContext context) {
        throw new IllegalStateException("Not implemented by NoCachingTenantDao");
    }
}
