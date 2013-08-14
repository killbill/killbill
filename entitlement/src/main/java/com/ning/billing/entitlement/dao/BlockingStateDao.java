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

import com.ning.billing.entitlement.api.Blockable;
import com.ning.billing.entitlement.api.BlockingState;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.clock.Clock;

public interface BlockingStateDao {

    /**
     * Returns the current state for that specific service
     *
     * @param blockableId
     * @param serviceName
     * @param context
     * @return
     */
    public BlockingState getBlockingStateForService(UUID blockableId, String serviceName, InternalTenantContext context);

    /**
     * Returns the current state across all the services
     *
     * @param blockableId
     * @param context
     * @return
     */
    public List<BlockingState> getBlockingState(UUID blockableId, InternalTenantContext context);

    /**
     * Returns the state history  for that specific service
     *
     * @param blockableId
     * @param serviceName
     * @param context
     * @return
     */
    public List<BlockingState> getBlockingHistoryForService(UUID blockableId, String serviceName, InternalTenantContext context);

    /**
     * Returns the state history across all the services
     *
     * @param blockableId
     * @param context
     * @return
     */
    public List<BlockingState> getBlockingHistory(UUID blockableId, InternalTenantContext context);

    /**
     * Return all the events (past and future) across all services
     *
     * @param blockableId
     * @param context
     * @return
     */
    public List<BlockingState> getBlockingAll(UUID blockableId, InternalTenantContext context);

    /**
     * Sets a new state for a specific service
     *
     * @param state
     * @param clock
     * @param context
     */
    void setBlockingState(BlockingState state, Clock clock, InternalCallContext context);
}
