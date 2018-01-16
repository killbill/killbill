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

import org.killbill.billing.subscription.events.EventBaseBuilder;

public class ApiEventBuilder extends EventBaseBuilder<ApiEventBuilder> {

    private ApiEventType apiEventType;
    private String eventPlan;
    private String eventPlanPhase;
    private String eventPriceList;
    private boolean fromDisk;

    public ApiEventBuilder() {
        super();
    }

    public ApiEventBuilder(final ApiEvent apiEvent) {
        super(apiEvent);
        this.apiEventType = apiEvent.getApiEventType();
        this.eventPlan = apiEvent.getEventPlan();
        this.eventPlanPhase = apiEvent.getEventPlanPhase();
        this.eventPriceList = apiEvent.getPriceList();
        this.fromDisk = apiEvent.isFromDisk();
    }

    public ApiEventBuilder(final EventBaseBuilder<?> base) {
        super(base);
    }

    public ApiEventType getApiEventType() {
        return apiEventType;
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

    public boolean isFromDisk() {
        return fromDisk;
    }

    public ApiEventBuilder setFromDisk(final boolean fromDisk) {
        this.fromDisk = fromDisk;
        return this;
    }

    public ApiEventBuilder setApiEventType(final ApiEventType eventType) {
        this.apiEventType = eventType;
        return this;
    }

    public ApiEventBuilder setEventPlan(final String eventPlan) {
        this.eventPlan = eventPlan;
        return this;
    }

    public ApiEventBuilder setEventPlanPhase(final String eventPlanPhase) {
        this.eventPlanPhase = eventPlanPhase;
        return this;
    }

    public ApiEventBuilder setEventPriceList(final String eventPriceList) {
        this.eventPriceList = eventPriceList;
        return this;
    }
    
    public ApiEventBase build() {
        final ApiEventBase result;
        if (apiEventType == ApiEventType.CREATE) {
            result = new ApiEventCreate(this);
        } else if (apiEventType == ApiEventType.TRANSFER) {
            result = new ApiEventTransfer(this);
        } else if (apiEventType == ApiEventType.CHANGE) {
            result = new ApiEventChange(this);
        } else if (apiEventType == ApiEventType.CANCEL) {
            result = new ApiEventCancel(this);
        } else if (apiEventType == ApiEventType.UNCANCEL) {
            result = new ApiEventUncancel(this);
        } else if (apiEventType == ApiEventType.UNDO_CHANGE) {
            result = new ApiEventUndoChange(this);
        } else {
            throw new IllegalStateException("Unknown ApiEventType " + apiEventType);
        }
        return result;
    }
}
