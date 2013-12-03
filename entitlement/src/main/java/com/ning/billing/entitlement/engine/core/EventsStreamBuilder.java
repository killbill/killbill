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

package com.ning.billing.entitlement.engine.core;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.skife.jdbi.v2.IDBI;

import com.ning.billing.ObjectType;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountInternalApi;
import com.ning.billing.callcontext.InternalTenantContext;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.clock.Clock;
import com.ning.billing.entitlement.AccountEventsStreams;
import com.ning.billing.entitlement.EventsStream;
import com.ning.billing.entitlement.api.BlockingState;
import com.ning.billing.entitlement.api.BlockingStateType;
import com.ning.billing.entitlement.api.EntitlementApiException;
import com.ning.billing.entitlement.api.svcs.DefaultAccountEventsStreams;
import com.ning.billing.entitlement.block.BlockingChecker;
import com.ning.billing.entitlement.dao.DefaultBlockingStateDao;
import com.ning.billing.entitlement.dao.OptimizedProxyBlockingStateDao;
import com.ning.billing.subscription.api.SubscriptionBase;
import com.ning.billing.subscription.api.SubscriptionBaseInternalApi;
import com.ning.billing.subscription.api.user.SubscriptionBaseApiException;
import com.ning.billing.subscription.api.user.SubscriptionBaseBundle;
import com.ning.billing.util.cache.CacheControllerDispatcher;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.callcontext.TenantContext;
import com.ning.billing.util.dao.NonEntityDao;

import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import static com.ning.billing.entitlement.dao.ProxyBlockingStateDao.BLOCKING_STATE_ORDERING;

@Singleton
public class EventsStreamBuilder {

    private final AccountInternalApi accountInternalApi;
    private final SubscriptionBaseInternalApi subscriptionInternalApi;
    private final BlockingChecker checker;
    private final OptimizedProxyBlockingStateDao blockingStateDao;
    private final DefaultBlockingStateDao defaultBlockingStateDao;
    private final Clock clock;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public EventsStreamBuilder(final AccountInternalApi accountInternalApi, final SubscriptionBaseInternalApi subscriptionInternalApi,
                               final BlockingChecker checker, final IDBI dbi, final Clock clock,
                               final CacheControllerDispatcher cacheControllerDispatcher,
                               final NonEntityDao nonEntityDao,
                               final InternalCallContextFactory internalCallContextFactory) {
        this.accountInternalApi = accountInternalApi;
        this.subscriptionInternalApi = subscriptionInternalApi;
        this.checker = checker;
        this.clock = clock;
        this.internalCallContextFactory = internalCallContextFactory;

        this.defaultBlockingStateDao = new DefaultBlockingStateDao(dbi, clock, cacheControllerDispatcher, nonEntityDao);
        this.blockingStateDao = new OptimizedProxyBlockingStateDao(this, subscriptionInternalApi, dbi, clock, cacheControllerDispatcher, nonEntityDao);
    }

    public EventsStream refresh(final EventsStream eventsStream, final TenantContext tenantContext) throws EntitlementApiException {
        return buildForEntitlement(eventsStream.getEntitlementId(), tenantContext);
    }

    public EventsStream buildForBaseSubscription(final UUID bundleId, final TenantContext tenantContext) throws EntitlementApiException {
        final SubscriptionBase baseSubscription;
        try {
            final InternalTenantContext internalTenantContext = internalCallContextFactory.createInternalTenantContext(tenantContext);
            baseSubscription = subscriptionInternalApi.getBaseSubscription(bundleId, internalTenantContext);
        } catch (SubscriptionBaseApiException e) {
            throw new EntitlementApiException(e);
        }

        return buildForEntitlement(baseSubscription.getId(), tenantContext);
    }

    public EventsStream buildForEntitlement(final UUID entitlementId, final TenantContext tenantContext) throws EntitlementApiException {
        final InternalTenantContext internalTenantContext = internalCallContextFactory.createInternalTenantContext(entitlementId, ObjectType.SUBSCRIPTION, tenantContext);
        return buildForEntitlement(entitlementId, internalTenantContext);
    }

    public AccountEventsStreams buildForAccount(final InternalTenantContext internalTenantContext) throws EntitlementApiException {
        // Retrieve the subscriptions (map bundle id -> subscriptions)
        final Map<UUID, List<SubscriptionBase>> subscriptions = subscriptionInternalApi.getSubscriptionsForAccount(internalTenantContext);
        return buildForAccount(subscriptions, internalTenantContext);
    }

