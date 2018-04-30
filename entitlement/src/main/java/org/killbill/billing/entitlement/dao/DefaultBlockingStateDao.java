/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.entitlement.dao;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;
import javax.inject.Named;

import org.joda.time.DateTime;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.ObjectType;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.Catalog;
import org.killbill.billing.entitlement.DefaultEntitlementService;
import org.killbill.billing.entitlement.api.BlockingApiException;
import org.killbill.billing.entitlement.api.BlockingState;
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.killbill.billing.entitlement.api.DefaultBlockingTransitionInternalEvent;
import org.killbill.billing.entitlement.api.EntitlementApiException;
import org.killbill.billing.entitlement.block.BlockingChecker.BlockingAggregator;
import org.killbill.billing.entitlement.block.StatelessBlockingChecker;
import org.killbill.billing.entitlement.engine.core.BlockingTransitionNotificationKey;
import org.killbill.billing.util.cache.Cachable.CacheType;
import org.killbill.billing.util.cache.CacheController;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.billing.util.entity.dao.EntityDaoBase;
import org.killbill.billing.util.entity.dao.EntitySqlDaoTransactionWrapper;
import org.killbill.billing.util.entity.dao.EntitySqlDaoTransactionalJdbiWrapper;
import org.killbill.billing.util.entity.dao.EntitySqlDaoWrapperFactory;
import org.killbill.bus.api.BusEvent;
import org.killbill.bus.api.PersistentBus;
import org.killbill.bus.api.PersistentBus.EventBusException;
import org.killbill.clock.Clock;
import org.killbill.notificationq.api.NotificationEvent;
import org.killbill.notificationq.api.NotificationQueue;
import org.killbill.notificationq.api.NotificationQueueService;
import org.killbill.notificationq.api.NotificationQueueService.NoSuchNotificationQueue;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.IDBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;

import static org.killbill.billing.util.glue.IDBISetup.MAIN_RO_IDBI_NAMED;

public class DefaultBlockingStateDao extends EntityDaoBase<BlockingStateModelDao, BlockingState, EntitlementApiException> implements BlockingStateDao {

    private static final Logger log = LoggerFactory.getLogger(DefaultBlockingStateDao.class);

    // Assume the input is blocking states for a single blockable id
    private static final Ordering<BlockingStateModelDao> BLOCKING_STATE_MODEL_DAO_ORDERING = Ordering.<BlockingStateModelDao>from(new Comparator<BlockingStateModelDao>() {
        @Override
        public int compare(final BlockingStateModelDao o1, final BlockingStateModelDao o2) {
            // effective_date column NOT NULL
            final int comparison = o1.getEffectiveDate().compareTo(o2.getEffectiveDate());
            if (comparison == 0) {
                // Keep a stable ordering for ties
                final int comparison2 = o1.getCreatedDate().compareTo(o2.getCreatedDate());
                if (comparison2 == 0) {
                    // New element is last
                    if (o1.getRecordId() == null) {
                        return 1;
                    } else if (o2.getRecordId() == null) {
                        return -1;
                    } else {
                        return o1.getRecordId().compareTo(o2.getRecordId());
                    }
                } else {
                    return comparison2;
                }
            } else {
                return comparison;
            }
        }
    });

    private final Clock clock;
    private final NotificationQueueService notificationQueueService;
    private final PersistentBus eventBus;
    private final CacheController<String, UUID> objectIdCacheController;
    private final NonEntityDao nonEntityDao;

    private final StatelessBlockingChecker statelessBlockingChecker = new StatelessBlockingChecker();

    public DefaultBlockingStateDao(final IDBI dbi, @Named(MAIN_RO_IDBI_NAMED) final IDBI roDbi, final Clock clock, final NotificationQueueService notificationQueueService, final PersistentBus eventBus,
                                   final CacheControllerDispatcher cacheControllerDispatcher, final NonEntityDao nonEntityDao, final InternalCallContextFactory internalCallContextFactory) {
        super(new EntitySqlDaoTransactionalJdbiWrapper(dbi, roDbi, clock, cacheControllerDispatcher, nonEntityDao, internalCallContextFactory), BlockingStateSqlDao.class);
        this.clock = clock;
        this.notificationQueueService = notificationQueueService;
        this.eventBus = eventBus;
        this.objectIdCacheController = cacheControllerDispatcher.getCacheController(CacheType.OBJECT_ID);
        this.nonEntityDao = nonEntityDao;
    }

