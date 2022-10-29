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

package org.killbill.billing.entitlement.engine.core;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.account.api.ImmutableAccountData;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.BillingAlignment;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.CatalogInternalApi;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.catalog.api.VersionedCatalog;
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
import org.killbill.billing.util.audit.dao.AuditDao;
import org.killbill.billing.util.bcd.BillCycleDayCalculator;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.commons.utils.collect.Iterables;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.billing.util.optimizer.BusOptimizer;
import org.killbill.clock.Clock;
import org.killbill.notificationq.api.NotificationQueueService;
import org.skife.jdbi.v2.IDBI;

import static org.killbill.billing.util.glue.IDBISetup.MAIN_RO_IDBI_NAMED;

@Singleton
public class EventsStreamBuilder {

    private final AccountInternalApi accountInternalApi;
    private final SubscriptionBaseInternalApi subscriptionInternalApi;
    private final CatalogInternalApi catalogInternalApi;
    private final BlockingChecker checker;
    private final OptimizedProxyBlockingStateDao blockingStateDao;
    private final DefaultBlockingStateDao defaultBlockingStateDao;
    private final Clock clock;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public EventsStreamBuilder(final AccountInternalApi accountInternalApi,
                               final SubscriptionBaseInternalApi subscriptionInternalApi,
                               final CatalogInternalApi catalogInternalApi,
                               final BlockingChecker checker,
                               final IDBI dbi,
                               @Named(MAIN_RO_IDBI_NAMED) final IDBI roDbi,
                               final Clock clock,
                               final NotificationQueueService notificationQueueService,
                               final BusOptimizer eventBus,
                               final CacheControllerDispatcher cacheControllerDispatcher,
                               final NonEntityDao nonEntityDao,
                               final AuditDao auditDao,
                               final InternalCallContextFactory internalCallContextFactory) {
        this.accountInternalApi = accountInternalApi;
        this.subscriptionInternalApi = subscriptionInternalApi;
        this.catalogInternalApi = catalogInternalApi;
        this.checker = checker;
        this.clock = clock;
        this.internalCallContextFactory = internalCallContextFactory;
        this.defaultBlockingStateDao = new DefaultBlockingStateDao(dbi, roDbi, clock, notificationQueueService, eventBus, cacheControllerDispatcher, nonEntityDao, auditDao, internalCallContextFactory);
        this.blockingStateDao = new OptimizedProxyBlockingStateDao(this, subscriptionInternalApi, dbi, roDbi, clock, notificationQueueService, eventBus, cacheControllerDispatcher, nonEntityDao, auditDao, internalCallContextFactory);
    }

    public EventsStream refresh(final EventsStream eventsStream, final TenantContext tenantContext) throws EntitlementApiException {
        return buildForEntitlement(eventsStream.getEntitlementId(), tenantContext);
    }

    public EventsStream buildForBaseSubscription(final UUID bundleId, final TenantContext tenantContext) throws EntitlementApiException {
        final SubscriptionBase baseSubscription;
        try {
            final InternalTenantContext internalTenantContext = internalCallContextFactory.createInternalTenantContext(bundleId, ObjectType.BUNDLE, tenantContext);
            baseSubscription = subscriptionInternalApi.getBaseSubscription(bundleId, internalTenantContext);
        } catch (final SubscriptionBaseApiException e) {
            throw new EntitlementApiException(e);
        }

        return buildForEntitlement(baseSubscription.getId(), tenantContext);
    }

    public EventsStream buildForEntitlement(final UUID entitlementId, final TenantContext tenantContext) throws EntitlementApiException {
        final InternalTenantContext internalTenantContext = internalCallContextFactory.createInternalTenantContext(entitlementId, ObjectType.SUBSCRIPTION, tenantContext);
        return buildForEntitlement(entitlementId, false, internalTenantContext);
    }

    public AccountEventsStreams buildForAccount(final InternalTenantContext internalTenantContext) throws EntitlementApiException {
        // Retrieve the subscriptions (map bundle id -> subscriptions)
        final Map<UUID, List<SubscriptionBase>> subscriptions;
        try {
            final VersionedCatalog catalog = getCatalog(internalTenantContext);
            subscriptions = subscriptionInternalApi.getSubscriptionsForAccount(catalog, null, internalTenantContext);
            return buildForAccount(subscriptions, catalog, internalTenantContext);
        } catch (final SubscriptionBaseApiException e) {
            throw new EntitlementApiException(e);
        }
    }

