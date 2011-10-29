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


public class ApiEventUncancel extends ApiEventBase {

    public ApiEventUncancel(UUID subscriptionId, DateTime bundleStartDate, DateTime now, DateTime requestedDate, DateTime effectiveDate, long version) {
        super(subscriptionId, bundleStartDate, now, requestedDate, ApiEventType.UNCANCEL, effectiveDate, version);
    }

    public ApiEventUncancel(UUID id, UUID subscriptionId, DateTime processed, String eventPlan, String eventPhase,
            String priceList, DateTime requestedDate, DateTime effectiveDate, long activeVersion,
            boolean isActive, UUID processingOwner, DateTime nextAvailableProcessingTime,IEventLyfecycleState processingState) {
        super(id, subscriptionId, processed, eventPlan, eventPhase, priceList, requestedDate, ApiEventType.UNCANCEL, effectiveDate,
                activeVersion, isActive, processingOwner, nextAvailableProcessingTime, processingState);
    }
}
