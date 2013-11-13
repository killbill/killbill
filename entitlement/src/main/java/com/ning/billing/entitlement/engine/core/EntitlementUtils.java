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

package com.ning.billing.entitlement.engine.core;

import java.io.IOException;
import java.util.UUID;

import javax.inject.Inject;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.bus.api.BusEvent;
import com.ning.billing.bus.api.PersistentBus;
import com.ning.billing.bus.api.PersistentBus.EventBusException;
import com.ning.billing.callcontext.InternalCallContext;
import com.ning.billing.clock.Clock;
import com.ning.billing.entitlement.DefaultEntitlementService;
import com.ning.billing.entitlement.api.BlockingApiException;
import com.ning.billing.entitlement.api.BlockingState;
import com.ning.billing.entitlement.api.BlockingStateType;
import com.ning.billing.entitlement.api.DefaultBlockingTransitionInternalEvent;
import com.ning.billing.entitlement.block.BlockingChecker;
import com.ning.billing.entitlement.block.BlockingChecker.BlockingAggregator;
import com.ning.billing.entitlement.dao.BlockingStateDao;
import com.ning.billing.notificationq.api.NotificationEvent;
import com.ning.billing.notificationq.api.NotificationQueue;
import com.ning.billing.notificationq.api.NotificationQueueService;
import com.ning.billing.notificationq.api.NotificationQueueService.NoSuchNotificationQueue;

public class EntitlementUtils {

    private static final Logger log = LoggerFactory.getLogger(EntitlementUtils.class);

    private final BlockingStateDao dao;
    private final BlockingChecker blockingChecker;
    private final PersistentBus eventBus;
    private final Clock clock;
    protected final NotificationQueueService notificationQueueService;

    @Inject
    public EntitlementUtils(final BlockingStateDao dao, final BlockingChecker blockingChecker,
                            final PersistentBus eventBus, final Clock clock,
                            final NotificationQueueService notificationQueueService) {
        this.dao = dao;
        this.blockingChecker = blockingChecker;
        this.eventBus = eventBus;
        this.clock = clock;
        this.notificationQueueService = notificationQueueService;
    }

    /**
     * Wrapper around BlockingStateDao#setBlockingState which will send an event on the bus if needed
     *
     * @param state   new state to store
     * @param context call context
     */
    public void setBlockingStateAndPostBlockingTransitionEvent(final BlockingState state, final InternalCallContext context) {
        final BlockingAggregator previousState = getBlockingStateFor(state.getBlockedId(), state.getType(), context);

        dao.setBlockingState(state, clock, context);

        final BlockingAggregator currentState = getBlockingStateFor(state.getBlockedId(), state.getType(), context);
        if (previousState != null && currentState != null) {
            postBlockingTransitionEvent(state.getId(), state.getEffectiveDate(), state.getBlockedId(), state.getType(), previousState, currentState, context);
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

    private void postBlockingTransitionEvent(final UUID blockingStateId, final DateTime effectiveDate, final UUID blockableId, final BlockingStateType type,
                                             final BlockingAggregator previousState, final BlockingAggregator currentState,
                                             final InternalCallContext context) {
        final boolean isTransitionToBlockedBilling = !previousState.isBlockBilling() && currentState.isBlockBilling();
        final boolean isTransitionToUnblockedBilling = previousState.isBlockBilling() && !currentState.isBlockBilling();

        final boolean isTransitionToBlockedEntitlement = !previousState.isBlockEntitlement() && currentState.isBlockEntitlement();
        final boolean isTransitionToUnblockedEntitlement = previousState.isBlockEntitlement() && !currentState.isBlockEntitlement();

        if (effectiveDate.compareTo(clock.getUTCNow()) > 0) {
            // Add notification entry to send the bus event at the effective date
            final NotificationEvent notificationEvent = new BlockingTransitionNotificationKey(blockingStateId, blockableId, type,
                                                                                              isTransitionToBlockedBilling, isTransitionToUnblockedBilling,
                                                                                              isTransitionToBlockedEntitlement, isTransitionToUnblockedEntitlement);
            recordFutureNotification(effectiveDate, notificationEvent, context);
        } else {
            // TODO Do we want to send a DefaultEffectiveEntitlementEvent for entitlement specific blocking states?
            final BusEvent event = new DefaultBlockingTransitionInternalEvent(blockableId, type,
                                                                              isTransitionToBlockedBilling, isTransitionToUnblockedBilling,
                                                                              isTransitionToBlockedEntitlement, isTransitionToUnblockedEntitlement,
                                                                              context.getAccountRecordId(), context.getTenantRecordId(), context.getUserToken());

            postBusEvent(event);
        }
    }

    private void postBusEvent(final BusEvent event) {
        try {
            // TODO STEPH Ideally we would like to post from transaction when we inserted the new blocking state, but new state would have to be recalculated from transaction which is
            // difficult without the help of BlockingChecker -- which itself relies on dao. Other alternative is duplicating the logic, or refactoring the DAO to export higher level api.
            eventBus.post(event);
        } catch (EventBusException e) {
            log.warn("Failed to post event {}", e);
        }
    }

    private void recordFutureNotification(final DateTime effectiveDate,
                                          final NotificationEvent notificationEvent,
                                          final InternalCallContext context) {
        try {
            final NotificationQueue subscriptionEventQueue = notificationQueueService.getNotificationQueue(DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME,
                                                                                                           DefaultEntitlementService.NOTIFICATION_QUEUE_NAME);
            subscriptionEventQueue.recordFutureNotification(effectiveDate, notificationEvent, context.getUserToken(), context.getAccountRecordId(), context.getTenantRecordId());
        } catch (NoSuchNotificationQueue e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
