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

package com.ning.billing.entitlement.api.user;

import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.entitlement.api.SubscriptionTransitionType;
import com.ning.billing.entitlement.events.EntitlementEvent;
import com.ning.billing.util.events.RequestedSubscriptionInternalEvent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class DefaultRequestedSubscriptionEvent extends DefaultSubscriptionEvent implements RequestedSubscriptionInternalEvent {

    public DefaultRequestedSubscriptionEvent(final SubscriptionTransitionData in, final DateTime startDate) {
        super(in, startDate);
    }

    @JsonCreator
    public DefaultRequestedSubscriptionEvent(@JsonProperty("eventId") final UUID eventId,
                                             @JsonProperty("subscriptionId") final UUID subscriptionId,
                                             @JsonProperty("bundleId") final UUID bundleId,
                                             @JsonProperty("requestedTransitionTime") final DateTime requestedTransitionTime,
                                             @JsonProperty("effectiveTransitionTime") final DateTime effectiveTransitionTime,
                                             @JsonProperty("previousState") final SubscriptionState previousState,
                                             @JsonProperty("previousPlan") final String previousPlan,
                                             @JsonProperty("previousPhase") final String previousPhase,
                                             @JsonProperty("previousPriceList") final String previousPriceList,
                                             @JsonProperty("nextState") final SubscriptionState nextState,
                                             @JsonProperty("nextPlan") final String nextPlan,
                                             @JsonProperty("nextPhase") final String nextPhase,
                                             @JsonProperty("nextPriceList") final String nextPriceList,
                                             @JsonProperty("totalOrdering") final Long totalOrdering,
                                             @JsonProperty("transitionType") final SubscriptionTransitionType transitionType,
                                             @JsonProperty("remainingEventsForUserOperation") final Integer remainingEventsForUserOperation,
                                             @JsonProperty("startDate") final DateTime startDate) {
        super(eventId, subscriptionId, bundleId, requestedTransitionTime, effectiveTransitionTime, previousState, previousPlan,
              previousPhase, previousPriceList, nextState, nextPlan, nextPhase, nextPriceList, totalOrdering,
              transitionType, remainingEventsForUserOperation, startDate);
    }

    public DefaultRequestedSubscriptionEvent(final SubscriptionData subscription, final EntitlementEvent nextEvent) {
        this(nextEvent.getId(), nextEvent.getSubscriptionId(), subscription.getBundleId(), nextEvent.getRequestedDate(), nextEvent.getEffectiveDate(),
             null, null, null, null, null, null, null, null, nextEvent.getTotalOrdering(), null, 0, null);
    }
}
