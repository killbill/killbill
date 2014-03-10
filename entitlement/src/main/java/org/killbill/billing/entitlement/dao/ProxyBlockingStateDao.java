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

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.joda.time.DateTime;
import org.skife.jdbi.v2.IDBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.clock.Clock;
import org.killbill.billing.entitlement.EntitlementService;
import org.killbill.billing.entitlement.EventsStream;
import org.killbill.billing.entitlement.api.BlockingState;
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.killbill.billing.entitlement.api.DefaultEntitlementApi;
import org.killbill.billing.entitlement.api.EntitlementApiException;
import org.killbill.billing.entitlement.engine.core.EventsStreamBuilder;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseInternalApi;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.customfield.ShouldntHappenException;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.billing.util.entity.Pagination;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;

@Singleton
public class ProxyBlockingStateDao implements BlockingStateDao {

    private static final Logger log = LoggerFactory.getLogger(ProxyBlockingStateDao.class);

    // Ordering is critical here, especially for Junction
    public static List<BlockingState> sortedCopy(final Iterable<BlockingState> blockingStates) {
        final List<BlockingState> blockingStatesSomewhatSorted = BLOCKING_STATE_ORDERING_WITH_TIES_UNHANDLED.immutableSortedCopy(blockingStates);

        final List<BlockingState> result = new LinkedList<BlockingState>();

        // Take care of the ties
        final Iterator<BlockingState> iterator = blockingStatesSomewhatSorted.iterator();
        BlockingState prev = null;
        while (iterator.hasNext()) {
            final BlockingState current = iterator.next();
            if (iterator.hasNext()) {
                final BlockingState next = iterator.next();
                if (prev != null && current.getEffectiveDate().equals(next.getEffectiveDate()) && current.getBlockedId().equals(next.getBlockedId())) {
                    // Same date, same blockable id

                    // Make sure block billing transitions are respected first
                    BlockingState prevCandidate = insertTiedBlockingStatesInTheRightOrder(result, current, next, prev.isBlockBilling(), current.isBlockBilling(), next.isBlockBilling());
                    if (prevCandidate == null) {
                        // Then respect block entitlement transitions
                        prevCandidate = insertTiedBlockingStatesInTheRightOrder(result, current, next, prev.isBlockEntitlement(), current.isBlockEntitlement(), next.isBlockEntitlement());
                        if (prevCandidate == null) {
                            // And finally block changes transitions
                            prevCandidate = insertTiedBlockingStatesInTheRightOrder(result, current, next, prev.isBlockChange(), current.isBlockChange(), next.isBlockChange());
                            if (prevCandidate == null) {
                                // Trust the creation date (see BLOCKING_STATE_ORDERING_WITH_TIES_UNHANDLED below)
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

    private static final Ordering<BlockingState> BLOCKING_STATE_ORDERING_WITH_TIES_UNHANDLED = Ordering.<BlockingState>from(new Comparator<BlockingState>() {
        @Override
        public int compare(final BlockingState o1, final BlockingState o2) {
            // effective_date column NOT NULL
            final int effectiveDateComparison = o1.getEffectiveDate().compareTo(o2.getEffectiveDate());
            if (effectiveDateComparison != 0) {
                return effectiveDateComparison;
            } else {
                final int blockableIdComparison = o1.getBlockedId().compareTo(o2.getBlockedId());
                if (blockableIdComparison != 0) {
                    return blockableIdComparison;
                } else {
                    // Same date, same blockable id, just respect the created date for now (see sortedCopyOf method above)
                    return o1.getCreatedDate().compareTo(o2.getCreatedDate());
                }
            }
        }
    });

    private final SubscriptionBaseInternalApi subscriptionInternalApi;
    private final Clock clock;

    protected final EventsStreamBuilder eventsStreamBuilder;
    protected final DefaultBlockingStateDao delegate;

    @Inject
    public ProxyBlockingStateDao(final EventsStreamBuilder eventsStreamBuilder, final SubscriptionBaseInternalApi subscriptionBaseInternalApi,
                                 final IDBI dbi, final Clock clock,
                                 final CacheControllerDispatcher cacheControllerDispatcher, final NonEntityDao nonEntityDao) {
        this.eventsStreamBuilder = eventsStreamBuilder;
        this.subscriptionInternalApi = subscriptionBaseInternalApi;
        this.clock = clock;
        this.delegate = new DefaultBlockingStateDao(dbi, clock, cacheControllerDispatcher, nonEntityDao);
    }

    @Override
    public void create(final BlockingStateModelDao entity, final InternalCallContext context) throws EntitlementApiException {
        delegate.create(entity, context);
    }

    @Override
    public Long getRecordId(final UUID id, final InternalTenantContext context) {
        return delegate.getRecordId(id, context);
    }

    @Override
    public BlockingStateModelDao getByRecordId(final Long recordId, final InternalTenantContext context) {
        return delegate.getByRecordId(recordId, context);
    }

    @Override
    public BlockingStateModelDao getById(final UUID id, final InternalTenantContext context) {
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
    public List<BlockingState> getBlockingState(final UUID blockableId, final BlockingStateType blockingStateType, final InternalTenantContext context) {
        return delegate.getBlockingState(blockableId, blockingStateType, context);
    }

    @Override
    public List<BlockingState> getBlockingAllForAccountRecordId(final InternalTenantContext context) {
        final List<BlockingState> statesOnDisk = delegate.getBlockingAllForAccountRecordId(context);
        return addBlockingStatesNotOnDisk(statesOnDisk, context);
    }

    @Override
    public void setBlockingState(final BlockingState state, final Clock clock, final InternalCallContext context) {
        delegate.setBlockingState(state, clock, context);
    }

    @Override
    public void unactiveBlockingState(final UUID blockableId, final InternalCallContext context) {
        delegate.unactiveBlockingState(blockableId, context);
    }

    // Add blocking states for add-ons, which would be impacted by a future cancellation or change of their base plan
    // See DefaultEntitlement#blockAddOnsIfRequired
    private List<BlockingState> addBlockingStatesNotOnDisk(final List<BlockingState> blockingStatesOnDisk,
                                                           final InternalTenantContext context) {
        final Collection<BlockingState> blockingStatesOnDiskCopy = new LinkedList<BlockingState>(blockingStatesOnDisk);

        // Find all base entitlements that we care about (for which we want to find future cancelled add-ons)
        final Iterable<SubscriptionBase> baseSubscriptionsToConsider;
        final Iterable<EventsStream> eventsStreams;
        try {
            final Map<UUID, List<SubscriptionBase>> subscriptions = subscriptionInternalApi.getSubscriptionsForAccount(context);
            baseSubscriptionsToConsider = Iterables.<SubscriptionBase>filter(Iterables.<SubscriptionBase>concat(subscriptions.values()),
                                                                             new Predicate<SubscriptionBase>() {
                                                                                 @Override
                                                                                 public boolean apply(final SubscriptionBase input) {
                                                                                     return ProductCategory.BASE.equals(input.getCategory());
                                                                                 }
                                                                             });
            eventsStreams = Iterables.<EventsStream>concat(eventsStreamBuilder.buildForAccount(subscriptions, context).getEventsStreams().values());
        } catch (EntitlementApiException e) {
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
        // Compute the blocking states not on disk for all base subscriptions
        final DateTime now = clock.getUTCNow();
        for (final SubscriptionBase baseSubscription : baseSubscriptionsToConsider) {
            final EventsStream eventsStream = Iterables.<EventsStream>find(eventsStreams,
                                                                           new Predicate<EventsStream>() {
                                                                               @Override
                                                                               public boolean apply(final EventsStream input) {
                                                                                   return input.getSubscriptionBase().getId().equals(baseSubscription.getId());
                                                                               }
                                                                           });

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

        return Iterables.<BlockingState>tryFind(blockingStatesOnDisk,
                                                new Predicate<BlockingState>() {
                                                    @Override
                                                    public boolean apply(final BlockingState input) {
                                                        return input.getBlockedId().equals(blockedId) &&
                                                               isEntitlementCancellationBlockingState(input);
                                                    }
                                                })
                        .orNull();
    }

    private static boolean isEntitlementCancellationBlockingState(final BlockingState blockingState) {
        return BlockingStateType.SUBSCRIPTION.equals(blockingState.getType()) &&
               EntitlementService.ENTITLEMENT_SERVICE_NAME.equals(blockingState.getService()) &&
               DefaultEntitlementApi.ENT_STATE_CANCELLED.equals(blockingState.getStateName());
    }
}
