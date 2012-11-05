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

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.skife.jdbi.v2.SQLStatement;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.Binder;
import org.skife.jdbi.v2.sqlobject.BinderFactory;
import org.skife.jdbi.v2.sqlobject.BindingAnnotation;
import org.skife.jdbi.v2.sqlobject.SqlBatch;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.invoice.api.InvoicePayment.InvoicePaymentType;
import com.ning.billing.invoice.model.DefaultInvoicePayment;
import com.ning.billing.util.audit.ChangeType;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.callcontext.InternalTenantContextBinder;
import com.ning.billing.util.dao.BinderBase;
import com.ning.billing.util.dao.MapperBase;
import com.ning.billing.util.dao.UuidMapper;
import com.ning.billing.util.entity.dao.Audited;
import com.ning.billing.util.entity.dao.EntitySqlDao;
import com.ning.billing.util.entity.dao.EntitySqlDaoStringTemplate;

@EntitySqlDaoStringTemplate
@RegisterMapper(InvoicePaymentSqlDao.InvoicePaymentMapper.class)
public interface InvoicePaymentSqlDao extends EntitySqlDao<InvoicePayment> {

    @SqlQuery
    List<Long> getRecordIds(@Bind("invoiceId") final String invoiceId,
                            @InternalTenantContextBinder final InternalTenantContext context);

    @SqlQuery
    public InvoicePayment getByPaymentId(@Bind("paymentId") final String paymentId,
                                         @InternalTenantContextBinder final InternalTenantContext context);

    @Override
    @SqlQuery
    public List<InvoicePayment> get(@InternalTenantContextBinder final InternalTenantContext context);

    @Override
    @SqlUpdate
    @Audited(ChangeType.INSERT)
    public void create(@InvoicePaymentBinder final InvoicePayment invoicePayment,
                       @InternalTenantContextBinder final InternalCallContext context);

    @SqlBatch(transactional = false)
    @Audited(ChangeType.INSERT)
    void batchCreateFromTransaction(@InvoicePaymentBinder final List<InvoicePayment> items,
                                    @InternalTenantContextBinder final InternalCallContext context);

    @SqlQuery
    public List<InvoicePayment> getPaymentsForInvoice(@Bind("invoiceId") final String invoiceId,
                                                      @InternalTenantContextBinder final InternalTenantContext context);

    @SqlQuery
    List<InvoicePayment> getInvoicePayments(@Bind("paymentId") final String paymentId,
                                            @InternalTenantContextBinder final InternalTenantContext context);

    @SqlQuery
    InvoicePayment getPaymentsForCookieId(@Bind("paymentCookieId") final String paymentCookieId,
                                          @InternalTenantContextBinder final InternalTenantContext context);

    @SqlUpdate
    @Audited(ChangeType.UPDATE)
    void notifyOfPayment(@InvoicePaymentBinder final InvoicePayment invoicePayment,
                         @InternalTenantContextBinder final InternalCallContext context);

    @SqlQuery
    BigDecimal getRemainingAmountPaid(@Bind("invoicePaymentId") final String invoicePaymentId,
                                      @InternalTenantContextBinder final InternalTenantContext context);

    @SqlQuery
    @RegisterMapper(UuidMapper.class)
    UUID getAccountIdFromInvoicePaymentId(@Bind("invoicePaymentId") final String invoicePaymentId,
                                          @InternalTenantContextBinder final InternalTenantContext context);

    @SqlQuery
    List<InvoicePayment> getChargeBacksByAccountId(@Bind("accountId") final String accountId,
                                                   @InternalTenantContextBinder final InternalTenantContext context);

    @SqlQuery
    List<InvoicePayment> getChargebacksByPaymentId(@Bind("paymentId") final String paymentId,
                                                   @InternalTenantContextBinder final InternalTenantContext context);

    public static class InvoicePaymentMapper extends MapperBase implements ResultSetMapper<InvoicePayment> {

        @Override
        public InvoicePayment map(final int index, final ResultSet result, final StatementContext context) throws SQLException {
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

            return new DefaultInvoicePayment(id, createdDate, type, paymentId, invoiceId, paymentDate,
                                             amount, currency, paymentCookieId, linkedInvoicePaymentId);
        }
    }

    @BindingAnnotation(InvoicePaymentBinder.InvoicePaymentBinderFactory.class)
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.PARAMETER})
    public @interface InvoicePaymentBinder {

        public static class InvoicePaymentBinderFactory extends BinderBase implements BinderFactory {

            @Override
            public Binder build(final Annotation annotation) {
                return new Binder<InvoicePaymentBinder, InvoicePayment>() {
                    @Override
                    public void bind(final SQLStatement q, final InvoicePaymentBinder bind, final InvoicePayment payment) {
                        q.bind("id", payment.getId().toString());
                        q.bind("type", payment.getType().toString());
                        q.bind("invoiceId", payment.getInvoiceId().toString());
                        q.bind("paymentId", uuidToString(payment.getPaymentId()));
                        q.bind("paymentDate", payment.getPaymentDate().toDate());
                        q.bind("amount", payment.getAmount());
                        final Currency currency = payment.getCurrency();
                        q.bind("currency", (currency == null) ? null : currency.toString());
                        q.bind("paymentCookieId", uuidToString(payment.getPaymentCookieId()));
                        q.bind("linkedInvoicePaymentId", uuidToString(payment.getLinkedInvoicePaymentId()));
                    }
                };
            }
        }
    }
}
