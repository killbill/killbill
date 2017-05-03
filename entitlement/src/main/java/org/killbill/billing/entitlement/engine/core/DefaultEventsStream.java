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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.account.api.ImmutableAccountData;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.EntitlementService;
import org.killbill.billing.entitlement.EventsStream;
import org.killbill.billing.entitlement.api.BlockingState;
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.killbill.billing.entitlement.api.DefaultEntitlementApi;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.entitlement.block.BlockingChecker;
import org.killbill.billing.entitlement.block.BlockingChecker.BlockingAggregator;
import org.killbill.billing.junction.DefaultBlockingState;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;
import org.killbill.billing.subscription.api.user.SubscriptionBaseBundle;
import org.killbill.billing.subscription.api.user.SubscriptionBaseTransition;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

public class DefaultEventsStream implements EventsStream {

    private final ImmutableAccountData account;
    private final SubscriptionBaseBundle bundle;
    // All blocking states for the account, associated bundle or subscription
    private final List<BlockingState> blockingStates;
    private final BlockingChecker blockingChecker;
    // Base subscription for the bundle if it exists, null otherwise
    private final SubscriptionBase baseSubscription;
    // Subscription associated with this entitlement (equals to baseSubscription for base subscriptions)
    private final SubscriptionBase subscription;
    // All subscriptions for that bundle
    private final List<SubscriptionBase> allSubscriptionsForBundle;
    private final InternalTenantContext internalTenantContext;
    private final DateTime utcNow;
    private final LocalDate utcToday;
    private final int defaultBillCycleDayLocal;

    private BlockingAggregator currentStateBlockingAggregator;
    private List<BlockingState> subscriptionEntitlementStates;
    private LocalDate entitlementEffectiveStartDate;
    private DateTime entitlementEffectiveStartDateTime;

    private LocalDate entitlementEffectiveEndDate;
    private DateTime entitlementEffectiveEndDateTime;

    private BlockingState entitlementStartEvent;
    private BlockingState entitlementCancelEvent;
    private EntitlementState entitlementState;

