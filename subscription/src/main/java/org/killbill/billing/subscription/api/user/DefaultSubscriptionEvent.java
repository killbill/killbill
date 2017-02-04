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

package org.killbill.billing.subscription.api.user;

import java.util.UUID;

import org.joda.time.DateTime;

import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;
import org.killbill.billing.events.BusEventBase;
import org.killbill.billing.events.SubscriptionInternalEvent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public abstract class DefaultSubscriptionEvent extends BusEventBase implements SubscriptionInternalEvent {

    private final Long totalOrdering;
    private final UUID subscriptionId;
    private final UUID bundleId;
    private final String bundleExternalKey;
    private final UUID eventId;
    private final DateTime requestedTransitionTime;
    private final DateTime effectiveTransitionTime;
    private final EntitlementState previousState;
    private final String previousPriceList;
    private final String previousPlan;
    private final String previousPhase;
    private final Integer previousBillCycleDayLocal;
    private final EntitlementState nextState;
    private final String nextPriceList;
    private final String nextPlan;
    private final String nextPhase;
    private final Integer nextBillCycleDayLocal;
    private final Integer remainingEventsForUserOperation;
    private final SubscriptionBaseTransitionType transitionType;
    private final DateTime startDate;


    public DefaultSubscriptionEvent(final SubscriptionBaseTransitionData in, final DateTime startDate,
                                    final Long searchKey1,
                                    final Long searchKey2,
                                    final UUID userToken) {
        this(in.getId(),
             in.getSubscriptionId(),
             in.getBundleId(),
             in.getBundleExternalKey(),
             in.getEffectiveTransitionTime(),
             in.getEffectiveTransitionTime(),
             in.getPreviousState(),
             (in.getPreviousPlan() != null) ? in.getPreviousPlan().getName() : null,
             (in.getPreviousPhase() != null) ? in.getPreviousPhase().getName() : null,
             (in.getPreviousPriceList() != null) ? in.getPreviousPriceList().getName() : null,
             in.getPreviousBillingCycleDayLocal(),
             in.getNextState(),
             (in.getNextPlan() != null) ? in.getNextPlan().getName() : null,
             (in.getNextPhase() != null) ? in.getNextPhase().getName() : null,
             (in.getNextPriceList() != null) ? in.getNextPriceList().getName() : null,
             in.getNextBillingCycleDayLocal(),
             in.getTotalOrdering(),
             in.getTransitionType(),
             in.getRemainingEventsForUserOperation(),
             startDate,
             searchKey1,
             searchKey2,
             userToken);
    }

    @JsonCreator
    public DefaultSubscriptionEvent(@JsonProperty("eventId") final UUID eventId,
                                    @JsonProperty("subscriptionId") final UUID subscriptionId,
                                    @JsonProperty("bundleId") final UUID bundleId,
                                    @JsonProperty("bundleExternalKey") final String bundleExternalKey,
                                    @JsonProperty("requestedTransitionTime") final DateTime requestedTransitionTime,
                                    @JsonProperty("effectiveTransitionTime") final DateTime effectiveTransitionTime,
                                    @JsonProperty("previousState") final EntitlementState previousState,
                                    @JsonProperty("previousPlan") final String previousPlan,
                                    @JsonProperty("previousPhase") final String previousPhase,
                                    @JsonProperty("previousPriceList") final String previousPriceList,
                                    @JsonProperty("previousBillCycleDayLocal") final Integer previousBillCycleDayLocal,
                                    @JsonProperty("nextState") final EntitlementState nextState,
                                    @JsonProperty("nextPlan") final String nextPlan,
                                    @JsonProperty("nextPhase") final String nextPhase,
                                    @JsonProperty("nextPriceList") final String nextPriceList,
                                    @JsonProperty("nextBillCycleDayLocal") final Integer nextBillCycleDayLocal,
                                    @JsonProperty("totalOrdering") final Long totalOrdering,
                                    @JsonProperty("transitionType") final SubscriptionBaseTransitionType transitionType,
                                    @JsonProperty("remainingEventsForUserOperation") final Integer remainingEventsForUserOperation,
                                    @JsonProperty("startDate") final DateTime startDate,
                                    @JsonProperty("searchKey1") final Long searchKey1,
                                    @JsonProperty("searchKey2") final Long searchKey2,
                                    @JsonProperty("userToken") final UUID userToken) {
        super(searchKey1, searchKey2, userToken);
        this.eventId = eventId;
        this.subscriptionId = subscriptionId;
        this.bundleId = bundleId;
        this.bundleExternalKey = bundleExternalKey;
        this.requestedTransitionTime = requestedTransitionTime;
        this.effectiveTransitionTime = effectiveTransitionTime;
        this.previousState = previousState;
        this.previousPriceList = previousPriceList;
        this.previousBillCycleDayLocal = previousBillCycleDayLocal;
        this.previousPlan = previousPlan;
        this.previousPhase = previousPhase;
        this.nextState = nextState;
        this.nextPlan = nextPlan;
        this.nextPriceList = nextPriceList;
        this.nextBillCycleDayLocal = nextBillCycleDayLocal;
        this.nextPhase = nextPhase;
        this.totalOrdering = totalOrdering;
        this.transitionType = transitionType;
        this.remainingEventsForUserOperation = remainingEventsForUserOperation;
        this.startDate = startDate;
    }

    @JsonIgnore
    @Override
    public BusInternalEventType getBusEventType() {
        return BusInternalEventType.SUBSCRIPTION_TRANSITION;
    }

    @JsonProperty("eventId")
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
    public String getBundleExternalKey() {
        return bundleExternalKey;
    }

    @Override
    public EntitlementState getPreviousState() {
        return previousState;
    }

    @Override
    public String getPreviousPlan() {
        return previousPlan;
    }

    @Override
    public String getPreviousPhase() {
        return previousPhase;
    }

    @Override
    public Integer getPreviousBillCycleDayLocal() {
        return previousBillCycleDayLocal;
    }

    @Override
    public String getNextPlan() {
        return nextPlan;
    }

    @Override
    public String getNextPhase() {
        return nextPhase;
    }

    @Override
    public EntitlementState getNextState() {
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

    @Override
    public Integer getNextBillCycleDayLocal() {
        return nextBillCycleDayLocal;
    }

    @Override
    public Integer getRemainingEventsForUserOperation() {
        return remainingEventsForUserOperation;
    }

    @Override
    public DateTime getRequestedTransitionTime() {
        return effectiveTransitionTime;
    }

    @Override
    public DateTime getEffectiveTransitionTime() {
        return effectiveTransitionTime;
    }

    @Override
    public Long getTotalOrdering() {
        return totalOrdering;
    }

    @Override
    public SubscriptionBaseTransitionType getTransitionType() {
        return transitionType;
    }

    @JsonProperty("startDate")
    @Override
    public DateTime getSubscriptionStartDate() {
        return startDate;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append("{bundleId=").append(bundleId);
        sb.append(", totalOrdering=").append(totalOrdering);
        sb.append(", subscriptionId=").append(subscriptionId);
        sb.append(", eventId=").append(eventId);
        sb.append(", requestedTransitionTime=").append(requestedTransitionTime);
        sb.append(", effectiveTransitionTime=").append(effectiveTransitionTime);
        sb.append(", previousState=").append(previousState);
        sb.append(", previousPriceList='").append(previousPriceList).append('\'');
        sb.append(", previousBillCycleDayLocal='").append(previousBillCycleDayLocal).append('\'');
        sb.append(", previousPlan='").append(previousPlan).append('\'');
        sb.append(", previousPhase='").append(previousPhase).append('\'');
        sb.append(", nextState=").append(nextState);
        sb.append(", nextPriceList='").append(nextPriceList).append('\'');
        sb.append(", nextBillCycleDayLocal='").append(nextBillCycleDayLocal).append('\'');
        sb.append(", nextPlan='").append(nextPlan).append('\'');
        sb.append(", nextPhase='").append(nextPhase).append('\'');
        sb.append(", remainingEventsForUserOperation=").append(remainingEventsForUserOperation);
        sb.append(", transitionType=").append(transitionType);
        sb.append(", startDate=").append(startDate);
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

        final DefaultSubscriptionEvent that = (DefaultSubscriptionEvent) o;

        if (bundleId != null ? !bundleId.equals(that.bundleId) : that.bundleId != null) {
            return false;
        }
        if (effectiveTransitionTime != null ? effectiveTransitionTime.compareTo(that.effectiveTransitionTime) != 0 : that.effectiveTransitionTime != null) {
            return false;
        }
        if (eventId != null ? !eventId.equals(that.eventId) : that.eventId != null) {
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
        if (nextBillCycleDayLocal != null ? !nextBillCycleDayLocal.equals(that.nextBillCycleDayLocal) : that.nextBillCycleDayLocal != null) {
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
        if (previousBillCycleDayLocal != null ? !previousBillCycleDayLocal.equals(that.previousBillCycleDayLocal) : that.previousBillCycleDayLocal != null) {
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
        if (startDate != null ? startDate.compareTo(that.startDate) != 0 : that.startDate != null) {
            return false;
        }
        if (subscriptionId != null ? !subscriptionId.equals(that.subscriptionId) : that.subscriptionId != null) {
            return false;
        }
        if (totalOrdering != null ? !totalOrdering.equals(that.totalOrdering) : that.totalOrdering != null) {
            return false;
        }
        if (transitionType != that.transitionType) {
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
        result = 31 * result + (requestedTransitionTime != null ? requestedTransitionTime.hashCode() : 0);
        result = 31 * result + (effectiveTransitionTime != null ? effectiveTransitionTime.hashCode() : 0);
        result = 31 * result + (previousState != null ? previousState.hashCode() : 0);
        result = 31 * result + (previousPriceList != null ? previousPriceList.hashCode() : 0);
        result = 31 * result + (previousBillCycleDayLocal != null ? previousBillCycleDayLocal.hashCode() : 0);
        result = 31 * result + (previousPlan != null ? previousPlan.hashCode() : 0);
        result = 31 * result + (previousPhase != null ? previousPhase.hashCode() : 0);
        result = 31 * result + (nextState != null ? nextState.hashCode() : 0);
        result = 31 * result + (nextPriceList != null ? nextPriceList.hashCode() : 0);
        result = 31 * result + (nextBillCycleDayLocal != null ? nextBillCycleDayLocal.hashCode() : 0);
        result = 31 * result + (nextPlan != null ? nextPlan.hashCode() : 0);
        result = 31 * result + (nextPhase != null ? nextPhase.hashCode() : 0);
        result = 31 * result + (remainingEventsForUserOperation != null ? remainingEventsForUserOperation.hashCode() : 0);
        result = 31 * result + (transitionType != null ? transitionType.hashCode() : 0);
        result = 31 * result + (startDate != null ? startDate.hashCode() : 0);
        return result;
    }
}
