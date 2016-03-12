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

package org.killbill.billing.entitlement.api;

import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.events.BlockingTransitionInternalEvent;
import org.killbill.billing.events.BusEventBase;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class DefaultBlockingTransitionInternalEvent extends BusEventBase implements BlockingTransitionInternalEvent {

    private final UUID blockableId;
    private final String stateName;
    private final String service;
    private final DateTime effectiveDate;
    private final BlockingStateType blockingType;
    private final Boolean isTransitionedToBlockedBilling;
    private final Boolean isTransitionedToUnblockedBilling;
    private final Boolean isTransitionedToBlockedEntitlement;
    private final Boolean isTransitionedToUnblockedEntitlement;

    @JsonCreator
    public DefaultBlockingTransitionInternalEvent(@JsonProperty("blockableId") final UUID blockableId,
                                                  @JsonProperty("stateName") final String stateName,
                                                  @JsonProperty("service") final String service,
                                                  @JsonProperty("effectiveDate") final DateTime effectiveDate,
                                                  @JsonProperty("blockingType") final BlockingStateType blockingType,
                                                  @JsonProperty("isTransitionedToBlockedBilling") final Boolean transitionedToBlockedBilling,
                                                  @JsonProperty("isTransitionedToUnblockedBilling") final Boolean transitionedToUnblockedBilling,
                                                  @JsonProperty("isTransitionedToBlockedEntitlement") final Boolean transitionedToBlockedEntitlement,
                                                  @JsonProperty("isTransitionedToUnblockedEntitlement") final Boolean transitionedToUnblockedEntitlement,
                                                  @JsonProperty("searchKey1") final Long searchKey1,
                                                  @JsonProperty("searchKey2") final Long searchKey2,
                                                  @JsonProperty("userToken") final UUID userToken) {
        super(searchKey1, searchKey2, userToken);
        this.blockableId = blockableId;
        this.blockingType = blockingType;
        this.service = service;
        this.stateName = stateName;
        this.effectiveDate = effectiveDate;
        isTransitionedToBlockedBilling = transitionedToBlockedBilling;
        isTransitionedToUnblockedBilling = transitionedToUnblockedBilling;
        isTransitionedToBlockedEntitlement = transitionedToBlockedEntitlement;
        isTransitionedToUnblockedEntitlement = transitionedToUnblockedEntitlement;
    }

    @Override
    public UUID getBlockableId() {
        return blockableId;
    }

    @Override
    public BlockingStateType getBlockingType() {
        return blockingType;
    }

    @Override
    public String getStateName() {
        return stateName;
    }

    @Override
    public String getService() {
        return service;
    }

    @Override
    public DateTime getEffectiveDate() {
        return effectiveDate;
    }

    @JsonProperty("isTransitionedToBlockedBilling")
    @Override
    public Boolean isTransitionedToBlockedBilling() {
        return isTransitionedToBlockedBilling;
    }

    @JsonProperty("isTransitionedToUnblockedBilling")
    @Override
    public Boolean isTransitionedToUnblockedBilling() {
        return isTransitionedToUnblockedBilling;
    }

    @JsonProperty("isTransitionedToBlockedEntitlement")
    @Override
    public Boolean isTransitionedToBlockedEntitlement() {
        return isTransitionedToBlockedEntitlement;
    }

    @JsonProperty("isTransitionedToUnblockedEntitlement")
    @Override
    public Boolean isTransitionedToUnblockedEntitlement() {
        return isTransitionedToUnblockedEntitlement;
    }

    @JsonIgnore
    @Override
    public BusInternalEventType getBusEventType() {
        return BusInternalEventType.BLOCKING_STATE;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultBlockingTransitionInternalEvent)) {
            return false;
        }

        final DefaultBlockingTransitionInternalEvent that = (DefaultBlockingTransitionInternalEvent) o;

        if (blockableId != null ? !blockableId.equals(that.blockableId) : that.blockableId != null) {
            return false;
        }
        if (blockingType != that.blockingType) {
            return false;
        }
        if (stateName != null ? !stateName.equals(that.stateName) : that.stateName != null) {
            return false;
        }
        if (service != null ? !service.equals(that.service) : that.service != null) {
            return false;
        }
        if (effectiveDate != null ? effectiveDate.compareTo(that.effectiveDate) != 0 : that.effectiveDate != null) {
            return false;
        }
        if (isTransitionedToBlockedBilling != null ? !isTransitionedToBlockedBilling.equals(that.isTransitionedToBlockedBilling) : that.isTransitionedToBlockedBilling != null) {
            return false;
        }
        if (isTransitionedToBlockedEntitlement != null ? !isTransitionedToBlockedEntitlement.equals(that.isTransitionedToBlockedEntitlement) : that.isTransitionedToBlockedEntitlement != null) {
            return false;
        }
        if (isTransitionedToUnblockedBilling != null ? !isTransitionedToUnblockedBilling.equals(that.isTransitionedToUnblockedBilling) : that.isTransitionedToUnblockedBilling != null) {
            return false;
        }
        if (isTransitionedToUnblockedEntitlement != null ? !isTransitionedToUnblockedEntitlement.equals(that.isTransitionedToUnblockedEntitlement) : that.isTransitionedToUnblockedEntitlement != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = blockableId != null ? blockableId.hashCode() : 0;
        result = 31 * result + (blockingType != null ? blockingType.hashCode() : 0);
        result = 31 * result + (stateName != null ? stateName.hashCode() : 0);
        result = 31 * result + (service != null ? service.hashCode() : 0);
        result = 31 * result + (effectiveDate != null ? effectiveDate.hashCode() : 0);
        result = 31 * result + (isTransitionedToBlockedBilling != null ? isTransitionedToBlockedBilling.hashCode() : 0);
        result = 31 * result + (isTransitionedToUnblockedBilling != null ? isTransitionedToUnblockedBilling.hashCode() : 0);
        result = 31 * result + (isTransitionedToBlockedEntitlement != null ? isTransitionedToBlockedEntitlement.hashCode() : 0);
        result = 31 * result + (isTransitionedToUnblockedEntitlement != null ? isTransitionedToUnblockedEntitlement.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("DefaultBlockingTransitionInternalEvent{");
        sb.append("blockableId=").append(blockableId);
        sb.append(", blockingType=").append(blockingType);
        sb.append(", stateName=").append(stateName);
        sb.append(", service=").append(service);
        sb.append(", effectiveDate=").append(effectiveDate);
        sb.append(", isTransitionedToBlockedBilling=").append(isTransitionedToBlockedBilling);
        sb.append(", isTransitionedToUnblockedBilling=").append(isTransitionedToUnblockedBilling);
        sb.append(", isTransitionedToBlockedEntitlement=").append(isTransitionedToBlockedEntitlement);
        sb.append(", isTransitionedToUnblockedEntitlement=").append(isTransitionedToUnblockedEntitlement);
        sb.append('}');
        return sb.toString();
    }
}
