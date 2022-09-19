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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.VersionedCatalog;
import org.killbill.billing.entitlement.api.BlockingState;
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.killbill.billing.entitlement.api.EntitlementApiException;
import org.killbill.billing.util.api.AuditLevel;
import org.killbill.billing.util.audit.AuditLogWithHistory;
import org.killbill.billing.util.entity.dao.MockEntityDaoBase;
import org.killbill.commons.utils.collect.MultiValueHashMap;
import org.killbill.commons.utils.collect.MultiValueMap;

public class MockBlockingStateDao extends MockEntityDaoBase<BlockingStateModelDao, BlockingState, EntitlementApiException> implements BlockingStateDao {

    private final MultiValueMap<UUID, BlockingState> blockingStates = new MultiValueHashMap<>();
    private final MultiValueMap<Long, BlockingState> blockingStatesPerAccountRecordId = new MultiValueHashMap<>();

    // TODO This mock class should also check that events are past or present

    @Override
    public BlockingState getBlockingStateForService(final UUID blockableId, final BlockingStateType blockingStateType, final String serviceName, final InternalTenantContext context) {
        final List<BlockingState> states = blockingStates.get(blockableId);
        if (states == null) {
            return null;
        }
        final List<BlockingState> filtered = states
                .stream()
                .filter(input -> input.getService().equals(serviceName))
                .collect(Collectors.toUnmodifiableList());
        return filtered.isEmpty() ? null : filtered.get(filtered.size() - 1);
    }

    @Override
    public List<BlockingState> getBlockingState(final UUID blockableId, final BlockingStateType blockingStateType, final DateTime upToDate, final InternalTenantContext context) {
        final List<BlockingState> blockingStatesForId = blockingStates.get(blockableId);
        if (blockingStatesForId == null) {
            return new ArrayList<BlockingState>();
        }

        final Map<String, BlockingState> tmp = new HashMap<>();
        for (final BlockingState cur : blockingStatesForId) {
            final BlockingState curStateForService = tmp.get(cur.getService());
            if (curStateForService == null || curStateForService.getEffectiveDate().compareTo(cur.getEffectiveDate()) < 0) {
                tmp.put(cur.getService(), cur);
            }
        }
        return new ArrayList<BlockingState>(tmp.values());
    }

    @Override
    public List<BlockingState> getBlockingAllForAccountRecordId(final VersionedCatalog catalog, final InternalTenantContext context) {
        return Objects.requireNonNullElse(blockingStatesPerAccountRecordId.get(context.getAccountRecordId()), Collections.emptyList());
    }

    @Override
    public List<BlockingState> getBlockingActiveForAccount(final VersionedCatalog catalog, @Nullable final LocalDate cutoffDt, final InternalTenantContext context) {
        return Objects.requireNonNullElse(blockingStatesPerAccountRecordId.get(context.getAccountRecordId()), Collections.emptyList());
    }

    @Override
    public List<BlockingState> getByBlockingIds(final Iterable<UUID> blockableIds, final boolean includeDeletedEvents, final InternalTenantContext context) {
        final List<BlockingState> result = new ArrayList<>();
        for (final UUID cur : blockableIds) {
            final List<BlockingState> objs = blockingStates.get(cur);
            if (objs != null && !objs.isEmpty()) {
                result.addAll(objs);
            }

        }
        return result;
    }

    @Override
    public synchronized void setBlockingStatesAndPostBlockingTransitionEvent(final Map<BlockingState, Optional<UUID>> states, final InternalCallContext context) {
        for (final BlockingState state : states.keySet()) {
            if (blockingStates.get(state.getBlockedId()) == null) {
                blockingStates.put(state.getBlockedId(), new ArrayList<>());
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

    @Override
    public List<AuditLogWithHistory> getBlockingStateAuditLogsWithHistoryForId(final UUID blockableId, final AuditLevel auditLevel, final InternalTenantContext context) {
        return null;
    }

    public synchronized void clear() {
        blockingStates.clear();
        blockingStatesPerAccountRecordId.clear();
    }
}
