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
import com.ning.billing.entitlement.api.BlockingState;
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

    private static final Ordering<BlockingState> BLOCKING_STATE_ORDERING = Ordering.<BlockingState>from(new Comparator<BlockingState>() {
        @Override
        public int compare(final BlockingState o1, final BlockingState o2) {
            final int blockableIdComparison = o1.getBlockedId().compareTo(o2.getBlockedId());
            if (blockableIdComparison == 0) {
                // effective_date column NOT NULL
                final int comparison = o1.getEffectiveDate().compareTo(o2.getEffectiveDate());
                if (comparison == 0) {
                    // Keep a stable ordering for ties
                    return o1.getCreatedDate().compareTo(o2.getCreatedDate());
                } else {
                    return comparison;
                }
            } else {
                return blockableIdComparison;
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
    public BlockingState getBlockingStateForService(final UUID blockableId, final String serviceName, final InternalTenantContext context) {
        return delegate.getBlockingStateForService(blockableId, serviceName, context);
    }

    @Override
    public List<BlockingState> getBlockingState(final UUID blockableId, final InternalTenantContext context) {
        return delegate.getBlockingState(blockableId, context);
    }

    @Override
    public List<BlockingState> getBlockingHistoryForService(final UUID blockableId, final String serviceName, final InternalTenantContext context) {
        final List<BlockingState> statesOnDisk = delegate.getBlockingHistoryForService(blockableId, serviceName, context);
        return addBlockingStatesNotOnDisk(blockableId, statesOnDisk, context);
    }

    @Override
    public List<BlockingState> getBlockingAll(final UUID blockableId, final InternalTenantContext context) {
        final List<BlockingState> statesOnDisk = delegate.getBlockingAll(blockableId, context);
        return addBlockingStatesNotOnDisk(blockableId, statesOnDisk, context);
    }

    @Override
    public List<BlockingState> getBlockingAllForAccountRecordId(final InternalTenantContext context) {
        final List<BlockingState> statesOnDisk = delegate.getBlockingAllForAccountRecordId(context);
        return addBlockingStatesNotOnDisk(null, statesOnDisk, context);
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
                                                           final List<BlockingState> blockingStatesOnDisk,
                                                           final InternalTenantContext context) {
        final Collection<BlockingState> blockingStatesOnDiskCopy = new LinkedList<BlockingState>(blockingStatesOnDisk);

        // Find all base entitlements that we care about (for which we want to find future cancelled add-ons)
        final Iterable<SubscriptionBase> baseSubscriptionsToConsider;
        try {
            if (blockableId == null) {
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
            } else {
                // We're coming from getBlockingHistoryForService / getBlockingAll, but we don't know the blocking type
                final SubscriptionBase addOnSubscription;
                try {
                    addOnSubscription = subscriptionInternalApi.getSubscriptionFromId(blockableId, context);
                } catch (SubscriptionBaseApiException ignored) {
                    // blockable id points to an account or bundle, in which case there are no extra blocking states to add
                    return blockingStatesOnDisk;
                }

                // blockable id points to a subscription, but make sure it's an add-on
                if (ProductCategory.ADD_ON.equals(addOnSubscription.getCategory())) {
                    final SubscriptionBase baseSubscription = subscriptionInternalApi.getBaseSubscription(addOnSubscription.getBundleId(), context);
                    baseSubscriptionsToConsider = ImmutableList.<SubscriptionBase>of(baseSubscription);
                } else {
                    // blockable id points to a base or standalone subscription, there is nothing to do
                    return blockingStatesOnDisk;
                }
            }
        } catch (SubscriptionBaseApiException e) {
            log.error("Error retrieving subscriptions for account record id " + context.getAccountRecordId(), e);
            throw new RuntimeException(e);
        }

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

            final Collection<BlockingState> blockingStatesNotOnDisk = eventsStream.computeAddonsBlockingStatesForFutureSubscriptionBaseEvents();

            // Inject the extra blocking states into the stream
            for (final BlockingState blockingState : blockingStatesNotOnDisk) {
                final BlockingStateModelDao blockingStateModelDao = new BlockingStateModelDao(blockingState, now, now);
                blockingStatesOnDiskCopy.add(BlockingStateModelDao.toBlockingState(blockingStateModelDao));
            }
        }

        // Return the sorted list
        return BLOCKING_STATE_ORDERING.immutableSortedCopy(blockingStatesOnDiskCopy);
    }
}
