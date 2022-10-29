/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.account.api.ImmutableAccountData;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.EventsStream;
import org.killbill.billing.entitlement.api.BlockingState;
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.killbill.billing.entitlement.api.DefaultEntitlementApi;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.entitlement.block.BlockingChecker;
import org.killbill.billing.entitlement.block.BlockingChecker.BlockingAggregator;
import org.killbill.billing.junction.DefaultBlockingState;
import org.killbill.billing.platform.api.KillbillService.KILLBILL_SERVICES;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;
import org.killbill.billing.subscription.api.user.SubscriptionBaseBundle;
import org.killbill.billing.subscription.api.user.SubscriptionBaseTransition;
import org.killbill.commons.utils.Preconditions;

public class DefaultEventsStream implements EventsStream {

    private final ImmutableAccountData account;
    private final SubscriptionBaseBundle bundle;
    // All blocking states for the account, associated bundle or subscription
    private final Collection<BlockingState> blockingStates;
    private final Collection<BlockingState> blockingStatesWithDeletedEvents;
    private final BlockingChecker blockingChecker;
    // Base subscription for the bundle if it exists, null otherwise
    private final SubscriptionBase baseSubscription;
    // Subscription associated with this entitlement (equals to baseSubscription for base subscriptions)
    private final SubscriptionBase subscription;
    // All subscriptions for that bundle
    private final Collection<SubscriptionBase> allSubscriptionsForBundle;
    private final InternalTenantContext internalTenantContext;
    private final DateTime utcNow;
    private final LocalDate utcToday;
    private final Integer defaultBillCycleDayLocal;

    private BlockingAggregator currentStateBlockingAggregator;
    private List<BlockingState> subscriptionEntitlementStates;
    private LocalDate entitlementEffectiveStartDate;
    private DateTime entitlementEffectiveStartDateTime;

    private LocalDate entitlementEffectiveEndDate;
    private DateTime entitlementEffectiveEndDateTime;

    private BlockingState entitlementStartEvent;
    private BlockingState entitlementCancelEvent;
    private EntitlementState entitlementState;

    private final boolean includeDeletedEvents;

    public DefaultEventsStream(final ImmutableAccountData account,
                               final SubscriptionBaseBundle bundle,
                               final Collection<BlockingState> blockingStates,
                               final Collection<BlockingState> blockingStatesWithDeletedEvents,
                               final BlockingChecker blockingChecker,
                               @Nullable final SubscriptionBase baseSubscription,
                               final SubscriptionBase subscription,
                               final Collection<SubscriptionBase> allSubscriptionsForBundle,
                               @Nullable final Integer defaultBillCycleDayLocal,
                               final InternalTenantContext contextWithValidAccountRecordId, final DateTime utcNow) {
        sanityChecks(account, bundle, baseSubscription, subscription);
        this.account = account;
        this.bundle = bundle;
        this.blockingChecker = blockingChecker;
        this.baseSubscription = baseSubscription;
        this.subscription = subscription;
        this.allSubscriptionsForBundle = allSubscriptionsForBundle;
        this.defaultBillCycleDayLocal = defaultBillCycleDayLocal;
        this.internalTenantContext = contextWithValidAccountRecordId;
        this.utcNow = utcNow;
        this.utcToday = contextWithValidAccountRecordId.toLocalDate(utcNow);

        this.blockingStates = blockingStates;
        this.blockingStatesWithDeletedEvents = blockingStatesWithDeletedEvents;
        this.includeDeletedEvents = !blockingStatesWithDeletedEvents.isEmpty();

        setup();
    }

