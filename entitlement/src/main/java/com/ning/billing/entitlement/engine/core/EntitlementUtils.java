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

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.bus.api.BusEvent;
import com.ning.billing.bus.api.PersistentBus;
import com.ning.billing.bus.api.PersistentBus.EventBusException;
import com.ning.billing.callcontext.InternalCallContext;
import com.ning.billing.callcontext.InternalTenantContext;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.Product;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.clock.Clock;
import com.ning.billing.entitlement.DefaultEntitlementService;
import com.ning.billing.entitlement.EntitlementService;
import com.ning.billing.entitlement.api.BlockingApiException;
import com.ning.billing.entitlement.api.BlockingState;
import com.ning.billing.entitlement.api.BlockingStateType;
import com.ning.billing.entitlement.api.DefaultBlockingTransitionInternalEvent;
import com.ning.billing.entitlement.api.DefaultEntitlementApi;
import com.ning.billing.entitlement.api.Entitlement.EntitlementState;
import com.ning.billing.entitlement.api.EntitlementApiException;
import com.ning.billing.entitlement.block.BlockingChecker;
import com.ning.billing.entitlement.block.BlockingChecker.BlockingAggregator;
import com.ning.billing.entitlement.dao.BlockingStateDao;
import com.ning.billing.entitlement.dao.BlockingStateModelDao;
import com.ning.billing.junction.DefaultBlockingState;
import com.ning.billing.notificationq.api.NotificationEvent;
import com.ning.billing.notificationq.api.NotificationQueue;
import com.ning.billing.notificationq.api.NotificationQueueService;
import com.ning.billing.notificationq.api.NotificationQueueService.NoSuchNotificationQueue;
import com.ning.billing.subscription.api.SubscriptionBase;
import com.ning.billing.subscription.api.SubscriptionBaseInternalApi;
import com.ning.billing.subscription.api.SubscriptionBaseTransitionType;
import com.ning.billing.subscription.api.user.SubscriptionBaseTransition;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

public class EntitlementUtils {

    private static final Logger log = LoggerFactory.getLogger(EntitlementUtils.class);

    private final SubscriptionBaseInternalApi subscriptionInternalApi;
    private final BlockingStateDao dao;
    private final BlockingChecker blockingChecker;
    private final PersistentBus eventBus;
    private final Clock clock;
    protected final NotificationQueueService notificationQueueService;

    @Inject
    public EntitlementUtils(final SubscriptionBaseInternalApi subscriptionInternalApi,
                            final BlockingStateDao dao, final BlockingChecker blockingChecker,
                            final PersistentBus eventBus, final Clock clock,
                            final NotificationQueueService notificationQueueService) {
        this.subscriptionInternalApi = subscriptionInternalApi;
        this.dao = dao;
        this.blockingChecker = blockingChecker;
        this.eventBus = eventBus;
        this.clock = clock;
        this.notificationQueueService = notificationQueueService;
    }

