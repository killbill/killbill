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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;

import com.ning.billing.account.api.Account;
import com.ning.billing.callcontext.InternalTenantContext;
import com.ning.billing.catalog.api.Product;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.EntitlementService;
import com.ning.billing.entitlement.EventsStream;
import com.ning.billing.entitlement.api.BlockingState;
import com.ning.billing.entitlement.api.BlockingStateType;
import com.ning.billing.entitlement.api.DefaultEntitlementApi;
import com.ning.billing.entitlement.api.Entitlement.EntitlementState;
import com.ning.billing.entitlement.block.BlockingChecker;
import com.ning.billing.entitlement.block.BlockingChecker.BlockingAggregator;
import com.ning.billing.junction.DefaultBlockingState;
import com.ning.billing.subscription.api.SubscriptionBase;
import com.ning.billing.subscription.api.SubscriptionBaseTransitionType;
import com.ning.billing.subscription.api.user.SubscriptionBaseBundle;
import com.ning.billing.subscription.api.user.SubscriptionBaseTransition;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

public class DefaultEventsStream implements EventsStream {

    private final Account account;
    private final SubscriptionBaseBundle bundle;
    private final List<BlockingState> subscriptionEntitlementStates;
    private final List<BlockingState> bundleEntitlementStates;
    private final List<BlockingState> accountEntitlementStates;
    private final BlockingChecker blockingChecker;
    // Base subscription for the bundle if it exists, null otherwise
    private final SubscriptionBase baseSubscription;
    // Subscription associated with this entitlement (equals to baseSubscription for base subscriptions)
    private final SubscriptionBase subscription;
    // All subscriptions for that bundle
    private final List<SubscriptionBase> allSubscriptionsForBundle;
    private final InternalTenantContext internalTenantContext;
    private final DateTime utcNow;

    private BlockingAggregator blockingAggregator;
    private List<BlockingState> currentSubscriptionEntitlementBlockingStatesForServices;
    private List<BlockingState> currentBundleEntitlementBlockingStatesForServices;
    private List<BlockingState> currentAccountEntitlementBlockingStateForServices;
    private LocalDate entitlementEffectiveEndDate;
    private BlockingState entitlementCancelEvent;
    private EntitlementState entitlementState;

    public DefaultEventsStream(final Account account, final SubscriptionBaseBundle bundle,
                               final List<BlockingState> subscriptionEntitlementStates, final List<BlockingState> bundleEntitlementStates,
                               final List<BlockingState> accountEntitlementStates, final BlockingChecker blockingChecker,
                               @Nullable final SubscriptionBase baseSubscription, final SubscriptionBase subscription,
                               final List<SubscriptionBase> allSubscriptionsForBundle, final InternalTenantContext contextWithValidAccountRecordId, final DateTime utcNow) {
        this.account = account;
        this.bundle = bundle;
        this.subscriptionEntitlementStates = subscriptionEntitlementStates;
        this.bundleEntitlementStates = bundleEntitlementStates;
        this.accountEntitlementStates = accountEntitlementStates;
        this.blockingChecker = blockingChecker;
        this.baseSubscription = baseSubscription;
        this.subscription = subscription;
        this.allSubscriptionsForBundle = allSubscriptionsForBundle;
        this.internalTenantContext = contextWithValidAccountRecordId;
        this.utcNow = utcNow;

        setup();
    }

    public Account getAccount() {
        return account;
    }

    @Override
    public DateTimeZone getAccountTimeZone() {
        return account.getTimeZone();
    }

    @Override
    public UUID getAccountId() {
        return account.getId();
    }

    @Override
    public UUID getBundleId() {
        return bundle.getId();
    }

    @Override
    public String getBundleExternalKey() {
        return bundle.getExternalKey();
    }

    @Override
    public UUID getEntitlementId() {
        return subscription.getId();
    }

    public SubscriptionBaseBundle getBundle() {
        return bundle;
    }

    @Override
    public SubscriptionBase getBaseSubscription() {
        return baseSubscription;
    }

