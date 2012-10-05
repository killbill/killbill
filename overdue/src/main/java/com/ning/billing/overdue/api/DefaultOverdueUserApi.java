/*
 * Copyright 2010-2011 Ning, Inc.
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
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.junction.api.Blockable;
import com.ning.billing.junction.api.Blockable.Type;
import com.ning.billing.junction.api.BlockingApi;
import com.ning.billing.overdue.OverdueApiException;
import com.ning.billing.overdue.OverdueState;
import com.ning.billing.overdue.OverdueUserApi;
import com.ning.billing.overdue.config.OverdueConfig;
import com.ning.billing.overdue.config.api.BillingState;
import com.ning.billing.overdue.config.api.OverdueException;
import com.ning.billing.overdue.config.api.OverdueStateSet;
import com.ning.billing.overdue.wrapper.OverdueWrapper;
import com.ning.billing.overdue.wrapper.OverdueWrapperFactory;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.callcontext.TenantContext;
import com.ning.billing.util.dao.ObjectType;

import com.google.inject.Inject;

public class DefaultOverdueUserApi implements OverdueUserApi {

    Logger log = LoggerFactory.getLogger(DefaultOverdueUserApi.class);

    private final OverdueWrapperFactory factory;
    private final BlockingApi accessApi;
    private final InternalCallContextFactory internalCallContextFactory;

    private OverdueConfig overdueConfig;

    @Inject
    public DefaultOverdueUserApi(final OverdueWrapperFactory factory, final BlockingApi accessApi, final InternalCallContextFactory internalCallContextFactory) {
        this.factory = factory;
        this.accessApi = accessApi;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Blockable> OverdueState<T> getOverdueStateFor(final T overdueable, final TenantContext context) throws OverdueException {
        try {
            final String stateName = accessApi.getBlockingStateFor(overdueable, context).getStateName();
            final OverdueStateSet<SubscriptionBundle> states = overdueConfig.getBundleStateSet();
            return (OverdueState<T>) states.findState(stateName);
        } catch (OverdueApiException e) {
            throw new OverdueException(e, ErrorCode.OVERDUE_CAT_ERROR_ENCOUNTERED, overdueable.getId(), overdueable.getClass().getSimpleName());
        }
    }

    @Override
    public <T extends Blockable> BillingState<T> getBillingStateFor(final T overdueable, final TenantContext context) throws OverdueException {
        log.info(String.format("Billing state of of %s requested", overdueable.getId()));
        final OverdueWrapper<T> wrapper = factory.createOverdueWrapperFor(overdueable);
        return wrapper.billingState(internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public <T extends Blockable> OverdueState<T> refreshOverdueStateFor(final T blockable, final CallContext context) throws OverdueException, OverdueApiException {
        log.info(String.format("Refresh of %s requested", blockable.getId()));
        final OverdueWrapper<T> wrapper = factory.createOverdueWrapperFor(blockable);
        return wrapper.refresh(createInternalCallContext(blockable, context));
    }

    private <T extends Blockable> InternalCallContext createInternalCallContext(final T blockable, final CallContext context) {
        final ObjectType objectType = Type.getObjectType(blockable);
        return internalCallContextFactory.createInternalCallContext(blockable.getId(), objectType, context);
    }

    @Override
    public <T extends Blockable> void setOverrideBillingStateForAccount(final T overdueable, final BillingState<T> state, final CallContext context) {
        throw new UnsupportedOperationException();
    }

    public void setOverdueConfig(final OverdueConfig config) {
        this.overdueConfig = config;
    }
}
