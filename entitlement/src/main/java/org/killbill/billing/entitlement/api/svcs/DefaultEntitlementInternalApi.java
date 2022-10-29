/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2019 Groupon, Inc
 * Copyright 2014-2019 The Billing Project, LLC
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

package org.killbill.billing.entitlement.api.svcs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.entitlement.DefaultEntitlementService;
import org.killbill.billing.entitlement.EntitlementInternalApi;
import org.killbill.billing.entitlement.api.BaseEntitlementWithAddOnsSpecifier;
import org.killbill.billing.entitlement.api.BlockingState;
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.killbill.billing.entitlement.api.DefaultBaseEntitlementWithAddOnsSpecifier;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.entitlement.api.DefaultEntitlementApi;
import org.killbill.billing.entitlement.api.DefaultEntitlementContext;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.entitlement.api.EntitlementApi;
import org.killbill.billing.entitlement.api.EntitlementApiException;
import org.killbill.billing.entitlement.api.EntitlementPluginExecution;
import org.killbill.billing.entitlement.api.EntitlementPluginExecution.WithEntitlementPlugin;
import org.killbill.billing.entitlement.block.BlockingChecker;
import org.killbill.billing.entitlement.dao.BlockingStateDao;
import org.killbill.billing.entitlement.engine.core.EntitlementUtils;
import org.killbill.billing.entitlement.engine.core.EventsStreamBuilder;
import org.killbill.billing.entitlement.plugin.api.EntitlementContext;
import org.killbill.billing.entitlement.plugin.api.OperationType;
import org.killbill.billing.junction.DefaultBlockingState;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.platform.api.KillbillService.KILLBILL_SERVICES;
import org.killbill.billing.security.api.SecurityApi;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseInternalApi;
import org.killbill.billing.subscription.api.user.SubscriptionBaseApiException;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.optimizer.BusOptimizer;
import org.killbill.clock.Clock;
import org.killbill.notificationq.api.NotificationEvent;
import org.killbill.notificationq.api.NotificationQueue;
import org.killbill.notificationq.api.NotificationQueueService;
import org.killbill.notificationq.api.NotificationQueueService.NoSuchNotificationQueue;

public class DefaultEntitlementInternalApi extends DefaultEntitlementApiBase implements EntitlementInternalApi {

    private final BlockingStateDao blockingStateDao;

    @Inject
    public DefaultEntitlementInternalApi(final BusOptimizer eventBus,
                                         final EntitlementApi entitlementApi, final EntitlementPluginExecution pluginExecution,
                                         final InternalCallContextFactory internalCallContextFactory,
                                         final SubscriptionBaseInternalApi subscriptionInternalApi,
                                         final AccountInternalApi accountApi, final BlockingStateDao blockingStateDao, final Clock clock,
                                         final BlockingChecker checker, final NotificationQueueService notificationQueueService,
                                         final EventsStreamBuilder eventsStreamBuilder, final EntitlementUtils entitlementUtils, final SecurityApi securityApi) {
        super(eventBus, entitlementApi, pluginExecution, internalCallContextFactory, subscriptionInternalApi, accountApi, blockingStateDao, clock, checker, notificationQueueService, eventsStreamBuilder, entitlementUtils, securityApi);
        this.blockingStateDao = blockingStateDao;
    }

