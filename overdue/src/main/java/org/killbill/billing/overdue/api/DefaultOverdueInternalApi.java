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

package org.killbill.billing.overdue.api;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.ImmutableAccountData;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.killbill.billing.junction.BlockingInternalApi;
import org.killbill.billing.overdue.OverdueInternalApi;
import org.killbill.billing.overdue.OverdueService;
import org.killbill.billing.overdue.caching.OverdueConfigCache;
import org.killbill.billing.overdue.config.DefaultOverdueConfig;
import org.killbill.billing.overdue.config.api.BillingState;
import org.killbill.billing.overdue.config.api.OverdueException;
import org.killbill.billing.overdue.config.api.OverdueStateSet;
import org.killbill.billing.overdue.wrapper.OverdueWrapper;
import org.killbill.billing.overdue.wrapper.OverdueWrapperFactory;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;

public class DefaultOverdueInternalApi implements OverdueInternalApi {

    Logger log = LoggerFactory.getLogger(DefaultOverdueInternalApi.class);

    private final OverdueWrapperFactory factory;
    private final BlockingInternalApi accessApi;
    private final InternalCallContextFactory internalCallContextFactory;
    private final OverdueConfigCache overdueConfigCache;

    @Inject
    public DefaultOverdueInternalApi(final OverdueWrapperFactory factory,
                                     final BlockingInternalApi accessApi,
                                     final OverdueConfigCache overdueConfigCache,
                                     final InternalCallContextFactory internalCallContextFactory) {
        this.factory = factory;
        this.accessApi = accessApi;
        this.overdueConfigCache = overdueConfigCache;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @SuppressWarnings("unchecked")
    @Override
    public OverdueState getOverdueStateFor(final ImmutableAccountData overdueable, final TenantContext context) throws OverdueException {
        try {
            final InternalTenantContext internalTenantContext = internalCallContextFactory.createInternalTenantContext(context);
            final String stateName = accessApi.getBlockingStateForService(overdueable.getId(), BlockingStateType.ACCOUNT, OverdueService.OVERDUE_SERVICE_NAME, internalCallContextFactory.createInternalTenantContext(context)).getStateName();
            final OverdueConfig overdueConfig = overdueConfigCache.getOverdueConfig(internalTenantContext);
            final OverdueStateSet states = ((DefaultOverdueConfig) overdueConfig).getOverdueStatesAccount();
            return states.findState(stateName);
        } catch (OverdueApiException e) {
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
    public OverdueState refreshOverdueStateFor(final ImmutableAccountData blockable, final CallContext context) throws OverdueException, OverdueApiException {
        log.info("Refresh of blockable {} ({}) requested", blockable.getId(), blockable.getClass());
        final InternalCallContext internalCallContext = createInternalCallContext(blockable, context);
        final OverdueWrapper wrapper = factory.createOverdueWrapperFor(blockable, internalCallContext);
        return wrapper.refresh(internalCallContext);
    }

    private InternalCallContext createInternalCallContext(final ImmutableAccountData blockable, final CallContext context) {
        return internalCallContextFactory.createInternalCallContext(blockable.getId(), ObjectType.ACCOUNT, context);
    }

    @Override
    public void setOverrideBillingStateForAccount(final ImmutableAccountData overdueable, final BillingState state, final CallContext context) {
        throw new UnsupportedOperationException();
    }
}
