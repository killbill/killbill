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

package com.ning.billing.invoice.dao;

import java.util.List;

import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.BindBean;
import org.skife.jdbi.v2.sqlobject.SqlQuery;

import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.entity.dao.EntitySqlDao;
import com.ning.billing.util.entity.dao.EntitySqlDaoStringTemplate;

@EntitySqlDaoStringTemplate
public interface InvoiceItemSqlDao extends EntitySqlDao<InvoiceItemModelDao> {

    @SqlQuery
    List<InvoiceItemModelDao> getInvoiceItemsByInvoice(@Bind("invoiceId") final String invoiceId,
                                                       @BindBean final InternalTenantContext context);

    @SqlQuery
    List<InvoiceItemModelDao> getInvoiceItemsByAccount(@Bind("accountId") final String accountId,
                                                       @BindBean final InternalTenantContext context);

    @SqlQuery
    List<InvoiceItemModelDao> getInvoiceItemsBySubscription(@Bind("subscriptionId") final String subscriptionId,
                                                            @BindBean final InternalTenantContext context);
}
