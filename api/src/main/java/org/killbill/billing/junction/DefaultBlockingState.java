/*
 * Copyright 2010-2012 Ning, Inc.
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.junction;

import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.entitlement.api.BlockingState;
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.killbill.billing.entity.EntityBase;
import org.killbill.billing.util.UUIDs;

public class DefaultBlockingState extends EntityBase implements BlockingState {


    private final UUID blockedId;
    private final String stateName;
    private final String service;
    private final boolean blockChange;
    private final boolean blockEntitlement;
    private final boolean blockBilling;
    private final DateTime effectiveDate;
    private final BlockingStateType type;
    private final Long totalOrdering;

    // Used by the DAO
    public DefaultBlockingState(final UUID id,
                                final UUID blockedId,
                                final BlockingStateType type,
                                final String stateName,
                                final String service,
                                final boolean blockChange,
                                final boolean blockEntitlement,
                                final boolean blockBilling,
                                final DateTime effectiveDate,
                                final DateTime createDate,
                                final DateTime updatedDate,
                                final Long totalOrdering) {
        super(id, createDate, updatedDate);
        this.blockedId = blockedId;
        this.type = type;
        this.stateName = stateName;
        this.service = service;
        this.blockChange = blockChange;
        this.blockEntitlement = blockEntitlement;
        this.blockBilling = blockBilling;
        this.effectiveDate = effectiveDate;
        this.totalOrdering = totalOrdering;
    }

    public DefaultBlockingState(final UUID blockedId,
                                final BlockingStateType type,
                                final String stateName,
                                final String service,
                                final boolean blockChange,
                                final boolean blockEntitlement,
                                final boolean blockBilling,
                                final DateTime effectiveDate) {
        this(UUIDs.randomUUID(),
             blockedId,
             type,
             stateName,
             service,
             blockChange,
             blockEntitlement,
             blockBilling,
             effectiveDate,
             null,
             null,
             0L);
    }

    public DefaultBlockingState(final BlockingState input,
                                final DateTime effectiveDate) {
        this(input.getBlockedId(),
             input.getType(),
             input.getStateName(),
             input.getService(),
             input.isBlockChange(),
             input.isBlockEntitlement(),
             input.isBlockBilling(),
             effectiveDate);
    }

    @Override
    public UUID getBlockedId() {
        return blockedId;
    }

    @Override
    public String getStateName() {
        return stateName;
    }

    @Override
    public BlockingStateType getType() {
        return type;
    }

    @Override
    public DateTime getEffectiveDate() {
        return effectiveDate;
    }

    @Override
    public String getService() {
        return service;
    }

    @Override
    public boolean isBlockChange() {
        return blockChange;
    }

    @Override
    public boolean isBlockEntitlement() {
        return blockEntitlement;
    }

    @Override
    public boolean isBlockBilling() {
        return blockBilling;
    }

    public Long getTotalOrdering() {
        return totalOrdering;
    }

    // Notes:
    //  + we need to keep the same implementation here as DefaultBlockingStateDao.BLOCKING_STATE_MODEL_DAO_ORDERING
    //  + to sort blocking states in entitlement, check ProxyBlockingStateDao#sortedCopy
    @Override
    public int compareTo(final BlockingState arg0) {
        // effective_date column NOT NULL
        final int comparison = effectiveDate.compareTo(arg0.getEffectiveDate());
        if (comparison == 0) {
            // Keep a stable ordering for ties
            final int comparison2 = createdDate.compareTo(arg0.getCreatedDate());
            if (comparison2 == 0 && arg0 instanceof DefaultBlockingState) {
                final DefaultBlockingState other = (DefaultBlockingState) arg0;
                // New element is last
                if (totalOrdering == null) {
                    return 1;
                } else if (other.getTotalOrdering() == null) {
                    return -1;
                } else {
                    return totalOrdering.compareTo(other.getTotalOrdering());
                }
            } else {
                return comparison2;
            }
        } else {
            return comparison;
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        final DefaultBlockingState that = (DefaultBlockingState) o;

        if (blockBilling != that.blockBilling) {
            return false;
        }
        if (blockChange != that.blockChange) {
            return false;
        }
        if (blockEntitlement != that.blockEntitlement) {
            return false;
        }
        if (blockedId != null ? !blockedId.equals(that.blockedId) : that.blockedId != null) {
            return false;
        }
        if (effectiveDate != null ? effectiveDate.compareTo(that.effectiveDate) != 0 : that.effectiveDate != null) {
            return false;
        }
        if (service != null ? !service.equals(that.service) : that.service != null) {
            return false;
        }
        if (stateName != null ? !stateName.equals(that.stateName) : that.stateName != null) {
            return false;
        }
        if (totalOrdering != null ? !totalOrdering.equals(that.totalOrdering) : that.totalOrdering != null) {
            return false;
        }
        if (type != that.type) {
            return false;
        }

        return true;
    }


    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (blockedId != null ? blockedId.hashCode() : 0);
        result = 31 * result + (stateName != null ? stateName.hashCode() : 0);
        result = 31 * result + (service != null ? service.hashCode() : 0);
        result = 31 * result + (blockChange ? 1 : 0);
        result = 31 * result + (blockEntitlement ? 1 : 0);
        result = 31 * result + (blockBilling ? 1 : 0);
        result = 31 * result + (effectiveDate != null ? effectiveDate.hashCode() : 0);
        result = 31 * result + (type != null ? type.hashCode() : 0);
        result = 31 * result + (totalOrdering != null ? totalOrdering.hashCode() : 0);
        return result;
    }

    @Override
    public String getDescription() {
        final String entitlement = onOff(isBlockEntitlement());
        final String billing = onOff(isBlockBilling());
        final String change = onOff(isBlockChange());

        return String.format("(Change: %s, Entitlement: %s, Billing: %s)", change, entitlement, billing);
    }

    private String onOff(final boolean val) {
        if (val) {
            return "Off";
        } else {
            return "On";
        }
    }

    @Override
    public String toString() {
        return "BlockingState [blockedId=" + blockedId + ", stateName=" + stateName + ", service="
               + service + ", blockChange=" + blockChange + ", blockEntitlement=" + blockEntitlement
               + ", blockBilling=" + blockBilling + ", effectiveDate=" + effectiveDate + "]";
    }
}
