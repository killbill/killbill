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

package com.ning.billing.entitlement.alignment;

import org.joda.time.DateTime;

import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.entitlement.events.EntitlementEvent.EventType;
import com.ning.billing.entitlement.events.user.ApiEventType;

public class TimedMigration {

    private final DateTime eventTime;
    private final EventType eventType;
    private final ApiEventType apiEventType;

    private final Plan plan;
    private final PlanPhase phase;
    private final String priceList;


    public TimedMigration(DateTime eventTime, EventType eventType,
            ApiEventType apiEventType, Plan plan, PlanPhase phase, String priceList) {
        super();
        this.eventTime = eventTime;
        this.eventType = eventType;
        this.apiEventType = apiEventType;
        this.plan = plan;
        this.phase = phase;
        this.priceList = priceList;
    }

    public DateTime getEventTime() {
        return eventTime;
    }

    public EventType getEventType() {
        return eventType;
    }


    public ApiEventType getApiEventType() {
        return apiEventType;
    }

    public Plan getPlan() {
        return plan;
    }

    public PlanPhase getPhase() {
        return phase;
    }

    public String getPriceList() {
        return priceList;
    }
}
