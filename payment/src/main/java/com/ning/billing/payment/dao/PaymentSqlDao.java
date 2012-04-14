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
import java.util.List;
import java.util.UUID;

import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallContextBinder;
import com.ning.billing.util.dao.BinderBase;
import com.ning.billing.util.dao.MapperBase;
import org.joda.time.DateTime;
import org.skife.jdbi.v2.SQLStatement;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.Binder;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.sqlobject.mixins.CloseMe;
import org.skife.jdbi.v2.sqlobject.mixins.Transactional;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;
import org.skife.jdbi.v2.sqlobject.stringtemplate.ExternalizedSqlViaStringTemplate3;
import org.skife.jdbi.v2.tweak.ResultSetMapper;
import org.skife.jdbi.v2.unstable.BindIn;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.payment.api.PaymentAttempt;
import com.ning.billing.payment.api.PaymentInfo;

@ExternalizedSqlViaStringTemplate3()
@RegisterMapper({PaymentSqlDao.PaymentAttemptMapper.class, PaymentSqlDao.PaymentInfoMapper.class})
public interface PaymentSqlDao extends Transactional<PaymentSqlDao>, CloseMe, Transmogrifier {
    @SqlUpdate
    void insertPaymentAttempt(@Bind(binder = PaymentAttemptBinder.class) PaymentAttempt paymentAttempt,
                              @CallContextBinder CallContext context);

    @SqlUpdate
    void insertPaymentAttemptHistory(@Bind("historyRecordId") final String historyRecordId,
                                     @Bind(binder = PaymentAttemptBinder.class) final PaymentAttempt paymentAttempt,
                                     @CallContextBinder final CallContext context);

    @SqlQuery
    PaymentAttempt getPaymentAttemptForPaymentId(@Bind("payment_id") String paymentId);

    @SqlQuery
    PaymentAttempt getPaymentAttemptById(@Bind("payment_attempt_id") String paymentAttemptId);

    @SqlQuery
    List<PaymentAttempt> getPaymentAttemptsForInvoiceId(@Bind("invoice_id") String invoiceId);

    @SqlQuery
    List<PaymentAttempt> getPaymentAttemptsForInvoiceIds(@BindIn("invoiceIds") List<String> invoiceIds);

    @SqlQuery
    PaymentInfo getPaymentInfoForPaymentAttemptId(@Bind("payment_attempt_id") String paymentAttemptId);

    @SqlUpdate
    void updatePaymentAttemptWithPaymentId(@Bind("payment_attempt_id") String paymentAttemptId,
                                           @Bind("payment_id") String paymentId,
                                           @CallContextBinder CallContext context);

    @SqlUpdate
    void updatePaymentAttemptWithRetryInfo(@Bind("payment_attempt_id") String paymentAttemptId,
                                           @Bind("retry_count") int retryCount,
                                           @CallContextBinder CallContext context);

    @SqlUpdate
    void updatePaymentInfo(@Bind("payment_method") String paymentMethod,
                           @Bind("payment_id") String paymentId,
                           @Bind("card_type") String cardType,
                           @Bind("card_country") String cardCountry,
                           @CallContextBinder CallContext context);

    @SqlQuery
    List<PaymentInfo> getPaymentInfos(@BindIn("invoiceIds") final List<String> invoiceIds);

    @SqlUpdate
    void insertPaymentInfo(@Bind(binder = PaymentInfoBinder.class) final PaymentInfo paymentInfo,
                           @CallContextBinder final CallContext context);

    @SqlUpdate
    void insertPaymentInfoHistory(@Bind("historyRecordId") final String historyRecordId,
                                  @Bind(binder = PaymentInfoBinder.class) final PaymentInfo paymentInfo,
                                  @CallContextBinder final CallContext context);

    @SqlQuery
    PaymentInfo getPaymentInfo(@Bind("paymentId") final String paymentId);

    public static final class PaymentAttemptBinder extends BinderBase implements Binder<Bind, PaymentAttempt> {
        @Override
        public void bind(@SuppressWarnings("rawtypes") SQLStatement stmt, Bind bind, PaymentAttempt paymentAttempt) {
            stmt.bind("payment_attempt_id", paymentAttempt.getPaymentAttemptId().toString());
            stmt.bind("invoice_id", paymentAttempt.getInvoiceId().toString());
            stmt.bind("account_id", paymentAttempt.getAccountId().toString());
            stmt.bind("amount", paymentAttempt.getAmount());
            stmt.bind("currency", paymentAttempt.getCurrency().toString());
            stmt.bind("invoice_dt", getDate(paymentAttempt.getInvoiceDate()));
            stmt.bind("payment_attempt_dt", getDate(paymentAttempt.getPaymentAttemptDate()));
            stmt.bind("payment_id", paymentAttempt.getPaymentId());
            stmt.bind("retry_count", paymentAttempt.getRetryCount());
            stmt.bind("created_dt", getDate(paymentAttempt.getCreatedDate()));
            stmt.bind("updated_dt", getDate(paymentAttempt.getUpdatedDate()));
        }
    }

