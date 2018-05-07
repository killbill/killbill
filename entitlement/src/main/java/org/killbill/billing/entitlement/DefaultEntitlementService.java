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

package org.killbill.billing.entitlement;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.entitlement.api.BlockingState;
import org.killbill.billing.entitlement.api.DefaultBlockingTransitionInternalEvent;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.entitlement.api.EntitlementApiException;
import org.killbill.billing.entitlement.dao.BlockingStateDao;
import org.killbill.billing.entitlement.engine.core.BlockingTransitionNotificationKey;
import org.killbill.billing.entitlement.engine.core.EntitlementNotificationKey;
import org.killbill.billing.entitlement.engine.core.EntitlementNotificationKeyAction;
import org.killbill.billing.entitlement.engine.core.EntitlementUtils;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.platform.api.LifecycleHandlerType;
import org.killbill.billing.platform.api.LifecycleHandlerType.LifecycleLevel;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.CallOrigin;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.callcontext.UserType;
import org.killbill.bus.api.BusEvent;
import org.killbill.bus.api.PersistentBus;
import org.killbill.bus.api.PersistentBus.EventBusException;
import org.killbill.notificationq.api.NotificationEvent;
import org.killbill.notificationq.api.NotificationQueue;
import org.killbill.notificationq.api.NotificationQueueService;
import org.killbill.notificationq.api.NotificationQueueService.NoSuchNotificationQueue;
import org.killbill.notificationq.api.NotificationQueueService.NotificationQueueAlreadyExists;
import org.killbill.notificationq.api.NotificationQueueService.NotificationQueueHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

public class DefaultEntitlementService implements EntitlementService {

    public static final String NOTIFICATION_QUEUE_NAME = "entitlement-events";

    private static final Logger log = LoggerFactory.getLogger(DefaultEntitlementService.class);

    private final EntitlementInternalApi entitlementInternalApi;
    private final BlockingStateDao blockingStateDao;
    private final PersistentBus eventBus;
    private final NotificationQueueService notificationQueueService;
    private final EntitlementUtils entitlementUtils;
    private final InternalCallContextFactory internalCallContextFactory;

    private NotificationQueue entitlementEventQueue;

