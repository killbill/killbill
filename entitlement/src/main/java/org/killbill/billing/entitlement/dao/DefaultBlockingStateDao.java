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

package org.killbill.billing.entitlement.dao;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.skife.jdbi.v2.IDBI;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.clock.Clock;
import org.killbill.billing.entitlement.api.BlockingState;
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.killbill.billing.entitlement.api.EntitlementApiException;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.billing.util.entity.dao.EntityDaoBase;
import org.killbill.billing.util.entity.dao.EntitySqlDaoTransactionWrapper;
import org.killbill.billing.util.entity.dao.EntitySqlDaoTransactionalJdbiWrapper;
import org.killbill.billing.util.entity.dao.EntitySqlDaoWrapperFactory;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.Ordering;

public class DefaultBlockingStateDao extends EntityDaoBase<BlockingStateModelDao, BlockingState, EntitlementApiException> implements BlockingStateDao {

    // Assume the input is blocking states for a single blockable id
    private static final Ordering<BlockingStateModelDao> BLOCKING_STATE_MODEL_DAO_ORDERING = Ordering.<BlockingStateModelDao>from(new Comparator<BlockingStateModelDao>() {
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
    });

    private final Clock clock;

    public DefaultBlockingStateDao(final IDBI dbi, final Clock clock,
                                   final CacheControllerDispatcher cacheControllerDispatcher, final NonEntityDao nonEntityDao) {
        super(new EntitySqlDaoTransactionalJdbiWrapper(dbi, clock, cacheControllerDispatcher, nonEntityDao), BlockingStateSqlDao.class);
        this.clock = clock;
    }

    @Override
    protected EntitlementApiException generateAlreadyExistsException(final BlockingStateModelDao blockingStateModelDao, final InternalCallContext context) {
        return new EntitlementApiException(ErrorCode.ENT_ALREADY_BLOCKED, blockingStateModelDao.getBlockableId());
    }

    @Override
    public BlockingState getBlockingStateForService(final UUID blockableId, final BlockingStateType blockingStateType, final String serviceName, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<BlockingState>() {
            @Override
            public BlockingState inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                // Upper bound time limit is now
                final Date upTo = clock.getUTCNow().toDate();
                final BlockingStateModelDao model = entitySqlDaoWrapperFactory.become(BlockingStateSqlDao.class).getBlockingStateForService(blockableId, serviceName, upTo, context);
                return (model != null && model.getType().equals(blockingStateType)) ? BlockingStateModelDao.toBlockingState(model) : null;
            }
        });
    }

    @Override
    public List<BlockingState> getBlockingState(final UUID blockableId, final BlockingStateType blockingStateType, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<List<BlockingState>>() {
            @Override
            public List<BlockingState> inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                // Upper bound time limit is now
                final Date upTo = clock.getUTCNow().toDate();
                final List<BlockingStateModelDao> models = entitySqlDaoWrapperFactory.become(BlockingStateSqlDao.class).getBlockingState(blockableId, upTo, context);
                final Collection<BlockingStateModelDao> modelsFiltered = filterBlockingStates(models, blockingStateType);
                return new ArrayList<BlockingState>(Collections2.transform(modelsFiltered, new Function<BlockingStateModelDao, BlockingState>() {
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
            public List<BlockingState> inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final BlockingStateSqlDao sqlDao = entitySqlDaoWrapperFactory.become(BlockingStateSqlDao.class);
                return new ArrayList<BlockingState>(Collections2.transform(sqlDao.getByAccountRecordId(context),
                                                                           new Function<BlockingStateModelDao, BlockingState>() {
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
            public Void inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final BlockingStateModelDao newBlockingStateModelDao = new BlockingStateModelDao(state, context);

                final BlockingStateSqlDao sqlDao = entitySqlDaoWrapperFactory.become(BlockingStateSqlDao.class);
                // Get all blocking states for that blocked id and service
                final List<BlockingStateModelDao> allForBlockedItAndService = sqlDao.getBlockingHistoryForService(state.getBlockedId(), state.getService(), context);

                // Add the new one (we rely below on the fact that the ID for newBlockingStateModelDao is now set)
                allForBlockedItAndService.add(newBlockingStateModelDao);

                // Re-order what should be the final list (allForBlockedItAndService is ordered by record_id in the SQL and we just added a new state)
                final List<BlockingStateModelDao> allForBlockedItAndServiceOrdered = BLOCKING_STATE_MODEL_DAO_ORDERING.immutableSortedCopy(allForBlockedItAndService);

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
                    sqlDao.create(newBlockingStateModelDao, context);
                }

                return null;
            }
        });
    }

    @Override
    public void unactiveBlockingState(final UUID id, final InternalCallContext context) {
        transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final BlockingStateSqlDao sqlDao = entitySqlDaoWrapperFactory.become(BlockingStateSqlDao.class);
                sqlDao.unactiveEvent(id.toString(), context);
                return null;
            }
        });
    }

    private Collection<BlockingStateModelDao> filterBlockingStates(final Collection<BlockingStateModelDao> models, final BlockingStateType blockingStateType) {
        return Collections2.<BlockingStateModelDao>filter(models,
                                                          new Predicate<BlockingStateModelDao>() {
                                                              @Override
                                                              public boolean apply(final BlockingStateModelDao input) {
                                                                  return input.getType().equals(blockingStateType);
                                                              }
                                                          });
    }
}
