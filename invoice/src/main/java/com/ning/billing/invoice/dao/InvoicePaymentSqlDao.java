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
import org.skife.jdbi.v2.sqlobject.mixins.Transactional;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;
import org.skife.jdbi.v2.sqlobject.stringtemplate.ExternalizedSqlViaStringTemplate3;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.invoice.model.DefaultInvoicePayment;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallContextBinder;
import com.ning.billing.util.dao.AuditSqlDao;
import com.ning.billing.util.dao.BinderBase;
import com.ning.billing.util.dao.MapperBase;
import com.ning.billing.util.dao.UuidMapper;
import com.ning.billing.util.entity.dao.EntitySqlDao;

@ExternalizedSqlViaStringTemplate3
@RegisterMapper(InvoicePaymentSqlDao.InvoicePaymentMapper.class)
public interface InvoicePaymentSqlDao extends EntitySqlDao<InvoicePayment>, Transactional<InvoicePaymentSqlDao>, AuditSqlDao, Transmogrifier {
    @SqlQuery
    List<Long> getRecordIds(@Bind("invoiceId") final String invoiceId);

    @SqlQuery
    public InvoicePayment getByPaymentAttemptId(@Bind("paymentAttempt") final String paymentAttemptId);

    @SqlQuery
    public List<InvoicePayment> get();

    @SqlUpdate
    public void create(@InvoicePaymentBinder final InvoicePayment invoicePayment,
                       @CallContextBinder final CallContext context);

    @SqlBatch(transactional = false)
    void batchCreateFromTransaction(@InvoicePaymentBinder final List<InvoicePayment> items,
                                    @CallContextBinder final CallContext context);

    @SqlQuery
    public List<InvoicePayment> getPaymentsForInvoice(@Bind("invoiceId") final String invoiceId);

    @SqlQuery
    InvoicePayment getInvoicePayment(@Bind("paymentAttemptId") final UUID paymentAttemptId);

    @SqlUpdate
    void notifyOfPaymentAttempt(@InvoicePaymentBinder final InvoicePayment invoicePayment,
                                @CallContextBinder final CallContext context);

    @SqlQuery
    BigDecimal getRemainingAmountPaid(@Bind("invoicePaymentId") final String invoicePaymentId);

    @SqlQuery
    @RegisterMapper(UuidMapper.class)
    UUID getAccountIdFromInvoicePaymentId(@Bind("invoicePaymentId") final String invoicePaymentId);

    @SqlQuery
    List<InvoicePayment> getChargeBacksByAccountId(@Bind("accountId") final String accountId);

    @SqlQuery
    List<InvoicePayment> getChargebacksByAttemptPaymentId(@Bind("paymentAttemptId") final String paymentAttemptId);

    public static class InvoicePaymentMapper extends MapperBase implements ResultSetMapper<InvoicePayment> {
        @Override
        public InvoicePayment map(final int index, final ResultSet result, final StatementContext context) throws SQLException {
            final UUID id = getUUID(result, "id");
            final UUID paymentAttemptId = getUUID(result, "payment_attempt_id");
            final UUID invoiceId = getUUID(result, "invoice_id");
            final DateTime paymentAttemptDate = getDate(result, "payment_attempt_date");
            final BigDecimal amount = result.getBigDecimal("amount");
            final String currencyString = result.getString("currency");
            final Currency currency = (currencyString == null) ? null : Currency.valueOf(currencyString);
            final UUID reversedInvoicePaymentId = getUUID(result, "reversed_invoice_Payment_id");

            return new DefaultInvoicePayment(id, paymentAttemptId, invoiceId, paymentAttemptDate,
                                             amount, currency, reversedInvoicePaymentId);
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
                        q.bind("invoiceId", payment.getInvoiceId().toString());
                        q.bind("paymentAttemptId", uuidToString(payment.getPaymentAttemptId()));
                        q.bind("paymentAttemptDate", payment.getPaymentAttemptDate().toDate());
                        q.bind("amount", payment.getAmount());
                        final Currency currency = payment.getCurrency();
                        q.bind("currency", (currency == null) ? null : currency.toString());
                        q.bind("reversedInvoicePaymentId", uuidToString(payment.getReversedInvoicePaymentId()));
                    }
                };
            }
        }
    }
}
