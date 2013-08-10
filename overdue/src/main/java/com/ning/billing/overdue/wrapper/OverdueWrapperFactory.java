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

package com.ning.billing.overdue.wrapper;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.ErrorCode;
import com.ning.billing.subscription.api.user.SubscriptionBaseApiException;
import com.ning.billing.subscription.api.user.SubscriptionBaseBundle;
import com.ning.billing.entitlement.api.Blockable;
import com.ning.billing.entitlement.api.Type;
import com.ning.billing.overdue.applicator.OverdueStateApplicator;
import com.ning.billing.overdue.calculator.BillingStateCalculatorBundle;
import com.ning.billing.overdue.config.DefaultOverdueState;
import com.ning.billing.overdue.config.DefaultOverdueStateSet;
import com.ning.billing.overdue.config.OverdueConfig;
import com.ning.billing.overdue.config.api.OverdueException;
import com.ning.billing.overdue.config.api.OverdueStateSet;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.clock.Clock;
import com.ning.billing.util.svcapi.subscription.SubscriptionBaseInternalApi;
import com.ning.billing.util.svcapi.junction.BlockingInternalApi;

import com.google.inject.Inject;

public class OverdueWrapperFactory {
    private static final Logger log = LoggerFactory.getLogger(OverdueWrapperFactory.class);

    private final SubscriptionBaseInternalApi subscriptionApi;
    private final BillingStateCalculatorBundle billingStateCalcuatorBundle;
    private final OverdueStateApplicator<SubscriptionBaseBundle> overdueStateApplicatorBundle;
    private final BlockingInternalApi api;
    private final Clock clock;
    private OverdueConfig config;

    @Inject
    public OverdueWrapperFactory(final BlockingInternalApi api, final Clock clock,
                                 final BillingStateCalculatorBundle billingStateCalcuatorBundle,
                                 final OverdueStateApplicator<SubscriptionBaseBundle> overdueStateApplicatorBundle,
                                 final SubscriptionBaseInternalApi subscriptionApi) {
        this.billingStateCalcuatorBundle = billingStateCalcuatorBundle;
        this.overdueStateApplicatorBundle = overdueStateApplicatorBundle;
        this.subscriptionApi = subscriptionApi;
        this.api = api;
        this.clock = clock;
    }

    @SuppressWarnings("unchecked")
    public <T extends Blockable> OverdueWrapper<T> createOverdueWrapperFor(final T blockable) throws OverdueException {

        if (blockable instanceof SubscriptionBaseBundle) {
            return (OverdueWrapper<T>) new OverdueWrapper<SubscriptionBaseBundle>((SubscriptionBaseBundle) blockable, api, getOverdueStateSetBundle(),
                                                                              clock, billingStateCalcuatorBundle, overdueStateApplicatorBundle);
        } else {
            throw new OverdueException(ErrorCode.OVERDUE_TYPE_NOT_SUPPORTED, blockable.getId(), blockable.getClass());
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends Blockable> OverdueWrapper<T> createOverdueWrapperFor(final Type type, final UUID id, final InternalTenantContext context) throws OverdueException {
        try {
            switch (type) {
                case SUBSCRIPTION_BUNDLE: {
                    final SubscriptionBaseBundle bundle = subscriptionApi.getBundleFromId(id, context);
                    return (OverdueWrapper<T>) new OverdueWrapper<SubscriptionBaseBundle>(bundle, api, getOverdueStateSetBundle(),
                                                                                      clock, billingStateCalcuatorBundle, overdueStateApplicatorBundle);
                }
                default: {
                    throw new OverdueException(ErrorCode.OVERDUE_TYPE_NOT_SUPPORTED, id, type);
                }

            }
        } catch (SubscriptionBaseApiException e) {
            throw new OverdueException(e);
        }
    }

    private OverdueStateSet<SubscriptionBaseBundle> getOverdueStateSetBundle() {
        if (config == null || config.getBundleStateSet() == null) {
            return new DefaultOverdueStateSet<SubscriptionBaseBundle>() {

                @SuppressWarnings("unchecked")
                @Override
                protected DefaultOverdueState<SubscriptionBaseBundle>[] getStates() {
                    return new DefaultOverdueState[0];
                }
            };
        } else {
            return config.getBundleStateSet();
        }
    }

    public void setOverdueConfig(final OverdueConfig config) {
        this.config = config;
    }

}
