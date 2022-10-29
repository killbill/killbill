/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2020 Groupon, Inc
 * Copyright 2020-2021 Equinix, Inc
 * Copyright 2014-2021 The Billing Project, LLC
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

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.catalog.api.VersionedCatalog;
import org.killbill.billing.entitlement.EventsStream;
import org.killbill.billing.entitlement.api.BlockingState;
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.killbill.billing.entitlement.api.DefaultEntitlementApi;
import org.killbill.billing.entitlement.api.EntitlementApiException;
import org.killbill.billing.entitlement.engine.core.EventsStreamBuilder;
import org.killbill.billing.platform.api.KillbillService.KILLBILL_SERVICES;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseInternalApi;
import org.killbill.billing.subscription.api.user.SubscriptionBaseApiException;
import org.killbill.billing.util.api.AuditLevel;
import org.killbill.billing.util.audit.AuditLogWithHistory;
import org.killbill.billing.util.audit.dao.AuditDao;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.commons.utils.collect.Iterables;
import org.killbill.billing.util.customfield.ShouldntHappenException;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.billing.util.optimizer.BusOptimizer;
import org.killbill.clock.Clock;
import org.killbill.notificationq.api.NotificationQueueService;
import org.skife.jdbi.v2.IDBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.killbill.billing.util.glue.IDBISetup.MAIN_RO_IDBI_NAMED;

@Singleton
public class ProxyBlockingStateDao implements BlockingStateDao {

    private static final Logger log = LoggerFactory.getLogger(ProxyBlockingStateDao.class);

    // Ordering is critical here, especially for Junction
    public static List<BlockingState> sortedCopy(final Iterable<BlockingState> blockingStates) {
        final List<BlockingState> result = new LinkedList<>();

        // Make sure same-day transitions are always returned in the same order depending on their attributes
        final Iterator<BlockingState> iterator = Iterables.toStream(blockingStates)
                .sorted(Comparator.naturalOrder())
                .iterator();
        BlockingState prev = null;
        while (iterator.hasNext()) {
            final BlockingState current = iterator.next();
            if (iterator.hasNext()) {
                final BlockingState next = iterator.next();
                if (prev != null &&
                    current.getEffectiveDate().equals(next.getEffectiveDate()) &&
                    current.getBlockedId().equals(next.getBlockedId()) &&
                    !current.getService().equals(next.getService())) {
                    // Same date, same blockable id, different services (for same-service events, trust the total ordering)

                    // Make sure block billing transitions are respected first
                    BlockingState prevCandidate = insertTiedBlockingStatesInTheRightOrder(result, current, next, prev.isBlockBilling(), current.isBlockBilling(), next.isBlockBilling());
                    if (prevCandidate == null) {
                        // Then respect block entitlement transitions
                        prevCandidate = insertTiedBlockingStatesInTheRightOrder(result, current, next, prev.isBlockEntitlement(), current.isBlockEntitlement(), next.isBlockEntitlement());
                        if (prevCandidate == null) {
                            // And finally block changes transitions
                            prevCandidate = insertTiedBlockingStatesInTheRightOrder(result, current, next, prev.isBlockChange(), current.isBlockChange(), next.isBlockChange());
                            if (prevCandidate == null) {
                                // Trust the current sorting
                                result.add(current);
                                result.add(next);
                                prev = next;
                            } else {
                                prev = prevCandidate;
                            }
                        } else {
                            prev = prevCandidate;
                        }
                    } else {
                        prev = prevCandidate;
                    }
                } else {
                    result.add(current);
                    result.add(next);
                    prev = next;
                }
            } else {
                // End of the list
                result.add(current);
            }
        }

        return result;
    }

