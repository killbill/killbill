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

package com.ning.billing.junction.dao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ning.billing.junction.api.Blockable;
import com.ning.billing.junction.api.BlockingState;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.clock.Clock;

public class MockBlockingStateDao implements BlockingStateDao {

    private final Map<UUID, List<BlockingState>> blockingStates = new HashMap<UUID, List<BlockingState>>();

    @Override
    public BlockingState getBlockingStateFor(final UUID blockableId, final InternalTenantContext context) {
        final List<BlockingState> blockingStates = getBlockingHistoryFor(blockableId, context);
        return blockingStates == null ? null : blockingStates.get(blockingStates.size() - 1);
    }

    @Override
    public List<BlockingState> getBlockingHistoryFor(final UUID overdueableId, final InternalTenantContext context) {
        final List<BlockingState> states = blockingStates.get(overdueableId);
        // Note! The returned list cannot be immutable!
        return states == null ? new ArrayList<BlockingState>() : states;
    }

    @Override
    public synchronized <T extends Blockable> void setBlockingState(final BlockingState state, final Clock clock, final InternalCallContext context) {
        if (blockingStates.get(state.getBlockedId()) == null) {
            blockingStates.put(state.getBlockedId(), new ArrayList<BlockingState>());
        }
        blockingStates.get(state.getBlockedId()).add(state);
    }

    public synchronized <T extends Blockable> void setBlockingStates(final UUID blockedId, final List<BlockingState> states) {
        blockingStates.put(blockedId, states);
    }

    public synchronized void clear() {
        blockingStates.clear();
    }
}
