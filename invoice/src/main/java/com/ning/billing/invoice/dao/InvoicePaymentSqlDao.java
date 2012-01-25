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
import java.util.List;
import java.util.UUID;

import com.ning.billing.catalog.api.Currency;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.skife.jdbi.v2.SQLStatement;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.sqlobject.*;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.sqlobject.stringtemplate.ExternalizedSqlViaStringTemplate3;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import com.ning.billing.invoice.api.InvoicePayment;

@ExternalizedSqlViaStringTemplate3
@RegisterMapper(InvoicePaymentSqlDao.InvoicePaymentMapper.class)
public interface InvoicePaymentSqlDao {
    @SqlQuery
    public InvoicePayment getByPaymentAttemptId(@Bind("paymentAttempt") final String paymentAttemptId);

    @SqlQuery
    public List<InvoicePayment> get();

    @SqlUpdate
    public void create(@InvoicePaymentBinder  InvoicePayment invoicePayment);

    @SqlBatch
    void create(@InvoicePaymentBinder List<InvoicePayment> items);

    @SqlUpdate
    public void update(@InvoicePaymentBinder  InvoicePayment invoicePayment);

    @SqlQuery
    public List<InvoicePayment> getPaymentsForInvoice(@Bind("invoiceId") String invoiceId);

    @SqlQuery
    InvoicePayment getInvoicePayment(@Bind("paymentAttemptId") UUID paymentAttemptId);

    @SqlUpdate
    void notifyOfPaymentAttempt(@InvoicePaymentBinder InvoicePayment invoicePayment);

    public static class InvoicePaymentMapper implements ResultSetMapper<InvoicePayment> {
        private DateTime getDate(ResultSet rs, String fieldName) throws SQLException {
            final Timestamp resultStamp = rs.getTimestamp(fieldName);
            return rs.wasNull() ? null : new DateTime(resultStamp).toDateTime(DateTimeZone.UTC);
        }

        @Override
        public InvoicePayment map(int index, ResultSet result, StatementContext context) throws SQLException {
            final UUID paymentAttemptId = UUID.fromString(result.getString("payment_attempt_id"));
            final UUID invoiceId = UUID.fromString(result.getString("invoice_id"));
            final DateTime paymentAttemptDate = getDate(result, "payment_attempt_date");
            final BigDecimal amount = result.getBigDecimal("amount");
            final String currencyString = result.getString("currency");
            final Currency currency = (currencyString == null) ? null : Currency.valueOf(currencyString);
            final DateTime createdDate = getDate(result, "created_date");
            final DateTime updatedDate = getDate(result, "updated_date");

            return new InvoicePayment() {
                private  DateTime now = new DateTime();

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
                @Override
                public DateTime getCreatedDate() {
                    return createdDate ;
                }
                @Override
                public DateTime getUpdatedDate() {
                    return updatedDate;
                }
            };
        }
    }

    @BindingAnnotation(InvoicePaymentBinder.InvoicePaymentBinderFactory.class)
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.PARAMETER})
    public @interface InvoicePaymentBinder {
        public static class InvoicePaymentBinderFactory implements BinderFactory {
            public Binder build(Annotation annotation) {
                return new Binder<InvoicePaymentBinder, InvoicePayment>() {
                    public void bind(SQLStatement q, InvoicePaymentBinder bind, InvoicePayment payment) {
                        q.bind("invoiceId", payment.getInvoiceId().toString());
                        q.bind("paymentAttemptId", payment.getPaymentAttemptId().toString());
                        q.bind("paymentAttemptDate", payment.getPaymentAttemptDate().toDate());
                        q.bind("amount", payment.getAmount());
                        Currency currency = payment.getCurrency();
                        q.bind("currency", (currency == null) ? null : currency.toString());
                        DateTime createdDate = payment.getCreatedDate();
                        q.bind("createdDate", (createdDate == null) ? new DateTime().toDate() : createdDate.toDate());
                        DateTime updatedDate = payment.getUpdatedDate();
                        q.bind("updatedDate", (updatedDate == null) ? new DateTime().toDate() : updatedDate.toDate());
                    }
                };
            }
        }
    }
}
