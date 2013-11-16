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

package com.ning.billing.entitlement.api.svcs;

import java.util.List;
import java.util.UUID;

import com.ning.billing.callcontext.InternalCallContext;
import com.ning.billing.callcontext.InternalTenantContext;
import com.ning.billing.clock.Clock;
import com.ning.billing.entitlement.api.BlockingState;
import com.ning.billing.entitlement.api.BlockingStateType;
import com.ning.billing.entitlement.dao.BlockingStateDao;
import com.ning.billing.entitlement.engine.core.EntitlementUtils;
import com.ning.billing.junction.BlockingInternalApi;
import com.ning.billing.junction.DefaultBlockingState;

import com.google.inject.Inject;

public class DefaultInternalBlockingApi implements BlockingInternalApi {

    private final EntitlementUtils entitlementUtils;
    private final BlockingStateDao dao;
    private final Clock clock;

    @Inject
    public DefaultInternalBlockingApi(final EntitlementUtils entitlementUtils, final BlockingStateDao dao, final Clock clock) {
        this.entitlementUtils = entitlementUtils;
        this.dao = dao;
        this.clock = clock;
    }

    @Override
    public BlockingState getBlockingStateForService(final UUID overdueableId, final BlockingStateType blockingStateType, final String serviceName, final InternalTenantContext context) {
        final BlockingState blockingStateForService = dao.getBlockingStateForService(overdueableId, blockingStateType, serviceName, context);
        if (blockingStateForService == null) {
            return DefaultBlockingState.getClearState(blockingStateType, serviceName, clock);
        } else {
            return blockingStateForService;
        }
    }

    @Override
    public List<BlockingState> getBlockingAll(final UUID overdueableId, final BlockingStateType blockingStateType, final InternalTenantContext context) {
        return dao.getBlockingAll(overdueableId, blockingStateType, context);
    }

    @Override
    public void setBlockingState(final BlockingState state, final InternalCallContext context) {
        entitlementUtils.setBlockingStateAndPostBlockingTransitionEvent(state, context);
    }
}
