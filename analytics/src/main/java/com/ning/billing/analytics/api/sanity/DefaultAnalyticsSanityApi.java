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

package com.ning.billing.analytics.api.sanity;

import java.util.Collection;
import java.util.UUID;

import javax.inject.Inject;

import com.ning.billing.analytics.dao.AnalyticsSanityDao;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.callcontext.TenantContext;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

public class DefaultAnalyticsSanityApi implements AnalyticsSanityApi {

    private final AnalyticsSanityDao dao;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public DefaultAnalyticsSanityApi(final AnalyticsSanityDao dao,
                                     final InternalCallContextFactory internalCallContextFactory) {
        this.dao = dao;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Override
    public Collection<UUID> checkAnalyticsInSyncWithEntitlement(final TenantContext context) {
        final InternalTenantContext internalTenantContext = internalCallContextFactory.createInternalTenantContext(context);
        return dao.checkBstMatchesSubscriptionEvents(internalTenantContext);
    }

    @Override
    public Collection<UUID> checkAnalyticsInSyncWithInvoice(final TenantContext context) {
        final InternalTenantContext internalTenantContext = internalCallContextFactory.createInternalTenantContext(context);
        return dao.checkBiiMatchesInvoiceItems(internalTenantContext);
    }

    @Override
    public Collection<UUID> checkAnalyticsInSyncWithPayment(final TenantContext context) {
        final InternalTenantContext internalTenantContext = internalCallContextFactory.createInternalTenantContext(context);
        final Collection<UUID> check1 = dao.checkBipMatchesInvoicePayments(internalTenantContext);
        final Collection<UUID> check2 = dao.checkBinAmountPaidMatchesInvoicePayments(internalTenantContext);
        final Collection<UUID> check3 = dao.checkBinAmountChargedMatchesInvoicePayments(internalTenantContext);

        return ImmutableSet.<UUID>copyOf(Iterables.<UUID>concat(check1, check2, check3));
    }

    @Override
    public Collection<UUID> checkAnalyticsInSyncWithTag(final TenantContext context) {
        final InternalTenantContext internalTenantContext = internalCallContextFactory.createInternalTenantContext(context);
        return dao.checkBacTagsMatchesTags(internalTenantContext);
    }

    @Override
    public Collection<UUID> checkAnalyticsConsistency(final TenantContext context) {
        final InternalTenantContext internalTenantContext = internalCallContextFactory.createInternalTenantContext(context);
        final Collection<UUID> check1 = dao.checkBinBiiBalanceConsistency(internalTenantContext);
        final Collection<UUID> check2 = dao.checkBinBiiAmountCreditedConsistency(internalTenantContext);
        final Collection<UUID> check3 = dao.checkBacBinBiiConsistency(internalTenantContext);

        return ImmutableSet.<UUID>copyOf(Iterables.<UUID>concat(check1, check2, check3));
    }
}