    @Override
    public void cancel(final Iterable<Entitlement> entitlements, @Nullable final LocalDate effectiveDate, final BillingActionPolicy billingPolicy, final Iterable<PluginProperty> properties, final InternalCallContext internalCallContext) throws EntitlementApiException {

        if (!entitlements.iterator().hasNext()) {
            return;
        }

        final CallContext callContext = internalCallContextFactory.createCallContext(internalCallContext);

        final Map<BlockingState, Optional<UUID>> blockingStates = new HashMap<>();
        final Map<DateTime, Collection<NotificationEvent>> notificationEvents = new HashMap<>();
        final Collection<EntitlementContext> pluginContexts = new LinkedList<>();
        final List<WithEntitlementPlugin> callbacks = new LinkedList<>();
        final List<SubscriptionBase> subscriptions = new LinkedList<>();

        for (final Entitlement entitlement : entitlements) {
            if (entitlement.getState() == EntitlementState.CANCELLED) {
                // If subscription has already been cancelled, we ignore and carry on
                continue;
            }

            final DateTime effectiveDateTime = dateHelper.fromLocalDateAndReferenceTime(effectiveDate, callContext.getCreatedDate(), internalCallContext);

            final BaseEntitlementWithAddOnsSpecifier baseEntitlementWithAddOnsSpecifier = new DefaultBaseEntitlementWithAddOnsSpecifier(
                    entitlement.getBundleId(),
                    entitlement.getBundleExternalKey(),
                    null,
                    effectiveDateTime,
                    null,
                    false);
            final List<BaseEntitlementWithAddOnsSpecifier> baseEntitlementWithAddOnsSpecifierList = new ArrayList<BaseEntitlementWithAddOnsSpecifier>();
            baseEntitlementWithAddOnsSpecifierList.add(baseEntitlementWithAddOnsSpecifier);

            final EntitlementContext pluginContext = new DefaultEntitlementContext(OperationType.CANCEL_SUBSCRIPTION,
                                                                                   entitlement.getAccountId(),
                                                                                   null,
                                                                                   baseEntitlementWithAddOnsSpecifierList,
                                                                                   billingPolicy,
                                                                                   properties,
                                                                                   callContext);
            pluginContexts.add(pluginContext);

            final WithEntitlementPlugin<Entitlement> cancelEntitlementWithPlugin = new WithDateOverrideBillingPolicyEntitlementCanceler((DefaultEntitlement) entitlement,
                                                                                                                                        blockingStates,
                                                                                                                                        notificationEvents,
                                                                                                                                        callContext,
                                                                                                                                        internalCallContext);
            callbacks.add(cancelEntitlementWithPlugin);

            subscriptions.add(((DefaultEntitlement) entitlement).getSubscriptionBase());
        }

        final Callable<Void> preCallbacksCallback = new BulkSubscriptionBaseCancellation(subscriptions,
                                                                                         billingPolicy,
                                                                                         internalCallContext);

        pluginExecution.executeWithPlugin(preCallbacksCallback, callbacks, pluginContexts);

        final Map<BlockingState, Optional<UUID>> states = new HashMap<>();
        for (final Entry<BlockingState, Optional<UUID>> entry : blockingStates.entrySet()) {
            states.put(entry.getKey(), Optional.of(entry.getValue().get()));
        }
        // Record the new states first, then insert the notifications to avoid race conditions
        blockingStateDao.setBlockingStatesAndPostBlockingTransitionEvent(states, internalCallContext);
        for (final DateTime effectiveDateForNotification : notificationEvents.keySet()) {
            for (final NotificationEvent notificationEvent : notificationEvents.get(effectiveDateForNotification)) {
                recordFutureNotification(effectiveDateForNotification, notificationEvent, internalCallContext);
            }
        }
    }

