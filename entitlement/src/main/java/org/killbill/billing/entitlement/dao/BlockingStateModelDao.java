/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.entitlement.dao;

import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.entitlement.api.BlockingState;
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.killbill.billing.junction.DefaultBlockingState;
import org.killbill.billing.util.dao.TableName;
import org.killbill.billing.util.entity.dao.EntityModelDao;
import org.killbill.billing.util.entity.dao.EntityModelDaoBase;

public class BlockingStateModelDao extends EntityModelDaoBase implements EntityModelDao<BlockingState> {

    private UUID blockableId;
    private BlockingStateType type;
    private String state;
    private String service;
    private Boolean blockChange;
    private Boolean blockEntitlement;
    private Boolean blockBilling;
    private DateTime effectiveDate;
    private boolean isActive;

    public BlockingStateModelDao() { /* For the DAO mapper */ }

    public BlockingStateModelDao(final UUID id, final UUID blockableId, final BlockingStateType blockingStateType, final String state, final String service, final Boolean blockChange, final Boolean blockEntitlement,
                                 final Boolean blockBilling, final DateTime effectiveDate, final boolean isActive, final DateTime createDate, final DateTime updateDate) {
        super(id, createDate, updateDate);
        this.blockableId = blockableId;
        this.effectiveDate = effectiveDate;
        this.type = blockingStateType;
        this.state = state;
        this.service = service;
        this.blockChange = blockChange;
        this.blockEntitlement = blockEntitlement;
        this.blockBilling = blockBilling;
        this.isActive = isActive;
    }

    public BlockingStateModelDao(final BlockingState src, final InternalCallContext context) {
        this(src, context.getCreatedDate(), context.getUpdatedDate());
    }

    public BlockingStateModelDao(final BlockingState src, final DateTime createdDate, final DateTime updatedDate) {
        this(src.getId(), src.getBlockedId(), src.getType(), src.getStateName(), src.getService(), src.isBlockChange(),
             src.isBlockEntitlement(), src.isBlockBilling(), src.getEffectiveDate(), true, createdDate, updatedDate);
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

    public void setBlockableId(final UUID blockableId) {
        this.blockableId = blockableId;
    }

    public void setType(final BlockingStateType type) {
        this.type = type;
    }

    public void setState(final String state) {
        this.state = state;
    }

    public void setService(final String service) {
        this.service = service;
    }

    public void setBlockChange(final Boolean blockChange) {
        this.blockChange = blockChange;
    }

    public void setBlockEntitlement(final Boolean blockEntitlement) {
        this.blockEntitlement = blockEntitlement;
    }

    public void setBlockBilling(final Boolean blockBilling) {
        this.blockBilling = blockBilling;
    }

    public void setEffectiveDate(final DateTime effectiveDate) {
        this.effectiveDate = effectiveDate;
    }

    public void setIsActive(final boolean isActive) {
        this.isActive = isActive;
    }

    // TODO required for jdbi binder
    public boolean getIsActive() {
        return isActive;
    }

    public boolean isActive() {
        return isActive;
    }

    public static BlockingState toBlockingState(final BlockingStateModelDao src) {
        if (src == null) {
            return null;
        }
        return new DefaultBlockingState(src.getId(), src.getBlockableId(), src.getType(), src.getState(), src.getService(), src.getBlockChange(), src.getBlockEntitlement(), src.getBlockBilling(),
                                        src.getEffectiveDate(), src.getCreatedDate(), src.getUpdatedDate(), src.getRecordId());
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
        sb.append(", isActive=").append(isActive);
        sb.append('}');
        return sb.toString();
    }
}
