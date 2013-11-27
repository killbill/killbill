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

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.joda.time.DateTime;
import org.skife.jdbi.v2.IDBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.callcontext.InternalCallContext;
import com.ning.billing.callcontext.InternalTenantContext;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.clock.Clock;
import com.ning.billing.entitlement.EntitlementService;
import com.ning.billing.entitlement.api.BlockingState;
import com.ning.billing.entitlement.api.BlockingStateType;
import com.ning.billing.entitlement.api.DefaultEntitlementApi;
import com.ning.billing.entitlement.api.Entitlement.EntitlementState;
import com.ning.billing.entitlement.api.EntitlementApiException;
import com.ning.billing.entitlement.engine.core.EventsStream;
import com.ning.billing.entitlement.engine.core.EventsStreamBuilder;
import com.ning.billing.subscription.api.SubscriptionBase;
import com.ning.billing.subscription.api.SubscriptionBaseInternalApi;
import com.ning.billing.subscription.api.user.SubscriptionBaseApiException;
import com.ning.billing.util.cache.CacheControllerDispatcher;
import com.ning.billing.util.dao.NonEntityDao;
import com.ning.billing.util.entity.Pagination;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;

@Singleton
public class ProxyBlockingStateDao implements BlockingStateDao {

    private static final Logger log = LoggerFactory.getLogger(ProxyBlockingStateDao.class);

