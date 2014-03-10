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
import java.util.UUID;

import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.BindBean;
import org.skife.jdbi.v2.sqlobject.SqlBatch;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;

import org.killbill.billing.invoice.api.InvoicePayment;
import org.killbill.billing.util.audit.ChangeType;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.util.dao.UuidMapper;
import org.killbill.billing.util.entity.dao.Audited;
import org.killbill.billing.util.entity.dao.EntitySqlDao;
import org.killbill.billing.util.entity.dao.EntitySqlDaoStringTemplate;

@EntitySqlDaoStringTemplate
public interface InvoicePaymentSqlDao extends EntitySqlDao<InvoicePaymentModelDao, InvoicePayment> {

    @SqlQuery
    public InvoicePaymentModelDao getByPaymentId(@Bind("paymentId") final String paymentId,
                                                 @BindBean final InternalTenantContext context);

    @SqlBatch(transactional = false)
    @Audited(ChangeType.INSERT)
    void batchCreateFromTransaction(@BindBean final List<InvoicePaymentModelDao> items,
                                    @BindBean final InternalCallContext context);

    @SqlQuery
    public List<InvoicePaymentModelDao> getPaymentsForInvoice(@Bind("invoiceId") final String invoiceId,
                                                              @BindBean final InternalTenantContext context);

    @SqlQuery
    List<InvoicePaymentModelDao> getInvoicePayments(@Bind("paymentId") final String paymentId,
                                                    @BindBean final InternalTenantContext context);

    @SqlQuery
    InvoicePaymentModelDao getPaymentsForCookieId(@Bind("paymentCookieId") final String paymentCookieId,
                                                  @BindBean final InternalTenantContext context);

    @SqlQuery
    BigDecimal getRemainingAmountPaid(@Bind("invoicePaymentId") final String invoicePaymentId,
                                      @BindBean final InternalTenantContext context);

    @SqlQuery
    @RegisterMapper(UuidMapper.class)
    UUID getAccountIdFromInvoicePaymentId(@Bind("invoicePaymentId") final String invoicePaymentId,
                                          @BindBean final InternalTenantContext context);

    @SqlQuery
    List<InvoicePaymentModelDao> getChargeBacksByAccountId(@Bind("accountId") final String accountId,
                                                           @BindBean final InternalTenantContext context);

    @SqlQuery
    List<InvoicePaymentModelDao> getChargebacksByPaymentId(@Bind("paymentId") final String paymentId,
                                                           @BindBean final InternalTenantContext context);
}
