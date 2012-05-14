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

package com.ning.billing.entitlement.events.user;

import com.ning.billing.entitlement.events.EventBase;

import java.util.UUID;

public class ApiEventBase extends EventBase implements ApiEvent {

    private final ApiEventType eventType;
    // Only valid for CREATE/CHANGE
    private final String eventPlan;
    private final String eventPlanPhase;
    private final String eventPriceList;
    private final UUID userToken;
    private final boolean fromDisk;

    public ApiEventBase(ApiEventBuilder builder) {
        super(builder);
        this.eventType = builder.getEventType();
        this.eventPriceList = builder.getEventPriceList();
        this.eventPlan = builder.getEventPlan();
        this.eventPlanPhase = builder.getEventPlanPhase();
        this.fromDisk = builder.isFromDisk();
        this.userToken = builder.getUserToken();
    }

    @Override
    public ApiEventType getEventType() {
        return eventType;
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
	public UUID getUserToken() {
		return userToken;
	}


    @Override
    public boolean isFromDisk() {
        return fromDisk;
    }


    @Override
    public String toString() {
        return "ApiEventBase [ getId()= " + getId()
        		+ " eventType=" + eventType
        		+ ", eventPlan=" + eventPlan
                + ", eventPlanPhase=" + eventPlanPhase
                + ", getEventType()=" + getEventType()
                + ", getEventPlan()=" + getEventPlan()
                + ", getEventPlanPhase()=" + getEventPlanPhase()
                + ", getType()=" + getType()
                + ", getRequestedDate()=" + getRequestedDate()
                + ", getEffectiveDate()=" + getEffectiveDate()
                + ", getActiveVersion()=" + getActiveVersion()
                + ", getProcessedDate()=" + getProcessedDate()
                + ", getSubscriptionId()=" + getSubscriptionId()
                + ", evetnToken()=" + getUserToken()
                + ", isActive()=" + isActive() + "]";
    }
}
