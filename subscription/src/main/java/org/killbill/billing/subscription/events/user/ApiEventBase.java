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

package org.killbill.billing.subscription.events.user;

import org.killbill.billing.subscription.events.EventBase;

public class ApiEventBase extends EventBase implements ApiEvent {

    private final ApiEventType apiEventType;
    // Only valid for CREATE/CHANGE
    private final String eventPlan;
    private final String eventPlanPhase;
    private final String eventPriceList;
    private final boolean fromDisk;

    public ApiEventBase(final ApiEventBuilder builder) {
        super(builder);
        this.apiEventType = builder.getApiEventType();
        this.eventPriceList = builder.getEventPriceList();
        this.eventPlan = builder.getEventPlan();
        this.eventPlanPhase = builder.getEventPlanPhase();
        this.fromDisk = builder.isFromDisk();
    }

    @Override
    public ApiEventType getApiEventType() {
        return apiEventType;
    }

    @Override
    public String getEventPlan() {
        return eventPlan;
    }

    @Override
    public String getEventPlanPhase() {
        return eventPlanPhase;
    }

    @Override
    public EventType getType() {
        return EventType.API_USER;
    }

    @Override
    public String getPriceList() {
        return eventPriceList;
    }

    @Override
    public boolean isFromDisk() {
        return fromDisk;
    }


    @Override
    public String toString() {
        return "ApiEventBase [ getId()= " + getId()
               + " apiEventType=" + apiEventType
               + ", eventPlan=" + eventPlan
               + ", eventPlanPhase=" + eventPlanPhase
               + ", getApiEventType()=" + getApiEventType()
               + ", getEventPlan()=" + getEventPlan()
               + ", getEventPlanPhase()=" + getEventPlanPhase()
               + ", getType()=" + getType()
               + ", getEffectiveDate()=" + getEffectiveDate()
               + ", getSubscriptionId()=" + getSubscriptionId()
               + ", isActive()=" + isActive() + "]";
    }
}
