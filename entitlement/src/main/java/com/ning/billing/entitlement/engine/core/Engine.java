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



import java.util.Iterator;
import java.util.List;
import java.util.UUID;


import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;

import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.ProductCategory;
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
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.Subscription.SubscriptionState;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.engine.addon.AddonUtils;
import com.ning.billing.entitlement.engine.dao.EntitlementDao;
import com.ning.billing.entitlement.events.EntitlementEvent;
import com.ning.billing.entitlement.events.EntitlementEvent.EventType;
import com.ning.billing.entitlement.events.phase.PhaseEvent;
import com.ning.billing.entitlement.events.phase.PhaseEventData;
import com.ning.billing.entitlement.events.user.ApiEvent;
import com.ning.billing.entitlement.events.user.ApiEventBuilder;
import com.ning.billing.entitlement.events.user.ApiEventCancel;
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
    private final AddonUtils addonUtils;
    private final Bus eventBus;

    private final EntitlementConfig config;
    private final NotificationQueueService notificationQueueService;

    private NotificationQueue subscriptionEventQueue;

    @Inject
    public Engine(Clock clock, EntitlementDao dao, PlanAligner planAligner,
            EntitlementConfig config, DefaultEntitlementUserApi userApi,
            DefaultEntitlementBillingApi billingApi,
            DefaultEntitlementMigrationApi migrationApi, AddonUtils addonUtils, Bus eventBus,
            NotificationQueueService notificationQueueService) {
        super();
        this.clock = clock;
        this.dao = dao;
        this.planAligner = planAligner;
        this.userApi = userApi;
        this.billingApi = billingApi;
        this.migrationApi = migrationApi;
        this.addonUtils = addonUtils;
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
            subscriptionEventQueue = notificationQueueService.createNotificationQueue(ENTITLEMENT_SERVICE_NAME,
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
        subscriptionEventQueue.startQueue();
    }

    @LifecycleHandlerType(LifecycleLevel.STOP_SERVICE)
    public void stop() {
        if (subscriptionEventQueue != null) {
            subscriptionEventQueue.stopQueue();
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
        //
        // Do any internal processing on that event before we send the event to the bus
        //
        if (event.getType() == EventType.PHASE) {
            onPhaseEvent(subscription);
        } else if (event.getType() == EventType.API_USER &&
                subscription.getCategory() == ProductCategory.BASE) {
            onBasePlanEvent(subscription, (ApiEvent) event);
        }
        try {
            eventBus.post(subscription.getTransitionFromEvent(event));
        } catch (EventBusException e) {
            log.warn("Failed to post entitlement event " + event, e);
        }
    }


    private void onPhaseEvent(SubscriptionData subscription) {
        try {
            DateTime now = clock.getUTCNow();
            TimedPhase nextTimedPhase = planAligner.getNextTimedPhase(subscription, now, now);
            PhaseEvent nextPhaseEvent = (nextTimedPhase != null) ?
                    PhaseEventData.createNextPhaseEvent(nextTimedPhase.getPhase().getName(), subscription, now, nextTimedPhase.getStartPhase()) :
                        null;
            if (nextPhaseEvent != null) {
                dao.createNextPhaseEvent(subscription.getId(), nextPhaseEvent);
            }
        } catch (EntitlementError e) {
            log.error(String.format("Failed to insert next phase for subscription %s", subscription.getId()), e);
        }
    }

    private void onBasePlanEvent(SubscriptionData baseSubscription, ApiEvent event) {

        DateTime now = clock.getUTCNow();

        List<Subscription> subscriptions = dao.getSubscriptions(baseSubscription.getBundleId());
        Iterator<Subscription> it = subscriptions.iterator();
        while (it.hasNext()) {
            SubscriptionData cur = (SubscriptionData) it.next();
            if (cur.getState() == SubscriptionState.CANCELLED ||
                    cur.getCategory() != ProductCategory.ADD_ON) {
                continue;
            }
            Plan addonCurrentPlan = cur.getCurrentPlan();
            // If base Plan has been canceled, that will cancel all the OA
            if (addonUtils.isAddonIncluded(baseSubscription, addonCurrentPlan) ||
                    ! addonUtils.isAddonAvailable(baseSubscription, addonCurrentPlan)) {
                //
                // Perform AO cancellation using the effectiveDate of the BP
                //
                EntitlementEvent cancelEvent = new ApiEventCancel(new ApiEventBuilder()
                .setSubscriptionId(cur.getId())
                .setActiveVersion(cur.getActiveVersion())
                .setProcessedDate(now)
                .setEffectiveDate(event.getEffectiveDate())
                .setRequestedDate(now));
                dao.cancelSubscription(cur.getId(), cancelEvent);
            }
        }
    }
}