    @Override
    public SubscriptionBase getSubscription() {
        return subscription;
    }

    @Override
    public InternalTenantContext getInternalTenantContext() {
        return internalTenantContext;
    }

    @Override
    public LocalDate getEntitlementEffectiveEndDate() {
        return entitlementEffectiveEndDate;
    }

    public BlockingState getEntitlementCancelEvent() {
        return entitlementCancelEvent;
    }

    @Override
    public EntitlementState getEntitlementState() {
        return entitlementState;
    }

    public BlockingAggregator getCurrentBlockingAggregator() {
        return blockingAggregator;
    }

    @Override
    public boolean isBlockChange() {
        return blockingAggregator.isBlockChange();
    }

    @Override
    public List<BlockingState> getCurrentSubscriptionEntitlementBlockingStatesForServices() {
        return currentSubscriptionEntitlementBlockingStatesForServices;
    }

    public boolean isEntitlementFutureCancelled() {
        return entitlementCancelEvent != null && entitlementCancelEvent.getEffectiveDate().isAfter(utcNow);
    }

    public boolean isEntitlementFutureChanged() {
        return getPendingSubscriptionEvents(utcNow, SubscriptionBaseTransitionType.CHANGE).iterator().hasNext();
    }

    @Override
    public boolean isEntitlementActive() {
        return entitlementState == EntitlementState.ACTIVE;
    }

    @Override
    public boolean isEntitlementCancelled() {
        return entitlementState == EntitlementState.CANCELLED;
    }

    @Override
    public boolean isSubscriptionCancelled() {
        return subscription.getState() == EntitlementState.CANCELLED;
    }

    @Override
    public Collection<BlockingState> getSubscriptionEntitlementStates() {
        return subscriptionEntitlementStates;
    }

    @Override
    public Collection<BlockingState> getBundleEntitlementStates() {
        return bundleEntitlementStates;
    }

    @Override
    public Collection<BlockingState> getAccountEntitlementStates() {
        return accountEntitlementStates;
    }

    @Override
    public Collection<BlockingState> getPendingEntitlementCancellationEvents() {
        return getPendingEntitlementEvents(DefaultEntitlementApi.ENT_STATE_CANCELLED);
    }

    public Collection<BlockingState> getPendingEntitlementEvents(final String... types) {
        final List<String> typeList = ImmutableList.<String>copyOf(types);
        return Collections2.<BlockingState>filter(subscriptionEntitlementStates,
                                                  new Predicate<BlockingState>() {
                                                      @Override
                                                      public boolean apply(final BlockingState input) {
                                                          return !input.getEffectiveDate().isBefore(utcNow) &&
                                                                 typeList.contains(input.getStateName()) &&
                                                                 (
                                                                         // ... for that subscription
                                                                         BlockingStateType.SUBSCRIPTION.equals(input.getType()) && input.getBlockedId().equals(subscription.getId()) ||
                                                                         // ... for the associated base subscription
                                                                         BlockingStateType.SUBSCRIPTION.equals(input.getType()) && input.getBlockedId().equals(baseSubscription.getId()) ||
                                                                         // ... for that bundle
                                                                         BlockingStateType.SUBSCRIPTION_BUNDLE.equals(input.getType()) && input.getBlockedId().equals(bundle.getId()) ||
                                                                         // ... for that account
                                                                         BlockingStateType.ACCOUNT.equals(input.getType()) && input.getBlockedId().equals(account.getId())
                                                                 );
                                                      }
                                                  });
    }

    public BlockingState getEntitlementCancellationEvent(final UUID subscriptionId) {
        return Iterables.<BlockingState>tryFind(subscriptionEntitlementStates,
                                                new Predicate<BlockingState>() {
                                                    @Override
                                                    public boolean apply(final BlockingState input) {
                                                        return DefaultEntitlementApi.ENT_STATE_CANCELLED.equals(input.getStateName()) &&
                                                               input.getBlockedId().equals(subscriptionId);
                                                    }
                                                }).orNull();
    }