    // Special signature for ProxyBlockingStateDao to save a DAO call
    public AccountEventsStreams buildForAccount(final Map<UUID, List<SubscriptionBase>> subscriptions, final InternalTenantContext internalTenantContext) throws EntitlementApiException {
        // Retrieve the account
        final Account account;
        try {
            account = accountInternalApi.getAccountByRecordId(internalTenantContext.getAccountRecordId(), internalTenantContext);
        } catch (AccountApiException e) {
            throw new EntitlementApiException(e);
        }

        if (subscriptions.isEmpty()) {
            // Bail early
            return new DefaultAccountEventsStreams(account);
        }

        // Retrieve the bundles
        final List<SubscriptionBaseBundle> bundles = subscriptionInternalApi.getBundlesForAccount(account.getId(), internalTenantContext);
        // Map bundle id -> bundles
        final Map<UUID, SubscriptionBaseBundle> bundlesPerId = new HashMap<UUID, SubscriptionBaseBundle>();
        for (final SubscriptionBaseBundle bundle : bundles) {
            bundlesPerId.put(bundle.getId(), bundle);
        }

        // Retrieve the blocking states
        final List<BlockingState> blockingStatesForAccount = defaultBlockingStateDao.getBlockingAllForAccountRecordId(internalTenantContext);

        // Optimization: build lookup tables for blocking states states
        final Collection<BlockingState> accountBlockingStates = new LinkedList<BlockingState>();
        final Map<UUID, List<BlockingState>> blockingStatesPerSubscription = new HashMap<UUID, List<BlockingState>>();
        final Map<UUID, List<BlockingState>> blockingStatesPerBundle = new HashMap<UUID, List<BlockingState>>();
        for (final BlockingState blockingState : blockingStatesForAccount) {
            if (BlockingStateType.SUBSCRIPTION.equals(blockingState.getType())) {
                if (blockingStatesPerSubscription.get(blockingState.getBlockedId()) == null) {
                    blockingStatesPerSubscription.put(blockingState.getBlockedId(), new LinkedList<BlockingState>());
                }
                blockingStatesPerSubscription.get(blockingState.getBlockedId()).add(blockingState);
            } else if (BlockingStateType.SUBSCRIPTION_BUNDLE.equals(blockingState.getType())) {
                if (blockingStatesPerBundle.get(blockingState.getBlockedId()) == null) {
                    blockingStatesPerBundle.put(blockingState.getBlockedId(), new LinkedList<BlockingState>());
                }
                blockingStatesPerBundle.get(blockingState.getBlockedId()).add(blockingState);
            } else if (BlockingStateType.ACCOUNT.equals(blockingState.getType()) &&
                       account.getId().equals(blockingState.getBlockedId())) {
                accountBlockingStates.add(blockingState);
            }
        }

        // Build the EventsStream objects
        final Map<UUID, Collection<EventsStream>> entitlementsPerBundle = new HashMap<UUID, Collection<EventsStream>>();
        for (final UUID bundleId : subscriptions.keySet()) {
            final SubscriptionBaseBundle bundle = bundlesPerId.get(bundleId);
            final List<SubscriptionBase> allSubscriptionsForBundle = subscriptions.get(bundleId);
            final SubscriptionBase baseSubscription = Iterables.<SubscriptionBase>tryFind(allSubscriptionsForBundle,
                                                                                          new Predicate<SubscriptionBase>() {
                                                                                              @Override
                                                                                              public boolean apply(final SubscriptionBase input) {
                                                                                                  return ProductCategory.BASE.equals(input.getLastActiveProduct().getCategory());
                                                                                              }
                                                                                          }).orNull();
            final List<BlockingState> bundleBlockingStates = Objects.firstNonNull(blockingStatesPerBundle.get(bundleId), ImmutableList.<BlockingState>of());

            if (entitlementsPerBundle.get(bundleId) == null) {
                entitlementsPerBundle.put(bundleId, new LinkedList<EventsStream>());
            }

            for (final SubscriptionBase subscription : allSubscriptionsForBundle) {
                final List<BlockingState> subscriptionBlockingStatesOnDisk = Objects.firstNonNull(blockingStatesPerSubscription.get(subscription.getId()), ImmutableList.<BlockingState>of());

                // We cannot always use blockingStatesForAccount here: we need subscriptionBlockingStates to contain the events not on disk when building an EventsStream
                // for an add-on - which means going through the magic of ProxyBlockingStateDao, which will recursively
                // create EventsStream objects. To avoid an infinite recursion, bypass ProxyBlockingStateDao when it's not
                // needed, i.e. if this EventStream is for a standalone or a base subscription
                final List<BlockingState> subscriptionBlockingStates;
                if (baseSubscription == null || subscription.getId().equals(baseSubscription.getId())) {
                    subscriptionBlockingStates = subscriptionBlockingStatesOnDisk;
                } else {
                    subscriptionBlockingStates = blockingStateDao.getBlockingHistory(subscriptionBlockingStatesOnDisk,
                                                                                     blockingStatesForAccount,
                                                                                     account,
                                                                                     bundle,
                                                                                     baseSubscription,
                                                                                     subscription,
                                                                                     allSubscriptionsForBundle,
                                                                                     internalTenantContext);

                }

                // Merge the BlockingStates
                final Collection<BlockingState> blockingStateSet = new LinkedHashSet<BlockingState>(accountBlockingStates);
                blockingStateSet.addAll(bundleBlockingStates);
                blockingStateSet.addAll(subscriptionBlockingStates);
                final List<BlockingState> blockingStates = BLOCKING_STATE_ORDERING.immutableSortedCopy(blockingStateSet);

                final EventsStream eventStream = buildForEntitlement(account, bundle, baseSubscription, subscription, allSubscriptionsForBundle, blockingStates, internalTenantContext);
                entitlementsPerBundle.get(bundleId).add(eventStream);
            }
        }

        return new DefaultAccountEventsStreams(account, bundles, entitlementsPerBundle);
    }

