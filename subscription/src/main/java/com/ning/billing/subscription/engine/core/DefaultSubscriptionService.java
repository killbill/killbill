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

package com.ning.billing.subscription.engine.core;

import java.util.UUID;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.bus.api.PersistentBus;
import com.ning.billing.bus.api.PersistentBus.EventBusException;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.subscription.alignment.PlanAligner;
import com.ning.billing.subscription.alignment.TimedPhase;
import com.ning.billing.subscription.api.SubscriptionApiService;
import com.ning.billing.subscription.api.SubscriptionService;
import com.ning.billing.subscription.api.user.DefaultEffectiveSubscriptionEvent;
import com.ning.billing.subscription.api.user.SubscriptionData;
import com.ning.billing.subscription.api.user.SubscriptionTransitionData;
import com.ning.billing.subscription.engine.addon.AddonUtils;
import com.ning.billing.subscription.engine.dao.SubscriptionDao;
import com.ning.billing.subscription.events.SubscriptionEvent;
import com.ning.billing.subscription.events.SubscriptionEvent.EventType;
import com.ning.billing.subscription.events.phase.PhaseEvent;
import com.ning.billing.subscription.events.phase.PhaseEventData;
import com.ning.billing.subscription.events.user.ApiEvent;
import com.ning.billing.subscription.exceptions.SubscriptionError;
import com.ning.billing.lifecycle.LifecycleHandlerType;
import com.ning.billing.lifecycle.LifecycleHandlerType.LifecycleLevel;
import com.ning.billing.notificationq.api.NotificationEvent;
import com.ning.billing.notificationq.api.NotificationQueue;
import com.ning.billing.notificationq.api.NotificationQueueService;
import com.ning.billing.notificationq.api.NotificationQueueService.NoSuchNotificationQueue;
import com.ning.billing.notificationq.api.NotificationQueueService.NotificationQueueAlreadyExists;
import com.ning.billing.notificationq.api.NotificationQueueService.NotificationQueueHandler;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.callcontext.UserType;
import com.ning.billing.clock.Clock;
import com.ning.billing.util.events.EffectiveSubscriptionInternalEvent;

import com.google.inject.Inject;

public class DefaultSubscriptionService implements EventListener, SubscriptionService {

    public static final String NOTIFICATION_QUEUE_NAME = "subscription-events";
    public static final String ENTITLEMENT_SERVICE_NAME = "subscription-service";

    private static final Logger log = LoggerFactory.getLogger(DefaultSubscriptionService.class);

    private final Clock clock;
    private final SubscriptionDao dao;
    private final PlanAligner planAligner;
    private final AddonUtils addonUtils;
    private final PersistentBus eventBus;
    private final NotificationQueueService notificationQueueService;
    private final InternalCallContextFactory internalCallContextFactory;
    private NotificationQueue subscriptionEventQueue;
    private final SubscriptionApiService apiService;

    @Inject
    public DefaultSubscriptionService(final Clock clock, final SubscriptionDao dao, final PlanAligner planAligner,
                                      final AddonUtils addonUtils, final PersistentBus eventBus,
                                      final NotificationQueueService notificationQueueService,
                                      final InternalCallContextFactory internalCallContextFactory,
                                      final SubscriptionApiService apiService) {
        this.clock = clock;
        this.dao = dao;
        this.planAligner = planAligner;
        this.addonUtils = addonUtils;
        this.eventBus = eventBus;
        this.notificationQueueService = notificationQueueService;
        this.internalCallContextFactory = internalCallContextFactory;
        this.apiService = apiService;
    }

    @Override
    public String getName() {
        return ENTITLEMENT_SERVICE_NAME;
    }