    private static BlockingState insertTiedBlockingStatesInTheRightOrder(final Collection<BlockingState> result,
                                                                         final BlockingState current,
                                                                         final BlockingState next,
                                                                         final boolean prevBlocked,
                                                                         final boolean currentBlocked,
                                                                         final boolean nextBlocked) {
        final BlockingState prev;

        if (prevBlocked && currentBlocked && nextBlocked) {
            // Tricky use case, bail
            return null;
        } else if (prevBlocked && currentBlocked && !nextBlocked) {
            result.add(next);
            result.add(current);
            prev = current;
        } else if (prevBlocked && !currentBlocked && nextBlocked) {
            result.add(current);
            result.add(next);
            prev = next;
        } else if (prevBlocked && !currentBlocked && !nextBlocked) {
            // Tricky use case, bail
            return null;
        } else if (!prevBlocked && currentBlocked && nextBlocked) {
            // Tricky use case, bail
            return null;
        } else if (!prevBlocked && currentBlocked && !nextBlocked) {
            result.add(current);
            result.add(next);
            prev = next;
        } else if (!prevBlocked && !currentBlocked && nextBlocked) {
            result.add(next);
            result.add(current);
            prev = current;
        } else if (!prevBlocked && !currentBlocked && !nextBlocked) {
            // Tricky use case, bail
            return null;
        } else {
            throw new ShouldntHappenException("Marker exception for code clarity");
        }

        return prev;
    }

    private final SubscriptionBaseInternalApi subscriptionInternalApi;
    private final Clock clock;

    protected final EventsStreamBuilder eventsStreamBuilder;
    protected final DefaultBlockingStateDao delegate;

    @Inject
    public ProxyBlockingStateDao(final EventsStreamBuilder eventsStreamBuilder, final SubscriptionBaseInternalApi subscriptionBaseInternalApi,
                                 final IDBI dbi, @Named(MAIN_RO_IDBI_NAMED) final IDBI roDbi, final Clock clock, final NotificationQueueService notificationQueueService, final BusOptimizer eventBus,
                                 final CacheControllerDispatcher cacheControllerDispatcher, final NonEntityDao nonEntityDao, final AuditDao auditDao, final InternalCallContextFactory internalCallContextFactory) {
        this.eventsStreamBuilder = eventsStreamBuilder;
        this.subscriptionInternalApi = subscriptionBaseInternalApi;
        this.clock = clock;
        this.delegate = new DefaultBlockingStateDao(dbi, roDbi, clock, notificationQueueService, eventBus, cacheControllerDispatcher, nonEntityDao, auditDao, internalCallContextFactory);
    }

    @Override
    public void create(final BlockingStateModelDao entity, final InternalCallContext context) throws EntitlementApiException {
        delegate.create(entity, context);
    }

    @Override
    public BlockingStateModelDao getByRecordId(final Long recordId, final InternalTenantContext context) {
        return delegate.getByRecordId(recordId, context);
    }

    @Override
    public BlockingStateModelDao getById(final UUID id, final InternalTenantContext context) throws EntitlementApiException {
        return delegate.getById(id, context);
    }

    @Override
    public Pagination<BlockingStateModelDao> getAll(final InternalTenantContext context) {
        return delegate.getAll(context);
    }

    @Override
    public Pagination<BlockingStateModelDao> get(final Long offset, final Long limit, final InternalTenantContext context) {
        return delegate.get(offset, limit, context);
    }

    @Override
    public Long getCount(final InternalTenantContext context) {
        return delegate.getCount(context);
    }

    @Override
    public void test(final InternalTenantContext context) {
        delegate.test(context);
    }

    @Override
    public BlockingState getBlockingStateForService(final UUID blockableId, final BlockingStateType blockingStateType, final String serviceName, final InternalTenantContext context) {
        return delegate.getBlockingStateForService(blockableId, blockingStateType, serviceName, context);
    }

    @Override
    public List<BlockingState> getBlockingState(final UUID blockableId, final BlockingStateType blockingStateType, final DateTime upToDate, final InternalTenantContext context) {
        return delegate.getBlockingState(blockableId, blockingStateType, upToDate, context);
    }

