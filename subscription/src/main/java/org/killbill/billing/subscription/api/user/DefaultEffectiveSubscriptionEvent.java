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
import org.killbill.billing.events.EffectiveSubscriptionInternalEvent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class DefaultEffectiveSubscriptionEvent extends DefaultSubscriptionEvent implements EffectiveSubscriptionInternalEvent {

    public DefaultEffectiveSubscriptionEvent(final SubscriptionBaseTransitionData in, final DateTime startDate, final UUID userToken, final Long accountRecordId, final Long tenantRecordId) {
        super(in, startDate, accountRecordId, tenantRecordId, userToken);
    }

    @JsonCreator
    public DefaultEffectiveSubscriptionEvent(@JsonProperty("eventId") final UUID eventId,
                                             @JsonProperty("subscriptionId") final UUID subscriptionId,
                                             @JsonProperty("bundleId") final UUID bundleId,
                                             @JsonProperty("bundleExternalKey") final String bundleExternalKey,
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
        super(eventId, subscriptionId, bundleId, bundleExternalKey, effectiveTransitionTime, effectiveTransitionTime, previousState, previousPlan,
              previousPhase, previousPriceList, previousBillCycleDayLocal, nextState, nextPlan, nextPhase, nextPriceList, nextBillCycleDayLocal, totalOrdering,
              transitionType, remainingEventsForUserOperation, startDate, searchKey1, searchKey2, userToken);
    }
}
