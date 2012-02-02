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
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.skife.jdbi.v2.SQLStatement;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.Binder;
import org.skife.jdbi.v2.sqlobject.BinderFactory;
import org.skife.jdbi.v2.sqlobject.BindingAnnotation;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.sqlobject.mixins.CloseMe;
import org.skife.jdbi.v2.sqlobject.mixins.Transactional;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;
import org.skife.jdbi.v2.sqlobject.stringtemplate.ExternalizedSqlViaStringTemplate3;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.model.DefaultInvoice;
import com.ning.billing.payment.api.InvoicePayment;
import com.ning.billing.util.UuidMapper;
import com.ning.billing.util.entity.EntityDao;

@ExternalizedSqlViaStringTemplate3()
@RegisterMapper({UuidMapper.class, InvoiceSqlDao.InvoiceMapper.class})
public interface InvoiceSqlDao extends EntityDao<Invoice>, Transactional<InvoiceSqlDao>, Transmogrifier, CloseMe {
    @Override
    @SqlUpdate
    void create(@InvoiceBinder Invoice invoice);

    @Override
    @SqlUpdate
    void update(@InvoiceBinder Invoice invoice);

    @SqlQuery
    List<Invoice> getInvoicesByAccount(@Bind("accountId") final String accountId);

    @SqlQuery
    List<Invoice> getInvoicesBySubscription(@Bind("subscriptionId") final String subscriptionId);

    @SqlQuery
    String getInvoiceIdByPaymentAttemptId(@Bind("paymentAttemptId") final String paymentAttemptId);

    @SqlQuery
    List<UUID> getInvoicesForPayment(@Bind("targetDate") final Date targetDate,
                                     @Bind("numberOfDays") final int numberOfDays);

    @SqlQuery
    InvoicePayment getInvoicePayment(@Bind("paymentAttemptId") UUID paymentAttemptId);

    @SqlUpdate
    void notifyOfPaymentAttempt(@Bind(binder = InvoicePaymentBinder.class) InvoicePayment invoicePayment);
    
    @SqlQuery
    @RegisterMapper(BalanceMapper.class)
    BigDecimal getAccountBalance(@Bind("accountId") final String accountId);

    public static class BalanceMapper implements ResultSetMapper<BigDecimal> {
        @Override
        public BigDecimal map(final int index, final ResultSet result, final StatementContext context) throws SQLException {
            BigDecimal amount_invoiced = result.getBigDecimal("amount_invoiced");
            BigDecimal amount_paid = result.getBigDecimal("amount_paid");

            if (amount_invoiced == null) {
                amount_invoiced = BigDecimal.ZERO;
            }

            if (amount_paid == null) {
                amount_paid = BigDecimal.ZERO;
            }

            return amount_invoiced.subtract(amount_paid);
        };
    }

    @BindingAnnotation(InvoiceBinder.InvoiceBinderFactory.class)
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.PARAMETER})
    public @interface InvoiceBinder {
        public static class InvoiceBinderFactory implements BinderFactory {
            @Override
            public Binder<InvoiceBinder, Invoice> build(Annotation annotation) {
                return new Binder<InvoiceBinder, Invoice>() {
                    @Override
                    public void bind(@SuppressWarnings("rawtypes") SQLStatement q, InvoiceBinder bind, Invoice invoice) {
                        q.bind("id", invoice.getId().toString());
                        q.bind("accountId", invoice.getAccountId().toString());
                        q.bind("invoiceDate", invoice.getInvoiceDate().toDate());
                        q.bind("targetDate", invoice.getTargetDate().toDate());
                        q.bind("amountPaid", invoice.getAmountPaid());
                        q.bind("amountOutstanding", invoice.getAmountOutstanding());
                        DateTime last_payment_date = invoice.getLastPaymentAttempt();
                        q.bind("lastPaymentAttempt", last_payment_date == null ? null : last_payment_date.toDate());
                        q.bind("currency", invoice.getCurrency().toString());
                    }
                };
            }
        }
    }

    public static class InvoiceMapper implements ResultSetMapper<Invoice> {
        @Override
        public Invoice map(int index, ResultSet result, StatementContext context) throws SQLException {
            UUID id = UUID.fromString(result.getString("id"));
            UUID accountId = UUID.fromString(result.getString("account_id"));
            DateTime invoiceDate = new DateTime(result.getTimestamp("invoice_date"));
            DateTime targetDate = new DateTime(result.getTimestamp("target_date"));
            BigDecimal amountPaid = result.getBigDecimal("amount_paid");
            if (amountPaid == null) {
                amountPaid = BigDecimal.ZERO;
            }
            Timestamp lastPaymentAttemptTimeStamp = result.getTimestamp("last_payment_attempt");
            DateTime lastPaymentAttempt = lastPaymentAttemptTimeStamp == null ? null : new DateTime(lastPaymentAttemptTimeStamp);
            Currency currency = Currency.valueOf(result.getString("currency"));

            return new DefaultInvoice(id, accountId, invoiceDate, targetDate, currency, lastPaymentAttempt, amountPaid, new ArrayList<InvoiceItem>());
        }
    }

    @SqlUpdate
    void notifyFailedPayment(@Bind(binder = InvoicePaymentBinder.class) InvoicePayment invoicePayment);

    public static final class InvoicePaymentBinder implements Binder<Bind, InvoicePayment> {

        @Override
        public void bind(@SuppressWarnings("rawtypes") SQLStatement stmt, Bind bind, InvoicePayment invoicePayment) {
            stmt.bind("invoice_id", invoicePayment.getInvoiceId().toString());
            stmt.bind("amount", invoicePayment.getAmount());
            stmt.bind("currency", invoicePayment.getCurrency().toString());
            stmt.bind("payment_attempt_id", invoicePayment.getPaymentAttemptId().toString());
            stmt.bind("payment_attempt_date", invoicePayment.getPaymentAttemptDate() == null ? null : invoicePayment.getPaymentAttemptDate().toDate());
        }
    }


    public static class InvoicePaymentMapper implements ResultSetMapper<InvoicePayment> {

        private DateTime getDate(ResultSet rs, String fieldName) throws SQLException {
            final Timestamp resultStamp = rs.getTimestamp(fieldName);
            return rs.wasNull() ? null : new DateTime(resultStamp).toDateTime(DateTimeZone.UTC);
        }

        @Override
        public InvoicePayment map(int index, ResultSet rs, StatementContext ctx) throws SQLException {

            UUID invoiceId = UUID.fromString(rs.getString("invoice_id"));
            BigDecimal amount = rs.getBigDecimal("amount");
            Currency currency = Currency.valueOf(rs.getString("currency"));
            UUID paymentAttemptId = UUID.fromString(rs.getString("payment_attempt_id"));
            DateTime paymentAttemptDate = getDate(rs, "payment_attempt_date");

            return new InvoicePayment(invoiceId, amount, currency, paymentAttemptId, paymentAttemptDate);
        }
    }


}

