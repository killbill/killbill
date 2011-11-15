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
import com.ning.billing.config.IEntitlementConfig;

import com.ning.billing.entitlement.alignment.IPlanAligner;
import com.ning.billing.entitlement.alignment.IPlanAligner.TimedPhase;
import com.ning.billing.entitlement.api.IEntitlementService;
import com.ning.billing.entitlement.api.billing.EntitlementBillingApi;
import com.ning.billing.entitlement.api.billing.IEntitlementBillingApi;
import com.ning.billing.entitlement.api.test.EntitlementTestApi;
import com.ning.billing.entitlement.api.test.IEntitlementTestApi;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.entitlement.api.user.IEntitlementUserApi;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.engine.dao.IEntitlementDao;
import com.ning.billing.entitlement.events.IEvent;
import com.ning.billing.entitlement.events.IEvent.EventType;
import com.ning.billing.entitlement.events.phase.IPhaseEvent;
import com.ning.billing.entitlement.events.phase.PhaseEvent;
import com.ning.billing.entitlement.events.user.IApiEvent;
import com.ning.billing.lifecycle.IService;
import com.ning.billing.lifecycle.LyfecycleHandlerType;
import com.ning.billing.lifecycle.LyfecycleHandlerType.LyfecycleLevel;
import com.ning.billing.util.clock.IClock;
import com.ning.billing.util.eventbus.IEventBus;
import com.ning.billing.util.eventbus.IEventBus.EventBusException;

public class Engine implements IEventListener, IEntitlementService {

    private static final String ENTITLEMENT_SERVICE_NAME = "entitlement-service";

    private final static Logger log = LoggerFactory.getLogger(Engine.class);

    private final IClock clock;
    private final IEntitlementDao dao;
    private final IApiEventProcessor apiEventProcessor;
    private final IPlanAligner planAligner;
    private final IEntitlementUserApi userApi;
    private final IEntitlementBillingApi billingApi;
    private final IEntitlementTestApi testApi;
    private final IEventBus eventBus;

    @Inject
    public Engine(IClock clock, IEntitlementDao dao, IApiEventProcessor apiEventProcessor,
            IPlanAligner planAligner, IEntitlementConfig config, EntitlementUserApi userApi,
            EntitlementBillingApi billingApi, EntitlementTestApi testApi, IEventBus eventBus) {
        super();
        this.clock = clock;
        this.dao = dao;
        this.apiEventProcessor = apiEventProcessor;
        this.planAligner = planAligner;
        this.userApi = userApi;
        this.testApi = testApi;
        this.billingApi = billingApi;
        this.eventBus = eventBus;
    }

    @Override
    public String getName() {
        return ENTITLEMENT_SERVICE_NAME;
    }


    @LyfecycleHandlerType(LyfecycleLevel.INIT_SERVICE)
    public void initialize() {
    }

    @LyfecycleHandlerType(LyfecycleLevel.START_SERVICE)
    public void start() {
        apiEventProcessor.startNotifications(this);
    }

    @LyfecycleHandlerType(LyfecycleLevel.STOP_SERVICE)
    public void stop() {
        apiEventProcessor.stopNotifications();
    }

    @Override
    public IEntitlementUserApi getUserApi() {
        return userApi;
    }

    @Override
    public IEntitlementBillingApi getBillingApi() {
        return billingApi;
    }


    @Override
    public IEntitlementTestApi getTestApi() {
        return testApi;
    }

    @Override
    public void processEventReady(IEvent event) {
        Subscription subscription = (Subscription) dao.getSubscriptionFromId(event.getSubscriptionId());
        if (subscription == null) {
            log.warn("Failed to retrieve subscription for id %s", event.getSubscriptionId());
            return;
        }
        if (event.getType() == EventType.PHASE) {
            insertNextPhaseEvent(subscription);
        }
        try {
            eventBus.post(subscription.getLatestTranstion());
        } catch (EventBusException e) {
            log.warn("Failed to post entitlement event " + event, e);
        }

    }

    private void insertNextPhaseEvent(Subscription subscription) {

        DateTime now = clock.getUTCNow();

        TimedPhase nextTimedPhase = planAligner.getNextTimedPhase(subscription, subscription.getCurrentPlan(), now, subscription.getCurrentPlanStart());
        IPhaseEvent nextPhaseEvent = PhaseEvent.getNextPhaseEvent(nextTimedPhase, subscription, now);
        if (nextPhaseEvent != null) {
            dao.createNextPhaseEvent(subscription.getId(), nextPhaseEvent);
        }
    }

}
