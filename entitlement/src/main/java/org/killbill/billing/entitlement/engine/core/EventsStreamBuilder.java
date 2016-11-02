/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

package org.killbill.billing.entitlement.engine.core;

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

import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.account.api.ImmutableAccountData;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.AccountEventsStreams;
import org.killbill.billing.entitlement.EventsStream;
import org.killbill.billing.entitlement.api.BlockingState;
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.entitlement.api.EntitlementApiException;
import org.killbill.billing.entitlement.api.svcs.DefaultAccountEventsStreams;
import org.killbill.billing.entitlement.block.BlockingChecker;
import org.killbill.billing.entitlement.dao.DefaultBlockingStateDao;
import org.killbill.billing.entitlement.dao.OptimizedProxyBlockingStateDao;
import org.killbill.billing.entitlement.dao.ProxyBlockingStateDao;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseInternalApi;
import org.killbill.billing.subscription.api.user.SubscriptionBaseApiException;
import org.killbill.billing.subscription.api.user.SubscriptionBaseBundle;
import org.killbill.billing.subscription.api.user.SubscriptionBaseTransition;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.bus.api.PersistentBus;
import org.killbill.clock.Clock;
import org.killbill.notificationq.api.NotificationQueueService;
import org.skife.jdbi.v2.IDBI;