    public Iterable<SubscriptionBaseTransition> getPendingSubscriptionEvents(final SubscriptionBaseTransitionType... types) {
        return getPendingSubscriptionEvents(utcNow, types);
    }

    public Iterable<SubscriptionBaseTransition> getPendingSubscriptionEvents(final DateTime effectiveDatetime, final SubscriptionBaseTransitionType... types) {
        final List<SubscriptionBaseTransitionType> typeList = ImmutableList.<SubscriptionBaseTransitionType>copyOf(types);
        return Iterables.<SubscriptionBaseTransition>filter(subscription.getAllTransitions(),
                                                            new Predicate<SubscriptionBaseTransition>() {
                                                                @Override
                                                                public boolean apply(final SubscriptionBaseTransition input) {
                                                                    // Make sure we return the event for equality
                                                                    return !input.getEffectiveTransitionTime().isBefore(effectiveDatetime) &&
                                                                           typeList.contains(input.getTransitionType());
                                                                }
                                                            });
    }

    @Override
    public Collection<BlockingState> computeAddonsBlockingStatesForNextSubscriptionBaseEvent(final DateTime effectiveDate) {
        return computeAddonsBlockingStatesForNextSubscriptionBaseEvent(effectiveDate, false);
    }

    // Compute future blocking states not on disk for add-ons associated to this (base) events stream
    @Override
    public Collection<BlockingState> computeAddonsBlockingStatesForFutureSubscriptionBaseEvents() {
        if (!ProductCategory.BASE.equals(subscription.getCategory())) {
            // Only base subscriptions have add-ons
            return ImmutableList.of();
        }

        // We need to find the first "trigger" transition, from which we will create the add-ons cancellation events.
        // This can either be a future entitlement cancel...
        if (isEntitlementFutureCancelled()) {
            // Note that in theory we could always only look subscription base as we assume entitlement cancel means subscription base cancel
            // but we want to use the effective date of the entitlement cancel event to create the add-on cancel event
            final BlockingState futureEntitlementCancelEvent = getEntitlementCancellationEvent(subscription.getId());
            return computeAddonsBlockingStatesForNextSubscriptionBaseEvent(futureEntitlementCancelEvent.getEffectiveDate(), false);
        } else if (isEntitlementFutureChanged()) {
            // ...or a subscription change (i.e. a change plan where the new plan has an impact on the existing add-on).
            // We need to go back to subscription base as entitlement doesn't know about these
            return computeAddonsBlockingStatesForNextSubscriptionBaseEvent(utcNow, true);
        } else {
            return ImmutableList.of();
        }
    }

    private Collection<BlockingState> computeAddonsBlockingStatesForNextSubscriptionBaseEvent(final DateTime effectiveDate,
                                                                                              final boolean useBillingEffectiveDate) {
        SubscriptionBaseTransition subscriptionBaseTransitionTrigger = null;
        if (!isEntitlementFutureCancelled()) {
            // Compute the transition trigger (either subscription cancel or change)
            final Iterable<SubscriptionBaseTransition> pendingSubscriptionBaseTransitions = getPendingSubscriptionEvents(effectiveDate, SubscriptionBaseTransitionType.CHANGE, SubscriptionBaseTransitionType.CANCEL);
            if (!pendingSubscriptionBaseTransitions.iterator().hasNext()) {
                return ImmutableList.<BlockingState>of();
            }

            subscriptionBaseTransitionTrigger = pendingSubscriptionBaseTransitions.iterator().next();
        }

        final Product baseTransitionTriggerNextProduct;
        final DateTime blockingStateEffectiveDate;
        if (subscriptionBaseTransitionTrigger == null) {
            baseTransitionTriggerNextProduct = null;
            blockingStateEffectiveDate = effectiveDate;
        } else {
            baseTransitionTriggerNextProduct = (EntitlementState.CANCELLED.equals(subscriptionBaseTransitionTrigger.getNextState()) ? null : subscriptionBaseTransitionTrigger.getNextPlan().getProduct());
            blockingStateEffectiveDate = useBillingEffectiveDate ? subscriptionBaseTransitionTrigger.getEffectiveTransitionTime() : effectiveDate;
        }

        return computeAddonsBlockingStatesForSubscriptionBaseEvent(baseTransitionTriggerNextProduct, blockingStateEffectiveDate);
    }

