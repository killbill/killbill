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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.ning.billing.clock.Clock;
import com.ning.billing.entitlement.api.Blockable;
import com.ning.billing.entitlement.api.BlockingState;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;

public class MockBlockingStateDao implements BlockingStateDao {

    private final Map<UUID, List<BlockingState>> blockingStates = new HashMap<UUID, List<BlockingState>>();

    @Override
    public BlockingState getBlockingStateForService(final UUID blockableId, final String serviceName, final InternalTenantContext context) {
        final List<BlockingState> states = getBlockingHistory(blockableId, context);
        if (states == null) {
            return null;
        }
        final ImmutableList<BlockingState> filtered = ImmutableList.<BlockingState>copyOf(Collections2.filter(states, new Predicate<BlockingState>() {
            @Override
            public boolean apply(@Nullable final BlockingState input) {
                return input.getService().equals(serviceName);
            }
        }));
        return filtered.size() == 0 ? null : filtered.get(filtered.size() - 1);
    }

    @Override
    public List<BlockingState> getBlockingState(final UUID blockableId, final InternalTenantContext context) {
        final List<BlockingState> blockingStatesForId = blockingStates.get(blockableId);
        if (blockingStatesForId == null) {
            return new ArrayList<BlockingState>();
        }

        final Map<String, BlockingState> tmp  = new HashMap<String, BlockingState>();
        for (BlockingState cur : blockingStatesForId) {
            final BlockingState curStateForService = tmp.get(cur.getService());
            if (curStateForService == null || curStateForService.getCreatedDate().compareTo(cur.getCreatedDate()) < 0) {
                tmp.put(cur.getService(), cur);
            }
        }
        return new ArrayList<BlockingState>(tmp.values());
    }

    @Override
    public List<BlockingState> getBlockingHistoryForService(final UUID overdueableId, final String serviceName, final InternalTenantContext context) {
        final List<BlockingState> states = blockingStates.get(overdueableId);
        if (states == null) {
            return new ArrayList<BlockingState>();
        }
        final ImmutableList<BlockingState> filtered = ImmutableList.<BlockingState>copyOf(Collections2.filter(states, new Predicate<BlockingState>() {
            @Override
            public boolean apply(@Nullable final BlockingState input) {
                return input.getService().equals(serviceName);
            }
        }));

        // Note! The returned list cannot be immutable!
        return states == null ? new ArrayList<BlockingState>() : new ArrayList<BlockingState>(filtered);
    }

    @Override
    public List<BlockingState> getBlockingHistory(final UUID overdueableId, final InternalTenantContext context) {
        final List<BlockingState> states = blockingStates.get(overdueableId);
        // Note! The returned list cannot be immutable!
        return states == null ? new ArrayList<BlockingState>() : states;
    }

    @Override
    public synchronized void setBlockingState(final BlockingState state, final Clock clock, final InternalCallContext context) {
        if (blockingStates.get(state.getBlockedId()) == null) {
            blockingStates.put(state.getBlockedId(), new ArrayList<BlockingState>());
        }
        blockingStates.get(state.getBlockedId()).add(state);
    }

    public synchronized void setBlockingStates(final UUID blockedId, final List<BlockingState> states) {
        blockingStates.put(blockedId, states);
    }

    public synchronized void clear() {
        blockingStates.clear();
    }
}
