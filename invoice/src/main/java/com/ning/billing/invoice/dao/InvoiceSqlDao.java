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
    void notifySuccessfulPayment(@Bind("invoiceId") final String invoiceId,
                                 @Bind("amount") final BigDecimal paymentAmount,
                                 @Bind("currency") final String currency,
                                 @Bind("paymentId") final String paymentId,
                                 @Bind("paymentDate") final Date paymentDate);

    @SqlUpdate
    void notifyFailedPayment(@Bind("invoiceId") final String invoiceId,
                             @Bind("paymentId") final String paymentId,
                             @Bind("paymentAttemptDate") final Date paymentAttemptDate);

    @BindingAnnotation(InvoiceBinder.InvoiceBinderFactory.class)
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.PARAMETER})
    public @interface InvoiceBinder {
        public static class InvoiceBinderFactory implements BinderFactory {
            @Override
            public Binder build(Annotation annotation) {
                return new Binder<InvoiceBinder, Invoice>() {
                    @Override
                    public void bind(SQLStatement q, InvoiceBinder bind, Invoice invoice) {
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

}