    @Inject
    public DefaultEntitlementService(final EntitlementInternalApi entitlementInternalApi,
                                     final BlockingStateDao blockingStateDao,
                                     final PersistentBus eventBus,
                                     final NotificationQueueService notificationQueueService,
                                     final EntitlementUtils entitlementUtils,
                                     final InternalCallContextFactory internalCallContextFactory) {
        this.entitlementInternalApi = entitlementInternalApi;
        this.blockingStateDao = blockingStateDao;
        this.eventBus = eventBus;
        this.notificationQueueService = notificationQueueService;
        this.entitlementUtils = entitlementUtils;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Override
    public String getName() {
        return EntitlementService.ENTITLEMENT_SERVICE_NAME;
    }

    @LifecycleHandlerType(LifecycleLevel.INIT_SERVICE)
    public void initialize() {
        try {
            final NotificationQueueHandler queueHandler = new NotificationQueueHandler() {
                @Override
                public void handleReadyNotification(final NotificationEvent inputKey, final DateTime eventDateTime, final UUID fromNotificationQueueUserToken, final Long accountRecordId, final Long tenantRecordId) {
                    final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(tenantRecordId, accountRecordId, "EntitlementQueue", CallOrigin.INTERNAL, UserType.SYSTEM, fromNotificationQueueUserToken);

                    if (inputKey instanceof EntitlementNotificationKey) {
                        final CallContext callContext = internalCallContextFactory.createCallContext(internalCallContext);
                        processEntitlementNotification((EntitlementNotificationKey) inputKey, internalCallContext, callContext);
                    } else if (inputKey instanceof BlockingTransitionNotificationKey) {
                        processBlockingNotification((BlockingTransitionNotificationKey) inputKey, internalCallContext);
                    } else if (inputKey != null) {
                        log.error("Entitlement service received an unexpected event className='{}", inputKey.getClass());
                    } else {
                        log.error("Entitlement service received an unexpected null event");
                    }
                }
            };

            entitlementEventQueue = notificationQueueService.createNotificationQueue(ENTITLEMENT_SERVICE_NAME,
                                                                                     NOTIFICATION_QUEUE_NAME,
                                                                                     queueHandler);
        } catch (final NotificationQueueAlreadyExists e) {
            throw new RuntimeException(e);
        }
    }

    private void processEntitlementNotification(final EntitlementNotificationKey key, final InternalCallContext internalCallContext, final CallContext callContext) {
        final Entitlement entitlement;
        try {
            entitlement = entitlementInternalApi.getEntitlementForId(key.getEntitlementId(), internalCallContext);
        } catch (final EntitlementApiException e) {
            log.error("Error retrieving entitlementId='{}'", key.getEntitlementId(), e);
            return;
        }

        if (!(entitlement instanceof DefaultEntitlement)) {
            log.error("Error retrieving entitlementId='{}', unexpected entitlement className='{}'", key.getEntitlementId(), entitlement.getClass().getName());
            return;
        }

        final EntitlementNotificationKeyAction entitlementNotificationKeyAction = key.getEntitlementNotificationKeyAction();
        try {
            if (EntitlementNotificationKeyAction.CHANGE.equals(entitlementNotificationKeyAction) ||
                EntitlementNotificationKeyAction.CANCEL.equals(entitlementNotificationKeyAction)) {
                blockAddOnsIfRequired(key, (DefaultEntitlement) entitlement, callContext, internalCallContext);
            } else if (EntitlementNotificationKeyAction.PAUSE.equals(entitlementNotificationKeyAction)) {
                entitlementInternalApi.pause(key.getBundleId(), internalCallContext.toLocalDate(key.getEffectiveDate()), ImmutableList.<PluginProperty>of(), internalCallContext);
            } else if (EntitlementNotificationKeyAction.RESUME.equals(entitlementNotificationKeyAction)) {
                entitlementInternalApi.resume(key.getBundleId(), internalCallContext.toLocalDate(key.getEffectiveDate()), ImmutableList.<PluginProperty>of(), internalCallContext);
            }
        } catch (final EntitlementApiException e) {
            log.error("Error processing event for entitlementId='{}'", entitlement.getId(), e);
        }
    }

    private void blockAddOnsIfRequired(final EntitlementNotificationKey key, final DefaultEntitlement entitlement, final TenantContext callContext, final InternalCallContext internalCallContext) throws EntitlementApiException {
        final Collection<NotificationEvent> notificationEvents = new ArrayList<NotificationEvent>();
        final Collection<BlockingState> blockingStates = entitlement.computeAddOnBlockingStates(key.getEffectiveDate(), notificationEvents, callContext, internalCallContext);
        // Record the new state first, then insert the notifications to avoid race conditions
        entitlementUtils.setBlockingStatesAndPostBlockingTransitionEvent(blockingStates, entitlement.getBundleId(), internalCallContext);
        for (final NotificationEvent notificationEvent : notificationEvents) {
            recordFutureNotification(key.getEffectiveDate(), notificationEvent, internalCallContext);
        }
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

    private void processBlockingNotification(final BlockingTransitionNotificationKey key, final InternalCallContext internalCallContext){
        // Check if the blocking state has been deleted since
        try {
            if (blockingStateDao.getById(key.getBlockingStateId(), internalCallContext) == null) {
                log.debug("BlockingState {} has been deleted, not sending a bus event", key.getBlockingStateId());
                return;
            }
        } catch (final EntitlementApiException e) {
            throw new IllegalStateException(String.format("Unexpected exception when fetching blockingState='%s'", key.getBlockingStateId()), e);
        }

        final BusEvent event = new DefaultBlockingTransitionInternalEvent(key.getBlockableId(),
                                                                          key.getStateName(),
                                                                          key.getService(),
                                                                          key.getEffectiveDate(),
                                                                          key.getBlockingType(),
                                                                          key.isTransitionedToBlockedBilling(),
                                                                          key.isTransitionedToUnblockedBilling(),
                                                                          key.isTransitionedToBlockedEntitlement(),
                                                                          key.isTransitionToUnblockedEntitlement(),
                                                                          internalCallContext.getAccountRecordId(),
                                                                          internalCallContext.getTenantRecordId(),
                                                                          internalCallContext.getUserToken());

        try {
            eventBus.post(event);
        } catch (final EventBusException e) {
            log.warn("Failed to post event {}", event, e);
        }
    }

    @LifecycleHandlerType(LifecycleLevel.START_SERVICE)
    public void start() {
        entitlementEventQueue.startQueue();
    }

    @LifecycleHandlerType(LifecycleLevel.STOP_SERVICE)
    public void stop() throws NoSuchNotificationQueue {
        if (entitlementEventQueue != null) {
            entitlementEventQueue.stopQueue();
            notificationQueueService.deleteNotificationQueue(entitlementEventQueue.getServiceName(), entitlementEventQueue.getQueueName());
        }
    }
}
