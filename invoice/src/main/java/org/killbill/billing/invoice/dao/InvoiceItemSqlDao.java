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

package org.killbill.billing.invoice.dao;

import java.math.BigDecimal;
import java.util.List;

import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.util.audit.ChangeType;
import org.killbill.billing.util.entity.dao.Audited;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.BindBean;
import org.skife.jdbi.v2.sqlobject.SqlQuery;

import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.util.entity.dao.EntitySqlDao;
import org.killbill.billing.util.entity.dao.EntitySqlDaoStringTemplate;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;

@EntitySqlDaoStringTemplate
public interface InvoiceItemSqlDao extends EntitySqlDao<InvoiceItemModelDao, InvoiceItem> {

    @SqlQuery
    List<InvoiceItemModelDao> getInvoiceItemsByInvoice(@Bind("invoiceId") final String invoiceId,
                                                       @BindBean final InternalTenantContext context);

    @SqlQuery
    List<InvoiceItemModelDao> getInvoiceItemsBySubscription(@Bind("subscriptionId") final String subscriptionId,
                                                            @BindBean final InternalTenantContext context);


    @SqlQuery
    List<InvoiceItemModelDao> getAdjustedOrRepairedInvoiceItemsByLinkedId(@Bind("linkedItemId") final String linkedItemId,
                                                            @BindBean final InternalTenantContext context);

    @SqlUpdate
    @Audited(ChangeType.UPDATE)
    void updateAmount(@Bind("id") String invoiceItemId,
                      @Bind("amount")BigDecimal amount,
                      @BindBean final InternalCallContext context);

    @SqlQuery
    List<InvoiceItemModelDao> getInvoiceItemsByParentInvoice(@Bind("parentInvoiceId") final String parentInvoiceId,
                                                             @BindBean final InternalTenantContext context);
}