    // Ordering is critical here, especially for Junction
    private static final Ordering<BlockingState> BLOCKING_STATE_ORDERING = Ordering.<BlockingState>from(new Comparator<BlockingState>() {
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
                    // Same date, same blockable id - make sure billing transitions are respected first (assume block -> clear transitions)
                    if (!o1.isBlockBilling() && o2.isBlockBilling()) {
                        return 1;
                    } else if (o1.isBlockBilling() && !o2.isBlockBilling()) {
                        return -1;
                    }

                    // Then respect other blocking states
                    if ((!o1.isBlockChange() && o2.isBlockChange()) ||
                        (!o1.isBlockEntitlement() && o2.isBlockEntitlement())) {
                        return 1;
                    } else if ((o1.isBlockChange() && !o2.isBlockChange()) ||
                               (o1.isBlockEntitlement() && !o2.isBlockEntitlement())) {
                        return -1;
                    }

                    // Otherwise, just respect the created date
                    return o1.getCreatedDate().compareTo(o2.getCreatedDate());
                }
            }
        }
    });

    private final EventsStreamBuilder eventsStreamBuilder;
    private final SubscriptionBaseInternalApi subscriptionInternalApi;
    private final Clock clock;
    private final DefaultBlockingStateDao delegate;

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
    public List<BlockingState> getBlockingHistoryForService(final UUID blockableId, final BlockingStateType blockingStateType, final String serviceName, final InternalTenantContext context) {
        final List<BlockingState> statesOnDisk = delegate.getBlockingHistoryForService(blockableId, blockingStateType, serviceName, context);
        return addBlockingStatesNotOnDisk(blockableId, blockingStateType, statesOnDisk, context);
    }

    @Override
    public List<BlockingState> getBlockingAll(final UUID blockableId, final BlockingStateType blockingStateType, final InternalTenantContext context) {
        final List<BlockingState> statesOnDisk = delegate.getBlockingAll(blockableId, blockingStateType, context);
        return addBlockingStatesNotOnDisk(blockableId, blockingStateType, statesOnDisk, context);
    }

    @Override
    public List<BlockingState> getBlockingAllForAccountRecordId(final InternalTenantContext context) {
        final List<BlockingState> statesOnDisk = delegate.getBlockingAllForAccountRecordId(context);
        return addBlockingStatesNotOnDisk(null, null, statesOnDisk, context);
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
    private List<BlockingState> addBlockingStatesNotOnDisk(@Nullable final UUID blockableId,
                                                           @Nullable final BlockingStateType blockingStateType,
                                                           final List<BlockingState> blockingStatesOnDisk,
                                                           final InternalTenantContext context) {
        final Collection<BlockingState> blockingStatesOnDiskCopy = new LinkedList<BlockingState>(blockingStatesOnDisk);

        // Find all base entitlements that we care about (for which we want to find future cancelled add-ons)
        final Iterable<SubscriptionBase> baseSubscriptionsToConsider;
        try {
            if (blockingStateType == null) {
                // We're coming from getBlockingAllForAccountRecordId
                final Iterable<SubscriptionBase> subscriptions = Iterables.<SubscriptionBase>concat(subscriptionInternalApi.getSubscriptionsForAccount(context).values());
                baseSubscriptionsToConsider = Iterables.<SubscriptionBase>filter(subscriptions,
                                                                                 new Predicate<SubscriptionBase>() {
                                                                                     @Override
                                                                                     public boolean apply(final SubscriptionBase input) {
                                                                                         return ProductCategory.BASE.equals(input.getCategory()) &&
                                                                                                !EntitlementState.CANCELLED.equals(input.getState());
                                                                                     }
                                                                                 });
            } else if (BlockingStateType.SUBSCRIPTION.equals(blockingStateType)) {
                // We're coming from getBlockingHistoryForService / getBlockingAll
                final SubscriptionBase subscription = subscriptionInternalApi.getSubscriptionFromId(blockableId, context);

                // blockable id points to a subscription, but make sure it's an add-on
                if (ProductCategory.ADD_ON.equals(subscription.getCategory())) {
                    final SubscriptionBase baseSubscription = subscriptionInternalApi.getBaseSubscription(subscription.getBundleId(), context);
                    baseSubscriptionsToConsider = ImmutableList.<SubscriptionBase>of(baseSubscription);
                } else {
                    // blockable id points to a base or standalone subscription, there is nothing to do
                    // Simply return the sorted list
                    return BLOCKING_STATE_ORDERING.immutableSortedCopy(blockingStatesOnDisk);
                }
            } else {
                // blockable id points to an account or bundle, in which case there are no extra blocking states to add
                // Simply return the sorted list
                return BLOCKING_STATE_ORDERING.immutableSortedCopy(blockingStatesOnDisk);
            }
        } catch (SubscriptionBaseApiException e) {
            log.error("Error retrieving subscriptions for account record id " + context.getAccountRecordId(), e);
            throw new RuntimeException(e);
        }

        // Retrieve the cancellation blocking state on disk, if it exists (will be used later)
        final BlockingState cancellationBlockingStateOnDisk = findEntitlementCancellationBlockingState(blockableId, blockingStatesOnDiskCopy);

        // Compute the blocking states not on disk for all base subscriptions
        final DateTime now = clock.getUTCNow();
        for (final SubscriptionBase baseSubscription : baseSubscriptionsToConsider) {
            final EventsStream eventsStream;
            try {
                eventsStream = eventsStreamBuilder.buildForEntitlement(baseSubscription.getId(), context);
            } catch (EntitlementApiException e) {
                log.error("Error computing blocking states for addons for account record id " + context.getAccountRecordId(), e);
                throw new RuntimeException(e);
            }

            // First, check to see if the base entitlement is cancelled. If so, cancel the
            final Collection<BlockingState> blockingStatesNotOnDisk = eventsStream.computeAddonsBlockingStatesForFutureSubscriptionBaseEvents();

            // Inject the extra blocking states into the stream if needed
            for (final BlockingState blockingState : blockingStatesNotOnDisk) {
                // If this entitlement is actually already cancelled, add the cancellation event we computed
                // only if it's prior to the blocking state on disk (e.g. add-on future cancelled but base plan cancelled earlier).
                final boolean overrideCancellationBlockingStateOnDisk = cancellationBlockingStateOnDisk != null &&
                                                                        isEntitlementCancellationBlockingState(blockingState) &&
                                                                        blockingState.getEffectiveDate().isBefore(cancellationBlockingStateOnDisk.getEffectiveDate());

                if ((
                            blockingStateType == null ||
                            // In case we're coming from getBlockingHistoryForService / getBlockingAll, make sure we don't add
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
        return BLOCKING_STATE_ORDERING.immutableSortedCopy(blockingStatesOnDiskCopy);
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
