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

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.BindBean;
import org.skife.jdbi.v2.sqlobject.SqlBatch;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.InvoicePayment.InvoicePaymentType;
import com.ning.billing.invoice.dao.InvoicePaymentSqlDao.InvoicePaymentModelDaoMapper;
import com.ning.billing.util.audit.ChangeType;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.dao.MapperBase;
import com.ning.billing.util.dao.UuidMapper;
import com.ning.billing.util.entity.dao.Audited;
import com.ning.billing.util.entity.dao.EntitySqlDao;
import com.ning.billing.util.entity.dao.EntitySqlDaoStringTemplate;

@EntitySqlDaoStringTemplate
@RegisterMapper(InvoicePaymentModelDaoMapper.class)
public interface InvoicePaymentSqlDao extends EntitySqlDao<InvoicePaymentModelDao> {

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

    public static class InvoicePaymentModelDaoMapper extends MapperBase implements ResultSetMapper<InvoicePaymentModelDao> {

        @Override
        public InvoicePaymentModelDao map(final int index, final ResultSet result, final StatementContext context) throws SQLException {
            final UUID id = getUUID(result, "id");
            final InvoicePaymentType type = InvoicePaymentType.valueOf(result.getString("type"));
            final UUID paymentId = getUUID(result, "payment_id");
            final UUID invoiceId = getUUID(result, "invoice_id");
            final DateTime paymentDate = getDateTime(result, "payment_date");
            final BigDecimal amount = result.getBigDecimal("amount");
            final String currencyString = result.getString("currency");
            final Currency currency = (currencyString == null) ? null : Currency.valueOf(currencyString);
            final UUID paymentCookieId = getUUID(result, "payment_cookie_id");
            final UUID linkedInvoicePaymentId = getUUID(result, "linked_invoice_payment_id");
            final DateTime createdDate = getDateTime(result, "created_date");

            return new InvoicePaymentModelDao(id, createdDate, type, paymentId, invoiceId, paymentDate,
                                              amount, currency, paymentCookieId, linkedInvoicePaymentId);
        }
    }
}