    // Special signature for ProxyBlockingStateDao to save a DAO call
    public AccountEventsStreams buildForAccount(final Map<UUID, List<SubscriptionBase>> subscriptions, final VersionedCatalog catalog, final InternalTenantContext internalTenantContext) throws EntitlementApiException {
        // Retrieve the account
        final ImmutableAccountData account;
        final int accountBCD;
        try {
            account = accountInternalApi.getImmutableAccountDataByRecordId(internalTenantContext.getAccountRecordId(), internalTenantContext);
            accountBCD = accountInternalApi.getBCD(internalTenantContext);
        } catch (final AccountApiException e) {
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
        final List<BlockingState> blockingStatesForAccount = defaultBlockingStateDao.getBlockingAllForAccountRecordId(catalog, internalTenantContext);

        // Optimization: build lookup tables for blocking states states
        final Collection<BlockingState> accountBlockingStates = new LinkedList<>();
        final Map<UUID, List<BlockingState>> blockingStatesPerSubscription = new HashMap<>();
        final Map<UUID, List<BlockingState>> blockingStatesPerBundle = new HashMap<>();
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
        final Map<UUID, Integer> bcdCache = new HashMap<>();
        final Map<UUID, Collection<EventsStream>> eventsStreamPerBundle = new HashMap<>();
        final Map<UUID, Collection<SubscriptionBase>> subscriptionsPerBundle = new HashMap<>();
        for (final UUID bundleId : subscriptions.keySet()) {
            final SubscriptionBaseBundle bundle = bundlesPerId.get(bundleId);
            final List<SubscriptionBase> allSubscriptionsForBundle = subscriptions.get(bundleId);
            final SubscriptionBase baseSubscription = findBaseSubscription(allSubscriptionsForBundle);
            final List<BlockingState> bundleBlockingStates = Objects.requireNonNullElse(blockingStatesPerBundle.get(bundleId), Collections.emptyList());

            if (eventsStreamPerBundle.get(bundleId) == null) {
                eventsStreamPerBundle.put(bundleId, new LinkedList<EventsStream>());
            }
            if (subscriptionsPerBundle.get(bundleId) == null) {
                subscriptionsPerBundle.put(bundleId, allSubscriptionsForBundle);
            }

            for (final SubscriptionBase subscription : allSubscriptionsForBundle) {
                final List<BlockingState> subscriptionBlockingStatesOnDisk = Objects.requireNonNullElse(blockingStatesPerSubscription.get(subscription.getId()), Collections.emptyList());

                // We cannot always use blockingStatesForAccount here: we need subscriptionBlockingStates to contain the events not on disk when building an EventsStream
                // for an add-on - which means going through the magic of ProxyBlockingStateDao, which will recursively
                // create EventsStream objects. To avoid an infinite recursion, bypass ProxyBlockingStateDao when it's not
                // needed, i.e. if this EventStream is for a standalone or a base subscription
                final Collection<BlockingState> subscriptionBlockingStates;
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
                                                                                     accountBCD,
                                                                                     catalog,
                                                                                     false, //includeDeletedEvents set to false since deleted events are not needed while building entitlements for account.
                                                                                     internalTenantContext);

                }

                // Merge the BlockingStates
                final Collection<BlockingState> blockingStateSet = new LinkedHashSet<BlockingState>(accountBlockingStates);
                blockingStateSet.addAll(bundleBlockingStates);
                blockingStateSet.addAll(subscriptionBlockingStates);
                final List<BlockingState> blockingStates = ProxyBlockingStateDao.sortedCopy(blockingStateSet);

                final EventsStream eventStream = buildForEntitlement(account,
                                                                     bundle,
                                                                     baseSubscription,
                                                                     subscription,
                                                                     allSubscriptionsForBundle,
                                                                     blockingStates,
                                                                     accountBCD,
                                                                     bcdCache,
                                                                     catalog,
                                                                     false, //includeDeletedEvents - set to false since it does not matter while building entitlements for account.
                                                                     internalTenantContext);
                eventsStreamPerBundle.get(bundleId).add(eventStream);
            }
        }

