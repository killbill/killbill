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

package org.killbill.billing.entitlement.api.svcs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.inject.Inject;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.entitlement.DefaultEntitlementService;
import org.killbill.billing.entitlement.EntitlementInternalApi;
import org.killbill.billing.entitlement.EntitlementService;
import org.killbill.billing.entitlement.api.BlockingState;
import org.killbill.billing.entitlement.api.BlockingStateType;
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
import org.killbill.billing.security.api.SecurityApi;
import org.killbill.billing.subscription.api.SubscriptionBaseInternalApi;
import org.killbill.billing.subscription.api.user.SubscriptionBaseApiException;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.bus.api.PersistentBus;
import org.killbill.clock.Clock;
import org.killbill.notificationq.api.NotificationEvent;
import org.killbill.notificationq.api.NotificationQueue;
import org.killbill.notificationq.api.NotificationQueueService;
import org.killbill.notificationq.api.NotificationQueueService.NoSuchNotificationQueue;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;

public class DefaultEntitlementInternalApi extends DefaultEntitlementApiBase implements EntitlementInternalApi {

    private final BlockingStateDao blockingStateDao;

    @Inject
    public DefaultEntitlementInternalApi(final PersistentBus eventBus,
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
    public void cancel(final Iterable<Entitlement> entitlements, final LocalDate effectiveDate, final BillingActionPolicy billingPolicy, final Iterable<PluginProperty> properties, final InternalCallContext internalCallContext) throws EntitlementApiException {
        final CallContext callContext = internalCallContextFactory.createCallContext(internalCallContext);

        final ImmutableMap.Builder<BlockingState, Optional<UUID>> states = new ImmutableMap.Builder<BlockingState, Optional<UUID>>();
        final Map<DateTime, Collection<NotificationEvent>> notificationEvents = new HashMap<DateTime, Collection<NotificationEvent>>();
        for (final Entitlement entitlement : entitlements) {
            final DefaultEntitlement defaultEntitlement = getDefaultEntitlement(entitlement, internalCallContext);
            final Collection<BlockingState> blockingStates = new ArrayList<BlockingState>();

            try {
                cancelEntitlementWithDateOverrideBillingPolicy(defaultEntitlement, effectiveDate, billingPolicy, blockingStates, notificationEvents, properties, callContext, internalCallContext);
            } catch (final EntitlementApiException e) {
                // If subscription has already been cancelled, we ignore and carry on
                if (e.getCode() != ErrorCode.SUB_CANCEL_BAD_STATE.getCode()) {
                    throw e;
                }
            }

            for (final BlockingState blockingState : blockingStates) {
                states.put(blockingState, Optional.<UUID>fromNullable(entitlement.getBundleId()));
            }
        }

        // Record the new states first, then insert the notifications to avoid race conditions
        blockingStateDao.setBlockingStatesAndPostBlockingTransitionEvent(states.build(), internalCallContext);
        for (final DateTime effectiveDateForNotification : notificationEvents.keySet()) {
            for (final NotificationEvent notificationEvent : notificationEvents.get(effectiveDateForNotification)) {
                recordFutureNotification(effectiveDateForNotification, notificationEvent, internalCallContext);
            }
        }
    }

    // Note that the implementation is similar to DefaultEntitlement#cancelEntitlementWithDateOverrideBillingPolicy but state isn't persisted on disk
    private void cancelEntitlementWithDateOverrideBillingPolicy(final DefaultEntitlement entitlement,
                                                                final LocalDate localCancelDate,
                                                                final BillingActionPolicy billingPolicy,
                                                                final Collection<BlockingState> blockingStates,
                                                                final Map<DateTime, Collection<NotificationEvent>> notificationEventsWithEffectiveDate,
                                                                final Iterable<PluginProperty> properties,
                                                                final CallContext callContext,
                                                                final InternalCallContext internalCallContext) throws EntitlementApiException {
        final EntitlementContext pluginContext = new DefaultEntitlementContext(OperationType.CANCEL_SUBSCRIPTION,
                                                                               entitlement.getAccountId(),
                                                                               null,
                                                                               entitlement.getBundleId(),
                                                                               entitlement.getExternalKey(),
                                                                               null,
                                                                               localCancelDate,
                                                                               properties,
                                                                               callContext);

        final WithEntitlementPlugin<Entitlement> cancelEntitlementWithPlugin = new WithEntitlementPlugin<Entitlement>() {
            @Override
            public Entitlement doCall(final EntitlementApi entitlementApi, final EntitlementContext updatedPluginContext) throws EntitlementApiException {
                if (entitlement.getState() == EntitlementState.CANCELLED) {
                    throw new EntitlementApiException(ErrorCode.SUB_CANCEL_BAD_STATE, entitlement.getId(), EntitlementState.CANCELLED);
                }

                // Make sure to compute the entitlement effective date first to avoid timing issues for IMM cancellations
                // (we don't want an entitlement cancel date one second or so after the subscription cancel date or add-ons cancellations
                // computations won't work).
                final LocalDate effectiveLocalDate = new LocalDate(updatedPluginContext.getEffectiveDate(), entitlement.getAccountTimeZone());
                final DateTime effectiveDate = dateHelper.fromLocalDateAndReferenceTime(effectiveLocalDate, entitlement.getSubscriptionBase().getStartDate(), internalCallContext);

                try {
                    // Cancel subscription base first, to correctly compute the add-ons entitlements we need to cancel (see below)
                    entitlement.getSubscriptionBase().cancelWithPolicy(billingPolicy, callContext);
                } catch (final SubscriptionBaseApiException e) {
                    throw new EntitlementApiException(e);
                }

                final BlockingState newBlockingState = new DefaultBlockingState(entitlement.getId(), BlockingStateType.SUBSCRIPTION, DefaultEntitlementApi.ENT_STATE_CANCELLED, EntitlementService.ENTITLEMENT_SERVICE_NAME, true, true, false, effectiveDate);
                final Collection<NotificationEvent> notificationEvents = new ArrayList<NotificationEvent>();
                final Collection<BlockingState> addOnsBlockingStates = entitlement.computeAddOnBlockingStates(effectiveDate, notificationEvents, callContext, internalCallContext);

                blockingStates.add(newBlockingState);
                blockingStates.addAll(addOnsBlockingStates);

                if (notificationEventsWithEffectiveDate.get(effectiveDate) == null) {
                    notificationEventsWithEffectiveDate.put(effectiveDate, notificationEvents);
                } else {
                    notificationEventsWithEffectiveDate.get(effectiveDate).addAll(notificationEvents);
                }

                // Unable to return the new state (not on disk yet)
                return null;
            }
        };
        pluginExecution.executeWithPlugin(cancelEntitlementWithPlugin, pluginContext);
    }

    private void recordFutureNotification(final DateTime effectiveDate,
                                          final NotificationEvent notificationEvent,
                                          final InternalCallContext context) {
        try {
            final NotificationQueue subscriptionEventQueue = notificationQueueService.getNotificationQueue(DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME,
                                                                                                           DefaultEntitlementService.NOTIFICATION_QUEUE_NAME);
            subscriptionEventQueue.recordFutureNotification(effectiveDate, notificationEvent, context.getUserToken(), context.getAccountRecordId(), context.getTenantRecordId());
        } catch (final NoSuchNotificationQueue e) {
            throw new RuntimeException(e);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    // For forward-compatibility
    private DefaultEntitlement getDefaultEntitlement(final Entitlement entitlement, final InternalTenantContext context) throws EntitlementApiException {
        if (entitlement instanceof DefaultEntitlement) {
            return (DefaultEntitlement) entitlement;
        } else {
            // Safe cast
            return (DefaultEntitlement) getEntitlementForId(entitlement.getId(), context);
        }
    }
}
