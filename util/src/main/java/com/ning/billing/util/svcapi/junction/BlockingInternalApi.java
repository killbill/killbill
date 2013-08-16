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

package com.ning.billing.util.svcapi.junction;

import java.util.List;
import java.util.UUID;

import com.ning.billing.account.api.Account;
import com.ning.billing.entitlement.api.Blockable;
import com.ning.billing.entitlement.api.BlockingState;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;

public interface BlockingInternalApi {

    public BlockingState getBlockingStateForService(Blockable blockable, String serviceName, InternalTenantContext context);

    public BlockingState getBlockingStateForService(UUID blockableId, String serviceName, InternalTenantContext context);

    public List<BlockingState> getBlockingHistoryForService(Blockable blockable, String serviceName, InternalTenantContext context);

    public List<BlockingState> getBlockingHistoryForService(UUID blockableId, String serviceName, InternalTenantContext context);

    public List<BlockingState> getBlockingHistory(Blockable blockable, InternalTenantContext context);

    public List<BlockingState> getBlockingHistory(UUID blockableId, InternalTenantContext context);

    public List<BlockingState> getBlockingAll(Blockable blockable, InternalTenantContext context);

    public List<BlockingState> getBlockingAll(UUID blockableId, InternalTenantContext context);

    public void setBlockingState(BlockingState state, InternalCallContext context);

}