    private void recordFutureNotification(final DateTime effectiveDate,
                                          final NotificationEvent notificationEvent,
                                          final InternalCallContext context) {
        try {
            final NotificationQueue subscriptionEventQueue = notificationQueueService.getNotificationQueue(KILLBILL_SERVICES.ENTITLEMENT_SERVICE.getServiceName(),
                                                                                                           DefaultEntitlementService.NOTIFICATION_QUEUE_NAME);
            subscriptionEventQueue.recordFutureNotification(effectiveDate, notificationEvent, context.getUserToken(), context.getAccountRecordId(), context.getTenantRecordId());
        } catch (final NoSuchNotificationQueue e) {
            throw new RuntimeException(e);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    private class BulkSubscriptionBaseCancellation implements Callable<Void> {

        private final Iterable<SubscriptionBase> subscriptions;
        private final BillingActionPolicy billingPolicy;
        private final InternalCallContext callContext;

        public BulkSubscriptionBaseCancellation(final Iterable<SubscriptionBase> subscriptions,
                                                final BillingActionPolicy billingPolicy,
                                                final InternalCallContext callContext) {
            this.subscriptions = subscriptions;
            this.billingPolicy = billingPolicy;
            this.callContext = callContext;
        }

        @Override
        public Void call() throws Exception {
            try {
                subscriptionInternalApi.cancelBaseSubscriptions(subscriptions, billingPolicy, callContext);
            } catch (final SubscriptionBaseApiException e) {
                throw new EntitlementApiException(e);
            }

            return null;
        }
    }

    // Note that the implementation is similar to DefaultEntitlement#cancelEntitlementWithDateOverrideBillingPolicy but state isn't persisted on disk
    private class WithDateOverrideBillingPolicyEntitlementCanceler implements WithEntitlementPlugin<Entitlement> {

        private final DefaultEntitlement entitlement;
        private final Map<BlockingState, Optional<UUID>> blockingStates;
        private final Map<DateTime, Collection<NotificationEvent>> notificationEventsWithEffectiveDate;
        private final CallContext callContext;
        private final InternalCallContext internalCallContext;

        public WithDateOverrideBillingPolicyEntitlementCanceler(final DefaultEntitlement entitlement,
                                                                final Map<BlockingState, Optional<UUID>> blockingStates,
                                                                final Map<DateTime, Collection<NotificationEvent>> notificationEventsWithEffectiveDate,
                                                                final CallContext callContext,
                                                                final InternalCallContext internalCallContext) {
            this.entitlement = entitlement;
            this.blockingStates = blockingStates;
            this.notificationEventsWithEffectiveDate = notificationEventsWithEffectiveDate;
            this.callContext = callContext;
            this.internalCallContext = internalCallContext;
        }

        @Override
        public Entitlement doCall(final EntitlementApi entitlementApi, final DefaultEntitlementContext updatedPluginContext) throws EntitlementApiException {
            DateTime effectiveDate = updatedPluginContext.getBaseEntitlementWithAddOnsSpecifiers().iterator().next().getEntitlementEffectiveDate();

            //
            // If the entitlementDate provided is ahead we default to the effective subscriptionBase cancellationDate to avoid weird timing issues.
            //
            // (Note that entitlement.getSubscriptionBase() returns the right state (although we did not refresh context) because the DefaultSubscriptionBaseApiService#doCancelPlan
            //  rebuild transitions on that same  DefaultSubscriptionBase object)
            //
            final DateTime subscriptionBaseCancellationDate = entitlement.getSubscriptionBase().getEndDate() != null ?
                                                              entitlement.getSubscriptionBase().getEndDate() :
                                                              entitlement.getSubscriptionBase().getFutureEndDate();

            if (effectiveDate.compareTo(subscriptionBaseCancellationDate) > 0) {
                effectiveDate = subscriptionBaseCancellationDate;
            }

            final BlockingState newBlockingState = new DefaultBlockingState(entitlement.getId(), BlockingStateType.SUBSCRIPTION, DefaultEntitlementApi.ENT_STATE_CANCELLED, KILLBILL_SERVICES.ENTITLEMENT_SERVICE.getServiceName(), true, true, false, effectiveDate);
            final Collection<NotificationEvent> notificationEvents = new ArrayList<NotificationEvent>();
            final Collection<BlockingState> addOnsBlockingStates = entitlement.computeAddOnBlockingStates(effectiveDate, notificationEvents, callContext, internalCallContext);

            final Optional<UUID> bundleIdOptional = Optional.ofNullable(entitlement.getBundleId());
            blockingStates.put(newBlockingState, bundleIdOptional);
            for (final BlockingState blockingState : addOnsBlockingStates) {
                blockingStates.put(blockingState, bundleIdOptional);
            }

            if (notificationEventsWithEffectiveDate.get(effectiveDate) == null) {
                notificationEventsWithEffectiveDate.put(effectiveDate, notificationEvents);
            } else {
                notificationEventsWithEffectiveDate.get(effectiveDate).addAll(notificationEvents);
            }

            // Unable to return the new state (not on disk yet)
            return null;
        }
    }
}
