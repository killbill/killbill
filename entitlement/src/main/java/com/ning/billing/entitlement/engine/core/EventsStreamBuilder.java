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

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.joda.time.DateTime;
import org.skife.jdbi.v2.IDBI;

import com.ning.billing.ObjectType;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountInternalApi;
import com.ning.billing.callcontext.InternalTenantContext;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.clock.Clock;
import com.ning.billing.entitlement.EntitlementService;
import com.ning.billing.entitlement.api.BlockingState;
import com.ning.billing.entitlement.api.BlockingStateType;
import com.ning.billing.entitlement.api.EntitlementApiException;
import com.ning.billing.entitlement.block.BlockingChecker;
import com.ning.billing.entitlement.block.BlockingChecker.BlockingAggregator;
import com.ning.billing.entitlement.dao.BlockingStateDao;
import com.ning.billing.entitlement.dao.DefaultBlockingStateDao;
import com.ning.billing.subscription.api.SubscriptionBase;
import com.ning.billing.subscription.api.SubscriptionBaseInternalApi;
import com.ning.billing.subscription.api.user.SubscriptionBaseApiException;
import com.ning.billing.subscription.api.user.SubscriptionBaseBundle;
import com.ning.billing.util.cache.CacheControllerDispatcher;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.callcontext.TenantContext;
import com.ning.billing.util.dao.NonEntityDao;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import static com.ning.billing.entitlement.dao.ProxyBlockingStateDao.BLOCKING_STATE_ORDERING;

@Singleton
public class EventsStreamBuilder {

    private final AccountInternalApi accountInternalApi;
    private final SubscriptionBaseInternalApi subscriptionInternalApi;
    private final BlockingChecker checker;
    private final BlockingStateDao blockingStateDao;
    private final DefaultBlockingStateDao defaultBlockingStateDao;
    private final Clock clock;
    private final NonEntityDao nonEntityDao;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public EventsStreamBuilder(final AccountInternalApi accountInternalApi, final SubscriptionBaseInternalApi subscriptionInternalApi,
                               final BlockingChecker checker, final IDBI dbi, final Clock clock,
                               final CacheControllerDispatcher cacheControllerDispatcher,
                               final BlockingStateDao blockingStateDao, final NonEntityDao nonEntityDao,
                               final InternalCallContextFactory internalCallContextFactory) {
        this.accountInternalApi = accountInternalApi;
        this.subscriptionInternalApi = subscriptionInternalApi;
        this.checker = checker;
        this.clock = clock;
        this.blockingStateDao = blockingStateDao;
        this.nonEntityDao = nonEntityDao;
        this.internalCallContextFactory = internalCallContextFactory;

        this.defaultBlockingStateDao = new DefaultBlockingStateDao(dbi, clock, cacheControllerDispatcher, nonEntityDao);
    }