    @Override
    protected EntitlementApiException generateAlreadyExistsException(final BlockingStateModelDao blockingStateModelDao, final InternalCallContext context) {
        return new EntitlementApiException(ErrorCode.ENT_ALREADY_BLOCKED, blockingStateModelDao.getBlockableId());
    }

    @Override
    public BlockingState getBlockingStateForService(final UUID blockableId, final BlockingStateType blockingStateType, final String serviceName, final InternalTenantContext context) {
        return transactionalSqlDao.execute(true, new EntitySqlDaoTransactionWrapper<BlockingState>() {
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
    public List<BlockingState> getBlockingState(final UUID blockableId, final BlockingStateType blockingStateType, final DateTime upToDate, final InternalTenantContext context) {
        return transactionalSqlDao.execute(true, new EntitySqlDaoTransactionWrapper<List<BlockingState>>() {
            @Override
            public List<BlockingState> inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final BlockingStateSqlDao sqlDao = entitySqlDaoWrapperFactory.become(BlockingStateSqlDao.class);
                return getBlockingState(sqlDao, blockableId, blockingStateType, upToDate, context);
            }
        });
    }

    private List<BlockingState> getBlockingState(final BlockingStateSqlDao sqlDao, final UUID blockableId, final BlockingStateType blockingStateType, final DateTime upToDate, final InternalTenantContext context) {
        final Date upTo = upToDate.toDate();
        final List<BlockingStateModelDao> models = sqlDao.getBlockingState(blockableId, blockingStateType, upTo, context);
        return new ArrayList<BlockingState>(Collections2.transform(models,
                                                                   new Function<BlockingStateModelDao, BlockingState>() {
                                                                       @Override
                                                                       public BlockingState apply(@Nullable final BlockingStateModelDao src) {
                                                                           return BlockingStateModelDao.toBlockingState(src);
                                                                       }
                                                                   }));
    }

    private List<BlockingState> getBlockingAllUpToForAccountRecordId(final BlockingStateSqlDao sqlDao, final DateTime upToDate, final InternalTenantContext context) {
        final Date upTo = upToDate.toDate();
        final List<BlockingStateModelDao> models = sqlDao.getBlockingAllUpToForAccount(upTo, context);
        return new ArrayList<BlockingState>(Collections2.transform(models,
                                                                   new Function<BlockingStateModelDao, BlockingState>() {
                                                                       @Override
                                                                       public BlockingState apply(@Nullable final BlockingStateModelDao src) {
                                                                           return BlockingStateModelDao.toBlockingState(src);
                                                                       }
                                                                   }));
    }

    @Override
    public List<BlockingState> getBlockingAllForAccountRecordId(final Catalog catalog, final InternalTenantContext context) {
        return transactionalSqlDao.execute(true, new EntitySqlDaoTransactionWrapper<List<BlockingState>>() {
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
    public void setBlockingStatesAndPostBlockingTransitionEvent(final Map<BlockingState, Optional<UUID>> states, final InternalCallContext context) {
        transactionalSqlDao.execute(false, new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final BlockingStateSqlDao sqlDao = entitySqlDaoWrapperFactory.become(BlockingStateSqlDao.class);

                for (final BlockingState state : states.keySet()) {
                    final DateTime upToDate = state.getEffectiveDate();
                    final UUID bundleId = states.get(state).orNull();
                    final BlockingAggregator previousState = getBlockedStatus(sqlDao, entitySqlDaoWrapperFactory.getHandle(), state.getBlockedId(), state.getType(), bundleId, upToDate, context);

                    final BlockingStateModelDao newBlockingStateModelDao = new BlockingStateModelDao(state, context);

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

                    boolean inserted = false;
                    // Create the state, if needed
                    if (!blockingStatesToRemove.contains(newBlockingStateModelDao.getId())) {
                        createAndRefresh(sqlDao, newBlockingStateModelDao, context);
                        inserted = true;
                    }

                    final BlockingAggregator currentState = getBlockedStatus(sqlDao, entitySqlDaoWrapperFactory.getHandle(), state.getBlockedId(), state.getType(), bundleId, upToDate, context);
                    if (previousState != null && currentState != null) {
                        recordBusOrFutureNotificationFromTransaction(entitySqlDaoWrapperFactory,
                                                                     state.getId(),
                                                                     state.getEffectiveDate(),
                                                                     state.getBlockedId(),
                                                                     state.getType(),
                                                                     state.getStateName(),
                                                                     state.getService(),
                                                                     inserted,
                                                                     previousState,
                                                                     currentState,
                                                                     context);
                    }
                }

                return null;
            }
        });
    }

    private BlockingAggregator getBlockedStatus(final BlockingStateSqlDao sqlDao, final Handle handle, final UUID blockableId, final BlockingStateType type, @Nullable final UUID bundleId, final DateTime upToDate, final InternalTenantContext context) throws BlockingApiException {
        final List<BlockingState> accountBlockingStates;
        final List<BlockingState> bundleBlockingStates;
        final List<BlockingState> subscriptionBlockingStates;
        if (type == BlockingStateType.SUBSCRIPTION) {
            final UUID accountId = nonEntityDao.retrieveIdFromObjectInTransaction(context.getAccountRecordId(), ObjectType.ACCOUNT, objectIdCacheController, handle);
            final List<BlockingState> allBlockingStatesForAccount = getBlockingAllUpToForAccountRecordId(sqlDao, upToDate, context);
            accountBlockingStates = filterBlockingStates(allBlockingStatesForAccount, accountId, BlockingStateType.ACCOUNT);
            bundleBlockingStates = filterBlockingStates(allBlockingStatesForAccount, bundleId, BlockingStateType.SUBSCRIPTION_BUNDLE);
            subscriptionBlockingStates = filterBlockingStates(allBlockingStatesForAccount, blockableId, BlockingStateType.SUBSCRIPTION);
        } else if (type == BlockingStateType.SUBSCRIPTION_BUNDLE) {
            final UUID accountId = nonEntityDao.retrieveIdFromObjectInTransaction(context.getAccountRecordId(), ObjectType.ACCOUNT, objectIdCacheController, handle);
            final List<BlockingState> allBlockingStatesForAccount = getBlockingAllUpToForAccountRecordId(sqlDao, upToDate, context);
            accountBlockingStates = filterBlockingStates(allBlockingStatesForAccount, accountId, BlockingStateType.ACCOUNT);
            bundleBlockingStates = filterBlockingStates(allBlockingStatesForAccount, blockableId, BlockingStateType.SUBSCRIPTION_BUNDLE);
            subscriptionBlockingStates = ImmutableList.<BlockingState>of();
        } else { // BlockingStateType.ACCOUNT {
            accountBlockingStates = getBlockingState(sqlDao, blockableId, BlockingStateType.ACCOUNT, upToDate, context);
            bundleBlockingStates = ImmutableList.<BlockingState>of();
            subscriptionBlockingStates = ImmutableList.<BlockingState>of();
        }
        return statelessBlockingChecker.getBlockedState(accountBlockingStates, bundleBlockingStates, subscriptionBlockingStates);
    }

    private void recordBusOrFutureNotificationFromTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory,
                                                              final UUID blockingStateId,
                                                              final DateTime effectiveDate,
                                                              final UUID blockableId,
                                                              final BlockingStateType type,
                                                              final String stateName,
                                                              final String serviceName,
                                                              final boolean blockingStateInserted,
                                                              final BlockingAggregator previousState,
                                                              final BlockingAggregator currentState,
                                                              final InternalCallContext context) {
        final boolean isTransitionToBlockedBilling = !previousState.isBlockBilling() && currentState.isBlockBilling();
        final boolean isTransitionToUnblockedBilling = previousState.isBlockBilling() && !currentState.isBlockBilling();

        final boolean isTransitionToBlockedEntitlement = !previousState.isBlockEntitlement() && currentState.isBlockEntitlement();
        final boolean isTransitionToUnblockedEntitlement = previousState.isBlockEntitlement() && !currentState.isBlockEntitlement();

        if (effectiveDate.compareTo(context.getCreatedDate()) > 0) {
            // Add notification entry to send the bus event at the effective date
            final NotificationEvent notificationEvent = new BlockingTransitionNotificationKey(blockingStateId,
                                                                                              blockableId,
                                                                                              stateName,
                                                                                              serviceName,
                                                                                              effectiveDate,
                                                                                              type,
                                                                                              isTransitionToBlockedBilling,
                                                                                              isTransitionToUnblockedBilling,
                                                                                              isTransitionToBlockedEntitlement,
                                                                                              isTransitionToUnblockedEntitlement);
            recordFutureNotificationFromTransaction(entitySqlDaoWrapperFactory, effectiveDate, notificationEvent, context);
        } else {
            if (blockingStateInserted) {
                final BusEvent event = new DefaultBlockingTransitionInternalEvent(blockableId,
                                                                                  stateName,
                                                                                  serviceName,
                                                                                  effectiveDate,
                                                                                  type,
                                                                                  isTransitionToBlockedBilling,
                                                                                  isTransitionToUnblockedBilling,
                                                                                  isTransitionToBlockedEntitlement,
                                                                                  isTransitionToUnblockedEntitlement,
                                                                                  context.getAccountRecordId(),
                                                                                  context.getTenantRecordId(),
                                                                                  context.getUserToken());
                notifyBusFromTransaction(entitySqlDaoWrapperFactory, event);
            } else {
                log.debug("Skipping event for service {} and blockableId {} (previousState={}, currentState={})", serviceName, blockableId, previousState, currentState);
            }

        }
    }

    private void recordFutureNotificationFromTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory,
                                                         final DateTime effectiveDate,
                                                         final NotificationEvent notificationEvent,
                                                         final InternalCallContext context) {
        try {
            final NotificationQueue subscriptionEventQueue = notificationQueueService.getNotificationQueue(DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME,
                                                                                                           DefaultEntitlementService.NOTIFICATION_QUEUE_NAME);
            subscriptionEventQueue.recordFutureNotificationFromTransaction(entitySqlDaoWrapperFactory.getHandle().getConnection(),
                                                                           effectiveDate,
                                                                           notificationEvent,
                                                                           context.getUserToken(),
                                                                           context.getAccountRecordId(),
                                                                           context.getTenantRecordId());
        } catch (final NoSuchNotificationQueue e) {
            throw new RuntimeException(e);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void notifyBusFromTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory, final BusEvent event) {
        try {
            eventBus.postFromTransaction(event, entitySqlDaoWrapperFactory.getHandle().getConnection());
        } catch (final EventBusException e) {
            log.warn("Failed to post event {}", event, e);
        }
    }

    @Override
    public void unactiveBlockingState(final UUID id, final InternalCallContext context) {
        transactionalSqlDao.execute(false, new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final BlockingStateSqlDao sqlDao = entitySqlDaoWrapperFactory.become(BlockingStateSqlDao.class);
                sqlDao.unactiveEvent(id.toString(), context);
                return null;
            }
        });
    }

    private List<BlockingState> filterBlockingStates(final Collection<BlockingState> models, final UUID objectId, final BlockingStateType blockingStateType) {
        return ImmutableList.<BlockingState>copyOf(Collections2.<BlockingState>filter(models,
                                                                                      new Predicate<BlockingState>() {
                                                                                          @Override
                                                                                          public boolean apply(final BlockingState input) {
                                                                                              return input.getBlockedId().equals(objectId) && input.getType().equals(blockingStateType);
                                                                                          }
                                                                                      }));
    }
}
