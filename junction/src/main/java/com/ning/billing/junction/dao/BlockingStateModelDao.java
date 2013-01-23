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

package com.ning.billing.junction.dao;

import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.junction.api.Blockable;
import com.ning.billing.junction.api.Blockable.Type;
import com.ning.billing.junction.api.BlockingState;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.dao.TableName;
import com.ning.billing.util.entity.EntityBase;
import com.ning.billing.util.entity.dao.EntityModelDao;
import com.ning.billing.util.svcapi.junction.DefaultBlockingState;

public class BlockingStateModelDao extends EntityBase implements EntityModelDao<BlockingState>{

    private final UUID blockableId;
    private final Type type;
    private final String state;
    private final String service;
    private final Boolean blockChange;
    private final Boolean blockEntitlement;
    private final Boolean blockBilling;

    public BlockingStateModelDao(final UUID id, final UUID blockableId, final Type type, final String state, final String service, final Boolean blockChange, final Boolean blockEntitlement,
                                 final Boolean blockBilling, final DateTime createDate, final DateTime updateDate) {
        super(id, createDate, updateDate);
        this.blockableId = blockableId;
        this.type = type;
        this.state = state;
        this.service = service;
        this.blockChange = blockChange;
        this.blockEntitlement = blockEntitlement;
        this.blockBilling = blockBilling;
    }

    public BlockingStateModelDao(final BlockingState src, InternalCallContext context) {
        this(src.getId(), src.getBlockedId(), src.getType(), src.getStateName(), src.getService(), src.isBlockChange(),
             src.isBlockEntitlement(), src.isBlockBilling(), context.getCreatedDate(), context.getUpdatedDate());
    }

    public UUID getBlockableId() {
        return blockableId;
    }

    public Type getType() {
        return type;
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

    public static BlockingState toBlockingState(BlockingStateModelDao src) {
        if (src == null) {
            return null;
        }
        return new DefaultBlockingState(src.getId(), src.getBlockableId(),src.getState(), src.getType(), src.getService(), src.getBlockChange(), src.getBlockEntitlement(), src.getBlockBilling(),
                                 src.getCreatedDate(), src.getUpdatedDate());
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
        sb.append(", type=").append(type);
        sb.append(", state='").append(state).append('\'');
        sb.append(", service='").append(service).append('\'');
        sb.append(", blockChange=").append(blockChange);
        sb.append(", blockEntitlement=").append(blockEntitlement);
        sb.append(", blockBilling=").append(blockBilling);
        sb.append('}');
        return sb.toString();
    }
}
