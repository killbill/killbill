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

import com.ning.billing.entitlement.events.EventBaseBuilder;

public class ApiEventBuilder extends EventBaseBuilder<ApiEventBuilder> {

    private ApiEventType eventType;
    private String eventPlan;
    private String eventPlanPhase;
    private String eventPriceList;

    public ApiEventBuilder() {
        super();
    }

    public ApiEventBuilder(EventBaseBuilder<?> base) {
        super(base);
    }

    public ApiEventType getEventType() {
        return eventType;
    }

    public String getEventPlan() {
        return eventPlan;
    }

    public String getEventPlanPhase() {
        return eventPlanPhase;
    }

    public String getEventPriceList() {
        return eventPriceList;
    }

    public ApiEventBuilder setEventType(ApiEventType eventType) {
        this.eventType = eventType;
        return this;
    }

    public ApiEventBuilder setEventPlan(String eventPlan) {
        this.eventPlan = eventPlan;
        return this;
    }

    public ApiEventBuilder setEventPlanPhase(String eventPlanPhase) {
        this.eventPlanPhase = eventPlanPhase;
        return this;
    }

    public ApiEventBuilder setEventPriceList(String eventPriceList) {
        this.eventPriceList = eventPriceList;
        return this;
    }
}
