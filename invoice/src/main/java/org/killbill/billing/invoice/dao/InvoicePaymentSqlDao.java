/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.InvoicePayment;
import org.killbill.billing.util.entity.dao.EntitySqlDao;
import org.killbill.commons.jdbi.binder.SmartBindBean;
import org.killbill.commons.jdbi.template.KillBillSqlDaoStringTemplate;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;

@KillBillSqlDaoStringTemplate
public interface InvoicePaymentSqlDao extends EntitySqlDao<InvoicePaymentModelDao, InvoicePayment> {

    @SqlQuery
    public List<InvoicePaymentModelDao> getByPaymentId(@Bind("paymentId") final String paymentId,
                                                       @SmartBindBean final InternalTenantContext context);

    @SqlQuery
    public List<InvoicePaymentModelDao> getAllPaymentsForInvoiceIncludedInit(@Bind("invoiceId") final String invoiceId,
                                                                             @SmartBindBean final InternalTenantContext context);

    @SqlQuery
    List<InvoicePaymentModelDao> getInvoicePayments(@Bind("paymentId") final String paymentId,
                                                    @SmartBindBean final InternalTenantContext context);

    @SqlQuery
    InvoicePaymentModelDao getPaymentForCookieId(@Bind("paymentCookieId") final String paymentCookieId,
                                                 @SmartBindBean final InternalTenantContext context);

    @SqlQuery
    BigDecimal getRemainingAmountPaid(@Bind("invoicePaymentId") final String invoicePaymentId,
                                      @SmartBindBean final InternalTenantContext context);

    @SqlQuery
    UUID getAccountIdFromInvoicePaymentId(@Bind("invoicePaymentId") final String invoicePaymentId,
                                          @SmartBindBean final InternalTenantContext context);

    @SqlQuery
    List<InvoicePaymentModelDao> getChargeBacksByAccountId(@Bind("accountId") final String accountId,
                                                           @SmartBindBean final InternalTenantContext context);

    @SqlQuery
    List<InvoicePaymentModelDao> getChargebacksByPaymentId(@Bind("paymentId") final String paymentId,
                                                           @SmartBindBean final InternalTenantContext context);

    @SqlUpdate
    void updateAttempt(@Bind("recordId") Long recordId,
                       @Bind("paymentId") final String paymentId,
                       @Bind("paymentDate") final Date paymentDate,
                       @Bind("amount") final BigDecimal amount,
                       @Bind("currency") final Currency currency,
                       @Bind("processedCurrency") final Currency processedCurrency,
                       @Bind("paymentCookieId") final String paymentCookieId,
                       @Bind("linkedInvoicePaymentId") final String linkedInvoicePaymentId,
                       @Bind("success") final boolean success,
                       @SmartBindBean final InternalTenantContext context);
}
