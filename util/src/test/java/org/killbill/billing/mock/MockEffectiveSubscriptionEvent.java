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

package org.killbill.billing.mock;

import java.util.UUID;

import org.joda.time.DateTime;

import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;
import org.killbill.billing.events.BusEventBase;
import org.killbill.billing.events.EffectiveSubscriptionInternalEvent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class MockEffectiveSubscriptionEvent extends BusEventBase implements EffectiveSubscriptionInternalEvent {

    private final Long totalOrdering;
    private final UUID subscriptionId;
    private final UUID bundleId;
    private final String bundleExternalKey;
    private final UUID eventId;
    private final DateTime requestedTransitionTime;
    private final DateTime effectiveTransitionTime;
    private final EntitlementState previousState;
    private final String previousPriceList;
    private final Integer previousBillCycleDayLocal;
    private final String previousPlan;
    private final String previousPhase;
    private final EntitlementState nextState;
    private final String nextPriceList;
    private final Integer nextBillCycleDayLocal;
    private final String nextPlan;
    private final String nextPhase;
    private final Integer remainingEventsForUserOperation;
    private final UUID userToken;
    private final SubscriptionBaseTransitionType transitionType;

    private final DateTime startDate;

    @JsonCreator
    public MockEffectiveSubscriptionEvent(@JsonProperty("eventId") final UUID eventId,
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
        this.userToken = userToken;
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
        return requestedTransitionTime;
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
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                 + ((bundleId == null) ? 0 : bundleId.hashCode());
        result = prime
                 * result
                 + ((effectiveTransitionTime == null) ? 0
                                                      : effectiveTransitionTime.hashCode());
        result = prime * result + ((eventId == null) ? 0 : eventId.hashCode());
        result = prime * result
                 + ((nextPhase == null) ? 0 : nextPhase.hashCode());
        result = prime * result
                 + ((nextPlan == null) ? 0 : nextPlan.hashCode());
        result = prime * result
                 + ((nextPriceList == null) ? 0 : nextPriceList.hashCode());
        result = prime * result
                 + ((nextState == null) ? 0 : nextState.hashCode());
        result = prime * result
                 + ((previousPhase == null) ? 0 : previousPhase.hashCode());
        result = prime * result
                 + ((previousPlan == null) ? 0 : previousPlan.hashCode());
        result = prime
                 * result
                 + ((previousPriceList == null) ? 0 : previousPriceList
                .hashCode());
        result = prime * result
                 + ((previousState == null) ? 0 : previousState.hashCode());
        result = prime
                 * result
                 + ((remainingEventsForUserOperation == null) ? 0
                                                              : remainingEventsForUserOperation.hashCode());
        result = prime
                 * result
                 + ((requestedTransitionTime == null) ? 0
                                                      : requestedTransitionTime.hashCode());
        result = prime * result
                 + ((subscriptionId == null) ? 0 : subscriptionId.hashCode());
        result = prime * result
                 + ((totalOrdering == null) ? 0 : totalOrdering.hashCode());
        result = prime * result
                 + ((transitionType == null) ? 0 : transitionType.hashCode());
        result = prime * result
                 + ((userToken == null) ? 0 : userToken.hashCode());
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final MockEffectiveSubscriptionEvent other = (MockEffectiveSubscriptionEvent) obj;
        if (bundleId == null) {
            if (other.bundleId != null) {
                return false;
            }
        } else if (!bundleId.equals(other.bundleId)) {
            return false;
        }
        if (effectiveTransitionTime == null) {
            if (other.effectiveTransitionTime != null) {
                return false;
            }
        } else if (effectiveTransitionTime
                           .compareTo(other.effectiveTransitionTime) != 0) {
            return false;
        }
        if (eventId == null) {
            if (other.eventId != null) {
                return false;
            }
        } else if (!eventId.equals(other.eventId)) {
            return false;
        }
        if (nextPhase == null) {
            if (other.nextPhase != null) {
                return false;
            }
        } else if (!nextPhase.equals(other.nextPhase)) {
            return false;
        }
        if (nextPlan == null) {
            if (other.nextPlan != null) {
                return false;
            }
        } else if (!nextPlan.equals(other.nextPlan)) {
            return false;
        }
        if (nextPriceList == null) {
            if (other.nextPriceList != null) {
                return false;
            }
        } else if (!nextPriceList.equals(other.nextPriceList)) {
            return false;
        }
        if (nextState != other.nextState) {
            return false;
        }
        if (previousPhase == null) {
            if (other.previousPhase != null) {
                return false;
            }
        } else if (!previousPhase.equals(other.previousPhase)) {
            return false;
        }
        if (previousPlan == null) {
            if (other.previousPlan != null) {
                return false;
            }
        } else if (!previousPlan.equals(other.previousPlan)) {
            return false;
        }
        if (previousPriceList == null) {
            if (other.previousPriceList != null) {
                return false;
            }
        } else if (!previousPriceList.equals(other.previousPriceList)) {
            return false;
        }
        if (previousState != other.previousState) {
            return false;
        }
        if (remainingEventsForUserOperation == null) {
            if (other.remainingEventsForUserOperation != null) {
                return false;
            }
        } else if (!remainingEventsForUserOperation
                .equals(other.remainingEventsForUserOperation)) {
            return false;
        }
        if (requestedTransitionTime == null) {
            if (other.requestedTransitionTime != null) {
                return false;
            }
        } else if (requestedTransitionTime
                           .compareTo(other.requestedTransitionTime) != 0) {
            return false;
        }
        if (subscriptionId == null) {
            if (other.subscriptionId != null) {
                return false;
            }
        } else if (!subscriptionId.equals(other.subscriptionId)) {
            return false;
        }
        if (totalOrdering == null) {
            if (other.totalOrdering != null) {
                return false;
            }
        } else if (!totalOrdering.equals(other.totalOrdering)) {
            return false;
        }
        if (transitionType != other.transitionType) {
            return false;
        }
        if (userToken == null) {
            if (other.userToken != null) {
                return false;
            }
        } else if (!userToken.equals(other.userToken)) {
            return false;
        }
        return true;
    }
}
