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

package org.killbill.billing.overdue.wrapper;

import java.util.UUID;

import org.joda.time.Period;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.clock.Clock;
import org.killbill.billing.overdue.applicator.OverdueStateApplicator;
import org.killbill.billing.overdue.calculator.BillingStateCalculator;
import org.killbill.billing.overdue.config.DefaultDuration;
import org.killbill.billing.overdue.config.DefaultOverdueState;
import org.killbill.billing.overdue.config.DefaultOverdueStateSet;
import org.killbill.billing.overdue.config.OverdueConfig;
import org.killbill.billing.overdue.config.api.OverdueException;
import org.killbill.billing.overdue.config.api.OverdueStateSet;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.junction.BlockingInternalApi;

import com.google.inject.Inject;

public class OverdueWrapperFactory {

    private static final Logger log = LoggerFactory.getLogger(OverdueWrapperFactory.class);

    private final AccountInternalApi accountApi;
    private final BillingStateCalculator billingStateCalculator;
    private final OverdueStateApplicator overdueStateApplicator;
    private final BlockingInternalApi api;
    private final Clock clock;
    private OverdueConfig config;

    @Inject
    public OverdueWrapperFactory(final BlockingInternalApi api, final Clock clock,
                                 final BillingStateCalculator billingStateCalculator,
                                 final OverdueStateApplicator overdueStateApplicatorBundle,
                                 final AccountInternalApi accountApi) {
        this.billingStateCalculator = billingStateCalculator;
        this.overdueStateApplicator = overdueStateApplicatorBundle;
        this.accountApi = accountApi;
        this.api = api;
        this.clock = clock;
    }

    @SuppressWarnings("unchecked")
    public OverdueWrapper createOverdueWrapperFor(final Account blockable) throws OverdueException {
        return (OverdueWrapper) new OverdueWrapper(blockable, api, getOverdueStateSet(),
                                                   clock, billingStateCalculator, overdueStateApplicator);
    }

    @SuppressWarnings("unchecked")
    public OverdueWrapper createOverdueWrapperFor(final UUID id, final InternalTenantContext context) throws OverdueException {

        try {
            Account account = accountApi.getAccountById(id, context);
            return new OverdueWrapper(account, api, getOverdueStateSet(),
                                      clock, billingStateCalculator, overdueStateApplicator);

        } catch (AccountApiException e) {
            throw new OverdueException(e);
        }
    }

    private OverdueStateSet getOverdueStateSet() {
        if (config == null || config.getStateSet() == null) {
            return new DefaultOverdueStateSet() {

                @SuppressWarnings("unchecked")
                @Override
                protected DefaultOverdueState[] getStates() {
                    return new DefaultOverdueState[0];
                }

                @Override
                public Period getInitialReevaluationInterval() {
                    return null;
                }
            };
        } else {
            return config.getStateSet();
        }
    }

    public void setOverdueConfig(final OverdueConfig config) {
        this.config = config;
    }

}
