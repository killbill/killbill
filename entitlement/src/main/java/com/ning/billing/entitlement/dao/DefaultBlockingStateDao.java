/*
 * Copyright 2010-2013 Ning, Inc.
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

package com.ning.billing.entitlement.dao;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.skife.jdbi.v2.IDBI;

import com.ning.billing.entitlement.api.Blockable;
import com.ning.billing.entitlement.api.BlockingState;
import com.ning.billing.util.cache.CacheControllerDispatcher;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.clock.Clock;
import com.ning.billing.util.dao.NonEntityDao;
import com.ning.billing.util.entity.dao.EntitySqlDao;
import com.ning.billing.util.entity.dao.EntitySqlDaoTransactionWrapper;
import com.ning.billing.util.entity.dao.EntitySqlDaoTransactionalJdbiWrapper;
import com.ning.billing.util.entity.dao.EntitySqlDaoWrapperFactory;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;

public class DefaultBlockingStateDao implements BlockingStateDao {

    private final EntitySqlDaoTransactionalJdbiWrapper transactionalSqlDao;
    private final Clock clock;

    @Inject
    public DefaultBlockingStateDao(final IDBI dbi, final Clock clock, final CacheControllerDispatcher cacheControllerDispatcher, final NonEntityDao nonEntityDao) {
        this.transactionalSqlDao = new EntitySqlDaoTransactionalJdbiWrapper(dbi, clock, cacheControllerDispatcher, nonEntityDao);
        this.clock = clock;
    }

    @Override
    public BlockingState getBlockingStateForService(final UUID blockableId, final String serviceName, final InternalTenantContext context) {
        return transactionalSqlDao.execute(  new EntitySqlDaoTransactionWrapper<BlockingState>() {
            @Override
            public BlockingState inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final BlockingStateModelDao model = entitySqlDaoWrapperFactory.become(BlockingStateSqlDao.class).getBlockingStateForService(blockableId, serviceName, clock.getUTCNow().toDate(), context);
                return BlockingStateModelDao.toBlockingState(model);

            }
        });
    }

    @Override
    public List<BlockingState> getBlockingState(final UUID blockableId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(  new EntitySqlDaoTransactionWrapper<List<BlockingState>>() {
            @Override
            public List<BlockingState> inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final  List<BlockingStateModelDao> models = entitySqlDaoWrapperFactory.become(BlockingStateSqlDao.class).getBlockingState(blockableId, clock.getUTCNow().toDate(), context);
                return new ArrayList<BlockingState>(Collections2.transform(models, new Function<BlockingStateModelDao, BlockingState>() {
                    @Override
                    public BlockingState apply(@Nullable final BlockingStateModelDao src) {
                        return BlockingStateModelDao.toBlockingState(src);
                    }
                }));
            }
        });
    }

    @Override
    public List<BlockingState> getBlockingHistoryForService(final UUID blockableId, final String serviceName, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<List<BlockingState>>() {
            @Override
            public List<BlockingState> inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final List<BlockingStateModelDao> models = entitySqlDaoWrapperFactory.become(BlockingStateSqlDao.class).getBlockingHistoryForService(blockableId, serviceName, clock.getUTCNow().toDate(), context);
                return new ArrayList<BlockingState>(Collections2.transform(models, new Function<BlockingStateModelDao, BlockingState>() {
                    @Override
                    public BlockingState apply(@Nullable final BlockingStateModelDao src) {
                        return BlockingStateModelDao.toBlockingState(src);
                    }
                }));
            }
        });
    }

    @Override
    public List<BlockingState> getBlockingHistory(final UUID blockableId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<List<BlockingState>>() {
            @Override
            public List<BlockingState> inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final List<BlockingStateModelDao> models = entitySqlDaoWrapperFactory.become(BlockingStateSqlDao.class).getBlockingHistory(blockableId, clock.getUTCNow().toDate(), context);
                return new ArrayList<BlockingState>(Collections2.transform(models, new Function<BlockingStateModelDao, BlockingState>() {
                    @Override
                    public BlockingState apply(@Nullable final BlockingStateModelDao src) {
                        return BlockingStateModelDao.toBlockingState(src);
                    }
                }));
            }
        });
    }

    @Override
    public List<BlockingState> getBlockingAll(final UUID blockableId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<List<BlockingState>>() {
            @Override
            public List<BlockingState> inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final List<BlockingStateModelDao> models = entitySqlDaoWrapperFactory.become(BlockingStateSqlDao.class).getBlockingAll(blockableId, context);
                return new ArrayList<BlockingState>(Collections2.transform(models, new Function<BlockingStateModelDao, BlockingState>() {
                    @Override
                    public BlockingState apply(@Nullable final BlockingStateModelDao src) {
                        return BlockingStateModelDao.toBlockingState(src);
                    }
                }));
            }
        });
    }

    @Override
    public List<BlockingState> getBlockingAllForAccountRecordId(final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<List<BlockingState>>() {
            @Override
            public List<BlockingState> inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final List<BlockingStateModelDao> models = entitySqlDaoWrapperFactory.become(BlockingStateSqlDao.class).getByAccountRecordId(context);
                return new ArrayList<BlockingState>(Collections2.transform(models, new Function<BlockingStateModelDao, BlockingState>() {
                    @Override
                    public BlockingState apply(@Nullable final BlockingStateModelDao src) {
                        return BlockingStateModelDao.toBlockingState(src);
                    }
                }));
            }
        });
    }

    @Override
    public void setBlockingState(final BlockingState state, final Clock clock, final InternalCallContext context) {
        transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final BlockingStateSqlDao sqlDao = entitySqlDaoWrapperFactory.become(BlockingStateSqlDao.class);
                sqlDao.create(new BlockingStateModelDao(state, context), context);
                return null;
            }
        });
    }
}
