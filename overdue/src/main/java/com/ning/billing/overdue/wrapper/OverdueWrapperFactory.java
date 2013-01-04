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

package com.ning.billing.overdue.wrapper;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.ErrorCode;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.junction.api.Blockable;
import com.ning.billing.overdue.applicator.OverdueStateApplicator;
import com.ning.billing.overdue.calculator.BillingStateCalculatorBundle;
import com.ning.billing.overdue.config.DefaultOverdueState;
import com.ning.billing.overdue.config.DefaultOverdueStateSet;
import com.ning.billing.overdue.config.OverdueConfig;
import com.ning.billing.overdue.config.api.OverdueException;
import com.ning.billing.overdue.config.api.OverdueStateSet;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.svcapi.entitlement.EntitlementInternalApi;
import com.ning.billing.util.svcapi.junction.BlockingInternalApi;

import com.google.inject.Inject;

public class OverdueWrapperFactory {
    private static final Logger log = LoggerFactory.getLogger(OverdueWrapperFactory.class);

    private final EntitlementInternalApi entitlementApi;
    private final BillingStateCalculatorBundle billingStateCalcuatorBundle;
    private final OverdueStateApplicator<SubscriptionBundle> overdueStateApplicatorBundle;
    private final BlockingInternalApi api;
    private final Clock clock;
    private OverdueConfig config;

    @Inject
    public OverdueWrapperFactory(final BlockingInternalApi api, final Clock clock,
                                 final BillingStateCalculatorBundle billingStateCalcuatorBundle,
                                 final OverdueStateApplicator<SubscriptionBundle> overdueStateApplicatorBundle,
                                 final EntitlementInternalApi entitlementApi) {
        this.billingStateCalcuatorBundle = billingStateCalcuatorBundle;
        this.overdueStateApplicatorBundle = overdueStateApplicatorBundle;
        this.entitlementApi = entitlementApi;
        this.api = api;
        this.clock = clock;
    }

    @SuppressWarnings("unchecked")
    public <T extends Blockable> OverdueWrapper<T> createOverdueWrapperFor(final T blockable) throws OverdueException {

        if (blockable instanceof SubscriptionBundle) {
            return (OverdueWrapper<T>) new OverdueWrapper<SubscriptionBundle>((SubscriptionBundle) blockable, api, getOverdueStateSetBundle(),
                                                                              clock, billingStateCalcuatorBundle, overdueStateApplicatorBundle);
        } else {
            throw new OverdueException(ErrorCode.OVERDUE_TYPE_NOT_SUPPORTED, blockable.getId(), blockable.getClass());
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends Blockable> OverdueWrapper<T> createOverdueWrapperFor(final Blockable.Type type, final UUID id, final InternalTenantContext context) throws OverdueException {
        try {
            switch (type) {
                case SUBSCRIPTION_BUNDLE: {
                    final SubscriptionBundle bundle = entitlementApi.getBundleFromId(id, context);
                    return (OverdueWrapper<T>) new OverdueWrapper<SubscriptionBundle>(bundle, api, getOverdueStateSetBundle(),
                                                                                      clock, billingStateCalcuatorBundle, overdueStateApplicatorBundle);
                }
                default: {
                    throw new OverdueException(ErrorCode.OVERDUE_TYPE_NOT_SUPPORTED, id, type);
                }

            }
        } catch (EntitlementUserApiException e) {
            throw new OverdueException(e);
        }
    }

    private OverdueStateSet<SubscriptionBundle> getOverdueStateSetBundle() {
        if (config == null || config.getBundleStateSet() == null) {
            return new DefaultOverdueStateSet<SubscriptionBundle>() {

                @SuppressWarnings("unchecked")
                @Override
                protected DefaultOverdueState<SubscriptionBundle>[] getStates() {
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