    public EventsStream buildForEntitlement(final UUID entitlementId, final InternalTenantContext internalTenantContext) throws EntitlementApiException {
        final SubscriptionBaseBundle bundle;
        final SubscriptionBase subscription;
        final List<SubscriptionBase> allSubscriptionsForBundle;
        final SubscriptionBase baseSubscription;
        try {
            subscription = subscriptionInternalApi.getSubscriptionFromId(entitlementId, internalTenantContext);
            bundle = subscriptionInternalApi.getBundleFromId(subscription.getBundleId(), internalTenantContext);
            allSubscriptionsForBundle = subscriptionInternalApi.getSubscriptionsForBundle(subscription.getBundleId(), internalTenantContext);
            baseSubscription = Iterables.<SubscriptionBase>tryFind(allSubscriptionsForBundle,
                                                                   new Predicate<SubscriptionBase>() {
                                                                       @Override
                                                                       public boolean apply(final SubscriptionBase input) {
                                                                           return ProductCategory.BASE.equals(input.getLastActiveProduct().getCategory());
                                                                       }
                                                                   }).orNull(); // null for standalone subscriptions
        } catch (SubscriptionBaseApiException e) {
            throw new EntitlementApiException(e);
        }

        final Account account;
        try {
            account = accountInternalApi.getAccountById(bundle.getAccountId(), internalTenantContext);
        } catch (AccountApiException e) {
            throw new EntitlementApiException(e);
        }

        // Retrieve the blocking states
        final List<BlockingState> blockingStatesForAccount = defaultBlockingStateDao.getBlockingAllForAccountRecordId(internalTenantContext);

        return buildForEntitlement(blockingStatesForAccount, account, bundle, baseSubscription, subscription, allSubscriptionsForBundle, internalTenantContext);
    }

    // Special signature for OptimizedProxyBlockingStateDao to save some DAO calls
    public EventsStream buildForEntitlement(final List<BlockingState> blockingStatesForAccount,
                                            final Account account,
                                            final SubscriptionBaseBundle bundle,
                                            final SubscriptionBase baseSubscription,
                                            final List<SubscriptionBase> allSubscriptionsForBundle,
                                            final InternalTenantContext internalTenantContext) throws EntitlementApiException {
        return buildForEntitlement(blockingStatesForAccount, account, bundle, baseSubscription, baseSubscription, allSubscriptionsForBundle, internalTenantContext);
    }

