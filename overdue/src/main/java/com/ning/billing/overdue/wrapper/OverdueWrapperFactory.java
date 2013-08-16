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

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.clock.Clock;
import com.ning.billing.entitlement.api.Blockable;
import com.ning.billing.overdue.applicator.OverdueStateApplicator;
import com.ning.billing.overdue.calculator.BillingStateCalculator;
import com.ning.billing.overdue.config.DefaultOverdueState;
import com.ning.billing.overdue.config.DefaultOverdueStateSet;
import com.ning.billing.overdue.config.OverdueConfig;
import com.ning.billing.overdue.config.api.OverdueException;
import com.ning.billing.overdue.config.api.OverdueStateSet;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.svcapi.junction.BlockingInternalApi;

import com.google.inject.Inject;

public class OverdueWrapperFactory {

    private static final Logger log = LoggerFactory.getLogger(OverdueWrapperFactory.class);

    private final AccountUserApi accountUserApi;
    private final BillingStateCalculator billingStateCalculator;
    private final OverdueStateApplicator overdueStateApplicator;
    private final BlockingInternalApi api;
    private final Clock clock;
    private OverdueConfig config;

    @Inject
    public OverdueWrapperFactory(final BlockingInternalApi api, final Clock clock,
                                 final BillingStateCalculator billingStateCalculator,
                                 final OverdueStateApplicator overdueStateApplicatorBundle,
                                 final AccountUserApi accountUserApi) {
        this.billingStateCalculator = billingStateCalculator;
        this.overdueStateApplicator = overdueStateApplicatorBundle;
        this.accountUserApi = accountUserApi;
        this.api = api;
        this.clock = clock;
    }

    @SuppressWarnings("unchecked")
    public OverdueWrapper createOverdueWrapperFor(final Account blockable) throws OverdueException {
        return (OverdueWrapper) new OverdueWrapper(blockable, api, getOverdueStateSetBundle(),
                                                   clock, billingStateCalculator, overdueStateApplicator);
    }

    @SuppressWarnings("unchecked")
    public OverdueWrapper createOverdueWrapperFor(final UUID id, final InternalTenantContext context) throws OverdueException {

        try {
            Account account = accountUserApi.getAccountById(id, context.toTenantContext());
            return new OverdueWrapper(account, api, getOverdueStateSetBundle(),
                                      clock, billingStateCalculator, overdueStateApplicator);

        } catch (AccountApiException e) {
            throw new OverdueException(e);
        }
    }

    private OverdueStateSet getOverdueStateSetBundle() {
        if (config == null || config.getBundleStateSet() == null) {
            return new DefaultOverdueStateSet() {

                @SuppressWarnings("unchecked")
                @Override
                protected DefaultOverdueState[] getStates() {
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
