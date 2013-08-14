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

package com.ning.billing.entitlement.dao;

import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.entitlement.api.BlockingState;
import com.ning.billing.entitlement.api.BlockingStateType;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.dao.TableName;
import com.ning.billing.util.entity.EntityBase;
import com.ning.billing.util.entity.dao.EntityModelDao;
import com.ning.billing.util.svcapi.junction.DefaultBlockingState;

public class BlockingStateModelDao extends EntityBase implements EntityModelDao<BlockingState>{

    private final UUID blockableId;
    private final BlockingStateType type;
    private final String state;
    private final String service;
    private final Boolean blockChange;
    private final Boolean blockEntitlement;
    private final Boolean blockBilling;
    private final DateTime effectiveDate;

    public BlockingStateModelDao(final UUID id, final UUID blockableId, final BlockingStateType blockingStateType, final String state, final String service, final Boolean blockChange, final Boolean blockEntitlement,
                                 final Boolean blockBilling, final DateTime effectiveDate, final DateTime createDate, final DateTime updateDate) {
        super(id, createDate, updateDate);
        this.blockableId = blockableId;
        this.effectiveDate = effectiveDate;
        this.type = blockingStateType;
        this.state = state;
        this.service = service;
        this.blockChange = blockChange;
        this.blockEntitlement = blockEntitlement;
        this.blockBilling = blockBilling;
    }

    public BlockingStateModelDao(final BlockingState src, InternalCallContext context) {
        this(src.getId(), src.getBlockedId(), src.getType(), src.getStateName(), src.getService(), src.isBlockChange(),
             src.isBlockEntitlement(), src.isBlockBilling(), src.getEffectiveDate(), context.getCreatedDate(), context.getUpdatedDate());
    }

    public UUID getBlockableId() {
        return blockableId;
    }

    public String getState() {
        return state;
    }

    public String getService() {
        return service;
    }

    public Boolean getBlockChange() {
        return blockChange;
    }

    public Boolean getBlockEntitlement() {
        return blockEntitlement;
    }

    public Boolean getBlockBilling() {
        return blockBilling;
    }

    public BlockingStateType getType() {
        return type;
    }

    public DateTime getEffectiveDate() {
        return effectiveDate;
    }

    public static BlockingState toBlockingState(BlockingStateModelDao src) {
        if (src == null) {
            return null;
        }
        return new DefaultBlockingState(src.getId(), src.getBlockableId(), src.getType(), src.getState(), src.getService(), src.getBlockChange(), src.getBlockEntitlement(), src.getBlockBilling(),
                                 src.getEffectiveDate(), src.getCreatedDate(), src.getUpdatedDate());
    }

    @Override
    public TableName getTableName() {
        return TableName.BLOCKING_STATES;
    }

    @Override
    public TableName getHistoryTableName() {
        return null;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("BlockingStateModelDao");
        sb.append("{blockableId=").append(blockableId);
        sb.append(", state='").append(state).append('\'');
        sb.append(", service='").append(service).append('\'');
        sb.append(", blockChange=").append(blockChange);
        sb.append(", blockEntitlement=").append(blockEntitlement);
        sb.append(", blockBilling=").append(blockBilling);
        sb.append('}');
        return sb.toString();
    }
}
