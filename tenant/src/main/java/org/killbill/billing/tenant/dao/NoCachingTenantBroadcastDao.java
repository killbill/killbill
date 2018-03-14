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

import java.util.List;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Named;

import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.tenant.api.TenantApiException;
import org.killbill.billing.tenant.glue.DefaultTenantModule;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.entity.Entity;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.billing.util.entity.dao.EntityDaoBase;
import org.killbill.billing.util.entity.dao.EntitySqlDaoTransactionWrapper;
import org.killbill.billing.util.entity.dao.EntitySqlDaoTransactionalJdbiWrapper;
import org.killbill.billing.util.entity.dao.EntitySqlDaoWrapperFactory;
import org.killbill.clock.Clock;
import org.skife.jdbi.v2.IDBI;

import static org.killbill.billing.util.glue.IDBISetup.MAIN_RO_IDBI_NAMED;

public class NoCachingTenantBroadcastDao extends EntityDaoBase<TenantBroadcastModelDao, Entity, TenantApiException> implements TenantBroadcastDao {

    @Inject
    public NoCachingTenantBroadcastDao(final IDBI dbi, @Named(MAIN_RO_IDBI_NAMED) final IDBI roDbi, final Clock clock, @Named(DefaultTenantModule.NO_CACHING_TENANT) final InternalCallContextFactory internalCallContextFactory) {
        super(new EntitySqlDaoTransactionalJdbiWrapper(dbi, roDbi, clock, null, null, internalCallContextFactory), TenantBroadcastSqlDao.class);
    }

    @Override
    public void create(final TenantBroadcastModelDao entity, final InternalCallContext context) throws TenantApiException {
        throw new IllegalStateException("Not implemented by NoCachingTenantBroadcastDao");
    }

    @Override
    protected TenantApiException generateAlreadyExistsException(final TenantBroadcastModelDao entity, final InternalCallContext context) {
        throw new IllegalStateException("Not implemented by NoCachingTenantBroadcastDao");
    }

    @Override
    public Long getRecordId(final UUID id, final InternalTenantContext context) {
        throw new IllegalStateException("Not implemented by NoCachingTenantBroadcastDao");
    }

    @Override
    public TenantBroadcastModelDao getByRecordId(final Long recordId, final InternalTenantContext context) {
        throw new IllegalStateException("Not implemented by NoCachingTenantBroadcastDao");
    }

    @Override
    public TenantBroadcastModelDao getById(final UUID id, final InternalTenantContext context) {
        throw new IllegalStateException("Not implemented by NoCachingTenantBroadcastDao");
    }

    @Override
    public Pagination<TenantBroadcastModelDao> getAll(final InternalTenantContext context) {
        throw new IllegalStateException("Not implemented by NoCachingTenantBroadcastDao");
    }

    @Override
    public Pagination<TenantBroadcastModelDao> get(final Long offset, final Long limit, final InternalTenantContext context) {
        throw new IllegalStateException("Not implemented by NoCachingTenantBroadcastDao");
    }

    @Override
    public Long getCount(final InternalTenantContext context) {
        throw new IllegalStateException("Not implemented by NoCachingTenantBroadcastDao");
    }

    @Override
    public void test(final InternalTenantContext context) {
        throw new IllegalStateException("Not implemented by NoCachingTenantBroadcastDao");
    }

    @Override
    public List<TenantBroadcastModelDao> getLatestEntriesFrom(final Long recordId) {
        return transactionalSqlDao.execute(true, new EntitySqlDaoTransactionWrapper<List<TenantBroadcastModelDao>>() {
            @Override
            public List<TenantBroadcastModelDao> inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(TenantBroadcastSqlDao.class).getLatestEntries(recordId);
            }
        });
    }

    @Override
    public TenantBroadcastModelDao getLatestEntry() {
        return transactionalSqlDao.execute(true, new EntitySqlDaoTransactionWrapper<TenantBroadcastModelDao>() {
            @Override
            public TenantBroadcastModelDao inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(TenantBroadcastSqlDao.class).getLatestEntry();
            }
        });
    }
}
