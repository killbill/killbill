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

import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.PriceList;
import com.ning.billing.entitlement.api.user.Subscription.SubscriptionState;
import com.ning.billing.entitlement.events.EntitlementEvent.EventType;
import com.ning.billing.entitlement.events.user.ApiEventType;
import com.ning.billing.entitlement.exceptions.EntitlementError;

public class SubscriptionTransitionData implements SubscriptionTransition {


    private final long totalOrdering;
    private final UUID subscriptionId;
    private final UUID bundleId;
    private final UUID eventId;
    private final EventType eventType;
    private final ApiEventType apiEventType;
    private final DateTime requestedTransitionTime;
    private final DateTime effectiveTransitionTime;
    private final SubscriptionState previousState;
    private final PriceList previousPriceList;
    private final Plan previousPlan;
    private final PlanPhase previousPhase;
    private final SubscriptionState nextState;
    private final PriceList nextPriceList;
    private final Plan nextPlan;
    private final PlanPhase nextPhase;
    private final boolean isFromDisk;

    public SubscriptionTransitionData(UUID eventId, UUID subscriptionId, UUID bundleId, EventType eventType,
            ApiEventType apiEventType, DateTime requestedTransitionTime, DateTime effectiveTransitionTime,
            SubscriptionState previousState, Plan previousPlan, PlanPhase previousPhase, PriceList previousPriceList,
            SubscriptionState nextState, Plan nextPlan, PlanPhase nextPhase, PriceList nextPriceList,
            long totalOrdering, boolean isFromDisk) {
        super();
        this.eventId = eventId;
        this.subscriptionId = subscriptionId;
        this.bundleId = bundleId;
        this.eventType = eventType;
        this.apiEventType = apiEventType;
        this.requestedTransitionTime = requestedTransitionTime;
        this.effectiveTransitionTime = effectiveTransitionTime;
        this.previousState = previousState;
        this.previousPriceList = previousPriceList;
        this.previousPlan = previousPlan;
        this.previousPhase = previousPhase;
        this.nextState = nextState;
        this.nextPlan = nextPlan;
        this.nextPriceList = nextPriceList;
        this.nextPhase = nextPhase;
        this.totalOrdering = totalOrdering;
        this.isFromDisk = isFromDisk;
    }

    @Override
    public UUID getId() {
        return eventId;
    }

    @Override
    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    @Override
    public UUID getBundleId() {
        return bundleId;
    }


    @Override
    public SubscriptionState getPreviousState() {
        return previousState;
    }

    @Override
    public Plan getPreviousPlan() {
        return previousPlan;
    }

    @Override
    public PlanPhase getPreviousPhase() {
        return previousPhase;
    }

    @Override
    public Plan getNextPlan() {
        return nextPlan;
    }

    @Override
    public PlanPhase getNextPhase() {
        return nextPhase;
    }

    @Override
    public SubscriptionState getNextState() {
        return nextState;
    }


    @Override
    public PriceList getPreviousPriceList() {
        return previousPriceList;
    }

    @Override
    public PriceList getNextPriceList() {
        return nextPriceList;
    }

    @Override
    public SubscriptionTransitionType getTransitionType() {
        switch(eventType) {
        case API_USER:
            return apiEventType.getSubscriptionTransitionType();
        case PHASE:
            return SubscriptionTransitionType.PHASE;
        default:
            throw new EntitlementError("Unexpected event type " + eventType);
        }
    }

    @Override
    public DateTime getRequestedTransitionTime() {
        return requestedTransitionTime;
    }

    @Override
    public DateTime getEffectiveTransitionTime() {
        return effectiveTransitionTime;
    }

    public long getTotalOrdering() {
        return totalOrdering;
    }

    public boolean isFromDisk() {
        return isFromDisk;
    }

    public ApiEventType getApiEventType() {
        return apiEventType;
    }

    public EventType getEventType() {
        return eventType;
    }



    @Override
    public String toString() {
        return "SubscriptionTransition [eventId=" + eventId
            + ", subscriptionId=" + subscriptionId
            + ", eventType=" + eventType + ", apiEventType="
            + apiEventType + ", requestedTransitionTime=" + requestedTransitionTime
            + ", effectiveTransitionTime=" + effectiveTransitionTime
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
