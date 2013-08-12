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

package com.ning.billing.subscription.api.user;

import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.PriceList;
import com.ning.billing.entitlement.api.Entitlement.EntitlementState;
import com.ning.billing.subscription.api.SubscriptionBaseTransitionType;
import com.ning.billing.subscription.events.SubscriptionBaseEvent.EventType;
import com.ning.billing.subscription.events.user.ApiEventType;
import com.ning.billing.subscription.exceptions.SubscriptionBaseError;

public class SubscriptionBaseTransitionData implements SubscriptionBaseTransition {
    private final Long totalOrdering;
    private final UUID subscriptionId;
    private final UUID bundleId;
    private final UUID eventId;
    private final EventType eventType;
    private final ApiEventType apiEventType;
    private final DateTime requestedTransitionTime;
    private final DateTime effectiveTransitionTime;
    private final EntitlementState previousState;
    private final PriceList previousPriceList;
    private final UUID previousEventId;
    private final DateTime previousEventCreatedDate;
    private final Plan previousPlan;
    private final PlanPhase previousPhase;
    private final UUID nextEventId;
    private final DateTime nextEventCreatedDate;
    private final EntitlementState nextState;
    private final PriceList nextPriceList;
    private final Plan nextPlan;
    private final PlanPhase nextPhase;
    private final Boolean isFromDisk;
    private final Integer remainingEventsForUserOperation;
    private final UUID userToken;

    public SubscriptionBaseTransitionData(final UUID eventId,
                                          final UUID subscriptionId,
                                          final UUID bundleId,
                                          final EventType eventType,
                                          final ApiEventType apiEventType,
                                          final DateTime requestedTransitionTime,
                                          final DateTime effectiveTransitionTime,
                                          final UUID previousEventId,
                                          final DateTime previousEventCreatedDate,
                                          final EntitlementState previousState,
                                          final Plan previousPlan,
                                          final PlanPhase previousPhase,
                                          final PriceList previousPriceList,
                                          final UUID nextEventId,
                                          final DateTime nextEventCreatedDate,
                                          final EntitlementState nextState,
                                          final Plan nextPlan,
                                          final PlanPhase nextPhase,
                                          final PriceList nextPriceList,
                                          final Long totalOrdering,
                                          final UUID userToken,
                                          final Boolean isFromDisk) {
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
        this.previousEventId = previousEventId;
        this.previousEventCreatedDate = previousEventCreatedDate;
        this.nextEventId = nextEventId;
        this.nextEventCreatedDate = nextEventCreatedDate;
        this.isFromDisk = isFromDisk;
        this.userToken = userToken;
        this.remainingEventsForUserOperation = 0;
    }

    public SubscriptionBaseTransitionData(final SubscriptionBaseTransitionData input, int remainingEventsForUserOperation) {
       this(input, input.getEventType(), input.getApiEventType(), remainingEventsForUserOperation);
    }

