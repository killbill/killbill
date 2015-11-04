/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.account.api.ImmutableAccountData;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.entitlement.AccountEntitlements;
import org.killbill.billing.entitlement.AccountEventsStreams;
import org.killbill.billing.entitlement.DefaultEntitlementService;
import org.killbill.billing.entitlement.EntitlementService;
import org.killbill.billing.entitlement.EntitlementTransitionType;
import org.killbill.billing.entitlement.EventsStream;
import org.killbill.billing.entitlement.api.BlockingState;
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.killbill.billing.entitlement.api.DefaultEffectiveEntitlementEvent;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.entitlement.api.DefaultEntitlementApi;
import org.killbill.billing.entitlement.api.DefaultEntitlementContext;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.entitlement.api.EntitlementApi;
import org.killbill.billing.entitlement.api.EntitlementApiException;
import org.killbill.billing.entitlement.api.EntitlementDateHelper;
import org.killbill.billing.entitlement.api.EntitlementPluginExecution;
import org.killbill.billing.entitlement.api.EntitlementPluginExecution.WithEntitlementPlugin;
import org.killbill.billing.entitlement.block.BlockingChecker;
import org.killbill.billing.entitlement.dao.BlockingStateDao;
import org.killbill.billing.entitlement.engine.core.EntitlementNotificationKey;
import org.killbill.billing.entitlement.engine.core.EntitlementNotificationKeyAction;
import org.killbill.billing.entitlement.engine.core.EntitlementUtils;
import org.killbill.billing.entitlement.engine.core.EventsStreamBuilder;
import org.killbill.billing.entitlement.plugin.api.EntitlementContext;
import org.killbill.billing.entitlement.plugin.api.OperationType;
import org.killbill.billing.junction.DefaultBlockingState;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.security.api.SecurityApi;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseInternalApi;
import org.killbill.billing.subscription.api.user.SubscriptionBaseApiException;
import org.killbill.billing.subscription.api.user.SubscriptionBaseBundle;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.bus.api.PersistentBus;
import org.killbill.bus.api.PersistentBus.EventBusException;
import org.killbill.clock.Clock;
import org.killbill.notificationq.api.NotificationEvent;
import org.killbill.notificationq.api.NotificationQueue;
import org.killbill.notificationq.api.NotificationQueueService;
import org.killbill.notificationq.api.NotificationQueueService.NoSuchNotificationQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultEntitlementApiBase {

    private static final Logger log = LoggerFactory.getLogger(DefaultEntitlementApiBase.class);

    protected final EntitlementApi entitlementApi;
    protected final AccountInternalApi accountApi;

    protected final SubscriptionBaseInternalApi subscriptionInternalApi;
    protected final Clock clock;
    protected final InternalCallContextFactory internalCallContextFactory;
    protected final BlockingChecker checker;
    protected final BlockingStateDao blockingStateDao;
    protected final EntitlementDateHelper dateHelper;
    protected final EventsStreamBuilder eventsStreamBuilder;
    protected final EntitlementUtils entitlementUtils;
    protected final NotificationQueueService notificationQueueService;
    protected final EntitlementPluginExecution pluginExecution;
    protected final SecurityApi securityApi;
    protected final PersistentBus eventBus;

    protected DefaultEntitlementApiBase(final PersistentBus eventBus,
                                        @Nullable final EntitlementApi entitlementApi, final EntitlementPluginExecution pluginExecution,
                                        final InternalCallContextFactory internalCallContextFactory,
                                        final SubscriptionBaseInternalApi subscriptionInternalApi,
                                        final AccountInternalApi accountApi, final BlockingStateDao blockingStateDao, final Clock clock,
                                        final BlockingChecker checker, final NotificationQueueService notificationQueueService,
                                        final EventsStreamBuilder eventsStreamBuilder, final EntitlementUtils entitlementUtils, final SecurityApi securityApi) {
        this.eventBus = eventBus;
        this.entitlementApi = entitlementApi != null ? entitlementApi : (EntitlementApi) this;
        this.accountApi = accountApi;
        this.pluginExecution = pluginExecution;
        this.internalCallContextFactory = internalCallContextFactory;
        this.subscriptionInternalApi = subscriptionInternalApi;
        this.clock = clock;
        this.checker = checker;
        this.blockingStateDao = blockingStateDao;
        this.notificationQueueService = notificationQueueService;
        this.eventsStreamBuilder = eventsStreamBuilder;
        this.entitlementUtils = entitlementUtils;
        this.securityApi = securityApi;
        this.dateHelper = new EntitlementDateHelper(accountApi, clock);
    }

    public AccountEntitlements getAllEntitlementsForAccountId(final UUID accountId, final InternalTenantContext tenantContext) throws EntitlementApiException {

        final AccountEventsStreams accountEventsStreams = eventsStreamBuilder.buildForAccount(tenantContext);

        final Map<UUID, Collection<Entitlement>> entitlementsPerBundle = new HashMap<UUID, Collection<Entitlement>>();
        for (final UUID bundleId : accountEventsStreams.getEventsStreams().keySet()) {
            if (entitlementsPerBundle.get(bundleId) == null) {
                entitlementsPerBundle.put(bundleId, new LinkedList<Entitlement>());
            }

            for (final EventsStream eventsStream : accountEventsStreams.getEventsStreams().get(bundleId)) {
                final Entitlement entitlement = new DefaultEntitlement(eventsStream, eventsStreamBuilder, entitlementApi, pluginExecution,
                                                                       blockingStateDao, subscriptionInternalApi, checker, notificationQueueService,
                                                                       entitlementUtils, dateHelper, clock, securityApi, internalCallContextFactory);
                entitlementsPerBundle.get(bundleId).add(entitlement);
            }
        }

        return new DefaultAccountEntitlements(accountEventsStreams, entitlementsPerBundle);
    }

    public Entitlement getEntitlementForId(final UUID entitlementId, final InternalTenantContext tenantContext) throws EntitlementApiException {
        final EventsStream eventsStream = eventsStreamBuilder.buildForEntitlement(entitlementId, tenantContext);
        return new DefaultEntitlement(eventsStream, eventsStreamBuilder, entitlementApi, pluginExecution,
                                      blockingStateDao, subscriptionInternalApi, checker, notificationQueueService,
                                      entitlementUtils, dateHelper, clock, securityApi, internalCallContextFactory);
    }

    public void pause(final UUID bundleId, final LocalDate localEffectiveDate, final Iterable<PluginProperty> properties, final InternalCallContext internalCallContext) throws EntitlementApiException {

        final EntitlementContext pluginContext = new DefaultEntitlementContext(OperationType.PAUSE_SUBSCRIPTION,
                                                                               null,
                                                                               null,
                                                                               bundleId,
                                                                               null,
                                                                               null,
                                                                               null,
                                                                               localEffectiveDate,
                                                                               properties,
                                                                               internalCallContextFactory.createCallContext(internalCallContext));

        final WithEntitlementPlugin<Void> pauseWithPlugin = new WithEntitlementPlugin<Void>() {
            @Override
            public Void doCall(final EntitlementApi entitlementApi, final EntitlementContext updatedPluginContext) throws EntitlementApiException {
                try {

                    final BlockingState currentState = blockingStateDao.getBlockingStateForService(bundleId, BlockingStateType.SUBSCRIPTION_BUNDLE, EntitlementService.ENTITLEMENT_SERVICE_NAME, internalCallContext);
                    if (currentState != null && currentState.getStateName().equals(DefaultEntitlementApi.ENT_STATE_BLOCKED)) {
                        throw new EntitlementApiException(ErrorCode.ENT_ALREADY_BLOCKED, bundleId);
                    }

                    final SubscriptionBaseBundle bundle = subscriptionInternalApi.getBundleFromId(bundleId, internalCallContext);
                    final ImmutableAccountData account = accountApi.getImmutableAccountDataById(bundle.getAccountId(), internalCallContext);
                    final SubscriptionBase baseSubscription = subscriptionInternalApi.getBaseSubscription(bundleId, internalCallContext);
                    final DateTime effectiveDate = dateHelper.fromLocalDateAndReferenceTime(updatedPluginContext.getEffectiveDate(), baseSubscription.getStartDate(), internalCallContext);

                    if (!dateHelper.isBeforeOrEqualsToday(effectiveDate, account.getTimeZone())) {
                        recordPauseResumeNotificationEntry(baseSubscription.getId(), bundleId, effectiveDate, true, internalCallContext);
                        return null;
                    }

                    final UUID blockingId = blockUnblockBundle(bundleId, DefaultEntitlementApi.ENT_STATE_BLOCKED, EntitlementService.ENTITLEMENT_SERVICE_NAME, localEffectiveDate, true, true, true, baseSubscription, internalCallContext);

                    // Should we send one event per entitlement in the bundle?
                    // Code below only sends one event for the bundle and use the base entitlementId
                    final DefaultEffectiveEntitlementEvent event = new DefaultEffectiveEntitlementEvent(blockingId, baseSubscription.getId(), bundleId, bundle.getAccountId(), EntitlementTransitionType.BLOCK_BUNDLE,
                                                                                                        effectiveDate, clock.getUTCNow(),
                                                                                                        internalCallContext.getAccountRecordId(), internalCallContext.getTenantRecordId(),
                                                                                                        internalCallContext.getUserToken());

                    try {
                        eventBus.post(event);
                    } catch (EventBusException e) {
                        log.warn("Failed to post bus event for pause operation on bundle " + bundleId);
                    }

                } catch (SubscriptionBaseApiException e) {
                    throw new EntitlementApiException(e);
                } catch (AccountApiException e) {
                    throw new EntitlementApiException(e);
                }
                return null;
            }
        };
        pluginExecution.executeWithPlugin(pauseWithPlugin, pluginContext);
    }

    public void resume(final UUID bundleId, final LocalDate localEffectiveDate, final Iterable<PluginProperty> properties, final InternalCallContext internalCallContext) throws EntitlementApiException {

        final EntitlementContext pluginContext = new DefaultEntitlementContext(OperationType.RESUME_SUBSCRIPTION,
                                                                               null,
                                                                               null,
                                                                               bundleId,
                                                                               null,
                                                                               null,
                                                                               null,
                                                                               localEffectiveDate,
                                                                               properties,
                                                                               internalCallContextFactory.createCallContext(internalCallContext));
        final WithEntitlementPlugin<Void> resumeWithPlugin = new WithEntitlementPlugin<Void>() {
            @Override
            public Void doCall(final EntitlementApi entitlementApi, final EntitlementContext updatedPluginContext) throws EntitlementApiException {
                try {
                    final SubscriptionBaseBundle bundle = subscriptionInternalApi.getBundleFromId(bundleId, internalCallContext);
                    final ImmutableAccountData account = accountApi.getImmutableAccountDataById(bundle.getAccountId(), internalCallContext);
                    final SubscriptionBase baseSubscription = subscriptionInternalApi.getBaseSubscription(bundleId, internalCallContext);

                    final DateTime effectiveDate = dateHelper.fromLocalDateAndReferenceTime(updatedPluginContext.getEffectiveDate(), baseSubscription.getStartDate(), internalCallContext);

                    if (!dateHelper.isBeforeOrEqualsToday(effectiveDate, account.getTimeZone())) {
                        recordPauseResumeNotificationEntry(baseSubscription.getId(), bundleId, effectiveDate, false, internalCallContext);
                        return null;
                    }

                    final BlockingState currentState = blockingStateDao.getBlockingStateForService(bundleId, BlockingStateType.SUBSCRIPTION_BUNDLE, EntitlementService.ENTITLEMENT_SERVICE_NAME, internalCallContext);
                    if (currentState == null || currentState.getStateName().equals(DefaultEntitlementApi.ENT_STATE_CLEAR)) {
                        // Nothing to do.
                        log.warn("Current state is {}, nothing to resume", currentState);
                        return null;
                    }

                    final UUID blockingId = blockUnblockBundle(bundleId, DefaultEntitlementApi.ENT_STATE_CLEAR, EntitlementService.ENTITLEMENT_SERVICE_NAME, localEffectiveDate, false, false, false, baseSubscription, internalCallContext);

                    // Should we send one event per entitlement in the bundle?
                    // Code below only sends one event for the bundle and use the base entitlementId
                    final DefaultEffectiveEntitlementEvent event = new DefaultEffectiveEntitlementEvent(blockingId, baseSubscription.getId(), bundleId, bundle.getAccountId(), EntitlementTransitionType.UNBLOCK_BUNDLE,
                                                                                                        effectiveDate, clock.getUTCNow(),
                                                                                                        internalCallContext.getAccountRecordId(), internalCallContext.getTenantRecordId(),
                                                                                                        internalCallContext.getUserToken());

                    try {
                        eventBus.post(event);
                    } catch (EventBusException e) {
                        log.warn("Failed to post bus event for resume operation on bundle " + bundleId);
                    }

                } catch (SubscriptionBaseApiException e) {
                    throw new EntitlementApiException(e);
                } catch (AccountApiException e) {
                    throw new EntitlementApiException(e);
                }
                return null;
            }
        };
        pluginExecution.executeWithPlugin(resumeWithPlugin, pluginContext);
    }

    public void setBlockingState(final UUID bundleId, final String stateName, final String serviceName, final LocalDate localEffectiveDate, boolean blockBilling, boolean blockEntitlement, boolean blockChange, final Iterable<PluginProperty> properties, final InternalCallContext internalCallContext)
            throws EntitlementApiException {
        blockUnblockBundle(bundleId, stateName, serviceName, localEffectiveDate, blockBilling, blockEntitlement, blockChange, null, internalCallContext);
    }

    private UUID blockUnblockBundle(final UUID bundleId, final String stateName, final String serviceName, final LocalDate localEffectiveDate, boolean blockBilling, boolean blockEntitlement, boolean blockChange, @Nullable final SubscriptionBase inputBaseSubscription, final InternalCallContext internalCallContext)
            throws EntitlementApiException {
        try {
            final SubscriptionBase baseSubscription = inputBaseSubscription == null ? subscriptionInternalApi.getBaseSubscription(bundleId, internalCallContext) : inputBaseSubscription;
            final DateTime effectiveDate = dateHelper.fromLocalDateAndReferenceTime(localEffectiveDate, baseSubscription.getStartDate(), internalCallContext);
            final BlockingState state = new DefaultBlockingState(bundleId, BlockingStateType.SUBSCRIPTION_BUNDLE, stateName, serviceName, blockChange, blockEntitlement, blockBilling, effectiveDate);
            entitlementUtils.setBlockingStateAndPostBlockingTransitionEvent(state, internalCallContext);
            return state.getId();
        } catch (SubscriptionBaseApiException e) {
            throw new EntitlementApiException(e);
        }
    }

    protected void recordPauseResumeNotificationEntry(final UUID entitlementId, final UUID bundleId, final DateTime effectiveDate, final boolean isPause, final InternalCallContext contextWithValidAccountRecordId) throws EntitlementApiException {
        final NotificationEvent notificationEvent = new EntitlementNotificationKey(entitlementId,
                                                                                   bundleId,
                                                                                   isPause ? EntitlementNotificationKeyAction.PAUSE : EntitlementNotificationKeyAction.RESUME,
                                                                                   effectiveDate);

        try {
            final NotificationQueue subscriptionEventQueue = notificationQueueService.getNotificationQueue(DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME,
                                                                                                           DefaultEntitlementService.NOTIFICATION_QUEUE_NAME);
            subscriptionEventQueue.recordFutureNotification(effectiveDate, notificationEvent, contextWithValidAccountRecordId.getUserToken(), contextWithValidAccountRecordId.getAccountRecordId(), contextWithValidAccountRecordId.getTenantRecordId());
        } catch (final NoSuchNotificationQueue e) {
            throw new EntitlementApiException(e, ErrorCode.__UNKNOWN_ERROR_CODE);
        } catch (final IOException e) {
            throw new EntitlementApiException(e, ErrorCode.__UNKNOWN_ERROR_CODE);
        }
    }

}
