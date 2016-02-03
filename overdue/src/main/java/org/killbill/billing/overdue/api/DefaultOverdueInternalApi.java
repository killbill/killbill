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

package org.killbill.billing.overdue.api;

import java.util.UUID;

import javax.inject.Named;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.ImmutableAccountData;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.entitlement.api.BlockingState;
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.killbill.billing.junction.BlockingInternalApi;
import org.killbill.billing.overdue.OverdueInternalApi;
import org.killbill.billing.overdue.OverdueService;
import org.killbill.billing.overdue.caching.OverdueConfigCache;
import org.killbill.billing.overdue.config.DefaultOverdueConfig;
import org.killbill.billing.overdue.config.DefaultOverdueState;
import org.killbill.billing.overdue.config.api.BillingState;
import org.killbill.billing.overdue.config.api.OverdueException;
import org.killbill.billing.overdue.config.api.OverdueStateSet;
import org.killbill.billing.overdue.glue.DefaultOverdueModule;
import org.killbill.billing.overdue.notification.OverdueAsyncBusNotificationKey;
import org.killbill.billing.overdue.notification.OverdueAsyncBusNotificationKey.OverdueAsyncBusNotificationAction;
import org.killbill.billing.overdue.notification.OverdueAsyncBusNotifier;
import org.killbill.billing.overdue.notification.OverduePoster;
import org.killbill.billing.overdue.wrapper.OverdueWrapper;
import org.killbill.billing.overdue.wrapper.OverdueWrapperFactory;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.clock.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.inject.Inject;

public class DefaultOverdueInternalApi implements OverdueInternalApi {

    private static final Logger log = LoggerFactory.getLogger(DefaultOverdueInternalApi.class);

    private final OverdueWrapperFactory factory;
    private final BlockingInternalApi accessApi;
    private final Clock clock;
    private final OverduePoster asyncPoster;
    private final OverdueConfigCache overdueConfigCache;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public DefaultOverdueInternalApi(final OverdueWrapperFactory factory,
                                     final BlockingInternalApi accessApi,
                                     final Clock clock,
                                     @Named(DefaultOverdueModule.OVERDUE_NOTIFIER_ASYNC_BUS_NAMED) final OverduePoster asyncPoster,
                                     final OverdueConfigCache overdueConfigCache,
                                     final InternalCallContextFactory internalCallContextFactory) {
        this.factory = factory;
        this.accessApi = accessApi;
        this.clock = clock;
        this.asyncPoster = asyncPoster;
        this.overdueConfigCache = overdueConfigCache;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @SuppressWarnings("unchecked")
    @Override
    public OverdueState getOverdueStateFor(final ImmutableAccountData overdueable, final TenantContext context) throws OverdueException {
        try {
            final InternalTenantContext internalTenantContext = internalCallContextFactory.createInternalTenantContext(context);
            final BlockingState blockingStateForService = accessApi.getBlockingStateForService(overdueable.getId(), BlockingStateType.ACCOUNT, OverdueService.OVERDUE_SERVICE_NAME, internalCallContextFactory.createInternalTenantContext(context));
            final String stateName = blockingStateForService != null ? blockingStateForService.getStateName() : OverdueWrapper.CLEAR_STATE_NAME;
            final OverdueConfig overdueConfig = overdueConfigCache.getOverdueConfig(internalTenantContext);
            final OverdueStateSet states = ((DefaultOverdueConfig) overdueConfig).getOverdueStatesAccount();
            return states.findState(stateName);
        } catch (final OverdueApiException e) {
            throw new OverdueException(e, ErrorCode.OVERDUE_CAT_ERROR_ENCOUNTERED, overdueable.getId(), overdueable.getClass().getSimpleName());
        }
    }

    @Override
    public BillingState getBillingStateFor(final ImmutableAccountData overdueable, final TenantContext context) throws OverdueException {
        log.debug("Billing state of of {} requested", overdueable.getId());

        final InternalTenantContext internalTenantContext = internalCallContextFactory.createInternalTenantContext(context);
        final OverdueWrapper wrapper = factory.createOverdueWrapperFor(overdueable, internalTenantContext);
        return wrapper.billingState(internalTenantContext);
    }

    @Override
    public void scheduleOverdueRefresh(final UUID accountId, final InternalCallContext internalCallContext) {
        insertBusEventIntoNotificationQueue(accountId, OverdueAsyncBusNotificationAction.REFRESH, internalCallContext);
    }

    @Override
    public void scheduleOverdueClear(final UUID accountId, final InternalCallContext internalCallContext) {
        insertBusEventIntoNotificationQueue(accountId, OverdueAsyncBusNotificationAction.CLEAR, internalCallContext);
    }

    private void insertBusEventIntoNotificationQueue(final UUID accountId, final OverdueAsyncBusNotificationAction action, final InternalCallContext callContext) {
        final boolean shouldInsertNotification = shouldInsertNotification(callContext);

        if (shouldInsertNotification) {
            final OverdueAsyncBusNotificationKey notificationKey = new OverdueAsyncBusNotificationKey(accountId, action);
            asyncPoster.insertOverdueNotification(accountId, clock.getUTCNow(), OverdueAsyncBusNotifier.OVERDUE_ASYNC_BUS_NOTIFIER_QUEUE, notificationKey, callContext);
        }
    }

    // Optimization: don't bother running the Overdue machinery if it's disabled
    private boolean shouldInsertNotification(final InternalTenantContext internalTenantContext) {
        OverdueConfig overdueConfig;
        try {
            overdueConfig = overdueConfigCache.getOverdueConfig(internalTenantContext);
        } catch (final OverdueApiException e) {
            log.warn("Failed to extract overdue config for tenant " + internalTenantContext.getTenantRecordId());
            overdueConfig = null;
        }
        if (overdueConfig == null || overdueConfig.getOverdueStatesAccount() == null || overdueConfig.getOverdueStatesAccount().getStates() == null) {
            return false;
        }

        for (final DefaultOverdueState state : ((DefaultOverdueConfig) overdueConfig).getOverdueStatesAccount().getStates()) {
            if (state.getConditionEvaluation() != null) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void setOverrideBillingStateForAccount(final ImmutableAccountData overdueable, final BillingState state, final CallContext context) {
        throw new UnsupportedOperationException();
    }
}
