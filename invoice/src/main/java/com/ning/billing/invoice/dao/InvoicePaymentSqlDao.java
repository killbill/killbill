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

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.util.dao.AuditSqlDao;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallContextBinder;
import com.ning.billing.util.dao.MapperBase;
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

import com.ning.billing.invoice.api.InvoicePayment;

@ExternalizedSqlViaStringTemplate3
@RegisterMapper(InvoicePaymentSqlDao.InvoicePaymentMapper.class)
public interface InvoicePaymentSqlDao extends Transactional<InvoicePaymentSqlDao>, AuditSqlDao, Transmogrifier {
    @SqlQuery
    public InvoicePayment getByPaymentAttemptId(@Bind("paymentAttempt") final String paymentAttemptId);

    @SqlQuery
    public List<InvoicePayment> get();

    @SqlUpdate
    public void create(@InvoicePaymentBinder final InvoicePayment invoicePayment,
                       @CallContextBinder final CallContext context);

    @SqlBatch(transactional=false)
    void batchCreateFromTransaction(@InvoicePaymentBinder final List<InvoicePayment> items,
                                    @CallContextBinder final CallContext context);

    @SqlQuery
    public List<InvoicePayment> getPaymentsForInvoice(@Bind("invoiceId") final String invoiceId);

    @SqlQuery
    InvoicePayment getInvoicePayment(@Bind("paymentAttemptId") final UUID paymentAttemptId);

    @SqlUpdate
    void notifyOfPaymentAttempt(@InvoicePaymentBinder final InvoicePayment invoicePayment,
                                @CallContextBinder final CallContext context);



    public static class InvoicePaymentMapper extends MapperBase implements ResultSetMapper<InvoicePayment> {
        @Override
        public InvoicePayment map(int index, ResultSet result, StatementContext context) throws SQLException {
            final UUID id = UUID.fromString(result.getString("id"));
            final UUID paymentAttemptId = UUID.fromString(result.getString("payment_attempt_id"));
            final UUID invoiceId = UUID.fromString(result.getString("invoice_id"));
            final DateTime paymentAttemptDate = getDate(result, "payment_attempt_date");
            final BigDecimal amount = result.getBigDecimal("amount");
            final String currencyString = result.getString("currency");
            final Currency currency = (currencyString == null) ? null : Currency.valueOf(currencyString);

            return new InvoicePayment() {
                @Override
                public UUID getId() {
                    return id;
                }
                @Override
                public UUID getPaymentAttemptId() {
                    return paymentAttemptId;
                }
                @Override
                public UUID getInvoiceId() {
                    return invoiceId;
                }
                @Override
                public DateTime getPaymentAttemptDate() {
                    return paymentAttemptDate;
                }
                @Override
                public BigDecimal getAmount() {
                    return amount;
                }
                @Override
                public Currency getCurrency() {
                    return currency;
                }
            };
        }
    }

    @BindingAnnotation(InvoicePaymentBinder.InvoicePaymentBinderFactory.class)
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.PARAMETER})
    public @interface InvoicePaymentBinder {
        public static class InvoicePaymentBinderFactory implements BinderFactory {
            @Override
            public Binder build(Annotation annotation) {
                return new Binder<InvoicePaymentBinder, InvoicePayment>() {
                    @Override
                    public void bind(SQLStatement q, InvoicePaymentBinder bind, InvoicePayment payment) {
                        q.bind("id", payment.getId().toString());
                        q.bind("invoiceId", payment.getInvoiceId().toString());
                        q.bind("paymentAttemptId", payment.getPaymentAttemptId().toString());
                        q.bind("paymentAttemptDate", payment.getPaymentAttemptDate().toDate());
                        q.bind("amount", payment.getAmount());
                        Currency currency = payment.getCurrency();
                        q.bind("currency", (currency == null) ? null : currency.toString());
                    }
                };
            }
        }
    }
}
