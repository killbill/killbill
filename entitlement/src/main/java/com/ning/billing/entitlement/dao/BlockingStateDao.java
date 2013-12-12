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

import java.util.List;
import java.util.UUID;

import com.ning.billing.callcontext.InternalCallContext;
import com.ning.billing.callcontext.InternalTenantContext;
import com.ning.billing.clock.Clock;
import com.ning.billing.entitlement.api.BlockingState;
import com.ning.billing.entitlement.api.BlockingStateType;
import com.ning.billing.entitlement.api.EntitlementApiException;
import com.ning.billing.util.entity.dao.EntityDao;

public interface BlockingStateDao extends EntityDao<BlockingStateModelDao, BlockingState, EntitlementApiException> {

    /**
     * Returns the current state for that specific service
     *
     * @param blockableId       id of the blockable object
     * @param blockingStateType blockable object type
     * @param serviceName       name of the service
     * @param context           call context
     * @return current blocking state for that blockable object and service
     */
    public BlockingState getBlockingStateForService(UUID blockableId, BlockingStateType blockingStateType, String serviceName, InternalTenantContext context);

    /**
     * Returns the current state across all the services
     *
     * @param blockableId       id of the blockable object
     * @param blockingStateType blockable object type
     * @param context           call context
     * @return list of current blocking states for that blockable object
     */
    public List<BlockingState> getBlockingState(UUID blockableId, BlockingStateType blockingStateType, InternalTenantContext context);

    /**
     * Return all events (past and future) across all services) for a given callcontext (account_record_id)
     *
     * @param context call context
     * @return list of all blocking states for that account
     */
    public List<BlockingState> getBlockingAllForAccountRecordId(InternalTenantContext context);

    /**
     * Sets a new state for a specific service.
     *
     * @param state   blocking state to set
     * @param clock   system clock
     * @param context call context
     */
    public void setBlockingState(BlockingState state, Clock clock, InternalCallContext context);

    /**
     * Unactive the blocking state
     *
     * @param blockableId blockable id to unactivate
     * @param context     call context
     */
    public void unactiveBlockingState(UUID blockableId, final InternalCallContext context);

}
