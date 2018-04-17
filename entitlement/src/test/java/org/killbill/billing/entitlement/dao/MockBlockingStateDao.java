/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.Catalog;
import org.killbill.billing.entitlement.api.BlockingState;
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.killbill.billing.entitlement.api.EntitlementApiException;
import org.killbill.billing.util.entity.dao.MockEntityDaoBase;

import com.google.common.base.MoreObjects;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;

public class MockBlockingStateDao extends MockEntityDaoBase<BlockingStateModelDao, BlockingState, EntitlementApiException> implements BlockingStateDao {

    private final Map<UUID, List<BlockingState>> blockingStates = new HashMap<UUID, List<BlockingState>>();
    private final Map<Long, List<BlockingState>> blockingStatesPerAccountRecordId = new HashMap<Long, List<BlockingState>>();

    // TODO This mock class should also check that events are past or present

    @Override
    public BlockingState getBlockingStateForService(final UUID blockableId, final BlockingStateType blockingStateType, final String serviceName, final InternalTenantContext context) {
        final List<BlockingState> states = blockingStates.get(blockableId);
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
    public List<BlockingState> getBlockingState(final UUID blockableId, final BlockingStateType blockingStateType, final DateTime upToDate, final InternalTenantContext context) {
        final List<BlockingState> blockingStatesForId = blockingStates.get(blockableId);
        if (blockingStatesForId == null) {
            return new ArrayList<BlockingState>();
        }

        final Map<String, BlockingState> tmp = new HashMap<String, BlockingState>();
        for (BlockingState cur : blockingStatesForId) {
            final BlockingState curStateForService = tmp.get(cur.getService());
            if (curStateForService == null || curStateForService.getEffectiveDate().compareTo(cur.getEffectiveDate()) < 0) {
                tmp.put(cur.getService(), cur);
            }
        }
        return new ArrayList<BlockingState>(tmp.values());
    }

    @Override
    public List<BlockingState> getBlockingAllForAccountRecordId(final Catalog catalog, final InternalTenantContext context) {
        return MoreObjects.firstNonNull(blockingStatesPerAccountRecordId.get(context.getAccountRecordId()), ImmutableList.<BlockingState>of());
    }

    @Override
    public synchronized void setBlockingStatesAndPostBlockingTransitionEvent(final Map<BlockingState, Optional<UUID>> states, final InternalCallContext context) {
        for (final BlockingState state : states.keySet()) {
            if (blockingStates.get(state.getBlockedId()) == null) {
                blockingStates.put(state.getBlockedId(), new ArrayList<BlockingState>());
            }
            blockingStates.get(state.getBlockedId()).add(state);

            if (blockingStatesPerAccountRecordId.get(context.getAccountRecordId()) == null) {
                blockingStatesPerAccountRecordId.put(context.getAccountRecordId(), new ArrayList<BlockingState>());
            }
            blockingStatesPerAccountRecordId.get(context.getAccountRecordId()).add(state);
        }
    }

    @Override
    public void unactiveBlockingState(final UUID blockableId, final InternalCallContext context) {
        throw new UnsupportedOperationException();
    }

    public synchronized void clear() {
        blockingStates.clear();
        blockingStatesPerAccountRecordId.clear();
    }
}