    /**
     * Compute future blocking states for addons associated to a base subscription following a change or a cancellation
     * on that base subscription
     * <p/>
     * This is only used in the "read" path, to add events that are not on disk.
     * See same logic in DefaultSubscriptionDao#buildBundleSubscriptions
     *
     * @param blockingStates        existing entitlement blocking states for that base subscription
     * @param subscriptionBase      base subscription, reflecting the latest state (cancellation or change applied)
     * @param now                   present reference time (to avoid timing issues in DefaultEntitlement#blockAddOnsIfRequired)
     * @param internalTenantContext context  @return the blocking states for the add-ons
     * @throws EntitlementApiException
     */
    public Collection<BlockingState> computeFutureBlockingStatesForAssociatedAddons(final Iterable<BlockingStateModelDao> blockingStates,
                                                                                    final SubscriptionBase subscriptionBase,
                                                                                    final DateTime now,
                                                                                    final InternalTenantContext internalTenantContext) throws EntitlementApiException {
        if (!ProductCategory.BASE.equals(subscriptionBase.getCategory())) {
            // Only base subscriptions have add-ons
            return ImmutableList.<BlockingState>of();
        }

        // We need to find the first "trigger" transition, from which we will create the add-ons cancellation events.
        // This can either be a future entitlement cancel...
        final BlockingStateModelDao futureEntitlementCancelEvent = Iterables.<BlockingStateModelDao>tryFind(blockingStates,
                                                                                                            new Predicate<BlockingStateModelDao>() {
                                                                                                                @Override
                                                                                                                public boolean apply(final BlockingStateModelDao input) {
                                                                                                                    // Look at future cancellations only
                                                                                                                    return input.getEffectiveDate().isAfter(now) &&
                                                                                                                           EntitlementService.ENTITLEMENT_SERVICE_NAME.equals(input.getService()) &&
                                                                                                                           DefaultEntitlementApi.ENT_STATE_CANCELLED.equals(input.getState());
                                                                                                                }
                                                                                                            }).orNull();

        if (futureEntitlementCancelEvent != null) {
            // Note that in theory we could always only look subscription base as we assume entitlement cancel means subscription base cancel
            // but we want to use the effective date of the entitlement cancel event to create the add-on cancel event
            return computeBlockingStatesForAssociatedAddons(subscriptionBase.getBundleId(), null, futureEntitlementCancelEvent.getEffectiveDate(), internalTenantContext);
        } else {
            // ...or a subscription change (i.e. a change plan where the new plan has an impact on the existing add-on).
            // We need to go back to subscription base as entitlement doesn't know about these
            final SubscriptionBaseTransition futureSubscriptionBaseChangeEvent = Iterables.<SubscriptionBaseTransition>tryFind(subscriptionBase.getAllTransitions(),
                                                                                                                               new Predicate<SubscriptionBaseTransition>() {
                                                                                                                                   @Override
                                                                                                                                   public boolean apply(final SubscriptionBaseTransition input) {
                                                                                                                                       // Look at future changes only
                                                                                                                                       return input.getEffectiveTransitionTime().isAfter(now) &&
                                                                                                                                              (SubscriptionBaseTransitionType.CHANGE.equals(input.getTransitionType()) ||
                                                                                                                                               // This should never happen, as we should always have an entitlement cancel event
                                                                                                                                               // (see above), but add it just in case...
                                                                                                                                               SubscriptionBaseTransitionType.CANCEL.equals(input.getTransitionType()));
                                                                                                                                   }
                                                                                                                               }).orNull();
            if (futureSubscriptionBaseChangeEvent == null) {
                // Nothing to do
                return ImmutableList.<BlockingState>of();
            }

            final Plan nextPlan = futureSubscriptionBaseChangeEvent.getNextPlan();
            final Product product = nextPlan == null ? futureSubscriptionBaseChangeEvent.getPreviousPlan().getProduct() : nextPlan.getProduct();
            return computeBlockingStatesForAssociatedAddons(subscriptionBase.getBundleId(), product, futureSubscriptionBaseChangeEvent.getEffectiveTransitionTime(), internalTenantContext);
        }
    }

    // "write" path (when the cancellation/change is effective)
    public Collection<BlockingState> computeBlockingStatesForAssociatedAddons(final SubscriptionBase subscriptionBase, final DateTime effectiveDate, final InternalTenantContext internalTenantContext) {
        return computeBlockingStatesForAssociatedAddons(subscriptionBase.getBundleId(), EntitlementState.CANCELLED.equals(subscriptionBase.getState()) ? null : subscriptionBase.getLastActiveProduct(), effectiveDate, internalTenantContext);
    }

    private Collection<BlockingState> computeBlockingStatesForAssociatedAddons(final UUID bundleId,
                                                                               @Nullable final Product baseTransitionTriggerNextProduct,
                                                                               final DateTime baseTransitionTriggerEffectiveTransitionTime,
                                                                               final InternalTenantContext internalTenantContext) {
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
        final List<SubscriptionBase> subscriptionsForBundle = subscriptionInternalApi.getSubscriptionsForBundle(bundleId, internalTenantContext);
        final Collection<SubscriptionBase> futureBlockedAddons = Collections2.<SubscriptionBase>filter(subscriptionsForBundle,
                                                                                                       new Predicate<SubscriptionBase>() {
                                                                                                           @Override
                                                                                                           public boolean apply(final SubscriptionBase subscription) {
                                                                                                               return ProductCategory.ADD_ON.equals(subscription.getCategory()) &&
                                                                                                                      (
                                                                                                                              // Base entitlement cancelled, cancel all add-ons
                                                                                                                              // We don't check if the associated entitlement had already blocked here
                                                                                                                              // but the dao should eventually do the right thing (it won't insert duplicated
                                                                                                                              // blocking events)
                                                                                                                              baseTransitionTriggerNextProduct == null ||
                                                                                                                              (
                                                                                                                                      // Change plan - check which add-ons to cancel
                                                                                                                                      includedAddonsForProduct.contains(subscription.getLastActivePlan().getProduct().getName()) ||
                                                                                                                                      !availableAddonsForProduct.contains(subscription.getLastActivePlan().getProduct().getName())
                                                                                                                              )
                                                                                                                      );
                                                                                                           }
                                                                                                       });
        return Collections2.<SubscriptionBase, BlockingState>transform(futureBlockedAddons,
                                                                       new Function<SubscriptionBase, BlockingState>() {
                                                                           @Override
                                                                           public BlockingState apply(final SubscriptionBase input) {
                                                                               return new DefaultBlockingState(input.getId(), BlockingStateType.SUBSCRIPTION, DefaultEntitlementApi.ENT_STATE_CANCELLED, EntitlementService.ENTITLEMENT_SERVICE_NAME, true, true, false, baseTransitionTriggerEffectiveTransitionTime);
                                                                           }
                                                                       });
    }

