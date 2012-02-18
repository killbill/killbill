/*
 * Copyright 2010-2011 Ning, Inc.
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

import java.util.UUID;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.ning.billing.config.EntitlementConfig;
import com.ning.billing.entitlement.alignment.PlanAligner;
import com.ning.billing.entitlement.alignment.TimedPhase;
import com.ning.billing.entitlement.api.EntitlementService;
import com.ning.billing.entitlement.api.billing.DefaultEntitlementBillingApi;
import com.ning.billing.entitlement.api.billing.EntitlementBillingApi;
import com.ning.billing.entitlement.api.migration.DefaultEntitlementMigrationApi;
import com.ning.billing.entitlement.api.migration.EntitlementMigrationApi;
import com.ning.billing.entitlement.api.user.DefaultEntitlementUserApi;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.engine.dao.EntitlementDao;
import com.ning.billing.entitlement.events.EntitlementEvent;
import com.ning.billing.entitlement.events.EntitlementEvent.EventType;
import com.ning.billing.entitlement.events.phase.PhaseEvent;
import com.ning.billing.entitlement.events.phase.PhaseEventData;
import com.ning.billing.entitlement.exceptions.EntitlementError;
import com.ning.billing.lifecycle.LifecycleHandlerType;
import com.ning.billing.lifecycle.LifecycleHandlerType.LifecycleLevel;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.bus.Bus;
import com.ning.billing.util.bus.Bus.EventBusException;
import com.ning.billing.util.notificationq.NotificationConfig;
import com.ning.billing.util.notificationq.NotificationQueue;
import com.ning.billing.util.notificationq.NotificationQueueService;
import com.ning.billing.util.notificationq.NotificationQueueService.NotificationQueueAlreadyExists;
import com.ning.billing.util.notificationq.NotificationQueueService.NotificationQueueHandler;

public class Engine implements EventListener, EntitlementService {

    public static final String NOTIFICATION_QUEUE_NAME = "subscription-events";
    public static final String ENTITLEMENT_SERVICE_NAME = "entitlement-service";

    private final static Logger log = LoggerFactory.getLogger(Engine.class);

    private final Clock clock;
    private final EntitlementDao dao;
    private final PlanAligner planAligner;
    private final EntitlementUserApi userApi;
    private final EntitlementBillingApi billingApi;
    private final EntitlementMigrationApi migrationApi;
    private final Bus eventBus;
    private final EntitlementConfig config;
    private final NotificationQueueService notificationQueueService;

    private NotificationQueue subscritionEventQueue;

    @Inject
    public Engine(Clock clock, EntitlementDao dao, PlanAligner planAligner,
            EntitlementConfig config, DefaultEntitlementUserApi userApi,
            DefaultEntitlementBillingApi billingApi,
            DefaultEntitlementMigrationApi migrationApi, Bus eventBus,
            NotificationQueueService notificationQueueService) {
        super();
        this.clock = clock;
        this.dao = dao;
        this.planAligner = planAligner;
        this.userApi = userApi;
        this.billingApi = billingApi;
        this.migrationApi = migrationApi;
        this.config = config;
        this.eventBus = eventBus;
        this.notificationQueueService = notificationQueueService;
    }

    @Override
    public String getName() {
        return ENTITLEMENT_SERVICE_NAME;
    }

    @LifecycleHandlerType(LifecycleLevel.INIT_SERVICE)
    public void initialize() {

        try {
            subscritionEventQueue = notificationQueueService.createNotificationQueue(ENTITLEMENT_SERVICE_NAME,
                    NOTIFICATION_QUEUE_NAME,
                    new NotificationQueueHandler() {
                @Override
                public void handleReadyNotification(String notificationKey, DateTime eventDateTime) {
                    EntitlementEvent event = dao.getEventById(UUID.fromString(notificationKey));
                    if (event == null) {
                        log.warn("Failed to extract event for notification key {}", notificationKey);
                    } else {
                        processEventReady(event);
                    }
                }
            },
            new NotificationConfig() {
                @Override
                public boolean isNotificationProcessingOff() {
                    return config.isEventProcessingOff();
                }
                @Override
                public long getNotificationSleepTimeMs() {
                    return config.getNotificationSleepTimeMs();
                }
                @Override
                public int getDaoMaxReadyEvents() {
                    return config.getDaoMaxReadyEvents();
                }
                @Override
                public long getDaoClaimTimeMs() {
                    return config.getDaoClaimTimeMs();
                }
            });
        } catch (NotificationQueueAlreadyExists e) {
            throw new RuntimeException(e);
        }
    }

    @LifecycleHandlerType(LifecycleLevel.START_SERVICE)
    public void start() {
        subscritionEventQueue.startQueue();
    }

    @LifecycleHandlerType(LifecycleLevel.STOP_SERVICE)
    public void stop() {
        if (subscritionEventQueue != null) {
            subscritionEventQueue.stopQueue();
         }
    }

    @Override
    public EntitlementUserApi getUserApi() {
        return userApi;
    }

    @Override
    public EntitlementBillingApi getBillingApi() {
        return billingApi;
    }


    @Override
    public EntitlementMigrationApi getMigrationApi() {
        return migrationApi;
    }


    @Override
    public void processEventReady(EntitlementEvent event) {
        if (!event.isActive()) {
            return;
        }
        SubscriptionData subscription = (SubscriptionData) dao.getSubscriptionFromId(event.getSubscriptionId());
        if (subscription == null) {
            log.warn("Failed to retrieve subscription for id %s", event.getSubscriptionId());
            return;
        }
        if (event.getType() == EventType.PHASE) {
            insertNextPhaseEvent(subscription);
        }
        try {
            eventBus.post(subscription.getTransitionFromEvent(event));
        } catch (EventBusException e) {
            log.warn("Failed to post entitlement event " + event, e);
        }
    }

    private void insertNextPhaseEvent(SubscriptionData subscription) {
        try {
            DateTime now = clock.getUTCNow();
            TimedPhase nextTimedPhase = planAligner.getNextTimedPhase(subscription.getCurrentPlan(), subscription.getInitialPhaseOnCurrentPlan().getPhaseType(), now, subscription.getCurrentPlanStart());
            PhaseEvent nextPhaseEvent = (nextTimedPhase != null) ?
                    PhaseEventData.getNextPhaseEvent(nextTimedPhase.getPhase().getName(), subscription, now, nextTimedPhase.getStartPhase()) :
                        null;
            if (nextPhaseEvent != null) {
                dao.createNextPhaseEvent(subscription.getId(), nextPhaseEvent);
            }
        } catch (EntitlementError e) {
            log.error(String.format("Failed to insert next phase for subscription %s", subscription.getId()), e);
        }
    }

}
