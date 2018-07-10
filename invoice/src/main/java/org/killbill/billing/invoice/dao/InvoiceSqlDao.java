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

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.util.audit.ChangeType;
import org.killbill.billing.util.entity.dao.Audited;
import org.killbill.billing.util.entity.dao.EntitySqlDao;
import org.killbill.commons.jdbi.binder.SmartBindBean;
import org.killbill.commons.jdbi.template.KillBillSqlDaoStringTemplate;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.unstable.BindIn;

@KillBillSqlDaoStringTemplate
public interface InvoiceSqlDao extends EntitySqlDao<InvoiceModelDao, Invoice> {

    @SqlQuery
    List<InvoiceModelDao> getInvoicesBySubscription(@Bind("subscriptionId") final String subscriptionId,
                                                    @SmartBindBean final InternalTenantContext context);

    @SqlQuery
    UUID getInvoiceIdByPaymentId(@Bind("paymentId") final String paymentId,
                                 @SmartBindBean final InternalTenantContext context);

    @SqlQuery
    InvoiceModelDao getInvoiceByInvoiceItemId(@Bind("invoiceItemId") final String invoiceItemId,
                                              @SmartBindBean final InternalTenantContext context);
    @SqlUpdate
    @Audited(ChangeType.UPDATE)
    void updateStatus(@Bind("id") String invoiceId,
                      @Bind("status") String status,
                      @SmartBindBean final InternalCallContext context);


    @SqlQuery
    InvoiceModelDao getParentDraftInvoice(@Bind("accountId") final String parentAccountId,
                                          @SmartBindBean final InternalTenantContext context);

    @SqlQuery
    List<InvoiceModelDao> getByIds(@BindIn("ids") final Collection<String> invoiceIds,
                                   @SmartBindBean final InternalTenantContext context);


}

