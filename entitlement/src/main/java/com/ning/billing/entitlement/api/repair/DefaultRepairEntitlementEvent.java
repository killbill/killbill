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
package com.ning.billing.entitlement.api.repair;

import java.util.UUID;

import org.joda.time.DateTime;

public class DefaultRepairEntitlementEvent implements RepairEntitlementEvent {

    private final UUID userToken;
    private final UUID bundleId;
    private final DateTime efectiveDate;
    
    
    public DefaultRepairEntitlementEvent(final UUID userToken, final UUID bundleId, final DateTime efectiveDate) {
        this.userToken = userToken;
        this.bundleId = bundleId;
        this.efectiveDate = efectiveDate;
    }
    
    @Override
    public BusEventType getBusEventType() {
        return BusEventType.BUNDLE_REPAIR;
    }

    @Override
    public UUID getUserToken() {
        return userToken;
    }

    @Override
    public UUID getBundleId() {
        return bundleId;
    }

    @Override
    public DateTime getEffectiveDate() {
        return efectiveDate;
    }
}
