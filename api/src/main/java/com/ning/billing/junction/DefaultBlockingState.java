/*
 * Copyright 2010-2012 Ning, Inc.
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

package com.ning.billing.junction;

import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.clock.Clock;
import com.ning.billing.entitlement.api.BlockingState;
import com.ning.billing.entitlement.api.BlockingStateType;
import com.ning.billing.entity.EntityBase;


public class DefaultBlockingState extends EntityBase implements BlockingState {

    public static final String CLEAR_STATE_NAME = "__KILLBILL__CLEAR__OVERDUE_STATE__";

    private static BlockingState clearState = null;

    private final UUID blockingId;
    private final String stateName;
    private final String service;
    private final boolean blockChange;
    private final boolean blockEntitlement;
    private final boolean blockBilling;
    private final DateTime effectiveDate;
    private final BlockingStateType type;

    public static BlockingState getClearState(final BlockingStateType type, final String serviceName, final Clock clock) {
        if (clearState == null) {
            clearState = new DefaultBlockingState(null, type, CLEAR_STATE_NAME, serviceName, false, false, false, clock.getUTCNow());
        }
        return clearState;
    }


    public DefaultBlockingState(final UUID id,
                                final UUID blockingId,
                                final BlockingStateType type,
                                final String stateName,
                                final String service,
                                final boolean blockChange,
                                final boolean blockEntitlement,
                                final boolean blockBilling,
                                final DateTime effectiveDate,
                                final DateTime createDate,
                                final DateTime updatedDate) {
        super(id, createDate, updatedDate);
        this.blockingId = blockingId;
        this.type = type;
        this.stateName = stateName;
        this.service = service;
        this.blockChange = blockChange;
        this.blockEntitlement = blockEntitlement;
        this.blockBilling = blockBilling;
        this.effectiveDate = effectiveDate;
    }


    public DefaultBlockingState(final UUID blockingId,
                                final BlockingStateType type,
                                 final String stateName,
                                 final String service,
                                 final boolean blockChange,
                                 final boolean blockEntitlement,
                                 final boolean blockBilling,
                                 final DateTime effectiveDate) {
        this(UUID.randomUUID(),
             blockingId,
             type,
             stateName,
             service,
             blockChange,
             blockEntitlement,
             blockBilling,
             effectiveDate,
             null,
             null);
    }

    @Override
    public UUID getBlockedId() {
        return blockingId;
    }

    /* (non-Javadoc)
    * @see com.ning.billing.junction.api.blocking.BlockingState#getStateName()
    */
    @Override
    public String getStateName() {
        return stateName;
    }

    @Override
    public BlockingStateType getType() {
        return type;
    }

    /* (non-Javadoc)
    * @see com.ning.billing.junction.api.blocking.BlockingState#getTimestamp()
    */
    @Override
    public DateTime getEffectiveDate() {
        return effectiveDate;
    }

    @Override
    public String getService() {
        return service;
    }

    /* (non-Javadoc)
     * @see com.ning.billing.junction.api.blocking.BlockingState#isBlockChange()
     */
    @Override
    public boolean isBlockChange() {
        return blockChange;
    }

    /* (non-Javadoc)
     * @see com.ning.billing.junction.api.blocking.BlockingState#isBlockEntitlement()
     */
    @Override
    public boolean isBlockEntitlement() {
        return blockEntitlement;
    }

    /* (non-Javadoc)
     * @see com.ning.billing.junction.api.blocking.BlockingState#isBlockBilling()
     */
    @Override
    public boolean isBlockBilling() {
        return blockBilling;
    }

    /* (non-Javadoc)
     * @see com.ning.billing.junction.api.blocking.BlockingState#compareTo(com.ning.billing.junction.api.blocking.DefaultBlockingState)
     */
    @Override
    public int compareTo(final BlockingState arg0) {
        if (effectiveDate.compareTo(arg0.getEffectiveDate()) != 0) {
            return effectiveDate.compareTo(arg0.getEffectiveDate());
        } else {
            return hashCode() - arg0.hashCode();
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (blockBilling ? 1231 : 1237);
        result = prime * result + (blockChange ? 1231 : 1237);
        result = prime * result + (blockEntitlement ? 1231 : 1237);
        result = prime * result + ((blockingId == null) ? 0 : blockingId.hashCode());
        result = prime * result + ((service == null) ? 0 : service.hashCode());
        result = prime * result + ((stateName == null) ? 0 : stateName.hashCode());
        result = prime * result + ((effectiveDate == null) ? 0 : effectiveDate.hashCode());
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final DefaultBlockingState other = (DefaultBlockingState) obj;
        if (blockBilling != other.blockBilling) {
            return false;
        }
        if (blockChange != other.blockChange) {
            return false;
        }
        if (blockEntitlement != other.blockEntitlement) {
            return false;
        }
        if (blockingId == null) {
            if (other.blockingId != null) {
                return false;
            }
        } else if (!blockingId.equals(other.blockingId)) {
            return false;
        }
        if (service == null) {
            if (other.service != null) {
                return false;
            }
        } else if (!service.equals(other.service)) {
            return false;
        }
        if (stateName == null) {
            if (other.stateName != null) {
                return false;
            }
        } else if (!stateName.equals(other.stateName)) {
            return false;
        }
        if (effectiveDate == null) {
            if (other.effectiveDate != null) {
                return false;
            }
        } else if (effectiveDate.compareTo(other.effectiveDate) != 0) {
            return false;
        }
        return true;
    }

    /* (non-Javadoc)
    * @see com.ning.billing.junction.api.blocking.BlockingState#getDescription()
    */
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
        return "BlockingState [blockingId=" + blockingId + ", stateName=" + stateName + ", service="
                + service + ", blockChange=" + blockChange + ", blockEntitlement=" + blockEntitlement
                + ", blockBilling=" + blockBilling + ", effectiveDate=" + effectiveDate + "]";
    }
}
