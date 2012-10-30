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

import java.util.List;

import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.sqlobject.mixins.Transactional;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;
import org.skife.jdbi.v2.sqlobject.stringtemplate.ExternalizedSqlViaStringTemplate3;

import com.ning.billing.analytics.model.BusinessInvoiceModelDao;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.callcontext.InternalTenantContextBinder;

@ExternalizedSqlViaStringTemplate3()
@RegisterMapper(BusinessInvoiceMapper.class)
public interface BusinessInvoiceSqlDao extends Transactional<BusinessInvoiceSqlDao>, Transmogrifier {

    @SqlQuery
    BusinessInvoiceModelDao getInvoice(@Bind("invoice_id") final String invoiceId,
                               @InternalTenantContextBinder final InternalTenantContext context);

    @SqlQuery
    List<BusinessInvoiceModelDao> getInvoicesForAccount(@Bind("account_id") final String accountId,
                                                @InternalTenantContextBinder final InternalTenantContext context);

    @SqlQuery
    List<BusinessInvoiceModelDao> getInvoicesForAccountByKey(@Bind("account_key") final String accountKey,
                                                     @InternalTenantContextBinder final InternalTenantContext context);

    @SqlUpdate
    int createInvoice(@BusinessInvoiceBinder final BusinessInvoiceModelDao invoice,
                      @InternalTenantContextBinder final InternalCallContext context);

    @SqlUpdate
    int deleteInvoice(@Bind("invoice_id") final String invoiceId,
                      @InternalTenantContextBinder final InternalCallContext context);

    @SqlUpdate
    void deleteInvoicesForAccount(@Bind("account_id") final String accountId,
                                  @InternalTenantContextBinder final InternalCallContext context);

    @SqlUpdate
    void test(@InternalTenantContextBinder final InternalTenantContext context);
}
