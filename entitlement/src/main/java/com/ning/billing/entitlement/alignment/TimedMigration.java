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

    public TimedMigration(final DateTime eventTime, final EventType eventType, final ApiEventType apiEventType,
                          final Plan plan, final PlanPhase phase, final String priceList) {
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

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("TimedMigration");
        sb.append("{apiEventType=").append(apiEventType);
        sb.append(", eventTime=").append(eventTime);
        sb.append(", eventType=").append(eventType);
        sb.append(", plan=").append(plan);
        sb.append(", phase=").append(phase);
        sb.append(", priceList='").append(priceList).append('\'');
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

        final TimedMigration that = (TimedMigration) o;

        if (apiEventType != that.apiEventType) {
            return false;
        }
        if (eventTime != null ? !eventTime.equals(that.eventTime) : that.eventTime != null) {
            return false;
        }
        if (eventType != that.eventType) {
            return false;
        }
        if (phase != null ? !phase.equals(that.phase) : that.phase != null) {
            return false;
        }
        if (plan != null ? !plan.equals(that.plan) : that.plan != null) {
            return false;
        }
        if (priceList != null ? !priceList.equals(that.priceList) : that.priceList != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = eventTime != null ? eventTime.hashCode() : 0;
        result = 31 * result + (eventType != null ? eventType.hashCode() : 0);
        result = 31 * result + (apiEventType != null ? apiEventType.hashCode() : 0);
        result = 31 * result + (plan != null ? plan.hashCode() : 0);
        result = 31 * result + (phase != null ? phase.hashCode() : 0);
        result = 31 * result + (priceList != null ? priceList.hashCode() : 0);
        return result;
    }
}
