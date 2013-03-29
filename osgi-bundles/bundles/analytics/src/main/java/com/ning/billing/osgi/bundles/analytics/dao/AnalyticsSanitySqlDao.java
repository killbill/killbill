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

package com.ning.billing.osgi.bundles.analytics.dao;

import java.util.Collection;
import java.util.UUID;

import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.sqlobject.mixins.Transactional;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;
import org.skife.jdbi.v2.sqlobject.stringtemplate.ExternalizedSqlViaStringTemplate3;

import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.callcontext.InternalTenantContextBinder;
import com.ning.billing.util.dao.UuidMapper;

@ExternalizedSqlViaStringTemplate3()
@RegisterMapper(UuidMapper.class)
public interface AnalyticsSanitySqlDao extends Transactional<AnalyticsSanitySqlDao>, Transmogrifier {

    @SqlQuery
    public Collection<UUID> checkBstMatchesSubscriptionEvents(@InternalTenantContextBinder final InternalTenantContext context);

    @SqlQuery
    public Collection<UUID> checkBiiMatchesInvoiceItems(@InternalTenantContextBinder final InternalTenantContext context);

    @SqlQuery
    public Collection<UUID> checkBipMatchesInvoicePayments(@InternalTenantContextBinder final InternalTenantContext context);

    @SqlQuery
    public Collection<UUID> checkBinAmountPaidMatchesInvoicePayments(@InternalTenantContextBinder final InternalTenantContext context);

    @SqlQuery
    public Collection<UUID> checkBinAmountChargedMatchesInvoicePayments(@InternalTenantContextBinder final InternalTenantContext context);

    @SqlQuery
    public Collection<UUID> checkBinBiiBalanceConsistency(@InternalTenantContextBinder final InternalTenantContext context);

    @SqlQuery
    public Collection<UUID> checkBinBiiAmountCreditedConsistency(@InternalTenantContextBinder final InternalTenantContext context);

    @SqlQuery
    public Collection<UUID> checkBacBinBiiConsistency(@InternalTenantContextBinder final InternalTenantContext context);

    @SqlQuery
    public Collection<UUID> checkBacTagsMatchesTags(@InternalTenantContextBinder final InternalTenantContext context);
}
