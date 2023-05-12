/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

package org.killbill.billing.entitlement.engine.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.entitlement.api.BlockingState;
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.killbill.billing.entitlement.api.DefaultEntitlementApi;
import org.killbill.billing.entitlement.dao.BlockingStateDao;
import org.killbill.billing.platform.api.KillbillService.KILLBILL_SERVICES;
import org.killbill.billing.subscription.api.SubscriptionBaseInternalApi;
import org.killbill.billing.subscription.api.user.SubscriptionBaseApiException;
import org.killbill.commons.utils.collect.Iterables;
import org.killbill.notificationq.api.NotificationQueueService;

public class EntitlementUtils {

    protected final NotificationQueueService notificationQueueService;

    private final BlockingStateDao dao;
    private final SubscriptionBaseInternalApi subscriptionBaseInternalApi;

    @Inject
    public EntitlementUtils(final BlockingStateDao dao,
                            final SubscriptionBaseInternalApi subscriptionBaseInternalApi,
                            final NotificationQueueService notificationQueueService) {
        this.dao = dao;
        this.subscriptionBaseInternalApi = subscriptionBaseInternalApi;
        this.notificationQueueService = notificationQueueService;
    }

    public void setBlockingStatesAndPostBlockingTransitionEvent(final Iterable<BlockingState> blockingStates, @Nullable final UUID bundleId, final InternalCallContext internalCallContext) {
        final Map<BlockingState, Optional<UUID>> states = new HashMap<>();
        final Optional<UUID> bundleIdOptional = Optional.ofNullable(bundleId);
        for (final BlockingState blockingState : blockingStates) {
            states.put(blockingState, bundleIdOptional);
        }
        dao.setBlockingStatesAndPostBlockingTransitionEvent(states, internalCallContext);
    }

    public void setBlockingStateAndPostBlockingTransitionEvent(final Map<BlockingState, UUID> blockingStates, final InternalCallContext internalCallContext) {
        final Map<BlockingState, Optional<UUID>> states = new HashMap<>();
        for (final Entry<BlockingState, UUID> entry : blockingStates.entrySet()) {
            states.put(entry.getKey(), Optional.ofNullable(entry.getValue()));
        }
        dao.setBlockingStatesAndPostBlockingTransitionEvent(states, internalCallContext);
    }

    public void setBlockingStateAndPostBlockingTransitionEvent(final BlockingState state, final InternalCallContext context) {
        UUID bundleId = null;
        // We only need the bundle id in case of subscriptions (at the account level, we don't need it and at the bundle level, we already have it)
        if (state.getType() == BlockingStateType.SUBSCRIPTION) {
            try {
                bundleId = subscriptionBaseInternalApi.getBundleIdFromSubscriptionId(state.getBlockedId(), context);
            } catch (final SubscriptionBaseApiException e) {
                throw new RuntimeException(e);
            }
        }
        dao.setBlockingStatesAndPostBlockingTransitionEvent(Map.<BlockingState, Optional<UUID>>of(state, Optional.ofNullable(bundleId)), context);
    }

    /**
     * @param externalKey   the bundle externalKey
     * @param tenantContext the context
     * @return the id of the first subscription (BASE or STANDALONE) that is still active for that key
     */
    public UUID getFirstActiveSubscriptionIdForKeyOrNull(final String externalKey, final InternalTenantContext tenantContext) {
        final Iterable<UUID> nonAddonUUIDs = subscriptionBaseInternalApi.getNonAOSubscriptionIdsForKey(externalKey, tenantContext);
        return Iterables.toStream(nonAddonUUIDs)
                        .filter(blockingStateIsNotNullOrNameNotEqualsENT_STATE_CANCELLED(tenantContext))
                        .findFirst().orElse(null);
    }

    private Predicate<UUID> blockingStateIsNotNullOrNameNotEqualsENT_STATE_CANCELLED(final InternalTenantContext tenantContext) {
        return input -> {
            final BlockingState state = dao.getBlockingStateForService(input, BlockingStateType.SUBSCRIPTION, KILLBILL_SERVICES.ENTITLEMENT_SERVICE.getServiceName(), tenantContext);
            return (state == null || !state.getStateName().equals(DefaultEntitlementApi.ENT_STATE_CANCELLED));
        };
    }
}
