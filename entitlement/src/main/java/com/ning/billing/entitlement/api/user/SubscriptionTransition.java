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

package com.ning.billing.entitlement.api.user;

import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.catalog.api.IPlan;
import com.ning.billing.catalog.api.IPlanPhase;
import com.ning.billing.entitlement.api.user.ISubscription.SubscriptionState;
import com.ning.billing.entitlement.events.IEvent.EventType;
import com.ning.billing.entitlement.events.user.ApiEventType;

public class SubscriptionTransition implements ISubscriptionTransition {


    private final UUID subscriptionId;
    private final EventType eventType;
    private final ApiEventType apiEventType;
    private final DateTime transitionTime;
    private final SubscriptionState previousState;
    private final String previousPriceList;
    private final IPlan previousPlan;
    private final IPlanPhase previousPhase;
    private final SubscriptionState nextState;
    private final String nextPriceList;
    private final IPlan nextPlan;
    private final IPlanPhase nextPhase;

    public SubscriptionTransition(UUID subscriptionId, EventType eventType,
            ApiEventType apiEventType, DateTime transitionTime,
            SubscriptionState previousState, IPlan previousPlan, IPlanPhase previousPhase, String previousPriceList,
            SubscriptionState nextState, IPlan nextPlan, IPlanPhase nextPhase, String nextPriceList) {
        super();
        this.subscriptionId = subscriptionId;
        this.eventType = eventType;
        this.apiEventType = apiEventType;
        this.transitionTime = transitionTime;
        this.previousState = previousState;
        this.previousPriceList = previousPriceList;
        this.previousPlan = previousPlan;
        this.previousPhase = previousPhase;
        this.nextState = nextState;
        this.nextPlan = nextPlan;
        this.nextPriceList = nextPriceList;
        this.nextPhase = nextPhase;
    }

    @Override
    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    @Override
    public DateTime getTransitionTime() {
        return transitionTime;
    }

    @Override
    public SubscriptionState getPreviousState() {
        return previousState;
    }

    @Override
    public IPlan getPreviousPlan() {
        return previousPlan;
    }

    @Override
    public IPlanPhase getPreviousPhase() {
        return previousPhase;
    }

    @Override
    public IPlan getNextPlan() {
        return nextPlan;
    }

    @Override
    public IPlanPhase getNextPhase() {
        return nextPhase;
    }

    @Override
    public SubscriptionState getNextState() {
        return nextState;
    }


    @Override
    public String getPreviousPriceList() {
        return previousPriceList;
    }

    @Override
    public String getNextPriceList() {
        return nextPriceList;
    }

    public ApiEventType getApiEventType() {
        return apiEventType;
    }

    public EventType getEventType() {
        return eventType;
    }

    @Override
    public String toString() {
        return "SubscriptionTransition [subscriptionId=" + subscriptionId
                + ", eventType=" + eventType + ", apiEventType="
                + apiEventType + ", transitionTime=" + transitionTime
                + ", previousState=" + previousState + ", previousPlan="
                + ((previousPlan != null) ? previousPlan.getName()  : null)
                + ", previousPhase=" + ((previousPhase != null) ? previousPhase.getName() : null)
                + ", previousPriceList " + previousPriceList
                + ", nextState=" + nextState
                + ", nextPlan=" + ((nextPlan != null) ? nextPlan.getName() : null)
                + ", nextPriceList " + nextPriceList
                + ", nextPhase=" + ((nextPhase != null) ? nextPhase.getName() : null) + "]";
    }

}