    @LifecycleHandlerType(LifecycleLevel.INIT_SERVICE)
    public void initialize() {
        try {
            final NotificationQueueHandler queueHandler = new NotificationQueueHandler() {
                @Override
                public void handleReadyNotification(final NotificationEvent inputKey, final DateTime eventDateTime, final UUID fromNotificationQueueUserToken, final Long accountRecordId, final Long tenantRecordId) {
                    if (!(inputKey instanceof SubscriptionNotificationKey)) {
                        log.error("Entitlement service received an unexpected event type {}" + inputKey.getClass().getName());
                        return;
                    }

                    final SubscriptionNotificationKey key = (SubscriptionNotificationKey) inputKey;
                    final SubscriptionEvent event = dao.getEventById(key.getEventId(), internalCallContextFactory.createInternalTenantContext(tenantRecordId, accountRecordId));
                    if (event == null) {
                        // This can be expected if the event is soft deleted (is_active = 0)
                        log.info("Failed to extract event for notification key {}", inputKey);
                        return;
                    }

                    final InternalCallContext context = internalCallContextFactory.createInternalCallContext(tenantRecordId, accountRecordId, "SubscriptionEventQueue", CallOrigin.INTERNAL, UserType.SYSTEM, fromNotificationQueueUserToken);
                    processEventReady(event, key.getSeqId(), context);
                }
            };

            subscriptionEventQueue = notificationQueueService.createNotificationQueue(ENTITLEMENT_SERVICE_NAME,
                                                                                      NOTIFICATION_QUEUE_NAME,
                                                                                      queueHandler);
        } catch (NotificationQueueAlreadyExists e) {
            throw new RuntimeException(e);
        }
    }

    @LifecycleHandlerType(LifecycleLevel.START_SERVICE)
    public void start() {
        subscriptionEventQueue.startQueue();
    }

    @LifecycleHandlerType(LifecycleLevel.STOP_SERVICE)
    public void stop() throws NoSuchNotificationQueue {
        if (subscriptionEventQueue != null) {
            subscriptionEventQueue.stopQueue();
            notificationQueueService.deleteNotificationQueue(subscriptionEventQueue.getServiceName(), subscriptionEventQueue.getQueueName());
        }
    }

    @Override
    public void processEventReady(final SubscriptionEvent event, final int seqId, final InternalCallContext context) {
        if (!event.isActive()) {
            return;
        }

        final SubscriptionData subscription = (SubscriptionData) dao.getSubscriptionFromId(event.getSubscriptionId(), context);
        if (subscription == null) {
            log.warn("Failed to retrieve subscription for id %s", event.getSubscriptionId());
            return;
        }
        if (subscription.getActiveVersion() > event.getActiveVersion()) {
            // Skip repaired events
            return;
        }

        //
        // Do any internal processing on that event before we send the event to the bus
        //
        int theRealSeqId = seqId;
        if (event.getType() == EventType.PHASE) {
            onPhaseEvent(subscription, context);
        } else if (event.getType() == EventType.API_USER && subscription.getCategory() == ProductCategory.BASE) {
            theRealSeqId = onBasePlanEvent(subscription, (ApiEvent) event, context);
        }

        try {
            final SubscriptionTransitionData transition = (subscription.getTransitionFromEvent(event, theRealSeqId));
            final EffectiveSubscriptionInternalEvent busEvent = new DefaultEffectiveSubscriptionEvent(transition, subscription.getAlignStartDate(),
                                                                                                      context.getUserToken(),
                                                                                                      context.getAccountRecordId(), context.getTenantRecordId());
            eventBus.post(busEvent);
        } catch (EventBusException e) {
            log.warn("Failed to post subscription event " + event, e);
        }
    }

    private void onPhaseEvent(final SubscriptionData subscription, final InternalCallContext context) {
        try {
            final DateTime now = clock.getUTCNow();
            final TimedPhase nextTimedPhase = planAligner.getNextTimedPhase(subscription, now, now);
            final PhaseEvent nextPhaseEvent = (nextTimedPhase != null) ?
                                              PhaseEventData.createNextPhaseEvent(nextTimedPhase.getPhase().getName(), subscription, now, nextTimedPhase.getStartPhase()) :
                                              null;
            if (nextPhaseEvent != null) {
                dao.createNextPhaseEvent(subscription, nextPhaseEvent, context);
            }
        } catch (SubscriptionError e) {
            log.error(String.format("Failed to insert next phase for subscription %s", subscription.getId()), e);
        }
    }

    private int onBasePlanEvent(final SubscriptionData baseSubscription, final ApiEvent event, final InternalCallContext context) {
        return apiService.cancelAddOnsIfRequired(baseSubscription, event.getEffectiveDate(), context);
    }


}