    @Override
    public List<BlockingState> getBlockingAllForAccountRecordId(final VersionedCatalog catalog, final InternalTenantContext context) {
        final List<BlockingState> statesOnDisk = delegate.getBlockingAllForAccountRecordId(catalog, context);
        return addBlockingStatesNotOnDisk(statesOnDisk, catalog, null, context);
    }

    @Override
    public List<BlockingState> getBlockingActiveForAccount(final VersionedCatalog catalog, @Nullable final LocalDate cutoffDt, final InternalTenantContext context) {
        final List<BlockingState> statesOnDisk = delegate.getBlockingActiveForAccount(catalog, cutoffDt, context);
        final List<BlockingState> result = (statesOnDisk.size() > 0) ? addBlockingStatesNotOnDisk(statesOnDisk, catalog, cutoffDt, context) : statesOnDisk;
        return result;
    }

    @Override
    public List<BlockingState> getByBlockingIds(final Iterable<UUID> blockableIds, final boolean includeDeletedEvents, final InternalTenantContext context) {
        return delegate.getByBlockingIds(blockableIds, includeDeletedEvents, context);
    }

    @Override
    public void setBlockingStatesAndPostBlockingTransitionEvent(final Map<BlockingState, Optional<UUID>> states, final InternalCallContext context) {
        delegate.setBlockingStatesAndPostBlockingTransitionEvent(states, context);
    }

    @Override
    public void unactiveBlockingState(final UUID blockableId, final InternalCallContext context) {
        delegate.unactiveBlockingState(blockableId, context);
    }

    @Override
    public List<AuditLogWithHistory> getBlockingStateAuditLogsWithHistoryForId(final UUID blockableId, final AuditLevel auditLevel, final InternalTenantContext context) {
        return delegate.getBlockingStateAuditLogsWithHistoryForId(blockableId, auditLevel, context);
    }

    // Add blocking states for add-ons, which would be impacted by a future cancellation or change of their base plan
    // See DefaultEntitlement#computeAddOnBlockingStates
    private List<BlockingState> addBlockingStatesNotOnDisk(final List<BlockingState> blockingStatesOnDisk,
                                                           final VersionedCatalog catalog,
                                                           @Nullable final LocalDate cutoffDt,
                                                           final InternalTenantContext context) {
        final Collection<BlockingState> blockingStatesOnDiskCopy = new LinkedList<BlockingState>(blockingStatesOnDisk);

        // Find all base entitlements that we care about (for which we want to find future cancelled add-ons)
        final Iterable<SubscriptionBase> baseSubscriptionsToConsider;
        final Iterable<EventsStream> eventsStreams;
        try {
            final Map<UUID, List<SubscriptionBase>> subscriptions = subscriptionInternalApi.getSubscriptionsForAccount(catalog, cutoffDt, context);
            baseSubscriptionsToConsider = subscriptions.values().stream()
                    .flatMap(Collection::stream)
                    .filter(input -> ProductCategory.BASE.equals(input.getCategory()))
                    .collect(Collectors.toList());
            eventsStreams = eventsStreamBuilder.buildForAccount(subscriptions, catalog, context)
                    .getEventsStreams().values()
                    .stream()
                    .flatMap(Collection::stream)
                    .collect(Collectors.toList());
        } catch (final EntitlementApiException e) {
            log.error("Error computing blocking states for addons for account record id " + context.getAccountRecordId(), e);
            throw new RuntimeException(e);
        } catch (final SubscriptionBaseApiException e) {
            log.error("Error computing blocking states for addons for account record id " + context.getAccountRecordId(), e);
            throw new RuntimeException(e);
        }

        return addBlockingStatesNotOnDisk(null, null, blockingStatesOnDiskCopy, baseSubscriptionsToConsider, eventsStreams);
    }