    public EventsStream refresh(final EventsStream eventsStream, final TenantContext tenantContext) throws EntitlementApiException {
        return buildForEntitlement(eventsStream.getSubscription().getId(), tenantContext);
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

    public List<EventsStream> buildForAccount(final InternalTenantContext internalTenantContext) throws EntitlementApiException {
        // Retrieve the subscriptions (map bundle id -> subscriptions)
        final Map<UUID, List<SubscriptionBase>> subscriptions = subscriptionInternalApi.getSubscriptionsForAccount(internalTenantContext);
        if (subscriptions.isEmpty()) {
            // Bail early
            return ImmutableList.<EventsStream>of();
        }

        // Retrieve the account
        final UUID accountId = nonEntityDao.retrieveIdFromObject(internalTenantContext.getAccountRecordId(), ObjectType.ACCOUNT);
        final Account account;
        try {
            account = accountInternalApi.getAccountById(accountId, internalTenantContext);
        } catch (AccountApiException e) {
            throw new EntitlementApiException(e);
        }

        // Retrieve the bundles
        final List<SubscriptionBaseBundle> bundles = subscriptionInternalApi.getBundlesForAccount(accountId, internalTenantContext);
        // Map bundle id -> bundles
        final Map<UUID, SubscriptionBaseBundle> bundlesPerId = new HashMap<UUID, SubscriptionBaseBundle>();
        for (final SubscriptionBaseBundle bundle : bundles) {
            bundlesPerId.put(bundle.getId(), bundle);
        }

        // Retrieve the blocking states
        final List<BlockingState> blockingStatesForAccount = BLOCKING_STATE_ORDERING.immutableSortedCopy(defaultBlockingStateDao.getBlockingAllForAccountRecordId(internalTenantContext));
        // Copy fully the list (avoid lazy loading)
        final List<BlockingState> accountEntitlementStates = ImmutableList.<BlockingState>copyOf(Iterables.<BlockingState>filter(blockingStatesForAccount,
                                                                                                                                 new Predicate<BlockingState>() {
                                                                                                                                     @Override
                                                                                                                                     public boolean apply(final BlockingState input) {
                                                                                                                                         return BlockingStateType.ACCOUNT.equals(input.getType()) &&
                                                                                                                                                EntitlementService.ENTITLEMENT_SERVICE_NAME.equals(input.getService()) &&
                                                                                                                                                accountId.equals(input.getBlockedId());
                                                                                                                                     }
                                                                                                                                 }));

        // Build the EventsStream objects
        final List<EventsStream> results = new LinkedList<EventsStream>();
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
            // Copy fully the list (avoid lazy loading)
            final List<BlockingState> bundleEntitlementStates = ImmutableList.<BlockingState>copyOf(Iterables.<BlockingState>filter(blockingStatesForAccount,
                                                                                                                                    new Predicate<BlockingState>() {
                                                                                                                                        @Override
                                                                                                                                        public boolean apply(final BlockingState input) {
                                                                                                                                            return BlockingStateType.SUBSCRIPTION_BUNDLE.equals(input.getType()) &&
                                                                                                                                                   EntitlementService.ENTITLEMENT_SERVICE_NAME.equals(input.getService()) &&
                                                                                                                                                   bundle.getId().equals(input.getBlockedId());
                                                                                                                                        }
                                                                                                                                    }));

            for (final SubscriptionBase subscriptionBase : allSubscriptionsForBundle) {
                // Copy fully the list (avoid lazy loading)
                final List<BlockingState> subscriptionEntitlementStates = ImmutableList.<BlockingState>copyOf(Iterables.<BlockingState>filter(blockingStatesForAccount,
                                                                                                                                              new Predicate<BlockingState>() {
                                                                                                                                                  @Override
                                                                                                                                                  public boolean apply(final BlockingState input) {
                                                                                                                                                      return BlockingStateType.SUBSCRIPTION.equals(input.getType()) &&
                                                                                                                                                             EntitlementService.ENTITLEMENT_SERVICE_NAME.equals(input.getService()) &&
                                                                                                                                                             subscriptionBase.getId().equals(input.getBlockedId());
                                                                                                                                                  }
                                                                                                                                              }));

                results.add(buildForEntitlement(account, bundle, baseSubscription, subscriptionBase, allSubscriptionsForBundle, subscriptionEntitlementStates, bundleEntitlementStates, accountEntitlementStates, internalTenantContext));
            }
        }

        return results;
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

    private EventsStream buildForEntitlement(final SubscriptionBaseBundle bundle,
                                             final SubscriptionBase baseSubscription,
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
        // TODO PIERRE Explain the magic
        final List<BlockingState> subscriptionEntitlementStates = subscription.getId().equals(baseSubscription.getId()) ?
                                                                  BLOCKING_STATE_ORDERING.immutableSortedCopy(defaultBlockingStateDao.getBlockingHistoryForService(subscription.getId(), BlockingStateType.SUBSCRIPTION, EntitlementService.ENTITLEMENT_SERVICE_NAME, internalTenantContext)) :
                                                                  BLOCKING_STATE_ORDERING.immutableSortedCopy(blockingStateDao.getBlockingHistoryForService(subscription.getId(), BlockingStateType.SUBSCRIPTION, EntitlementService.ENTITLEMENT_SERVICE_NAME, internalTenantContext));

        return buildForEntitlement(account, bundle, baseSubscription, subscription, allSubscriptionsForBundle, subscriptionEntitlementStates, bundleEntitlementStates, accountEntitlementStates, internalTenantContext);
    }

    private EventsStream buildForEntitlement(final Account account,
                                             final SubscriptionBaseBundle bundle,
                                             final SubscriptionBase baseSubscription,
                                             final SubscriptionBase subscription,
                                             final List<SubscriptionBase> allSubscriptionsForBundle,
                                             final List<BlockingState> subscriptionEntitlementStates,
                                             final List<BlockingState> bundleEntitlementStates,
                                             final List<BlockingState> accountEntitlementStates,
                                             final InternalTenantContext internalTenantContext) throws EntitlementApiException {
        final BlockingAggregator blockingAggregator = checker.getBlockedStatus(filterCurrentBlockableStatePerService(accountEntitlementStates),
                                                                               filterCurrentBlockableStatePerService(bundleEntitlementStates),
                                                                               filterCurrentBlockableStatePerService(subscriptionEntitlementStates),
                                                                               internalTenantContext);

        return new EventsStream(account,
                                bundle,
                                subscriptionEntitlementStates,
                                bundleEntitlementStates,
                                accountEntitlementStates,
                                blockingAggregator,
                                baseSubscription,
                                subscription,
                                allSubscriptionsForBundle,
                                internalTenantContext,
                                clock.getUTCNow());
    }

    private List<BlockingState> filterCurrentBlockableStatePerService(final Iterable<BlockingState> allBlockingStates) {
        final DateTime now = clock.getUTCNow();

        final Map<String, BlockingState> currentBlockingStatePerService = new HashMap<String, BlockingState>();
        for (final BlockingState blockingState : allBlockingStates) {
            if (blockingState.getEffectiveDate().isAfter(now)) {
                continue;
            }

            if (currentBlockingStatePerService.get(blockingState.getService()) == null ||
                currentBlockingStatePerService.get(blockingState.getService()).getEffectiveDate().isBefore(blockingState.getEffectiveDate())) {
                currentBlockingStatePerService.put(blockingState.getService(), blockingState);
            }
        }

        return ImmutableList.<BlockingState>copyOf(currentBlockingStatePerService.values());
    }
}