    private EventsStream buildForEntitlement(final List<BlockingState> blockingStatesForAccount,
                                             final Account account,
                                             final SubscriptionBaseBundle bundle,
                                             @Nullable final SubscriptionBase baseSubscription,
                                             final SubscriptionBase subscription,
                                             final List<SubscriptionBase> allSubscriptionsForBundle,
                                             final InternalTenantContext internalTenantContext) throws EntitlementApiException {
        // Optimization: build lookup tables for blocking states states
        final Collection<BlockingState> accountBlockingStates = new LinkedList<BlockingState>();
        final Map<UUID, List<BlockingState>> blockingStatesPerSubscription = new HashMap<UUID, List<BlockingState>>();
        final Map<UUID, List<BlockingState>> blockingStatesPerBundle = new HashMap<UUID, List<BlockingState>>();
        for (final BlockingState blockingState : blockingStatesForAccount) {
            if (BlockingStateType.SUBSCRIPTION.equals(blockingState.getType())) {
                if (blockingStatesPerSubscription.get(blockingState.getBlockedId()) == null) {
                    blockingStatesPerSubscription.put(blockingState.getBlockedId(), new LinkedList<BlockingState>());
                }
                blockingStatesPerSubscription.get(blockingState.getBlockedId()).add(blockingState);
            } else if (BlockingStateType.SUBSCRIPTION_BUNDLE.equals(blockingState.getType())) {
                if (blockingStatesPerBundle.get(blockingState.getBlockedId()) == null) {
                    blockingStatesPerBundle.put(blockingState.getBlockedId(), new LinkedList<BlockingState>());
                }
                blockingStatesPerBundle.get(blockingState.getBlockedId()).add(blockingState);
            } else if (BlockingStateType.ACCOUNT.equals(blockingState.getType()) &&
                       account.getId().equals(blockingState.getBlockedId())) {
                accountBlockingStates.add(blockingState);
            }
        }

        final List<BlockingState> bundleBlockingStates = Objects.firstNonNull(blockingStatesPerBundle.get(subscription.getBundleId()), ImmutableList.<BlockingState>of());
        final List<BlockingState> subscriptionBlockingStatesOnDisk = Objects.firstNonNull(blockingStatesPerSubscription.get(subscription.getId()), ImmutableList.<BlockingState>of());

        // We cannot always use blockingStatesForAccount here: we need subscriptionBlockingStates to contain the events not on disk when building an EventsStream
        // for an add-on - which means going through the magic of ProxyBlockingStateDao, which will recursively
        // create EventsStream objects. To avoid an infinite recursion, bypass ProxyBlockingStateDao when it's not
        // needed, i.e. if this EventStream is for a standalone or a base subscription
        final Collection<BlockingState> subscriptionBlockingStates;
        if (baseSubscription == null || subscription.getId().equals(baseSubscription.getId())) {
            // Note: we come here during the recursion from OptimizedProxyBlockingStateDao#getBlockingHistory
            // (called by blockingStateDao.getBlockingHistory below)
            subscriptionBlockingStates = subscriptionBlockingStatesOnDisk;
        } else {
            subscriptionBlockingStates = blockingStateDao.getBlockingHistory(ImmutableList.<BlockingState>copyOf(subscriptionBlockingStatesOnDisk),
                                                                             blockingStatesForAccount,
                                                                             account,
                                                                             bundle,
                                                                             baseSubscription,
                                                                             subscription,
                                                                             allSubscriptionsForBundle,
                                                                             internalTenantContext);
        }

        // Merge the BlockingStates
        final Collection<BlockingState> blockingStateSet = new LinkedHashSet<BlockingState>(accountBlockingStates);
        blockingStateSet.addAll(bundleBlockingStates);
        blockingStateSet.addAll(subscriptionBlockingStates);
        final List<BlockingState> blockingStates = BLOCKING_STATE_ORDERING.immutableSortedCopy(blockingStateSet);

        return buildForEntitlement(account, bundle, baseSubscription, subscription, allSubscriptionsForBundle, blockingStates, internalTenantContext);
    }

    private EventsStream buildForEntitlement(final Account account,
                                             final SubscriptionBaseBundle bundle,
                                             @Nullable final SubscriptionBase baseSubscription,
                                             final SubscriptionBase subscription,
                                             final List<SubscriptionBase> allSubscriptionsForBundle,
                                             final List<BlockingState> blockingStates,
                                             final InternalTenantContext internalTenantContext) throws EntitlementApiException {
        return new DefaultEventsStream(account,
                                       bundle,
                                       blockingStates,
                                       checker,
                                       baseSubscription,
                                       subscription,
                                       allSubscriptionsForBundle,
                                       internalTenantContext,
                                       clock.getUTCNow());
    }
}
