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

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.payment.api.DefaultPaymentAttempt;
import com.ning.billing.payment.api.PaymentAttempt;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallContextBinder;
import com.ning.billing.util.dao.BinderBase;
import com.ning.billing.util.dao.EntityHistory;
import com.ning.billing.util.dao.MapperBase;
import com.ning.billing.util.entity.dao.UpdatableEntitySqlDao;
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
import org.skife.jdbi.v2.sqlobject.stringtemplate.ExternalizedSqlViaStringTemplate3;
import org.skife.jdbi.v2.tweak.ResultSetMapper;
import org.skife.jdbi.v2.unstable.BindIn;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

@ExternalizedSqlViaStringTemplate3()
@RegisterMapper(PaymentAttemptSqlDao.PaymentAttemptMapper.class)
public interface PaymentAttemptSqlDao extends Transactional<PaymentAttemptSqlDao>, UpdatableEntitySqlDao<PaymentAttempt>, CloseMe {
    @SqlUpdate
    void insertPaymentAttempt(@Bind(binder = PaymentAttemptBinder.class) PaymentAttempt paymentAttempt,
                              @CallContextBinder CallContext context);

    @SqlQuery
    PaymentAttempt getPaymentAttemptForPaymentId(@Bind("paymentId") String paymentId);

    @SqlQuery
    PaymentAttempt getPaymentAttemptById(@Bind("id") String paymentAttemptId);

    @SqlQuery
    List<PaymentAttempt> getPaymentAttemptsForInvoiceId(@Bind("invoiceId") String invoiceId);

    @SqlQuery
    List<PaymentAttempt> getPaymentAttemptsForInvoiceIds(@BindIn("invoiceIds") List<String> invoiceIds);


    @SqlUpdate
    void updatePaymentAttemptWithPaymentId(@Bind("id") String paymentAttemptId,
                                           @Bind("payment_id") String paymentId,
                                           @CallContextBinder CallContext context);

    @SqlUpdate
    void updatePaymentAttemptWithRetryInfo(@Bind("id") String paymentAttemptId,
                                           @Bind("retry_count") int retryCount,
                                           @CallContextBinder CallContext context);
    
    @Override
    @SqlUpdate
    public void insertHistoryFromTransaction(@PaymentAttemptHistoryBinder final EntityHistory<PaymentAttempt> account,
                                            @CallContextBinder final CallContext context);

    public static class PaymentAttemptMapper extends MapperBase implements ResultSetMapper<PaymentAttempt> {
        @Override
        public PaymentAttempt map(int index, ResultSet rs, StatementContext ctx) throws SQLException {

            UUID paymentAttemptId = getUUID(rs, "id");
            UUID invoiceId = getUUID(rs, "invoice_id");
            UUID accountId = getUUID(rs, "account_id");
            BigDecimal amount = rs.getBigDecimal("amount");
            Currency currency = Currency.valueOf(rs.getString("currency"));
            DateTime invoiceDate = getDate(rs, "invoice_date");
            DateTime paymentAttemptDate = getDate(rs, "payment_attempt_date");
            UUID paymentId = getUUID(rs, "payment_id");
            Integer retryCount = rs.getInt("retry_count");
            DateTime createdDate = getDate(rs, "created_date");
            DateTime updatedDate = getDate(rs, "updated_date");

            return new DefaultPaymentAttempt(paymentAttemptId,
                                      invoiceId,
                                      accountId,
                                      amount,
                                      currency,
                                      invoiceDate,
                                      paymentAttemptDate,
                                      paymentId,
                                      retryCount,
                                      createdDate, updatedDate);
        }
    }

    public static final class PaymentAttemptBinder extends BinderBase implements Binder<Bind, PaymentAttempt> {
        @Override
        public void bind(@SuppressWarnings("rawtypes") SQLStatement stmt, Bind bind, PaymentAttempt paymentAttempt) {
            stmt.bind("id", paymentAttempt.getId().toString());
            stmt.bind("invoiceId", paymentAttempt.getInvoiceId().toString());
            stmt.bind("accountId", paymentAttempt.getAccountId().toString());
            stmt.bind("amount", paymentAttempt.getAmount());
            stmt.bind("currency", paymentAttempt.getCurrency().toString());
            stmt.bind("invoiceDate", getDate(paymentAttempt.getInvoiceDate()));
            stmt.bind("paymentAttemptDate", getDate(paymentAttempt.getPaymentAttemptDate()));
            stmt.bind("paymentId", paymentAttempt.getPaymentId() == null ? null : paymentAttempt.getPaymentId().toString());
            stmt.bind("retryCount", paymentAttempt.getRetryCount());
        }
    }
}
