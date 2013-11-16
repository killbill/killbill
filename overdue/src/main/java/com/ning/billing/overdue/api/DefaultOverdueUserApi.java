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

package com.ning.billing.overdue.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.ErrorCode;
import com.ning.billing.ObjectType;
import com.ning.billing.account.api.Account;
import com.ning.billing.callcontext.InternalCallContext;
import com.ning.billing.entitlement.api.BlockingStateType;
import com.ning.billing.junction.BlockingInternalApi;
import com.ning.billing.overdue.OverdueApiException;
import com.ning.billing.overdue.OverdueService;
import com.ning.billing.overdue.OverdueState;
import com.ning.billing.overdue.OverdueUserApi;
import com.ning.billing.overdue.config.OverdueConfig;
import com.ning.billing.overdue.config.api.BillingState;
import com.ning.billing.overdue.config.api.OverdueException;
import com.ning.billing.overdue.config.api.OverdueStateSet;
import com.ning.billing.overdue.wrapper.OverdueWrapper;
import com.ning.billing.overdue.wrapper.OverdueWrapperFactory;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.callcontext.TenantContext;

import com.google.inject.Inject;

public class DefaultOverdueUserApi implements OverdueUserApi {

    Logger log = LoggerFactory.getLogger(DefaultOverdueUserApi.class);

    private final OverdueWrapperFactory factory;
    private final BlockingInternalApi accessApi;
    private final InternalCallContextFactory internalCallContextFactory;

    private OverdueConfig overdueConfig;

    @Inject
    public DefaultOverdueUserApi(final OverdueWrapperFactory factory, final BlockingInternalApi accessApi, final InternalCallContextFactory internalCallContextFactory) {
        this.factory = factory;
        this.accessApi = accessApi;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @SuppressWarnings("unchecked")
    @Override
    public OverdueState getOverdueStateFor(final Account overdueable, final TenantContext context) throws OverdueException {
        try {
            final String stateName = accessApi.getBlockingStateForService(overdueable.getId(), BlockingStateType.ACCOUNT, OverdueService.OVERDUE_SERVICE_NAME, internalCallContextFactory.createInternalTenantContext(context)).getStateName();
            final OverdueStateSet states = overdueConfig.getStateSet();
            return states.findState(stateName);
        } catch (OverdueApiException e) {
            throw new OverdueException(e, ErrorCode.OVERDUE_CAT_ERROR_ENCOUNTERED, overdueable.getId(), overdueable.getClass().getSimpleName());
        }
    }

    @Override
    public BillingState getBillingStateFor(final Account overdueable, final TenantContext context) throws OverdueException {
        log.debug("Billing state of of {} requested", overdueable.getId());
        final OverdueWrapper wrapper = factory.createOverdueWrapperFor(overdueable);
        return wrapper.billingState(internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public OverdueState refreshOverdueStateFor(final Account blockable, final CallContext context) throws OverdueException, OverdueApiException {
        log.info("Refresh of blockable {} ({}) requested", blockable.getId(), blockable.getClass());
        final OverdueWrapper wrapper = factory.createOverdueWrapperFor(blockable);
        return wrapper.refresh(createInternalCallContext(blockable, context));
    }

    private InternalCallContext createInternalCallContext(final Account blockable, final CallContext context) {
        return internalCallContextFactory.createInternalCallContext(blockable.getId(), ObjectType.ACCOUNT, context);
    }

    @Override
    public void setOverrideBillingStateForAccount(final Account overdueable, final BillingState state, final CallContext context) {
        throw new UnsupportedOperationException();
    }

    public void setOverdueConfig(final OverdueConfig config) {
        this.overdueConfig = config;
    }
}
