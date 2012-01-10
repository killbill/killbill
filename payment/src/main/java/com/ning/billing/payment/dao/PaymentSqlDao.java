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

package com.ning.billing.payment.dao;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.skife.jdbi.v2.SQLStatement;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.Binder;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.Mapper;
import org.skife.jdbi.v2.sqlobject.mixins.CloseMe;
import org.skife.jdbi.v2.sqlobject.mixins.Transactional;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;
import org.skife.jdbi.v2.sqlobject.stringtemplate.ExternalizedSqlViaStringTemplate3;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.model.DefaultInvoice;
import com.ning.billing.payment.api.PaymentAttempt;
import com.ning.billing.payment.api.PaymentInfo;

@ExternalizedSqlViaStringTemplate3()
public interface PaymentSqlDao extends Transactional<PaymentSqlDao>, CloseMe, Transmogrifier {
    @SqlUpdate
    void insertPaymentAttempt(@Bind(binder = PaymentAttemptBinder.class) PaymentAttempt paymentAttempt);

    @SqlQuery
    @Mapper(PaymentAttemptMapper.class)
    PaymentAttempt getPaymentAttemptForPaymentId(@Bind("paymentId") String paymentId);

    @SqlUpdate
    void updatePaymentAttemptWithPaymentId(String paymentAttemptId, String id);

    @SqlUpdate
    void insertPaymentInfo(@Bind(binder = PaymentInfoBinder.class) PaymentInfo paymentInfo);

    public static final class PaymentAttemptBinder implements Binder<Bind, PaymentAttempt> {

        private Date getDate(DateTime dateTime) {
            return dateTime == null ? null : dateTime.toDate();
        }

        @Override
        public void bind(@SuppressWarnings("rawtypes") SQLStatement stmt, Bind bind, PaymentAttempt paymentAttempt) {
            Invoice invoice = paymentAttempt.getInvoice();

            stmt.bind("id", paymentAttempt.getPaymentAttemptId().toString());
            stmt.bind("payment_id", paymentAttempt.getPaymentId());
            stmt.bind("payment_attempt_dt", getDate(paymentAttempt.getPaymentAttemptDate()));
            stmt.bind("invoice_id", invoice.getId().toString());
            stmt.bind("account_id", invoice.getAccountId().toString());
            stmt.bind("invoice_dt", getDate(invoice.getInvoiceDate()));
            stmt.bind("target_dt", getDate(invoice.getTargetDate()));
            stmt.bind("amount_paid", invoice.getAmountPaid());
            stmt.bind("amount_outstanding", invoice.getAmountOutstanding());
            stmt.bind("last_payment_attempt", getDate(invoice.getLastPaymentAttempt()));
            stmt.bind("currency", invoice.getCurrency().toString());
        }
    }

    public static class PaymentAttemptMapper implements ResultSetMapper<PaymentAttempt> {

        private DateTime getDate(ResultSet rs, String fieldName) throws SQLException {
            final Timestamp resultStamp = rs.getTimestamp(fieldName);
            return rs.wasNull() ? null : new DateTime(resultStamp).toDateTime(DateTimeZone.UTC);
        }

        @Override
        public PaymentAttempt map(int index, ResultSet rs, StatementContext ctx) throws SQLException {

            UUID paymentAttemptId = UUID.fromString(rs.getString("id"));
            String paymentId = rs.getString("payment_id");
            DateTime paymentAttemptDate = getDate(rs, "payment_attempt_dt");

            UUID invoiceId = UUID.fromString(rs.getString("invoice_id"));
            UUID accountId = UUID.fromString(rs.getString("account_id"));
            DateTime invoiceDate = getDate(rs, "invoice_dt");
            DateTime targetDate = getDate(rs, "target_dt");
            Currency currency = Currency.valueOf(rs.getString("currency"));
            DateTime lastPaymentAttempt = getDate(rs, "last_payment_attempt");
            BigDecimal amountPaid = rs.getBigDecimal("amount_paid");

            Invoice invoice = new DefaultInvoice(invoiceId, accountId, invoiceDate, targetDate, currency, lastPaymentAttempt, amountPaid);

            return new PaymentAttempt(paymentAttemptId, invoice, paymentId, paymentAttemptDate);
        }
    }

    public static final class PaymentInfoBinder implements Binder<Bind, PaymentInfo> {

        private Date getDate(DateTime dateTime) {
            return dateTime == null ? null : dateTime.toDate();
        }

        @Override
        public void bind(@SuppressWarnings("rawtypes") SQLStatement stmt, Bind bind, PaymentInfo paymentInfo) {
            stmt.bind("id", paymentInfo.getId().toString());
            stmt.bind("amount", paymentInfo.getAmount());
            stmt.bind("refund_amount", paymentInfo.getRefundAmount());
            stmt.bind("applied_credit_balance_amount", paymentInfo.getAppliedCreditBalanceAmount());
            stmt.bind("payment_number", paymentInfo.getPaymentNumber());
            stmt.bind("bank_identification_number", paymentInfo.getBankIdentificationNumber());
            stmt.bind("status", paymentInfo.getStatus());
            stmt.bind("type", paymentInfo.getType());
            stmt.bind("reference_id", paymentInfo.getReferenceId());
            stmt.bind("effective_dt", getDate(paymentInfo.getEffectiveDate()));
            stmt.bind("created_dt", getDate(paymentInfo.getCreatedDate()));
            stmt.bind("updated_dt", getDate(paymentInfo.getUpdatedDate()));
        }
    }

    public static class PaymentInfoMapper implements ResultSetMapper<PaymentInfo> {

        private DateTime getDate(ResultSet rs, String fieldName) throws SQLException {
            final Timestamp resultStamp = rs.getTimestamp(fieldName);
            return rs.wasNull() ? null : new DateTime(resultStamp).toDateTime(DateTimeZone.UTC);
        }

        @Override
        public PaymentInfo map(int index, ResultSet rs, StatementContext ctx) throws SQLException {

            String id = rs.getString("id");
            BigDecimal amount = rs.getBigDecimal("amount");
            BigDecimal refundAmount = rs.getBigDecimal("refund_amount");
            BigDecimal appliedCreditBalanceAmount = rs.getBigDecimal("applied_credit_balance_amount");
            String paymentNumber = rs.getString("payment_number");
            String bankIdentificationNumber = rs.getString("bank_identification_number");
            String status = rs.getString("status");
            String type = rs.getString("type");
            String referenceId = rs.getString("reference_id");
            DateTime effectiveDate = getDate(rs, "effective_dt");
            DateTime createdDate = getDate(rs, "created_dt");
            DateTime updatedDate = getDate(rs, "updated_dt");

            return new PaymentInfo(id,
                                   amount,
                                   appliedCreditBalanceAmount,
                                   bankIdentificationNumber,
                                   paymentNumber,
                                   refundAmount,
                                   status,
                                   type,
                                   referenceId,
                                   effectiveDate,
                                   createdDate,
                                   updatedDate);
        }
    }
}