    public static class PaymentAttemptMapper extends MapperBase implements ResultSetMapper<PaymentAttempt> {
        @Override
        public PaymentAttempt map(int index, ResultSet rs, StatementContext ctx) throws SQLException {

            UUID paymentAttemptId = UUID.fromString(rs.getString("payment_attempt_id"));
            UUID invoiceId = UUID.fromString(rs.getString("invoice_id"));
            UUID accountId = UUID.fromString(rs.getString("account_id"));
            BigDecimal amount = rs.getBigDecimal("amount");
            Currency currency = Currency.valueOf(rs.getString("currency"));
            DateTime invoiceDate = getDate(rs, "invoice_dt");
            DateTime paymentAttemptDate = getDate(rs, "payment_attempt_dt");
            String paymentId = rs.getString("payment_id");
            Integer retryCount = rs.getInt("retry_count");
            DateTime createdDate = getDate(rs, "created_dt");
            DateTime updatedDate = getDate(rs, "updated_dt");

            return new PaymentAttempt(paymentAttemptId,
                                      invoiceId,
                                      accountId,
                                      amount,
                                      currency,
                                      invoiceDate,
                                      paymentAttemptDate,
                                      paymentId,
                                      retryCount,
                                      createdDate,
                                      updatedDate);
        }
    }

    public static final class PaymentInfoBinder extends BinderBase implements Binder<Bind, PaymentInfo> {
        @Override
        public void bind(@SuppressWarnings("rawtypes") SQLStatement stmt, Bind bind, PaymentInfo paymentInfo) {
            stmt.bind("payment_id", paymentInfo.getPaymentId().toString());
            stmt.bind("amount", paymentInfo.getAmount());
            stmt.bind("refund_amount", paymentInfo.getRefundAmount());
            stmt.bind("payment_number", paymentInfo.getPaymentNumber());
            stmt.bind("bank_identification_number", paymentInfo.getBankIdentificationNumber());
            stmt.bind("status", paymentInfo.getStatus());
            stmt.bind("payment_type", paymentInfo.getType());
            stmt.bind("reference_id", paymentInfo.getReferenceId());
            stmt.bind("payment_method_id", paymentInfo.getPaymentMethodId());
            stmt.bind("payment_method", paymentInfo.getPaymentMethod());
            stmt.bind("card_type", paymentInfo.getCardType());
            stmt.bind("card_country", paymentInfo.getCardCountry());
            stmt.bind("effective_dt", getDate(paymentInfo.getEffectiveDate()));
            stmt.bind("created_dt", getDate(paymentInfo.getCreatedDate()));
            stmt.bind("updated_dt", getDate(paymentInfo.getUpdatedDate()));
        }
    }

    public static class PaymentInfoMapper extends MapperBase implements ResultSetMapper<PaymentInfo> {
        @Override
        public PaymentInfo map(int index, ResultSet rs, StatementContext ctx) throws SQLException {

            String paymentId = rs.getString("payment_id");
            BigDecimal amount = rs.getBigDecimal("amount");
            BigDecimal refundAmount = rs.getBigDecimal("refund_amount");
            String paymentNumber = rs.getString("payment_number");
            String bankIdentificationNumber = rs.getString("bank_identification_number");
            String status = rs.getString("status");
            String type = rs.getString("payment_type");
            String referenceId = rs.getString("reference_id");
            String paymentMethodId = rs.getString("payment_method_id");
            String paymentMethod = rs.getString("payment_method");
            String cardType = rs.getString("card_type");
            String cardCountry = rs.getString("card_country");            
            DateTime effectiveDate = getDate(rs, "effective_dt");
            DateTime createdDate = getDate(rs, "created_dt");
            DateTime updatedDate = getDate(rs, "updated_dt");

            UUID userToken = null; //rs.getString("user_token") != null ? UUID.fromString(rs.getString("user_token")) : null;
            
            return new PaymentInfo(paymentId,
                                   amount,
                                   refundAmount,
                                   bankIdentificationNumber,
                                   paymentNumber,
                                   status,
                                   type,
                                   referenceId,
                                   paymentMethodId,
                                   paymentMethod,
                                   cardType,
                                   cardCountry,
                                   userToken,
                                   effectiveDate,
                                   createdDate,
                                   updatedDate);
        }
    }

}