import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

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
                               final NotificationQueueService notificationQueueService, final PersistentBus eventBus,
                               final CacheControllerDispatcher cacheControllerDispatcher,
                               final NonEntityDao nonEntityDao,
                               final InternalCallContextFactory internalCallContextFactory) {
        this.accountInternalApi = accountInternalApi;
        this.subscriptionInternalApi = subscriptionInternalApi;
        this.checker = checker;
        this.clock = clock;
        this.internalCallContextFactory = internalCallContextFactory;
        this.defaultBlockingStateDao = new DefaultBlockingStateDao(dbi, clock, notificationQueueService, eventBus, cacheControllerDispatcher, nonEntityDao, internalCallContextFactory);
        this.blockingStateDao = new OptimizedProxyBlockingStateDao(this, subscriptionInternalApi, dbi, clock, notificationQueueService, eventBus, cacheControllerDispatcher, nonEntityDao, internalCallContextFactory);
    }

    public EventsStream refresh(final EventsStream eventsStream, final TenantContext tenantContext) throws EntitlementApiException {
        return buildForEntitlement(eventsStream.getEntitlementId(), tenantContext);
    }

    public EventsStream buildForBaseSubscription(final UUID bundleId, final TenantContext tenantContext) throws EntitlementApiException {
        final SubscriptionBase baseSubscription;
        try {
            final InternalTenantContext internalTenantContext = internalCallContextFactory.createInternalTenantContext(bundleId, ObjectType.BUNDLE, tenantContext);
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
        final Map<UUID, List<SubscriptionBase>> subscriptions;
        try {
            subscriptions = subscriptionInternalApi.getSubscriptionsForAccount(internalTenantContext);
            return buildForAccount(subscriptions, internalTenantContext);
        } catch (SubscriptionBaseApiException e) {
            throw new EntitlementApiException(e);
        }
    }

    // Special signature for ProxyBlockingStateDao to save a DAO call
    public AccountEventsStreams buildForAccount(final Map<UUID, List<SubscriptionBase>> subscriptions, final InternalTenantContext internalTenantContext) throws EntitlementApiException {
        // Retrieve the account
        final ImmutableAccountData account;
        try {
            account = accountInternalApi.getImmutableAccountDataByRecordId(internalTenantContext.getAccountRecordId(), internalTenantContext);
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
            final SubscriptionBase baseSubscription = findBaseSubscription(allSubscriptionsForBundle);
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
                final List<BlockingState> blockingStates = ProxyBlockingStateDao.sortedCopy(blockingStateSet);

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
            allSubscriptionsForBundle = subscriptionInternalApi.getSubscriptionsForBundle(subscription.getBundleId(), null, internalTenantContext);
            baseSubscription = findBaseSubscription(allSubscriptionsForBundle);
        } catch (SubscriptionBaseApiException e) {
            throw new EntitlementApiException(e);
        }

        final ImmutableAccountData account;
        try {
            account = accountInternalApi.getImmutableAccountDataById(bundle.getAccountId(), internalTenantContext);
        } catch (AccountApiException e) {
            throw new EntitlementApiException(e);
        }

        // Retrieve the blocking states
        final List<BlockingState> blockingStatesForAccount = defaultBlockingStateDao.getBlockingAllForAccountRecordId(internalTenantContext);

        return buildForEntitlement(blockingStatesForAccount, account, bundle, baseSubscription, subscription, allSubscriptionsForBundle, internalTenantContext);
    }

    // Special signature for OptimizedProxyBlockingStateDao to save some DAO calls
    public EventsStream buildForEntitlement(final List<BlockingState> blockingStatesForAccount,
                                            final ImmutableAccountData account,
                                            final SubscriptionBaseBundle bundle,
                                            final SubscriptionBase baseSubscription,
                                            final List<SubscriptionBase> allSubscriptionsForBundle,
                                            final InternalTenantContext internalTenantContext) throws EntitlementApiException {
        return buildForEntitlement(blockingStatesForAccount, account, bundle, baseSubscription, baseSubscription, allSubscriptionsForBundle, internalTenantContext);
    }

    private EventsStream buildForEntitlement(final List<BlockingState> blockingStatesForAccount,
                                             final ImmutableAccountData account,
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
        final List<BlockingState> blockingStates = ProxyBlockingStateDao.sortedCopy(blockingStateSet);

        return buildForEntitlement(account, bundle, baseSubscription, subscription, allSubscriptionsForBundle, blockingStates, internalTenantContext);
    }

    private EventsStream buildForEntitlement(final ImmutableAccountData account,
                                             final SubscriptionBaseBundle bundle,
                                             @Nullable final SubscriptionBase baseSubscription,
                                             final SubscriptionBase subscription,
                                             final List<SubscriptionBase> allSubscriptionsForBundle,
                                             final List<BlockingState> blockingStates,
                                             final InternalTenantContext internalTenantContext) throws EntitlementApiException {


        try {
            int accountBCD = accountInternalApi.getBCD(account.getId(), internalTenantContext);
            int defaultAlignmentDay = subscriptionInternalApi.getDefaultBillCycleDayLocal(subscription, baseSubscription, createPlanPhaseSpecifier(subscription), account.getTimeZone(), accountBCD, clock.getUTCNow(), internalTenantContext);
            return new DefaultEventsStream(account,
                                           bundle,
                                           blockingStates,
                                           checker,
                                           baseSubscription,
                                           subscription,
                                           allSubscriptionsForBundle,
                                           defaultAlignmentDay,
                                           internalTenantContext,
                                           clock.getUTCNow());
        } catch (final SubscriptionBaseApiException e) {
            throw new EntitlementApiException(e);
        } catch (final AccountApiException e) {
            throw new EntitlementApiException(e);
        }
    }

    private PlanPhaseSpecifier createPlanPhaseSpecifier(final SubscriptionBase subscription) {

        final String lastActiveProductName;
        final BillingPeriod billingPeriod;
        final ProductCategory productCategory;
        final String priceListName;
        final PhaseType phaseType;

        if (subscription.getState() == EntitlementState.PENDING) {
            final SubscriptionBaseTransition transition = subscription.getPendingTransition();
            final Product pendingProduct = transition.getNextPlan().getProduct();
            lastActiveProductName = pendingProduct.getName();
            productCategory = pendingProduct.getCategory();
            final PlanPhase pendingPlanPhase = transition.getNextPhase();
            billingPeriod = pendingPlanPhase.getRecurring() != null ? pendingPlanPhase.getRecurring().getBillingPeriod() : BillingPeriod.NO_BILLING_PERIOD;
            priceListName = transition.getNextPriceList().getName();
            phaseType = transition.getNextPhase().getPhaseType();
        } else {
            final Product lastActiveProduct = subscription.getLastActiveProduct();
            lastActiveProductName = lastActiveProduct.getName();
            productCategory = lastActiveProduct.getCategory();
            final PlanPhase lastActivePlanPhase = subscription.getLastActivePhase();
            billingPeriod = lastActivePlanPhase.getRecurring() != null ? lastActivePlanPhase.getRecurring().getBillingPeriod() : BillingPeriod.NO_BILLING_PERIOD;
            priceListName = subscription.getLastActivePlan().getPriceListName();
            phaseType = subscription.getLastActivePhase().getPhaseType();
        }
        return new PlanPhaseSpecifier(lastActiveProductName,
                                      billingPeriod,
                                      priceListName,
                                      phaseType);

    }

    private SubscriptionBase findBaseSubscription(final Iterable<SubscriptionBase> subscriptions) {
        return Iterables.<SubscriptionBase>tryFind(subscriptions,
                                                   new Predicate<SubscriptionBase>() {
                                                       @Override
                                                       public boolean apply(final SubscriptionBase input) {
                                                           final List<SubscriptionBaseTransition> allTransitions = input.getAllTransitions();
                                                           return !allTransitions.isEmpty() &&
                                                                  allTransitions.get(0).getNextPlan() != null &&
                                                                  allTransitions.get(0).getNextPlan().getProduct() != null &&
                                                                  ProductCategory.BASE.equals(allTransitions.get(0).getNextPlan().getProduct().getCategory());
                                                       }
                                                   }).orNull(); // null for standalone subscriptions
    }
}
