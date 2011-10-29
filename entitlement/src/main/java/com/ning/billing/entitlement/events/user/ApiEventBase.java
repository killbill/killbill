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

import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.catalog.api.IPlan;
import com.ning.billing.entitlement.alignment.IPlanAligner;
import com.ning.billing.entitlement.alignment.PlanAligner;
import com.ning.billing.entitlement.engine.core.Engine;
import com.ning.billing.entitlement.events.EventBase;
import com.ning.billing.entitlement.events.IEventLyfecycle.IEventLyfecycleState;

public class ApiEventBase extends EventBase implements IUserEvent {

    private final ApiEventType eventType;
    // Only valid for CREATE/CHANGE
    private final String eventPlan;
    private final String eventPlanPhase;
    private final String eventPriceList;


    public ApiEventBase(UUID subscriptionId, DateTime bundleStartDate, DateTime processed, String planName, String phaseName,
            String priceList, DateTime requestedDate,  ApiEventType eventType, DateTime effectiveDate, long activeVersion) {
        super(subscriptionId, requestedDate, effectiveDate, processed, activeVersion, true);
        this.eventType = eventType;
        this.eventPriceList = priceList;
        this.eventPlan = planName;
        this.eventPlanPhase = phaseName;
    }

    public ApiEventBase(UUID subscriptionId, DateTime bundleStartDate, DateTime processed,
            DateTime requestedDate,  ApiEventType eventType, DateTime effectiveDate, long activeVersion) {
        super(subscriptionId, requestedDate, effectiveDate, processed, activeVersion, true);
        this.eventType = eventType;
        this.eventPriceList = null;
        this.eventPlan = null;
        this.eventPlanPhase = null;
    }


    public ApiEventBase(UUID id, UUID subscriptionId, DateTime processed, String eventPlan, String eventPhase,
            String priceList, DateTime requestedDate,  ApiEventType eventType, DateTime effectiveDate, long activeVersion,
            boolean isActive, UUID processingOwner, DateTime nextAvailableProcessingTime,IEventLyfecycleState processingState) {
        super(id, subscriptionId, requestedDate, effectiveDate, processed, activeVersion, isActive, processingOwner, nextAvailableProcessingTime, processingState);
        this.eventType = eventType;
        this.eventPlan = eventPlan;
        this.eventPlanPhase = eventPhase;
        this.eventPriceList = priceList;
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
                + ", isActive()=" + isActive() + "]";
    }
}
