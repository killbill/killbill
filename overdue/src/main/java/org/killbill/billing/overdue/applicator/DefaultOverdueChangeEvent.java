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

package org.killbill.billing.overdue.applicator;

import java.util.UUID;

import org.killbill.billing.events.BusEventBase;
import org.killbill.billing.events.OverdueChangeInternalEvent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class DefaultOverdueChangeEvent extends BusEventBase implements OverdueChangeInternalEvent {

    private final UUID overdueObjectId;
    private final String previousOverdueStateName;
    private final String nextOverdueStateName;
    private final Boolean isBlockedBilling;
    private final Boolean isUnblockedBilling;


    @JsonCreator
    public DefaultOverdueChangeEvent(@JsonProperty("overdueObjectId") final UUID overdueObjectId,
                                     @JsonProperty("previousOverdueStateName") final String previousOverdueStateName,
                                     @JsonProperty("nextOverdueStateName") final String nextOverdueStateName,
                                     @JsonProperty("isBlockedBilling") final Boolean isBlockedBilling,
                                     @JsonProperty("isUnblockedBilling") final Boolean isUnblockedBilling,
                                     @JsonProperty("searchKey1") final Long searchKey1,
                                     @JsonProperty("searchKey2") final Long searchKey2,
                                     @JsonProperty("userToken") final UUID userToken) {
        super(searchKey1, searchKey2, userToken);
        this.overdueObjectId = overdueObjectId;
        this.isBlockedBilling = isBlockedBilling;
        this.isUnblockedBilling = isUnblockedBilling;
        this.previousOverdueStateName = previousOverdueStateName;
        this.nextOverdueStateName = nextOverdueStateName;
    }

    @JsonIgnore
    @Override
    public BusInternalEventType getBusEventType() {
        return BusInternalEventType.OVERDUE_CHANGE;
    }

    @Override
    public String getPreviousOverdueStateName() {
        return previousOverdueStateName;
    }

    @Override
    public UUID getOverdueObjectId() {
        return overdueObjectId;
    }

    @Override
    public String getNextOverdueStateName() {
        return nextOverdueStateName;
    }

    @Override
    @JsonProperty("isBlockedBilling")
    public Boolean isBlockedBilling() {
        return isBlockedBilling;
    }

    @Override
    @JsonProperty("isUnblockedBilling")
    public Boolean isUnblockedBilling() {
        return isUnblockedBilling;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("DefaultOverdueChangeEvent{");
        sb.append("overdueObjectId=").append(overdueObjectId);
        sb.append(", previousOverdueStateName='").append(previousOverdueStateName).append('\'');
        sb.append(", nextOverdueStateName='").append(nextOverdueStateName).append('\'');
        sb.append(", isBlockedBilling=").append(isBlockedBilling);
        sb.append(", isUnblockedBilling=").append(isUnblockedBilling);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultOverdueChangeEvent)) {
            return false;
        }

        final DefaultOverdueChangeEvent that = (DefaultOverdueChangeEvent) o;

        if (isBlockedBilling != null ? !isBlockedBilling.equals(that.isBlockedBilling) : that.isBlockedBilling != null) {
            return false;
        }
        if (isUnblockedBilling != null ? !isUnblockedBilling.equals(that.isUnblockedBilling) : that.isUnblockedBilling != null) {
            return false;
        }
        if (nextOverdueStateName != null ? !nextOverdueStateName.equals(that.nextOverdueStateName) : that.nextOverdueStateName != null) {
            return false;
        }
        if (overdueObjectId != null ? !overdueObjectId.equals(that.overdueObjectId) : that.overdueObjectId != null) {
            return false;
        }
        if (previousOverdueStateName != null ? !previousOverdueStateName.equals(that.previousOverdueStateName) : that.previousOverdueStateName != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = overdueObjectId != null ? overdueObjectId.hashCode() : 0;
        result = 31 * result + (previousOverdueStateName != null ? previousOverdueStateName.hashCode() : 0);
        result = 31 * result + (nextOverdueStateName != null ? nextOverdueStateName.hashCode() : 0);
        result = 31 * result + (isBlockedBilling != null ? isBlockedBilling.hashCode() : 0);
        result = 31 * result + (isUnblockedBilling != null ? isUnblockedBilling.hashCode() : 0);
        return result;
    }
}
