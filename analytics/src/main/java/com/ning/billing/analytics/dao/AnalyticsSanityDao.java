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

import com.ning.billing.util.callcontext.InternalTenantContext;

public interface AnalyticsSanityDao {

    public Collection<UUID> checkBstMatchesSubscriptionEvents(InternalTenantContext context);

    public Collection<UUID> checkBiiMatchesInvoiceItems(InternalTenantContext context);

    public Collection<UUID> checkBipMatchesInvoicePayments(InternalTenantContext context);

    public Collection<UUID> checkBinAmountPaidMatchesInvoicePayments(InternalTenantContext context);

    public Collection<UUID> checkBinAmountChargedMatchesInvoicePayments(InternalTenantContext context);

    public Collection<UUID> checkBinBiiBalanceConsistency(InternalTenantContext context);

    public Collection<UUID> checkBinBiiAmountCreditedConsistency(InternalTenantContext context);

    public Collection<UUID> checkBacBinBiiConsistency(InternalTenantContext context);

    public Collection<UUID> checkBacTagsMatchesTags(InternalTenantContext context);
}