    // Special signature for OptimizedProxyBlockingStateDao
    protected List<BlockingState> addBlockingStatesNotOnDisk(@Nullable final UUID blockableId,
                                                             @Nullable final BlockingStateType blockingStateType,
                                                             final Collection<BlockingState> blockingStatesOnDiskCopy,
                                                             final Iterable<SubscriptionBase> baseSubscriptionsToConsider,
                                                             final Iterable<EventsStream> eventsStreams) {
        final Map<UUID, EventsStream> baseSubscriptionIdToEventsStream = new HashMap<>();
        for (final EventsStream eventsStream : eventsStreams) {
            baseSubscriptionIdToEventsStream.put(eventsStream.getSubscriptionBase().getId(), eventsStream);
        }

        // Compute the blocking states not on disk for all base subscriptions
        final DateTime now = clock.getUTCNow();
        for (final SubscriptionBase baseSubscription : baseSubscriptionsToConsider) {
            final EventsStream eventsStream = baseSubscriptionIdToEventsStream.get(baseSubscription.getId());

            // First, check to see if the base entitlement is cancelled
            final Collection<BlockingState> blockingStatesNotOnDisk = eventsStream.computeAddonsBlockingStatesForFutureSubscriptionBaseEvents();

            // Inject the extra blocking states into the stream if needed
            for (final BlockingState blockingState : blockingStatesNotOnDisk) {
                // If this entitlement is actually already cancelled, add the cancellation event we computed
                // only if it's prior to the blocking state on disk (e.g. add-on future cancelled but base plan cancelled earlier).
                BlockingState cancellationBlockingStateOnDisk = null;
                boolean overrideCancellationBlockingStateOnDisk = false;
                if (isEntitlementCancellationBlockingState(blockingState)) {
                    cancellationBlockingStateOnDisk = findEntitlementCancellationBlockingState(blockingState.getBlockedId(), blockingStatesOnDiskCopy);
                    overrideCancellationBlockingStateOnDisk = cancellationBlockingStateOnDisk != null && blockingState.getEffectiveDate().isBefore(cancellationBlockingStateOnDisk.getEffectiveDate());
                }

                if ((
                            blockingStateType == null ||
                            // In case we're coming from OptimizedProxyBlockingStateDao, make sure we don't add
                            // blocking states for other add-ons on that base subscription
                            (BlockingStateType.SUBSCRIPTION.equals(blockingStateType) && blockingState.getBlockedId().equals(blockableId))
                    ) && (
                            cancellationBlockingStateOnDisk == null || overrideCancellationBlockingStateOnDisk
                    )) {
                    final BlockingStateModelDao blockingStateModelDao = new BlockingStateModelDao(blockingState, now, now);
                    blockingStatesOnDiskCopy.add(BlockingStateModelDao.toBlockingState(blockingStateModelDao));

                    if (overrideCancellationBlockingStateOnDisk) {
                        blockingStatesOnDiskCopy.remove(cancellationBlockingStateOnDisk);
                    }
                }
            }
        }

        // Return the sorted list
        return sortedCopy(blockingStatesOnDiskCopy);
    }

    private BlockingState findEntitlementCancellationBlockingState(@Nullable final UUID blockedId, final Iterable<BlockingState> blockingStatesOnDisk) {
        if (blockedId == null) {
            return null;
        }

        return Iterables.toStream(blockingStatesOnDisk)
                        .filter(input -> input.getBlockedId().equals(blockedId) && isEntitlementCancellationBlockingState(input))
                        .findFirst().orElse(null);
    }

    private static boolean isEntitlementCancellationBlockingState(final BlockingState blockingState) {
        return BlockingStateType.SUBSCRIPTION.equals(blockingState.getType()) &&
               KILLBILL_SERVICES.ENTITLEMENT_SERVICE.getServiceName().equals(blockingState.getService()) &&
               DefaultEntitlementApi.ENT_STATE_CANCELLED.equals(blockingState.getStateName());
    }
}