    public DefaultEventsStream(final ImmutableAccountData account,
            				   final SubscriptionBaseBundle bundle,
            				   final Collection<BlockingState> blockingStates,
            				   final BlockingChecker blockingChecker,
            				   @Nullable final SubscriptionBase baseSubscription,
            				   final SubscriptionBase subscription,
            				   final Collection<SubscriptionBase> allSubscriptionsForBundle,
            				   @Nullable final Integer defaultBillCycleDayLocal,
            				   final InternalTenantContext contextWithValidAccountRecordId, final DateTime utcNow) {
    		this(account, bundle, blockingStates, Collections.emptyList(), blockingChecker, baseSubscription, subscription, allSubscriptionsForBundle, defaultBillCycleDayLocal, contextWithValidAccountRecordId, utcNow);
    }

    
    private void sanityChecks(@Nullable final ImmutableAccountData account,
                              @Nullable final SubscriptionBaseBundle bundle,
                              @Nullable final SubscriptionBase baseSubscription,
                              @Nullable final SubscriptionBase subscription) {
        // baseSubscription can be null for STANDALONE products (https://github.com/killbill/killbill/issues/840)
        for (final Object object : new Object[]{account, bundle, subscription}) {
            Preconditions.checkNotNull(object,
                                       "accountId='%s', bundleId='%s', baseSubscriptionId='%s', subscriptionId='%s'",
                                       account != null ? account.getId() : null,
                                       bundle != null ? bundle.getId() : null,
                                       baseSubscription != null ? baseSubscription.getId() : null,
                                       subscription != null ? subscription.getId() : null);
        }
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
    public String getExternalKey() {
        return subscription.getExternalKey();
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
    public boolean getIncludeDeletedEvents() {
    	return this.includeDeletedEvents;
    }

    @Override
    public boolean isBlockEntitlement(final DateTime effectiveDate) {
        Preconditions.checkState(effectiveDate != null);
        final BlockingAggregator aggregator = getBlockingAggregator(effectiveDate);
        return aggregator.isBlockEntitlement();
    }

    @Override
    public Integer getDefaultBillCycleDayLocal() {
        return defaultBillCycleDayLocal;
    }

    @Override
    public Collection<BlockingState> getBlockingStates(final boolean includeDeletedEvents) {
        return includeDeletedEvents ? blockingStatesWithDeletedEvents : blockingStates;
    }

    @Override
    public Collection<BlockingState> getPendingEntitlementCancellationEvents() {
        return subscriptionEntitlementStates
                .stream()
                .filter(input -> !input.getEffectiveDate().isBefore(utcNow) &&
                                 DefaultEntitlementApi.ENT_STATE_CANCELLED.equals(input.getStateName()) &&
                                 (
                                         // ... for that subscription
                                         BlockingStateType.SUBSCRIPTION.equals(input.getType()) && input.getBlockedId().equals(subscription.getId()) ||
                                         // ... for the associated base subscription
                                         BlockingStateType.SUBSCRIPTION.equals(input.getType()) && input.getBlockedId().equals(baseSubscription.getId())
                                 ))
                .collect(Collectors.toUnmodifiableList());

    }

    @Override
    public BlockingState getEntitlementCancellationEvent() {
        return entitlementCancelEvent;
    }

    public BlockingState getEntitlementCancellationEvent(final UUID subscriptionId) {
        return subscriptionEntitlementStates.stream()
                .filter(input -> DefaultEntitlementApi.ENT_STATE_CANCELLED.equals(input.getStateName()) &&
                                 input.getBlockedId().equals(subscriptionId))
                .findFirst().orElse(null);
    }

    public Iterable<SubscriptionBaseTransition> getPendingSubscriptionEvents(final DateTime effectiveDatetime, final SubscriptionBaseTransitionType... types) {
        final List<SubscriptionBaseTransitionType> typeList = List.of(types);
        return subscription.getAllTransitions(false)
                .stream()
                .filter(input -> !input.getEffectiveTransitionTime().isBefore(effectiveDatetime) &&
                                 typeList.contains(input.getTransitionType()))
                .collect(Collectors.toUnmodifiableList());
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
            return Collections.emptyList();
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
            return Collections.emptyList();
        }
    }

    private Collection<BlockingState> computeAddonsBlockingStatesForNextSubscriptionBaseEvent(final DateTime effectiveDate,
                                                                                              final boolean useBillingEffectiveDate) {
        SubscriptionBaseTransition subscriptionBaseTransitionTrigger = null;
        if (!isEntitlementFutureCancelled()) {
            // Compute the transition trigger (either subscription cancel or change)
            final Iterable<SubscriptionBaseTransition> pendingSubscriptionBaseTransitions = getPendingSubscriptionEvents(effectiveDate, SubscriptionBaseTransitionType.CHANGE, SubscriptionBaseTransitionType.CANCEL);
            if (!pendingSubscriptionBaseTransitions.iterator().hasNext()) {
                return Collections.emptyList();
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
        if (baseSubscription == null ||
            baseSubscription.getLastActivePlan() == null ||
            !ProductCategory.BASE.equals(baseSubscription.getLastActivePlan().getProduct().getCategory())) {
            return Collections.emptySet();
        }

        // Retrieve all add-ons to block for that base subscription
        final Collection<SubscriptionBase> futureBlockedAddons = allSubscriptionsForBundle
                .stream()
                .filter(subscription -> isAddOnsNeedToBeBlocked(subscription, baseTransitionTriggerNextProduct))
                .collect(Collectors.toUnmodifiableList());

        // Create the blocking states
        return futureBlockedAddons.stream()
                .map(input -> new DefaultBlockingState(input.getId(),
                                                       BlockingStateType.SUBSCRIPTION,
                                                       DefaultEntitlementApi.ENT_STATE_CANCELLED,
                                                       KILLBILL_SERVICES.ENTITLEMENT_SERVICE.getServiceName(),
                                                       true,
                                                       true,
                                                       false,
                                                       blockingStateEffectiveDate))
                .collect(Collectors.toUnmodifiableList());
    }

    private boolean isAddOnsNeedToBeBlocked(final SubscriptionBase subscription, final Product baseTransitionTriggerNextProduct) {
        // Compute included and available addons for the new product
        final Collection<String> includedAddonsForProduct;
        final Collection<String> availableAddonsForProduct;
        if (baseTransitionTriggerNextProduct == null) {
            includedAddonsForProduct = Collections.emptyList();
            availableAddonsForProduct = Collections.emptyList();
        } else {
            includedAddonsForProduct = baseTransitionTriggerNextProduct.getIncluded().stream()
                                                                       .map(Product::getName)
                                                                       .collect(Collectors.toUnmodifiableSet());

            availableAddonsForProduct = baseTransitionTriggerNextProduct.getAvailable()
                                                                        .stream()
                                                                        .map(Product::getName)
                                                                        .collect(Collectors.toUnmodifiableSet());
        }

        final Plan lastActivePlan = subscription.getLastActivePlan();

        return ProductCategory.ADD_ON.equals(subscription.getCategory()) &&
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

        final Map<String, BlockingState> currentBlockingStatePerService = new HashMap<>();
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

        return List.<BlockingState>copyOf(currentBlockingStatePerService.values());
    }

    private void computeEntitlementStartEvent() {
        entitlementStartEvent = subscriptionEntitlementStates.stream()
                .filter(input -> DefaultEntitlementApi.ENT_STATE_START.equals(input.getStateName()))
                .findFirst().orElse(null);

        // Note that we still default to subscriptionBase.startDate (for compatibility issue where ENT_STATE_START does not exist)
        entitlementEffectiveStartDateTime = entitlementStartEvent != null ?
                                            entitlementStartEvent.getEffectiveDate() :
                                            getSubscriptionBase().getStartDate();
        entitlementEffectiveStartDate = internalTenantContext.toLocalDate(entitlementEffectiveStartDateTime);

    }

    private void computeEntitlementCancelEvent() {
        entitlementCancelEvent = subscriptionEntitlementStates.stream()
                .filter(input -> DefaultEntitlementApi.ENT_STATE_CANCELLED.equals(input.getStateName()))
                .findFirst().orElse(null);
        entitlementEffectiveEndDateTime =  entitlementCancelEvent != null ? entitlementCancelEvent.getEffectiveDate() : null;
        entitlementEffectiveEndDate = entitlementEffectiveEndDateTime != null ? internalTenantContext.toLocalDate(entitlementEffectiveEndDateTime) : null;
    }

    private void computeStateForEntitlement() {
        // Current state for the ENTITLEMENT_SERVICE_NAME is set to cancelled
        if (entitlementEffectiveEndDateTime != null && entitlementEffectiveEndDateTime.compareTo(utcNow) <= 0) {
            entitlementState = EntitlementState.CANCELLED;
        } else {
            if (entitlementEffectiveStartDateTime.compareTo(utcNow) > 0) {
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

    private List<BlockingState> filterBlockingStatesForEntitlementService(final BlockingStateType blockingStateType,
                                                                          @Nullable final UUID blockableId) {
    	return blockingStates.stream()
                .filter(input -> blockingStateType.equals(input.getType()) &&
                                 KILLBILL_SERVICES.ENTITLEMENT_SERVICE.getServiceName().equals(input.getService()) &&
                                 input.getBlockedId().equals(blockableId))
                .collect(Collectors.toUnmodifiableList());
    }
}
