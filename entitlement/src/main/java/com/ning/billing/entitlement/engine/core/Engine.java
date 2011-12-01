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
import com.ning.billing.entitlement.api.test.DefaultEntitlementTestApi;
import com.ning.billing.entitlement.api.test.EntitlementTestApi;
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
import com.ning.billing.util.eventbus.EventBus;
import com.ning.billing.util.eventbus.EventBus.EventBusException;

public class Engine implements EventListener, EntitlementService {

    private static final String ENTITLEMENT_SERVICE_NAME = "entitlement-service";

    private final long MAX_NOTIFICATION_THREAD_WAIT_MS = 10000; // 10 secs
    private final long NOTIFICATION_THREAD_WAIT_INCREMENT_MS = 1000; // 1 sec
    private final long NANO_TO_MS = (1000 * 1000);

    private final static Logger log = LoggerFactory.getLogger(Engine.class);

    private final Clock clock;
    private final EntitlementDao dao;
    private final EventNotifier apiEventProcessor;
    private final PlanAligner planAligner;
    private final EntitlementUserApi userApi;
    private final EntitlementBillingApi billingApi;
    private final EntitlementTestApi testApi;
    private final EventBus eventBus;

    private boolean startedNotificationThread;

    @Inject
    public Engine(Clock clock, EntitlementDao dao, EventNotifier apiEventProcessor,
            PlanAligner planAligner, EntitlementConfig config, DefaultEntitlementUserApi userApi,
            DefaultEntitlementBillingApi billingApi, DefaultEntitlementTestApi testApi, EventBus eventBus) {
        super();
        this.clock = clock;
        this.dao = dao;
        this.apiEventProcessor = apiEventProcessor;
        this.planAligner = planAligner;
        this.userApi = userApi;
        this.testApi = testApi;
        this.billingApi = billingApi;
        this.eventBus = eventBus;

        this.startedNotificationThread = false;
    }

    @Override
    public String getName() {
        return ENTITLEMENT_SERVICE_NAME;
    }


    @LifecycleHandlerType(LifecycleLevel.INIT_SERVICE)
    public void initialize() {
    }

    @LifecycleHandlerType(LifecycleLevel.START_SERVICE)
    public void start() {
        apiEventProcessor.startNotifications(this);
        waitForNotificationStartCompletion();
    }

    @LifecycleHandlerType(LifecycleLevel.STOP_SERVICE)
    public void stop() {
        apiEventProcessor.stopNotifications();
        startedNotificationThread = false;
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
    public EntitlementTestApi getTestApi() {
        return testApi;
    }

    @Override
    public void processEventReady(EntitlementEvent event) {
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

    //
    // We want to ensure the notification thread is indeed started when we return from start()
    //
    @Override
    public void completedNotificationStart() {
        synchronized (this) {
            startedNotificationThread = true;
            this.notifyAll();
        }
    }

    private void waitForNotificationStartCompletion() {

        long ini = System.nanoTime();
        synchronized(this) {
            do {
                if (startedNotificationThread) {
                    break;
                }
                try {
                    this.wait(NOTIFICATION_THREAD_WAIT_INCREMENT_MS);
                } catch (InterruptedException e ) {
                    Thread.currentThread().interrupt();
                    throw new EntitlementError(e);
                }
            } while (!startedNotificationThread &&
                    (System.nanoTime() - ini) / NANO_TO_MS < MAX_NOTIFICATION_THREAD_WAIT_MS);

            if (!startedNotificationThread) {
                log.error("Could not start notification thread in {} msec !!!", MAX_NOTIFICATION_THREAD_WAIT_MS);
                throw new EntitlementError("Failed to start service!!");
            }
            log.info("Notification thread has been started in {} ms", (System.nanoTime() - ini) / NANO_TO_MS);
        }
    }

    private void insertNextPhaseEvent(SubscriptionData subscription) {

        DateTime now = clock.getUTCNow();

        TimedPhase nextTimedPhase = planAligner.getNextTimedPhase(subscription, subscription.getCurrentPlan(), now, subscription.getCurrentPlanStart());
        PhaseEvent nextPhaseEvent = PhaseEventData.getNextPhaseEvent(nextTimedPhase, subscription, now);
        if (nextPhaseEvent != null) {
            dao.createNextPhaseEvent(subscription.getId(), nextPhaseEvent);
        }
    }

}
