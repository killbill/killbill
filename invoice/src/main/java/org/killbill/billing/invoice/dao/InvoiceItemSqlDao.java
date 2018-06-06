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

package org.killbill.billing.invoice.dao;

import java.math.BigDecimal;
import java.util.List;

import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.util.audit.ChangeType;
import org.killbill.billing.util.entity.dao.Audited;
import org.killbill.billing.util.entity.dao.EntitySqlDao;
import org.killbill.commons.jdbi.binder.SmartBindBean;
import org.killbill.commons.jdbi.template.KillBillSqlDaoStringTemplate;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;

@KillBillSqlDaoStringTemplate
public interface InvoiceItemSqlDao extends EntitySqlDao<InvoiceItemModelDao, InvoiceItem> {

    @SqlQuery
    List<InvoiceItemModelDao> getInvoiceItemsByInvoice(@Bind("invoiceId") final String invoiceId,
                                                       @SmartBindBean final InternalTenantContext context);
    @SqlQuery
    List<InvoiceItemModelDao> getInvoiceItemsBySubscription(@Bind("subscriptionId") final String subscriptionId,
                                                            @SmartBindBean final InternalTenantContext context);


    @SqlQuery
    List<InvoiceItemModelDao> getAdjustedOrRepairedInvoiceItemsByLinkedId(@Bind("linkedItemId") final String linkedItemId,
                                                            @SmartBindBean final InternalTenantContext context);

    @SqlUpdate
    @Audited(ChangeType.UPDATE)
    void updateItemFields(@Bind("id") String invoiceItemId,
                          @Bind("amount") BigDecimal amount,
                          @Bind("description") String description,
                          @Bind("itemDetails") String itemDetails,
                          @SmartBindBean final InternalCallContext context);

    @SqlQuery
    List<InvoiceItemModelDao> getInvoiceItemsByParentInvoice(@Bind("parentInvoiceId") final String parentInvoiceId,
                                                             @SmartBindBean final InternalTenantContext context);

    @SqlQuery
    BigDecimal getAccountCBA(@SmartBindBean final InternalTenantContext context);
}
