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

package org.killbill.billing.entitlement.engine.core;

import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.killbill.notificationq.api.NotificationEvent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class BlockingTransitionNotificationKey implements NotificationEvent {

    private final UUID blockingStateId;
    private final UUID blockableId;
    private final BlockingStateType blockingType;
    private final String stateName;
    private final String service;
    private final DateTime effectiveDate;
    private final Boolean isTransitionToBlockedBilling;
    private final Boolean isTransitionToUnblockedBilling;
    private final Boolean isTransitionToBlockedEntitlement;
    private final Boolean isTransitionToUnblockedEntitlement;

    @JsonCreator
    public BlockingTransitionNotificationKey(@JsonProperty("blockingStateId") final UUID blockingStateId,
                                             @JsonProperty("blockableId") final UUID blockableId,
                                             @JsonProperty("stateName") final String stateName,
                                             @JsonProperty("service") final String service,
                                             @JsonProperty("effectiveDate") final DateTime effectiveDate,
                                             @JsonProperty("type") final BlockingStateType blockingType,
                                             @JsonProperty("isTransitionToBlockedBilling") final Boolean isTransitionToBlockedBilling,
                                             @JsonProperty("isTransitionToUnblockedBilling") final Boolean isTransitionToUnblockedBilling,
                                             @JsonProperty("isTransitionToBlockedEntitlement") final Boolean isTransitionToBlockedEntitlement,
                                             @JsonProperty("isTransitionToUnblockedEntitlement") final Boolean isTransitionToUnblockedEntitlement) {

        this.blockingStateId = blockingStateId;
        this.blockableId = blockableId;
        this.service = service;
        this.stateName = stateName;
        this.effectiveDate = effectiveDate;
        this.blockingType = blockingType;
        this.isTransitionToBlockedBilling = isTransitionToBlockedBilling;
        this.isTransitionToUnblockedBilling = isTransitionToUnblockedBilling;
        this.isTransitionToBlockedEntitlement = isTransitionToBlockedEntitlement;
        this.isTransitionToUnblockedEntitlement = isTransitionToUnblockedEntitlement;
    }

    public UUID getBlockingStateId() {
        return blockingStateId;
    }

    public UUID getBlockableId() {
        return blockableId;
    }

    public BlockingStateType getBlockingType() {
        return blockingType;
    }

    public String getStateName() {
        return stateName;
    }

    public String getService() {
        return service;
    }

    public DateTime getEffectiveDate() {
        return effectiveDate;
    }

    @JsonProperty("isTransitionToBlockedBilling")
    public Boolean isTransitionedToBlockedBilling() {
        return isTransitionToBlockedBilling;
    }

    @JsonProperty("isTransitionToUnblockedBilling")
    public Boolean isTransitionedToUnblockedBilling() {
        return isTransitionToUnblockedBilling;
    }

    @JsonProperty("isTransitionToBlockedEntitlement")
    public Boolean isTransitionedToBlockedEntitlement() {
        return isTransitionToBlockedEntitlement;
    }

    @JsonProperty("isTransitionToUnblockedEntitlement")
    public Boolean isTransitionToUnblockedEntitlement() {
        return isTransitionToUnblockedEntitlement;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("BlockingTransitionNotificationKey{");
        sb.append("blockingStateId=").append(blockingStateId);
        sb.append(", blockableId=").append(blockableId);
        sb.append(", blockingType=").append(blockingType);
        sb.append(", isTransitionToBlockedBilling=").append(isTransitionToBlockedBilling);
        sb.append(", isTransitionToUnblockedBilling=").append(isTransitionToUnblockedBilling);
        sb.append(", isTransitionToBlockedEntitlement=").append(isTransitionToBlockedEntitlement);
        sb.append(", isTransitionToUnblockedEntitlement=").append(isTransitionToUnblockedEntitlement);
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

        final BlockingTransitionNotificationKey that = (BlockingTransitionNotificationKey) o;

        if (blockingStateId != null ? !blockingStateId.equals(that.blockingStateId) : that.blockingStateId != null) {
            return false;
        }
        if (blockableId != null ? !blockableId.equals(that.blockableId) : that.blockableId != null) {
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
        if (blockingType != that.blockingType) {
            return false;
        }
        if (isTransitionToBlockedBilling != null ? !isTransitionToBlockedBilling.equals(that.isTransitionToBlockedBilling) : that.isTransitionToBlockedBilling != null) {
            return false;
        }
        if (isTransitionToBlockedEntitlement != null ? !isTransitionToBlockedEntitlement.equals(that.isTransitionToBlockedEntitlement) : that.isTransitionToBlockedEntitlement != null) {
            return false;
        }
        if (isTransitionToUnblockedBilling != null ? !isTransitionToUnblockedBilling.equals(that.isTransitionToUnblockedBilling) : that.isTransitionToUnblockedBilling != null) {
            return false;
        }
        if (isTransitionToUnblockedEntitlement != null ? !isTransitionToUnblockedEntitlement.equals(that.isTransitionToUnblockedEntitlement) : that.isTransitionToUnblockedEntitlement != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = blockingStateId != null ? blockingStateId.hashCode() : 0;
        result = 31 * result + (blockableId != null ? blockableId.hashCode() : 0);
        result = 31 * result + (stateName != null ? stateName.hashCode() : 0);
        result = 31 * result + (service != null ? service.hashCode() : 0);
        result = 31 * result + (effectiveDate != null ? effectiveDate.hashCode() : 0);
        result = 31 * result + (blockingType != null ? blockingType.hashCode() : 0);
        result = 31 * result + (isTransitionToBlockedBilling != null ? isTransitionToBlockedBilling.hashCode() : 0);
        result = 31 * result + (isTransitionToUnblockedBilling != null ? isTransitionToUnblockedBilling.hashCode() : 0);
        result = 31 * result + (isTransitionToBlockedEntitlement != null ? isTransitionToBlockedEntitlement.hashCode() : 0);
        result = 31 * result + (isTransitionToUnblockedEntitlement != null ? isTransitionToUnblockedEntitlement.hashCode() : 0);
        return result;
    }
}
