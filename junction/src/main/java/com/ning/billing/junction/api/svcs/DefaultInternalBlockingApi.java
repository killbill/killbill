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

package com.ning.billing.junction.api.svcs;

import java.util.List;
import java.util.UUID;

import com.ning.billing.junction.api.Blockable;
import com.ning.billing.junction.api.BlockingState;
import com.ning.billing.junction.dao.BlockingStateDao;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.clock.Clock;
import com.ning.billing.util.svcapi.junction.BlockingInternalApi;
import com.ning.billing.util.svcapi.junction.DefaultBlockingState;

import com.google.inject.Inject;

public class DefaultInternalBlockingApi implements BlockingInternalApi {

    private final BlockingStateDao dao;
    private final Clock clock;

    @Inject
    public DefaultInternalBlockingApi(final BlockingStateDao dao, final Clock clock) {
        this.dao = dao;
        this.clock = clock;
    }

    @Override
    public BlockingState getBlockingStateFor(final Blockable overdueable, final InternalTenantContext context) {
        BlockingState state = dao.getBlockingStateFor(overdueable.getId(), context);
        if (state == null) {
            state = DefaultBlockingState.getClearState();
        }
        return state;
    }

    @Override
    public BlockingState getBlockingStateFor(final UUID overdueableId, final InternalTenantContext context) {
        return dao.getBlockingStateFor(overdueableId, context);
    }

    @Override
    public List<BlockingState> getBlockingHistory(final Blockable overdueable, final InternalTenantContext context) {
        return dao.getBlockingHistoryFor(overdueable.getId(), context);
    }

    @Override
    public List<BlockingState> getBlockingHistory(final UUID overdueableId, final InternalTenantContext context) {
        return dao.getBlockingHistoryFor(overdueableId, context);
    }

    @Override
    public <T extends Blockable> void setBlockingState(final BlockingState state, final InternalCallContext context) {
        dao.setBlockingState(state, clock, context);
    }
}