    private Collection<BlockingState> computeAddonsBlockingStatesForSubscriptionBaseEvent(@Nullable final Product baseTransitionTriggerNextProduct,
                                                                                          final DateTime blockingStateEffectiveDate) {
        if (baseSubscription == null || baseSubscription.getLastActivePlan() == null || !ProductCategory.BASE.equals(baseSubscription.getLastActivePlan().getProduct().getCategory())) {
            return ImmutableList.<BlockingState>of();
        }

        // Compute included and available addons for the new product
        final Collection<String> includedAddonsForProduct;
        final Collection<String> availableAddonsForProduct;
        if (baseTransitionTriggerNextProduct == null) {
            includedAddonsForProduct = ImmutableList.<String>of();
            availableAddonsForProduct = ImmutableList.<String>of();
        } else {
            includedAddonsForProduct = Collections2.<Product, String>transform(ImmutableSet.<Product>copyOf(baseTransitionTriggerNextProduct.getIncluded()),
                                                                               new Function<Product, String>() {
                                                                                   @Override
                                                                                   public String apply(final Product product) {
                                                                                       return product.getName();
                                                                                   }
                                                                               });

            availableAddonsForProduct = Collections2.<Product, String>transform(ImmutableSet.<Product>copyOf(baseTransitionTriggerNextProduct.getAvailable()),
                                                                                new Function<Product, String>() {
                                                                                    @Override
                                                                                    public String apply(final Product product) {
                                                                                        return product.getName();
                                                                                    }
                                                                                });
        }

        // Retrieve all add-ons to block for that base subscription
        final Collection<SubscriptionBase> futureBlockedAddons = Collections2.<SubscriptionBase>filter(allSubscriptionsForBundle,
                                                                                                       new Predicate<SubscriptionBase>() {
                                                                                                           @Override
                                                                                                           public boolean apply(final SubscriptionBase subscription) {
                                                                                                               return ProductCategory.ADD_ON.equals(subscription.getCategory()) &&
                                                                                                                      // Check the entitlement for that add-on hasn't been cancelled yet
                                                                                                                      getEntitlementCancellationEvent(subscription.getId()) == null &&
                                                                                                                      (
                                                                                                                              // Base subscription cancelled
                                                                                                                              baseTransitionTriggerNextProduct == null ||
                                                                                                                              (
                                                                                                                                      // Change plan - check which add-ons to cancel
                                                                                                                                      includedAddonsForProduct.contains(subscription.getLastActivePlan().getProduct().getName()) ||
                                                                                                                                      !availableAddonsForProduct.contains(subscription.getLastActivePlan().getProduct().getName())
                                                                                                                              )
                                                                                                                      );
                                                                                                           }
                                                                                                       });

        // Create the blocking states
        return Collections2.<SubscriptionBase, BlockingState>transform(futureBlockedAddons,
                                                                       new Function<SubscriptionBase, BlockingState>() {
                                                                           @Override
                                                                           public BlockingState apply(final SubscriptionBase input) {
                                                                               return new DefaultBlockingState(input.getId(),
                                                                                                               BlockingStateType.SUBSCRIPTION,
                                                                                                               DefaultEntitlementApi.ENT_STATE_CANCELLED,
                                                                                                               EntitlementService.ENTITLEMENT_SERVICE_NAME,
                                                                                                               true,
                                                                                                               true,
                                                                                                               false,
                                                                                                               blockingStateEffectiveDate);
                                                                           }
                                                                       });
    }

    private void setup() {
        computeBlockingAggregator();
        computeEntitlementEffectiveEndDate();
        computeEntitlementCancelEvent();
        computeStateForEntitlement();
    }

