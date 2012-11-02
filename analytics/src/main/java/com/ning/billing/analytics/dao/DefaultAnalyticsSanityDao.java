/*
 * Copyright 2010-2012 Ning, Inc.
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

package com.ning.billing.analytics.dao;

import java.util.Collection;
import java.util.UUID;

import javax.inject.Inject;

import org.skife.jdbi.v2.IDBI;

import com.ning.billing.util.callcontext.InternalTenantContext;

public class DefaultAnalyticsSanityDao implements AnalyticsSanityDao {

    private final AnalyticsSanitySqlDao sqlDao;

    @Inject
    public DefaultAnalyticsSanityDao(final IDBI dbi) {
        sqlDao = dbi.onDemand(AnalyticsSanitySqlDao.class);
    }

    @Override
    public Collection<UUID> checkBstMatchesSubscriptionEvents(final InternalTenantContext context) {
        return sqlDao.checkBstMatchesSubscriptionEvents(context);
    }

    @Override
    public Collection<UUID> checkBiiMatchesInvoiceItems(final InternalTenantContext context) {
        return sqlDao.checkBiiMatchesInvoiceItems(context);
    }

    @Override
    public Collection<UUID> checkBipMatchesInvoicePayments(final InternalTenantContext context) {
        return sqlDao.checkBipMatchesInvoicePayments(context);
    }

    @Override
    public Collection<UUID> checkBinAmountPaidMatchesInvoicePayments(final InternalTenantContext context) {
        return sqlDao.checkBinAmountPaidMatchesInvoicePayments(context);
    }

    @Override
    public Collection<UUID> checkBinAmountChargedMatchesInvoicePayments(final InternalTenantContext context) {
        return sqlDao.checkBinAmountChargedMatchesInvoicePayments(context);
    }

    @Override
    public Collection<UUID> checkBinBiiBalanceConsistency(final InternalTenantContext context) {
        return sqlDao.checkBinBiiBalanceConsistency(context);
    }

    @Override
    public Collection<UUID> checkBinBiiAmountCreditedConsistency(final InternalTenantContext context) {
        return sqlDao.checkBinBiiAmountCreditedConsistency(context);
    }

    @Override
    public Collection<UUID> checkBacBinBiiConsistency(final InternalTenantContext context) {
        return sqlDao.checkBacBinBiiConsistency(context);
    }

    @Override
    public Collection<UUID> checkBacTagsMatchesTags(final InternalTenantContext context) {
        return sqlDao.checkBacTagsMatchesTags(context);
    }
}
