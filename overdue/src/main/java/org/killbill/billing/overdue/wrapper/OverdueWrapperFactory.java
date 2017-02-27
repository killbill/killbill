/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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

package org.killbill.billing.overdue.wrapper;

import java.util.UUID;

import org.joda.time.Period;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.junction.BlockingInternalApi;
import org.killbill.billing.overdue.api.OverdueApiException;
import org.killbill.billing.overdue.api.OverdueConfig;
import org.killbill.billing.overdue.applicator.OverdueStateApplicator;
import org.killbill.billing.overdue.caching.OverdueConfigCache;
import org.killbill.billing.overdue.calculator.BillingStateCalculator;
import org.killbill.billing.overdue.config.DefaultOverdueConfig;
import org.killbill.billing.overdue.config.DefaultOverdueState;
import org.killbill.billing.overdue.config.DefaultOverdueStateSet;
import org.killbill.billing.overdue.config.api.OverdueException;
import org.killbill.billing.overdue.config.api.OverdueStateSet;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.clock.Clock;
import org.killbill.commons.locker.GlobalLocker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;

public class OverdueWrapperFactory {

    private static final Logger log = LoggerFactory.getLogger(OverdueWrapperFactory.class);

    private final AccountInternalApi accountApi;
    private final BillingStateCalculator billingStateCalculator;
    private final OverdueStateApplicator overdueStateApplicator;
    private final BlockingInternalApi api;
    private final GlobalLocker locker;
    private final Clock clock;
    private final OverdueConfigCache overdueConfigCache;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public OverdueWrapperFactory(final BlockingInternalApi api,
                                 final GlobalLocker locker,
                                 final Clock clock,
                                 final BillingStateCalculator billingStateCalculator,
                                 final OverdueStateApplicator overdueStateApplicatorBundle,
                                 final OverdueConfigCache overdueConfigCache,
                                 final AccountInternalApi accountApi,
                                 final InternalCallContextFactory internalCallContextFactory) {
        this.billingStateCalculator = billingStateCalculator;
        this.overdueStateApplicator = overdueStateApplicatorBundle;
        this.accountApi = accountApi;
        this.api = api;
        this.locker = locker;
        this.clock = clock;
        this.overdueConfigCache = overdueConfigCache;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    public OverdueWrapper createOverdueWrapperFor(final Account blockable, final InternalTenantContext context) throws OverdueException {
        return new OverdueWrapper(blockable, api, getOverdueStateSet(context), locker, clock, billingStateCalculator, overdueStateApplicator, internalCallContextFactory);
    }

    public OverdueWrapper createOverdueWrapperFor(final UUID id, final InternalTenantContext context) throws OverdueException {
        try {
            final Account account = accountApi.getAccountById(id, context);
            return new OverdueWrapper(account, api, getOverdueStateSet(context), locker, clock, billingStateCalculator, overdueStateApplicator, internalCallContextFactory);
        } catch (final AccountApiException e) {
            throw new OverdueException(e);
        }
    }

    private OverdueStateSet getOverdueStateSet(final InternalTenantContext context) throws OverdueException {
        final OverdueConfig overdueConfig;
        try {
            overdueConfig = overdueConfigCache.getOverdueConfig(context);
            if (overdueConfig == null || overdueConfig.getOverdueStatesAccount() == null) {
                return new DefaultOverdueStateSet() {

                    @SuppressWarnings("unchecked")
                    @Override
                    public DefaultOverdueState[] getStates() {
                        return new DefaultOverdueState[0];
                    }

                    @Override
                    public Period getInitialReevaluationInterval() {
                        return null;
                    }
                };
            } else {
                return ((DefaultOverdueConfig) overdueConfig).getOverdueStatesAccount();
            }
        } catch (final OverdueApiException e) {
            throw new OverdueException(e);
        }
    }
}