    private void computeBlockingAggregator() {
        final List<BlockingState> currentAccountEntitlementBlockingStatesForServices = filterCurrentBlockableStatePerService(accountEntitlementStates);
        final List<BlockingState> currentBundleEntitlementBlockingStatesForServices = filterCurrentBlockableStatePerService(bundleEntitlementStates);
        final List<BlockingState> currentSubscriptionEntitlementBlockingStatesForServices = filterCurrentBlockableStatePerService(subscriptionEntitlementStates);
        blockingAggregator = blockingChecker.getBlockedStatus(currentAccountEntitlementBlockingStatesForServices,
                                                              currentBundleEntitlementBlockingStatesForServices,
                                                              currentSubscriptionEntitlementBlockingStatesForServices,
                                                              internalTenantContext);
    }

    private List<BlockingState> filterCurrentBlockableStatePerService(final Iterable<BlockingState> allBlockingStates) {
        final Map<String, BlockingState> currentBlockingStatePerService = new HashMap<String, BlockingState>();
        for (final BlockingState blockingState : allBlockingStates) {
            if (blockingState.getEffectiveDate().isAfter(utcNow)) {
                continue;
            }

            if (currentBlockingStatePerService.get(blockingState.getService()) == null ||
                currentBlockingStatePerService.get(blockingState.getService()).getEffectiveDate().isBefore(blockingState.getEffectiveDate())) {
                currentBlockingStatePerService.put(blockingState.getService(), blockingState);
            }
        }

        return ImmutableList.<BlockingState>copyOf(currentBlockingStatePerService.values());
    }

    private void computeEntitlementEffectiveEndDate() {
        LocalDate result = null;
        BlockingState lastEntry;

        lastEntry = (!subscriptionEntitlementStates.isEmpty()) ? subscriptionEntitlementStates.get(subscriptionEntitlementStates.size() - 1) : null;
        if (lastEntry != null && DefaultEntitlementApi.ENT_STATE_CANCELLED.equals(lastEntry.getStateName())) {
            result = new LocalDate(lastEntry.getEffectiveDate(), account.getTimeZone());
        }

        lastEntry = (!bundleEntitlementStates.isEmpty()) ? bundleEntitlementStates.get(bundleEntitlementStates.size() - 1) : null;
        if (lastEntry != null && DefaultEntitlementApi.ENT_STATE_CANCELLED.equals(lastEntry.getStateName())) {
            final LocalDate localDate = new LocalDate(lastEntry.getEffectiveDate(), account.getTimeZone());
            result = ((result == null) || (localDate.compareTo(result) < 0)) ? localDate : result;
        }

        lastEntry = (!accountEntitlementStates.isEmpty()) ? accountEntitlementStates.get(accountEntitlementStates.size() - 1) : null;
        if (lastEntry != null && DefaultEntitlementApi.ENT_STATE_CANCELLED.equals(lastEntry.getStateName())) {
            final LocalDate localDate = new LocalDate(lastEntry.getEffectiveDate(), account.getTimeZone());
            result = ((result == null) || (localDate.compareTo(result) < 0)) ? localDate : result;
        }

        entitlementEffectiveEndDate = result;
    }

    private void computeEntitlementCancelEvent() {
        entitlementCancelEvent = Iterables.<BlockingState>tryFind(subscriptionEntitlementStates,
                                                                  new Predicate<BlockingState>() {
                                                                      @Override
                                                                      public boolean apply(final BlockingState input) {
                                                                          return DefaultEntitlementApi.ENT_STATE_CANCELLED.equals(input.getStateName());
                                                                      }
                                                                  }).orNull();
    }

    private void computeStateForEntitlement() {
        // Current state for the ENTITLEMENT_SERVICE_NAME is set to cancelled
        if (entitlementEffectiveEndDate != null && entitlementEffectiveEndDate.compareTo(new LocalDate(utcNow, account.getTimeZone())) <= 0) {
            entitlementState = EntitlementState.CANCELLED;
        } else {
            // Gather states across all services and check if one of them is set to 'blockEntitlement'
            entitlementState = (blockingAggregator != null && blockingAggregator.isBlockEntitlement() ? EntitlementState.BLOCKED : EntitlementState.ACTIVE);
        }
    }
}