        return new DefaultAccountEventsStreams(account, bundles, subscriptionsPerBundle, eventsStreamPerBundle);
    }

    public List<EventsStream> buildForBundle(final UUID bundleId, final InternalTenantContext internalTenantContext) throws EntitlementApiException {
        final SubscriptionBaseBundle bundle;
        final List<SubscriptionBase> subscriptionsForBundle;
        try {
            bundle = subscriptionInternalApi.getBundleFromId(bundleId, internalTenantContext);
            subscriptionsForBundle = subscriptionInternalApi.getSubscriptionsForBundle(bundleId, null, internalTenantContext);
        } catch (final SubscriptionBaseApiException e) {
            throw new EntitlementApiException(e);
        }

        final List<EventsStream> eventsStreams = new LinkedList<EventsStream>();
        for (final SubscriptionBase subscription : subscriptionsForBundle) {
            eventsStreams.add(buildForEntitlement(bundle, subscription, subscriptionsForBundle, false, internalTenantContext)); //includeDeletedEvents is set to false since it does not matter while building entitlements for bundle.
        }
        return eventsStreams;
    }

    public EventsStream buildForEntitlement(final UUID entitlementId, final boolean includeDeletedEvents, final InternalTenantContext internalTenantContext) throws EntitlementApiException {
        try {
            final SubscriptionBase subscription = subscriptionInternalApi.getSubscriptionFromId(entitlementId, includeDeletedEvents, internalTenantContext);
            return buildForEntitlement(subscription, includeDeletedEvents, internalTenantContext);
        } catch (final SubscriptionBaseApiException e) {
            throw new EntitlementApiException(e);
        }
    }

    public EventsStream buildForEntitlement(final SubscriptionBaseBundle bundle,
                                            final SubscriptionBase subscription,
                                            final Collection<SubscriptionBase> allSubscriptionsForBundle,
                                            final boolean includeDeletedEvents,
                                            final InternalTenantContext internalTenantContext) throws EntitlementApiException {
        final int accountBCD;
        try {
            accountBCD = accountInternalApi.getBCD(internalTenantContext);
        } catch (final AccountApiException e) {
            throw new EntitlementApiException(e);
        }

        return buildForEntitlement(bundle, subscription, allSubscriptionsForBundle, accountBCD, includeDeletedEvents, internalTenantContext);
    }

    public EventsStream buildForEntitlement(final SubscriptionBaseBundle bundle,
                                            final SubscriptionBase subscription,
                                            final Collection<SubscriptionBase> allSubscriptionsForBundle,
                                            final int accountBCD,
                                            final boolean includeDeletedEvents,
                                            final InternalTenantContext internalTenantContext) throws EntitlementApiException {
        final SubscriptionBase baseSubscription = findBaseSubscription(allSubscriptionsForBundle);
        final ImmutableAccountData account;
        try {
            account = accountInternalApi.getImmutableAccountDataById(bundle.getAccountId(), internalTenantContext);
        } catch (final AccountApiException e) {
            throw new EntitlementApiException(e);
        }

        final VersionedCatalog catalog = getCatalog(internalTenantContext);

        // Retrieve the blocking states
        final Set<UUID> blockingStateIds = baseSubscription != null ?
                                           // List.of needed because Set.of will throw IllegalArgumentException (duplicate element) if the same object added
                                           Set.copyOf(List.of(account.getId(), bundle.getId(), baseSubscription.getId(), subscription.getId())) :
                                           Set.copyOf(List.of(account.getId(), bundle.getId(), subscription.getId()));
        final List<BlockingState> blockingStatesForAccount = defaultBlockingStateDao.getByBlockingIds(blockingStateIds, includeDeletedEvents, internalTenantContext);

        final Map<UUID, Integer> bcdCache = new HashMap<>();
        return buildForEntitlement(blockingStatesForAccount, account, bundle, baseSubscription, subscription, allSubscriptionsForBundle, accountBCD, bcdCache, catalog, includeDeletedEvents, internalTenantContext);
    }

    // Special signature for OptimizedProxyBlockingStateDao to save some DAO calls
    public EventsStream buildForEntitlement(final Collection<BlockingState> blockingStatesForAccount,
                                            final ImmutableAccountData account,
                                            final SubscriptionBaseBundle bundle,
                                            final SubscriptionBase baseSubscription,
                                            final Collection<SubscriptionBase> allSubscriptionsForBundle,
                                            final int accountBCD,
                                            final VersionedCatalog catalog,
                                            final boolean includeDeletedEvents,
                                            final InternalTenantContext internalTenantContext) throws EntitlementApiException {
        final Map<UUID, Integer> bcdCache = new HashMap<>();
        return buildForEntitlement(blockingStatesForAccount, account, bundle, baseSubscription, baseSubscription, allSubscriptionsForBundle, accountBCD, bcdCache, catalog, includeDeletedEvents, internalTenantContext);
    }

    private EventsStream buildForEntitlement(final Collection<BlockingState> blockingStatesForAccount,
                                             final ImmutableAccountData account,
                                             final SubscriptionBaseBundle bundle,
                                             @Nullable final SubscriptionBase baseSubscription,
                                             final SubscriptionBase subscription,
                                             final Collection<SubscriptionBase> allSubscriptionsForBundle,
                                             final int accountBCD,
                                             final Map<UUID, Integer> bcdCache,
                                             final VersionedCatalog catalog,
                                             final boolean includeDeletedEvents,
                                             final InternalTenantContext internalTenantContext) throws EntitlementApiException {
        // Optimization: build lookup tables for blocking states states
        final Collection<BlockingState> accountBlockingStates = new LinkedList<>();
        final Map<UUID, List<BlockingState>> blockingStatesPerSubscription = new HashMap<>();
        final Map<UUID, List<BlockingState>> blockingStatesPerBundle = new HashMap<>();
        for (final BlockingState blockingState : blockingStatesForAccount) {
            if (!includeDeletedEvents && !blockingState.isActive()) {
                continue;
            }
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

        final List<BlockingState> bundleBlockingStates = Objects.requireNonNullElse(blockingStatesPerBundle.get(subscription.getBundleId()), Collections.emptyList());
        final List<BlockingState> subscriptionBlockingStatesOnDisk = Objects.requireNonNullElse(blockingStatesPerSubscription.get(subscription.getId()), Collections.emptyList());

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
            subscriptionBlockingStates = blockingStateDao.getBlockingHistory(List.copyOf(subscriptionBlockingStatesOnDisk),
                                                                             blockingStatesForAccount,
                                                                             account,
                                                                             bundle,
                                                                             baseSubscription,
                                                                             subscription,
                                                                             allSubscriptionsForBundle,
                                                                             accountBCD,
                                                                             catalog,
                                                                             includeDeletedEvents,
                                                                             internalTenantContext);
        }

        // Merge the BlockingStates
        final Collection<BlockingState> blockingStateSet = new LinkedHashSet<>(accountBlockingStates);
        blockingStateSet.addAll(bundleBlockingStates);
        blockingStateSet.addAll(subscriptionBlockingStates);
        final List<BlockingState> blockingStates = ProxyBlockingStateDao.sortedCopy(blockingStateSet);

        if(includeDeletedEvents) {
            return buildForEntitlement(account, bundle, baseSubscription, subscription, allSubscriptionsForBundle, blockingStates.stream().filter(state -> state.isActive()).collect(Collectors.toList()), blockingStates, accountBCD, bcdCache, catalog, internalTenantContext);
        } else {
            return buildForEntitlement(account, bundle, baseSubscription, subscription, allSubscriptionsForBundle, blockingStates, Collections.emptyList(), accountBCD, bcdCache, catalog, internalTenantContext);
        }
    }
    
    private EventsStream buildForEntitlement(final ImmutableAccountData account,
            final SubscriptionBaseBundle bundle,
            @Nullable final SubscriptionBase baseSubscription,
            final SubscriptionBase subscription,
            final Collection<SubscriptionBase> allSubscriptionsForBundle,
            final Collection<BlockingState> blockingStates,
            final int accountBCD,
            final Map<UUID, Integer> bcdCache,
            final VersionedCatalog catalog,
            final boolean includeDeletedEvents,
            final InternalTenantContext internalTenantContext) throws EntitlementApiException {
    	
    	return buildForEntitlement(account, bundle, baseSubscription, subscription, allSubscriptionsForBundle, blockingStates, Collections.emptyList(), accountBCD, bcdCache, catalog, internalTenantContext);
    	
    }   

    private EventsStream buildForEntitlement(final ImmutableAccountData account,
                                             final SubscriptionBaseBundle bundle,
                                             @Nullable final SubscriptionBase baseSubscription,
                                             final SubscriptionBase subscription,
                                             final Collection<SubscriptionBase> allSubscriptionsForBundle,
                                             final Collection<BlockingState> blockingStates,
                                             final Collection<BlockingState> blockingStatesWithDeletedEvents,
                                             final int accountBCD,
                                             final Map<UUID, Integer> bcdCache,
                                             final VersionedCatalog catalog,
                                             final InternalTenantContext internalTenantContext) throws EntitlementApiException {

        try {
            Integer defaultAlignmentDay = null;
            final BillingAlignment alignment = subscription.getBillingAlignment(createPlanPhaseSpecifier(subscription), clock.getUTCNow(), catalog);

            if (alignment != BillingAlignment.ACCOUNT || accountBCD != 0) {
                defaultAlignmentDay = BillCycleDayCalculator.calculateBcdForAlignment(bcdCache, subscription, baseSubscription, alignment, internalTenantContext, accountBCD);
            }
            return new DefaultEventsStream(account,
                                           bundle,
                                           blockingStates,
                                           blockingStatesWithDeletedEvents,
                                           checker,
                                           baseSubscription,
                                           subscription,
                                           allSubscriptionsForBundle,
                                           defaultAlignmentDay,
                                           internalTenantContext,
                                           clock.getUTCNow());
        } catch (final SubscriptionBaseApiException e) {
            throw new EntitlementApiException(e);
        }
    }

    private EventsStream buildForEntitlement(final SubscriptionBase subscription, final boolean includeDeletedEvents, final InternalTenantContext internalTenantContext) throws EntitlementApiException {
        final SubscriptionBaseBundle bundle;
        final List<SubscriptionBase> subscriptionsForBundle;
        try {
            bundle = subscriptionInternalApi.getBundleFromId(subscription.getBundleId(), internalTenantContext);
            subscriptionsForBundle = subscriptionInternalApi.getSubscriptionsForBundle(subscription.getBundleId(), null, internalTenantContext);
        } catch (final SubscriptionBaseApiException e) {
            throw new EntitlementApiException(e);
        }

        return buildForEntitlement(bundle, subscription, subscriptionsForBundle, includeDeletedEvents, internalTenantContext);
    }

    private PlanPhaseSpecifier createPlanPhaseSpecifier(final SubscriptionBase subscription) {
        final String planName;
        final PhaseType phaseType;
        if (subscription.getState() == EntitlementState.PENDING) {
            final SubscriptionBaseTransition transition = subscription.getPendingTransition();
            planName = transition.getNextPlan().getName();
            phaseType = transition.getNextPhase().getPhaseType();
        } else {
            planName = subscription.getLastActivePlan().getName();
            phaseType = subscription.getLastActivePhase().getPhaseType();
        }
        return new PlanPhaseSpecifier(planName, phaseType);
    }

    private VersionedCatalog getCatalog(final InternalTenantContext internalTenantContext) throws EntitlementApiException {
        final VersionedCatalog catalog;
        try {
            catalog = catalogInternalApi.getFullCatalog(true, true, internalTenantContext);
        } catch (final CatalogApiException e) {
            throw new EntitlementApiException(e);
        }
        return catalog;
    }

    private SubscriptionBase findBaseSubscription(final Iterable<SubscriptionBase> subscriptions) {
        return Iterables.toStream(subscriptions)
                .filter(input -> {
                    final List<SubscriptionBaseTransition> allTransitions = input.getAllTransitions(false);
                    return !allTransitions.isEmpty() &&
                           allTransitions.get(0).getNextPlan() != null &&
                           allTransitions.get(0).getNextPlan().getProduct() != null &&
                           ProductCategory.BASE.equals(allTransitions.get(0).getNextPlan().getProduct().getCategory());
                })
                .findFirst().orElse(null); // null for standalone subscriptions
    }
}
