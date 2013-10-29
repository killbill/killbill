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
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.skife.jdbi.v2.IDBI;

import com.ning.billing.callcontext.InternalCallContext;
import com.ning.billing.callcontext.InternalTenantContext;
import com.ning.billing.clock.Clock;
import com.ning.billing.entitlement.api.BlockingState;
import com.ning.billing.util.cache.CacheControllerDispatcher;
import com.ning.billing.util.dao.NonEntityDao;
import com.ning.billing.util.entity.dao.EntitySqlDao;
import com.ning.billing.util.entity.dao.EntitySqlDaoTransactionWrapper;
import com.ning.billing.util.entity.dao.EntitySqlDaoTransactionalJdbiWrapper;
import com.ning.billing.util.entity.dao.EntitySqlDaoWrapperFactory;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.Ordering;

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
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<BlockingState>() {
            @Override
            public BlockingState inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final BlockingStateModelDao model = entitySqlDaoWrapperFactory.become(BlockingStateSqlDao.class).getBlockingStateForService(blockableId, serviceName, clock.getUTCNow().toDate(), context);
                return BlockingStateModelDao.toBlockingState(model);

            }
        });
    }

    @Override
    public List<BlockingState> getBlockingState(final UUID blockableId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<List<BlockingState>>() {
            @Override
            public List<BlockingState> inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final List<BlockingStateModelDao> models = entitySqlDaoWrapperFactory.become(BlockingStateSqlDao.class).getBlockingState(blockableId, clock.getUTCNow().toDate(), context);
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
                final BlockingStateModelDao newBlockingStateModelDao = new BlockingStateModelDao(state, context);

                final BlockingStateSqlDao sqlDao = entitySqlDaoWrapperFactory.become(BlockingStateSqlDao.class);
                // Get all blocking states for that blocked id and service
                final List<BlockingStateModelDao> allForBlockedItAndService = sqlDao.getBlockingHistoryForService(state.getBlockedId(), state.getService(), clock.getUTCNow().toDate(), context);

                // Add the new one (we rely below on the fact that the ID for newBlockingStateModelDao is now set)
                allForBlockedItAndService.add(newBlockingStateModelDao);

                // Re-order what should be the final list (allForBlockedItAndService is ordered by record_id in the SQL and we just added a new state)
                final List<BlockingStateModelDao> allForBlockedItAndServiceOrdered = Ordering.<BlockingStateModelDao>from(new Comparator<BlockingStateModelDao>() {
                    @Override
                    public int compare(final BlockingStateModelDao o1, final BlockingStateModelDao o2) {
                        // effective_date column NOT NULL
                        final int comparison = o1.getEffectiveDate().compareTo(o2.getEffectiveDate());
                        if (comparison == 0) {
                            // Keep a stable ordering for ties
                            return o1.getCreatedDate().compareTo(o2.getCreatedDate());
                        } else {
                            return comparison;
                        }
                    }
                }).immutableSortedCopy(allForBlockedItAndService);

                // Go through the (ordered) stream of blocking states for that blocked id and service and check
                // if there is one or more blocking states for the same state following each others.
                // If there are, delete them, as they are not needed anymore. A picture being worth a thousand words,
                // if the current stream is: t0 S1 t1 S2 t3 S3 and we want to insert S2 at t0 < t1' < t1,
                // the final stream should be: t0 S1 t1' S2 t3 S3 (and not t0 S1 t1' S2 t1 S2 t3 S3)
                // Note that we also take care of the use case t0 S1 t1 S2 t2 S2 t3 S3 to cleanup legacy systems, although
                // it shouldn't happen anymore
                final Collection<UUID> blockingStatesToRemove = new HashSet<UUID>();
                BlockingStateModelDao prevBlockingStateModelDao = null;
                for (final BlockingStateModelDao blockingStateModelDao : allForBlockedItAndServiceOrdered) {
                    if (prevBlockingStateModelDao != null && prevBlockingStateModelDao.getState().equals(blockingStateModelDao.getState())) {
                        blockingStatesToRemove.add(blockingStateModelDao.getId());
                    }
                    prevBlockingStateModelDao = blockingStateModelDao;
                }

                // Delete unnecessary states (except newBlockingStateModelDao, which doesn't exist in the database)
                for (final UUID blockedId : blockingStatesToRemove) {
                    if (!newBlockingStateModelDao.getId().equals(blockedId)) {
                        sqlDao.unactiveEvent(blockedId.toString(), context);
                    }
                }

                // Create the state, if needed
                if (!blockingStatesToRemove.contains(newBlockingStateModelDao.getId())) {
                    sqlDao.create(new BlockingStateModelDao(state, context), context);
                }

                return null;
            }
        });
    }

    @Override
    public void unactiveBlockingState(final UUID id, final InternalCallContext context) {
        transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final BlockingStateSqlDao sqlDao = entitySqlDaoWrapperFactory.become(BlockingStateSqlDao.class);
                sqlDao.unactiveEvent(id.toString(), context);
                return null;
            }
        });
    }
}