    public SubscriptionBaseTransitionData(final SubscriptionBaseTransitionData input, final EventType eventType,
                                          final ApiEventType apiEventType, int remainingEventsForUserOperation) {
        super();
        this.eventId = input.getId();
        this.subscriptionId = input.getSubscriptionId();
        this.bundleId = input.getBundleId();
        this.eventType = eventType;
        this.apiEventType = apiEventType;
        this.requestedTransitionTime = input.getRequestedTransitionTime();
        this.effectiveTransitionTime = input.getEffectiveTransitionTime();
        this.previousEventId = input.getPreviousEventId();
        this.previousEventCreatedDate = input.getPreviousEventCreatedDate();
        this.previousState = input.getPreviousState();
        this.previousPriceList = input.getPreviousPriceList();
        this.previousPlan = input.getPreviousPlan();
        this.previousPhase = input.getPreviousPhase();
        this.nextEventId = input.getNextEventId();
        this.nextEventCreatedDate = input.getNextEventCreatedDate();
        this.nextState = input.getNextState();
        this.nextPlan = input.getNextPlan();
        this.nextPriceList = input.getNextPriceList();
        this.nextPhase = input.getNextPhase();
        this.totalOrdering = input.getTotalOrdering();
        this.isFromDisk = input.isFromDisk();
        this.userToken = input.getUserToken();
        this.remainingEventsForUserOperation = remainingEventsForUserOperation;
    }

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
    public EntitlementState getPreviousState() {
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
    public UUID getNextEventId() {
        return nextEventId;
    }

    @Override
    public DateTime getNextEventCreatedDate() {
        return nextEventCreatedDate;
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
    public EntitlementState getNextState() {
        return nextState;
    }

    @Override
    public UUID getPreviousEventId() {
        return previousEventId;
    }

    @Override
    public DateTime getPreviousEventCreatedDate() {
        return previousEventCreatedDate;
    }

    @Override
    public PriceList getPreviousPriceList() {
        return previousPriceList;
    }

    @Override
    public PriceList getNextPriceList() {
        return nextPriceList;
    }

    public UUID getUserToken() {
        return userToken;
    }

    public Integer getRemainingEventsForUserOperation() {
        return remainingEventsForUserOperation;
    }

    @Override
    public SubscriptionBaseTransitionType getTransitionType() {
        return toSubscriptionTransitionType(eventType, apiEventType);
    }

    public static SubscriptionBaseTransitionType toSubscriptionTransitionType(final EventType eventType, final ApiEventType apiEventType) {
        switch (eventType) {
            case API_USER:
                return apiEventType.getSubscriptionTransitionType();
            case PHASE:
                return SubscriptionBaseTransitionType.PHASE;
            default:
                throw new SubscriptionBaseError("Unexpected event type " + eventType);
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

    public Long getTotalOrdering() {
        return totalOrdering;
    }

    public Boolean isFromDisk() {
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
        final StringBuilder sb = new StringBuilder();
        sb.append("SubscriptionBaseTransitionData");
        sb.append("{apiEventType=").append(apiEventType);
        sb.append(", totalOrdering=").append(totalOrdering);
        sb.append(", subscriptionId=").append(subscriptionId);
        sb.append(", bundleId=").append(bundleId);
        sb.append(", eventId=").append(eventId);
        sb.append(", eventType=").append(eventType);
        sb.append(", requestedTransitionTime=").append(requestedTransitionTime);
        sb.append(", effectiveTransitionTime=").append(effectiveTransitionTime);
        sb.append(", previousState=").append(previousState);
        sb.append(", previousPriceList=").append(previousPriceList);
        sb.append(", previousPlan=").append(previousPlan);
        sb.append(", previousPhase=").append(previousPhase);
        sb.append(", nextState=").append(nextState);
        sb.append(", nextPriceList=").append(nextPriceList);
        sb.append(", nextPlan=").append(nextPlan);
        sb.append(", nextPhase=").append(nextPhase);
        sb.append(", isFromDisk=").append(isFromDisk);
        sb.append(", remainingEventsForUserOperation=").append(remainingEventsForUserOperation);
        sb.append(", userToken=").append(userToken);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final SubscriptionBaseTransitionData that = (SubscriptionBaseTransitionData) o;

        if (apiEventType != that.apiEventType) {
            return false;
        }
        if (bundleId != null ? !bundleId.equals(that.bundleId) : that.bundleId != null) {
            return false;
        }
        if (effectiveTransitionTime != null ? effectiveTransitionTime.compareTo(that.effectiveTransitionTime) != 0 : that.effectiveTransitionTime != null) {
            return false;
        }
        if (eventId != null ? !eventId.equals(that.eventId) : that.eventId != null) {
            return false;
        }
        if (eventType != that.eventType) {
            return false;
        }
        if (isFromDisk != null ? !isFromDisk.equals(that.isFromDisk) : that.isFromDisk != null) {
            return false;
        }
        if (nextPhase != null ? !nextPhase.equals(that.nextPhase) : that.nextPhase != null) {
            return false;
        }
        if (nextPlan != null ? !nextPlan.equals(that.nextPlan) : that.nextPlan != null) {
            return false;
        }
        if (nextPriceList != null ? !nextPriceList.equals(that.nextPriceList) : that.nextPriceList != null) {
            return false;
        }
        if (nextState != that.nextState) {
            return false;
        }
        if (previousPhase != null ? !previousPhase.equals(that.previousPhase) : that.previousPhase != null) {
            return false;
        }
        if (previousPlan != null ? !previousPlan.equals(that.previousPlan) : that.previousPlan != null) {
            return false;
        }
        if (previousPriceList != null ? !previousPriceList.equals(that.previousPriceList) : that.previousPriceList != null) {
            return false;
        }
        if (previousState != that.previousState) {
            return false;
        }
        if (remainingEventsForUserOperation != null ? !remainingEventsForUserOperation.equals(that.remainingEventsForUserOperation) : that.remainingEventsForUserOperation != null) {
            return false;
        }
        if (requestedTransitionTime != null ? requestedTransitionTime.compareTo(that.requestedTransitionTime) != 0 : that.requestedTransitionTime != null) {
            return false;
        }
        if (subscriptionId != null ? !subscriptionId.equals(that.subscriptionId) : that.subscriptionId != null) {
            return false;
        }
        if (totalOrdering != null ? !totalOrdering.equals(that.totalOrdering) : that.totalOrdering != null) {
            return false;
        }
        if (userToken != null ? !userToken.equals(that.userToken) : that.userToken != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = totalOrdering != null ? totalOrdering.hashCode() : 0;
        result = 31 * result + (subscriptionId != null ? subscriptionId.hashCode() : 0);
        result = 31 * result + (bundleId != null ? bundleId.hashCode() : 0);
        result = 31 * result + (eventId != null ? eventId.hashCode() : 0);
        result = 31 * result + (eventType != null ? eventType.hashCode() : 0);
        result = 31 * result + (apiEventType != null ? apiEventType.hashCode() : 0);
        result = 31 * result + (requestedTransitionTime != null ? requestedTransitionTime.hashCode() : 0);
        result = 31 * result + (effectiveTransitionTime != null ? effectiveTransitionTime.hashCode() : 0);
        result = 31 * result + (previousState != null ? previousState.hashCode() : 0);
        result = 31 * result + (previousPriceList != null ? previousPriceList.hashCode() : 0);
        result = 31 * result + (previousPlan != null ? previousPlan.hashCode() : 0);
        result = 31 * result + (previousPhase != null ? previousPhase.hashCode() : 0);
        result = 31 * result + (nextState != null ? nextState.hashCode() : 0);
        result = 31 * result + (nextPriceList != null ? nextPriceList.hashCode() : 0);
        result = 31 * result + (nextPlan != null ? nextPlan.hashCode() : 0);
        result = 31 * result + (nextPhase != null ? nextPhase.hashCode() : 0);
        result = 31 * result + (isFromDisk != null ? isFromDisk.hashCode() : 0);
        result = 31 * result + (remainingEventsForUserOperation != null ? remainingEventsForUserOperation.hashCode() : 0);
        result = 31 * result + (userToken != null ? userToken.hashCode() : 0);
        return result;
    }
}