    public DefaultEventsStream(final ImmutableAccountData account, final SubscriptionBaseBundle bundle,
                               final List<BlockingState> blockingStates, final BlockingChecker blockingChecker,
                               @Nullable final SubscriptionBase baseSubscription, final SubscriptionBase subscription,
                               final List<SubscriptionBase> allSubscriptionsForBundle,
                               final int defaultBillCycleDayLocal,
                               final InternalTenantContext contextWithValidAccountRecordId, final DateTime utcNow) {
        this.account = account;
        this.bundle = bundle;
        this.blockingStates = blockingStates;
        this.blockingChecker = blockingChecker;
        this.baseSubscription = baseSubscription;
        this.subscription = subscription;
        this.allSubscriptionsForBundle = allSubscriptionsForBundle;
        this.defaultBillCycleDayLocal = defaultBillCycleDayLocal;
        this.internalTenantContext = contextWithValidAccountRecordId;
        this.utcNow = utcNow;
        this.utcToday = contextWithValidAccountRecordId.toLocalDate(utcNow);

        setup();
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

    @Override
    public SubscriptionBase getBasePlanSubscriptionBase() {
        return baseSubscription;
    }

    @Override
    public SubscriptionBase getSubscriptionBase() {
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

    @Override
    public DateTime getEntitlementEffectiveStartDateTime() {
        return entitlementEffectiveStartDateTime;
    }

    @Override
    public DateTime getEntitlementEffectiveEndDateTime() {
        return entitlementEffectiveEndDateTime;
    }

    @Override
    public EntitlementState getEntitlementState() {
        return entitlementState;
    }

    @Override
    public LocalDate getEntitlementEffectiveStartDate() {
        return entitlementEffectiveStartDate;
    }

    @Override
    public boolean isBlockChange() {
        return currentStateBlockingAggregator.isBlockChange();
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
    public boolean isEntitlementPending() {
        return entitlementState == EntitlementState.PENDING;
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
    public boolean isBlockChange(final DateTime effectiveDate) {
        Preconditions.checkState(effectiveDate != null);
        final BlockingAggregator aggregator = getBlockingAggregator(effectiveDate);
        return aggregator.isBlockChange();
    }

    @Override
    public int getDefaultBillCycleDayLocal() {
        return defaultBillCycleDayLocal;
    }

    @Override
    public Collection<BlockingState> getBlockingStates() {
        return blockingStates;
    }

    @Override
    public Collection<BlockingState> getPendingEntitlementCancellationEvents() {

        return Collections2.<BlockingState>filter(subscriptionEntitlementStates,
                                                  new Predicate<BlockingState>() {
                                                      @Override
                                                      public boolean apply(final BlockingState input) {
                                                          return !input.getEffectiveDate().isBefore(utcNow) &&
                                                                 DefaultEntitlementApi.ENT_STATE_CANCELLED.equals(input.getStateName()) &&
                                                                 (
                                                                         // ... for that subscription
                                                                         BlockingStateType.SUBSCRIPTION.equals(input.getType()) && input.getBlockedId().equals(subscription.getId()) ||
                                                                         // ... for the associated base subscription
                                                                         BlockingStateType.SUBSCRIPTION.equals(input.getType()) && input.getBlockedId().equals(baseSubscription.getId())
                                                                 );
                                                      }
                                                  });

    }

    @Override
    public BlockingState getEntitlementCancellationEvent() {
        return entitlementCancelEvent;
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
                                                                                                               final Plan lastActivePlan = subscription.getLastActivePlan();
                                                                                                               final boolean result = ProductCategory.ADD_ON.equals(subscription.getCategory()) &&
                                                                                                                                      // Check the subscription started, if not we don't want it, and that way we avoid doing NPE a few lines below.
                                                                                                                                      lastActivePlan != null &&
                                                                                                                                      // Check the entitlement for that add-on hasn't been cancelled yet
                                                                                                                                      getEntitlementCancellationEvent(subscription.getId()) == null &&
                                                                                                                                      (
                                                                                                                                              // Base subscription cancelled
                                                                                                                                              baseTransitionTriggerNextProduct == null ||
                                                                                                                                              (
                                                                                                                                                      // Change plan - check which add-ons to cancel
                                                                                                                                                      includedAddonsForProduct.contains(lastActivePlan.getProduct().getName()) ||
                                                                                                                                                      !availableAddonsForProduct.contains(subscription.getLastActivePlan().getProduct().getName())
                                                                                                                                              )
                                                                                                                                      );
                                                                                                               return result;
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
        computeEntitlementBlockingStates();
        computeCurrentBlockingAggregator();
        computeEntitlementStartEvent();
        computeEntitlementCancelEvent();
        computeStateForEntitlement();
    }

    private void computeCurrentBlockingAggregator() {
        currentStateBlockingAggregator = getBlockingAggregator(null);
    }

    private BlockingAggregator getBlockingAggregator(final DateTime upTo) {

        final List<BlockingState> currentSubscriptionBlockingStatesForServices = filterCurrentBlockableStatePerService(BlockingStateType.SUBSCRIPTION, subscription.getId(), upTo);
        final List<BlockingState> currentBundleBlockingStatesForServices = filterCurrentBlockableStatePerService(BlockingStateType.SUBSCRIPTION_BUNDLE, subscription.getBundleId(), upTo);
        final List<BlockingState> currentAccountBlockingStatesForServices = filterCurrentBlockableStatePerService(BlockingStateType.ACCOUNT, account.getId(), upTo);
        return blockingChecker.getBlockedStatus(currentAccountBlockingStatesForServices,
                                                currentBundleBlockingStatesForServices,
                                                currentSubscriptionBlockingStatesForServices, internalTenantContext);
    }



    private List<BlockingState> filterCurrentBlockableStatePerService(final BlockingStateType type, final UUID blockableId, @Nullable final DateTime upTo) {

        final DateTime resolvedUpTo = upTo != null ? upTo : utcNow;

        final Map<String, BlockingState> currentBlockingStatePerService = new HashMap<String, BlockingState>();
        for (final BlockingState blockingState : blockingStates) {
            if (!blockingState.getBlockedId().equals(blockableId)) {
                continue;
            }
            if (blockingState.getType() != type) {
                continue;
            }
            if (blockingState.getEffectiveDate().isAfter(resolvedUpTo)) {
                continue;
            }

            if (currentBlockingStatePerService.get(blockingState.getService()) == null ||
                !currentBlockingStatePerService.get(blockingState.getService()).getEffectiveDate().isAfter(blockingState.getEffectiveDate())) {
                currentBlockingStatePerService.put(blockingState.getService(), blockingState);
            }
        }

        return ImmutableList.<BlockingState>copyOf(currentBlockingStatePerService.values());
    }

    private void computeEntitlementStartEvent() {
        entitlementStartEvent = Iterables.<BlockingState>tryFind(subscriptionEntitlementStates,
                                                                  new Predicate<BlockingState>() {
                                                                      @Override
                                                                      public boolean apply(final BlockingState input) {
                                                                          return DefaultEntitlementApi.ENT_STATE_START.equals(input.getStateName());
                                                                      }
                                                                  }).orNull();

        // Note that we still default to subscriptionBase.startDate (for compatibility issue where ENT_STATE_START does not exist)
        entitlementEffectiveStartDateTime = entitlementStartEvent != null ?
                                            entitlementStartEvent.getEffectiveDate() :
                                            getSubscriptionBase().getStartDate();
        entitlementEffectiveStartDate = internalTenantContext.toLocalDate(entitlementEffectiveStartDateTime);

    }

    private void computeEntitlementCancelEvent() {
        entitlementCancelEvent = Iterables.<BlockingState>tryFind(subscriptionEntitlementStates,
                                                                  new Predicate<BlockingState>() {
                                                                      @Override
                                                                      public boolean apply(final BlockingState input) {
                                                                          return DefaultEntitlementApi.ENT_STATE_CANCELLED.equals(input.getStateName());
                                                                      }
                                                                  }).orNull();
        entitlementEffectiveEndDateTime =  entitlementCancelEvent != null ? entitlementCancelEvent.getEffectiveDate() : null;
        entitlementEffectiveEndDate = entitlementEffectiveEndDateTime != null ? internalTenantContext.toLocalDate(entitlementEffectiveEndDateTime) : null;
    }

    private void computeStateForEntitlement() {
        // Current state for the ENTITLEMENT_SERVICE_NAME is set to cancelled
        if (entitlementEffectiveEndDate != null && entitlementEffectiveEndDate.compareTo(internalTenantContext.toLocalDate(utcNow)) <= 0) {
            entitlementState = EntitlementState.CANCELLED;
        } else {
            if (entitlementEffectiveStartDate.compareTo(utcToday) > 0) {
                entitlementState = EntitlementState.PENDING;
            } else {
                // Gather states across all services and check if one of them is set to 'blockEntitlement'
                entitlementState = (currentStateBlockingAggregator != null && currentStateBlockingAggregator.isBlockEntitlement() ? EntitlementState.BLOCKED : EntitlementState.ACTIVE);
            }
        }
    }

    private void computeEntitlementBlockingStates() {
        subscriptionEntitlementStates = filterBlockingStatesForEntitlementService(BlockingStateType.SUBSCRIPTION, subscription.getId());
    }

    private List<BlockingState> filterBlockingStatesForEntitlementService(final BlockingStateType blockingStateType, @Nullable final UUID blockableId) {
        return ImmutableList.<BlockingState>copyOf(Iterables.<BlockingState>filter(blockingStates,
                                                                                   new Predicate<BlockingState>() {
                                                                                       @Override
                                                                                       public boolean apply(final BlockingState input) {
                                                                                           return blockingStateType.equals(input.getType()) &&
                                                                                                  EntitlementService.ENTITLEMENT_SERVICE_NAME.equals(input.getService()) &&
                                                                                                  input.getBlockedId().equals(blockableId);
                                                                                       }
                                                                                   }));
    }
}
