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
import com.ning.billing.entitlement.EntitlementService;
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

        return buildForAccount(account, subscriptions, internalTenantContext);
    }

    private AccountEventsStreams buildForAccount(final Account account, final Map<UUID, List<SubscriptionBase>> subscriptions, final InternalTenantContext internalTenantContext) throws EntitlementApiException {
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
        final List<BlockingState> blockingStatesForAccount = BLOCKING_STATE_ORDERING.immutableSortedCopy(defaultBlockingStateDao.getBlockingAllForAccountRecordId(internalTenantContext));

        // Optimization: build lookup tables for entitlement states
        final List<BlockingState> accountEntitlementStates = new LinkedList<BlockingState>();
        final Map<UUID, List<BlockingState>> entitlementStatesPerSubscription = new HashMap<UUID, List<BlockingState>>();
        final Map<UUID, List<BlockingState>> entitlementStatesPerBundle = new HashMap<UUID, List<BlockingState>>();
        for (final BlockingState blockingState : blockingStatesForAccount) {
            if (!EntitlementService.ENTITLEMENT_SERVICE_NAME.equals(blockingState.getService())) {
                continue;
            } else if (BlockingStateType.SUBSCRIPTION.equals(blockingState.getType())) {
                if (entitlementStatesPerSubscription.get(blockingState.getBlockedId()) == null) {
                    entitlementStatesPerSubscription.put(blockingState.getBlockedId(), new LinkedList<BlockingState>());
                }
                entitlementStatesPerSubscription.get(blockingState.getBlockedId()).add(blockingState);
            } else if (BlockingStateType.SUBSCRIPTION_BUNDLE.equals(blockingState.getType())) {
                if (entitlementStatesPerBundle.get(blockingState.getBlockedId()) == null) {
                    entitlementStatesPerBundle.put(blockingState.getBlockedId(), new LinkedList<BlockingState>());
                }
                entitlementStatesPerBundle.get(blockingState.getBlockedId()).add(blockingState);
            } else if (BlockingStateType.ACCOUNT.equals(blockingState.getType()) &&
                       account.getId().equals(blockingState.getBlockedId())) {
                accountEntitlementStates.add(blockingState);
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
            final List<BlockingState> bundleEntitlementStates = Objects.firstNonNull(entitlementStatesPerBundle.get(bundleId), ImmutableList.<BlockingState>of());

            if (entitlementsPerBundle.get(bundleId) == null) {
                entitlementsPerBundle.put(bundleId, new LinkedList<EventsStream>());
            }

            for (final SubscriptionBase subscription : allSubscriptionsForBundle) {
                final List<BlockingState> subscriptionBlockingStatesOnDisk = Objects.firstNonNull(entitlementStatesPerSubscription.get(subscription.getId()), ImmutableList.<BlockingState>of());

                // We cannot use blockingStatesForAccount here: we need subscriptionEntitlementStates to contain the events not on disk when building an EventsStream
                // for an add-on - which means going through the magic of ProxyBlockingStateDao, which will recursively
                // create EventsStream objects. To avoid an infinite recursion, bypass ProxyBlockingStateDao when it's not
                // needed, i.e. if this EventStream is for a standalone or a base subscription
                final List<BlockingState> subscriptionEntitlementStates = (baseSubscription == null || subscription.getId().equals(baseSubscription.getId())) ?
                                                                          subscriptionBlockingStatesOnDisk :
                                                                          blockingStateDao.getBlockingHistoryForService(subscriptionBlockingStatesOnDisk,
                                                                                                                        bundle,
                                                                                                                        baseSubscription,
                                                                                                                        subscription,
                                                                                                                        allSubscriptionsForBundle,
                                                                                                                        internalTenantContext);

                final EventsStream eventStream = buildForEntitlement(account, bundle, baseSubscription, subscription, allSubscriptionsForBundle, subscriptionEntitlementStates, bundleEntitlementStates, accountEntitlementStates, internalTenantContext);
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

        return buildForEntitlement(bundle, baseSubscription, subscription, allSubscriptionsForBundle, internalTenantContext);
    }

    // Special signature for ProxyBlockingStateDao to save some DAO calls
    public EventsStream buildForEntitlement(final SubscriptionBase subscription, final InternalTenantContext internalTenantContext) throws EntitlementApiException {
        final SubscriptionBaseBundle bundle;
        try {
            bundle = subscriptionInternalApi.getBundleFromId(subscription.getBundleId(), internalTenantContext);
        } catch (SubscriptionBaseApiException e) {
            throw new EntitlementApiException(e);
        }

        final List<SubscriptionBase> allSubscriptionsForBundle = subscriptionInternalApi.getSubscriptionsForBundle(subscription.getBundleId(), internalTenantContext);
        return buildForEntitlement(bundle, subscription, subscription, allSubscriptionsForBundle, internalTenantContext);
    }

    // Special signature for OptimizedProxyBlockingStateDao to save some DAO calls
    public EventsStream buildForEntitlement(final SubscriptionBaseBundle bundle,
                                            final SubscriptionBase subscription,
                                            final List<SubscriptionBase> allSubscriptionsForBundle,
                                            final InternalTenantContext internalTenantContext) throws EntitlementApiException {
        return buildForEntitlement(bundle, subscription, subscription, allSubscriptionsForBundle, internalTenantContext);
    }

    private EventsStream buildForEntitlement(final SubscriptionBaseBundle bundle,
                                             @Nullable final SubscriptionBase baseSubscription,
                                             final SubscriptionBase subscription,
                                             final List<SubscriptionBase> allSubscriptionsForBundle,
                                             final InternalTenantContext internalTenantContext) throws EntitlementApiException {
        final Account account;
        try {
            account = accountInternalApi.getAccountById(bundle.getAccountId(), internalTenantContext);
        } catch (AccountApiException e) {
            throw new EntitlementApiException(e);
        }

        final List<BlockingState> bundleEntitlementStates = BLOCKING_STATE_ORDERING.immutableSortedCopy(defaultBlockingStateDao.getBlockingHistoryForService(bundle.getId(), BlockingStateType.SUBSCRIPTION_BUNDLE, EntitlementService.ENTITLEMENT_SERVICE_NAME, internalTenantContext));
        final List<BlockingState> accountEntitlementStates = BLOCKING_STATE_ORDERING.immutableSortedCopy(defaultBlockingStateDao.getBlockingHistoryForService(account.getId(), BlockingStateType.ACCOUNT, EntitlementService.ENTITLEMENT_SERVICE_NAME, internalTenantContext));
        final ImmutableList<BlockingState> subscriptionEntitlementStatesOnDisk = BLOCKING_STATE_ORDERING.immutableSortedCopy(defaultBlockingStateDao.getBlockingHistoryForService(subscription.getId(), BlockingStateType.SUBSCRIPTION, EntitlementService.ENTITLEMENT_SERVICE_NAME, internalTenantContext));

        // We need subscriptionEntitlementStates to contain the events not on disk when building an EventsStream
        // for an add-on - which means going through the magic of ProxyBlockingStateDao, which will recursively
        // create EventsStream objects. To avoid an infinite recursion, bypass ProxyBlockingStateDao when it's not
        // needed, i.e. if this EventStream is for a standalone or a base subscription
        final List<BlockingState> subscriptionEntitlementStates = (baseSubscription == null || subscription.getId().equals(baseSubscription.getId())) ?
                                                                  subscriptionEntitlementStatesOnDisk :
                                                                  blockingStateDao.getBlockingHistoryForService(subscriptionEntitlementStatesOnDisk,
                                                                                                                bundle,
                                                                                                                baseSubscription,
                                                                                                                subscription,
                                                                                                                allSubscriptionsForBundle,
                                                                                                                internalTenantContext);

        return buildForEntitlement(account, bundle, baseSubscription, subscription, allSubscriptionsForBundle, subscriptionEntitlementStates, bundleEntitlementStates, accountEntitlementStates, internalTenantContext);
    }

    private EventsStream buildForEntitlement(final Account account,
                                             final SubscriptionBaseBundle bundle,
                                             @Nullable final SubscriptionBase baseSubscription,
                                             final SubscriptionBase subscription,
                                             final List<SubscriptionBase> allSubscriptionsForBundle,
                                             final List<BlockingState> subscriptionEntitlementStates,
                                             final List<BlockingState> bundleEntitlementStates,
                                             final List<BlockingState> accountEntitlementStates,
                                             final InternalTenantContext internalTenantContext) throws EntitlementApiException {
        return new DefaultEventsStream(account,
                                       bundle,
                                       subscriptionEntitlementStates,
                                       bundleEntitlementStates,
                                       accountEntitlementStates,
                                       checker,
                                       baseSubscription,
                                       subscription,
                                       allSubscriptionsForBundle,
                                       internalTenantContext,
                                       clock.getUTCNow());
    }
}