    /**
     * Wrapper around BlockingStateDao#setBlockingState which will send an event on the bus if needed
     *
     * @param state   new state to store
     * @param context call context
     */
    public void setBlockingStateAndPostBlockingTransitionEvent(final BlockingState state, final InternalCallContext context) {
        final BlockingAggregator previousState = getBlockingStateFor(state.getBlockedId(), state.getType(), context);

        dao.setBlockingState(state, clock, context);

        final BlockingAggregator currentState = getBlockingStateFor(state.getBlockedId(), state.getType(), context);
        if (previousState != null && currentState != null) {
            postBlockingTransitionEvent(state.getEffectiveDate(), state.getBlockedId(), state.getType(), previousState, currentState, context);
        }
    }

    private BlockingAggregator getBlockingStateFor(final UUID blockableId, final BlockingStateType type, final InternalCallContext context) {
        try {
            return blockingChecker.getBlockedStatus(blockableId, type, context);
        } catch (BlockingApiException e) {
            log.warn("Failed to retrieve blocking state for {} {}", blockableId, type);
            return null;
        }
    }

    private void postBlockingTransitionEvent(final DateTime effectiveDate, final UUID blockableId, final BlockingStateType type,
                                             final BlockingAggregator previousState, final BlockingAggregator currentState,
                                             final InternalCallContext context) {
        final boolean isTransitionToBlockedBilling = !previousState.isBlockBilling() && currentState.isBlockBilling();
        final boolean isTransitionToUnblockedBilling = previousState.isBlockBilling() && !currentState.isBlockBilling();

        final boolean isTransitionToBlockedEntitlement = !previousState.isBlockEntitlement() && currentState.isBlockEntitlement();
        final boolean isTransitionToUnblockedEntitlement = previousState.isBlockEntitlement() && !currentState.isBlockEntitlement();

        if (effectiveDate.compareTo(clock.getUTCNow()) > 0) {
            // Add notification entry to send the bus event at the effective date
            final NotificationEvent notificationEvent = new BlockingTransitionNotificationKey(blockableId, type,
                                                                                              isTransitionToBlockedBilling, isTransitionToUnblockedBilling,
                                                                                              isTransitionToBlockedEntitlement, isTransitionToUnblockedEntitlement);
            recordFutureNotification(effectiveDate, notificationEvent, context);
        } else {
            // TODO Do we want to send a DefaultEffectiveEntitlementEvent for entitlement specific blocking states?
            final BusEvent event = new DefaultBlockingTransitionInternalEvent(blockableId, type,
                                                                              isTransitionToBlockedBilling, isTransitionToUnblockedBilling,
                                                                              isTransitionToBlockedEntitlement, isTransitionToUnblockedEntitlement,
                                                                              context.getAccountRecordId(), context.getTenantRecordId(), context.getUserToken());

            postBusEvent(event);
        }
    }

    private void postBusEvent(final BusEvent event) {
        try {
            // TODO STEPH Ideally we would like to post from transaction when we inserted the new blocking state, but new state would have to be recalculated from transaction which is
            // difficult without the help of BlockingChecker -- which itself relies on dao. Other alternative is duplicating the logic, or refactoring the DAO to export higher level api.
            eventBus.post(event);
        } catch (EventBusException e) {
            log.warn("Failed to post event {}", e);
        }
    }

    private void recordFutureNotification(final DateTime effectiveDate,
                                          final NotificationEvent notificationEvent,
                                          final InternalCallContext context) {
        try {
            final NotificationQueue subscriptionEventQueue = notificationQueueService.getNotificationQueue(DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME,
                                                                                                           DefaultEntitlementService.NOTIFICATION_QUEUE_NAME);
            subscriptionEventQueue.recordFutureNotification(effectiveDate, notificationEvent, context.getUserToken(), context.getAccountRecordId(), context.getTenantRecordId());
        } catch (NoSuchNotificationQueue e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
