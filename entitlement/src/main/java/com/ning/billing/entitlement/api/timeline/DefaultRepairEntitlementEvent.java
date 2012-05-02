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
package com.ning.billing.entitlement.api.timeline;

import java.util.UUID;

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonProperty;
import org.joda.time.DateTime;

import com.ning.billing.entitlement.api.timeline.RepairEntitlementEvent;

public class DefaultRepairEntitlementEvent implements RepairEntitlementEvent {

    private final UUID userToken;
    private final UUID bundleId;
    private final UUID accountId;
    private final DateTime effectiveDate;
    
    
    @JsonCreator
    public DefaultRepairEntitlementEvent(@JsonProperty("userToken") final UUID userToken,
            @JsonProperty("accountId") final UUID accountId,
            @JsonProperty("bundleId") final UUID bundleId,
            @JsonProperty("effectiveDate") final DateTime effectiveDate) {
        this.userToken = userToken;
        this.bundleId = bundleId;
        this.accountId = accountId;
        this.effectiveDate = effectiveDate;
    }
    
    @JsonIgnore
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
    public UUID getAccountId() {
        return accountId;
    }

    @Override
    public DateTime getEffectiveDate() {
        return effectiveDate;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                + ((accountId == null) ? 0 : accountId.hashCode());
        result = prime * result
                + ((bundleId == null) ? 0 : bundleId.hashCode());
        result = prime * result
                + ((effectiveDate == null) ? 0 : effectiveDate.hashCode());
        result = prime * result
                + ((userToken == null) ? 0 : userToken.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        DefaultRepairEntitlementEvent other = (DefaultRepairEntitlementEvent) obj;
        if (accountId == null) {
            if (other.accountId != null)
                return false;
        } else if (!accountId.equals(other.accountId))
            return false;
        if (bundleId == null) {
            if (other.bundleId != null)
                return false;
        } else if (!bundleId.equals(other.bundleId))
            return false;
        if (effectiveDate == null) {
            if (other.effectiveDate != null)
                return false;
        } else if (effectiveDate.compareTo(other.effectiveDate) != 0)
            return false;
        if (userToken == null) {
            if (other.userToken != null)
                return false;
        } else if (!userToken.equals(other.userToken))
            return false;
        return true;
    }
}
