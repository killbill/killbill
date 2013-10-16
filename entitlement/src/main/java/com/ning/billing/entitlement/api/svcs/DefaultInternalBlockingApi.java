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

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.account.api.Account;
import com.ning.billing.bus.api.PersistentBus;
import com.ning.billing.bus.api.PersistentBus.EventBusException;
import com.ning.billing.callcontext.InternalCallContext;
import com.ning.billing.callcontext.InternalTenantContext;
import com.ning.billing.clock.Clock;
import com.ning.billing.entitlement.api.Blockable;
import com.ning.billing.entitlement.api.BlockingApiException;
import com.ning.billing.entitlement.api.BlockingState;
import com.ning.billing.entitlement.api.BlockingStateType;
import com.ning.billing.entitlement.api.DefaultBlockingTransitionInternalEvent;
import com.ning.billing.entitlement.block.BlockingChecker;
import com.ning.billing.entitlement.block.BlockingChecker.BlockingAggregator;
import com.ning.billing.entitlement.dao.BlockingStateDao;
import com.ning.billing.events.BlockingTransitionInternalEvent;
import com.ning.billing.junction.BlockingInternalApi;
import com.ning.billing.junction.DefaultBlockingState;

import com.google.inject.Inject;

public class DefaultInternalBlockingApi implements BlockingInternalApi {


    private static final Logger log = LoggerFactory.getLogger(DefaultInternalBlockingApi.class);

    private final BlockingStateDao dao;
    private final BlockingChecker blockingChecker;
    private final Clock clock;
    private final PersistentBus eventBus;

    @Inject
    public DefaultInternalBlockingApi(final BlockingStateDao dao, final BlockingChecker blockingChecker, final PersistentBus eventBus, final Clock clock) {
        this.dao = dao;
        this.clock = clock;
        this.blockingChecker = blockingChecker;
        this.eventBus = eventBus;
    }

    @Override
    public BlockingState getBlockingStateForService(final Blockable overdueable, final String serviceName, final InternalTenantContext context) {
        BlockingState state = dao.getBlockingStateForService(overdueable.getId(), serviceName, context);
        if (state == null) {
            state = DefaultBlockingState.getClearState(getBlockingStateType(overdueable), serviceName, clock);
        }
        return state;
    }

    @Override
    public BlockingState getBlockingStateForService(final UUID overdueableId, final String serviceName, final InternalTenantContext context) {
        return dao.getBlockingStateForService(overdueableId, serviceName, context);
    }

    @Override
    public List<BlockingState> getBlockingHistoryForService(final Blockable overdueable, final String serviceName, final InternalTenantContext context) {
        return dao.getBlockingHistoryForService(overdueable.getId(), serviceName, context);
    }

    @Override
    public List<BlockingState> getBlockingHistoryForService(final UUID overdueableId, final String serviceName, final InternalTenantContext context) {
        return dao.getBlockingHistoryForService(overdueableId, serviceName, context);
    }

    @Override
    public List<BlockingState> getBlockingHistory(final Blockable overdueable, final InternalTenantContext context) {
        return dao.getBlockingHistory(overdueable.getId(), context);
    }

    @Override
    public List<BlockingState> getBlockingHistory(final UUID overdueableId, final InternalTenantContext context) {
        return dao.getBlockingHistory(overdueableId, context);
    }

    @Override
    public List<BlockingState> getBlockingAll(final Blockable overdueable, final InternalTenantContext context) {
        return dao.getBlockingAll(overdueable.getId(), context);
    }

    @Override
    public List<BlockingState> getBlockingAll(final UUID overdueableId, final InternalTenantContext context) {
        return dao.getBlockingAll(overdueableId, context);
    }

    @Override
    public void setBlockingState(final BlockingState state, final InternalCallContext context) {

        final BlockingAggregator previousState = getBlockingStateFor(state.getBlockedId(), state.getType(), context);

        dao.setBlockingState(state, clock, context);

        final BlockingAggregator currentState = getBlockingStateFor(state.getBlockedId(), state.getType(), context);
        if (previousState != null && currentState != null) {
            postBlockingTransitionEvent(state.getBlockedId(), state.getType(), previousState, currentState, context);
        }
    }

    private BlockingAggregator getBlockingStateFor(final UUID blockableId, final BlockingStateType type, final InternalCallContext context) {
        try {
            return blockingChecker.getBlockedStatus(blockableId, type, context);
        } catch (BlockingApiException e) {
            log.warn("Failed to retrieve blocking state for {} {}", blockableId, type);
            return null;
        }
    }

    private void postBlockingTransitionEvent(final UUID blockableId, final BlockingStateType type,
            final BlockingAggregator previousState, final BlockingAggregator currentState, final InternalCallContext context) {

        try {
            final boolean isTransitionToBlockedBilling = !previousState.isBlockBilling() && currentState.isBlockBilling();
            final boolean isTransitionToUnblockedBilling = previousState.isBlockBilling() && !currentState.isBlockBilling();

            final boolean isTransitionToBlockedEntitlement = !previousState.isBlockEntitlement() && currentState.isBlockEntitlement();
            final boolean isTransitionToUnblockedEntitlement = previousState.isBlockEntitlement() && !currentState.isBlockEntitlement();

            final BlockingTransitionInternalEvent event = new DefaultBlockingTransitionInternalEvent(blockableId, type,
                                                                                               isTransitionToBlockedBilling, isTransitionToUnblockedBilling,
                                                                                               isTransitionToBlockedEntitlement, isTransitionToUnblockedEntitlement,

                                                                                               context.getAccountRecordId(), context.getTenantRecordId(), context.getUserToken());

            // TODO
            // STEPH Ideally we would like to post from transaction when we inserted the new blocking state, but new state would have to be recalculated from transaction which is
            // difficult without the help of BlockingChecker -- which itself relies on dao. Other alternative is duplicating the logic, or refactoring the DAO to export higher level api.
            eventBus.post(event);
        } catch (EventBusException e) {
            log.warn("Failed to post event {}", e);
        }

    }


    BlockingStateType getBlockingStateType(final Blockable overdueable) {
        if (overdueable instanceof Account) {
            return BlockingStateType.ACCOUNT;
        }
        // STEPH this is here to verify there are no service trying to block on something different than ACCOUNT level
        // All the other entities
        throw new RuntimeException("Unexpected blockable type");
    }
}
