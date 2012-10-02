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

import com.ning.billing.payment.api.PaymentStatus;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.callcontext.InternalTenantContextBinder;
import com.ning.billing.util.dao.BinderBase;
import com.ning.billing.util.dao.EntityHistory;
import com.ning.billing.util.dao.MapperBase;
import com.ning.billing.util.entity.dao.UpdatableEntitySqlDao;

@ExternalizedSqlViaStringTemplate3()
@RegisterMapper(PaymentAttemptSqlDao.PaymentAttemptModelDaoMapper.class)
public interface PaymentAttemptSqlDao extends Transactional<PaymentAttemptSqlDao>, UpdatableEntitySqlDao<PaymentAttemptModelDao>, Transmogrifier, CloseMe {

    @SqlUpdate
    void insertPaymentAttempt(@Bind(binder = PaymentAttemptModelDaoBinder.class) final PaymentAttemptModelDao attempt,
                              @InternalTenantContextBinder final InternalCallContext context);

    @SqlUpdate
    void updatePaymentAttemptStatus(@Bind("id") final String attemptId,
                                    @Bind("processingStatus") final String processingStatus,
                                    @Bind("gatewayErrorCode") final String gatewayErrorCode,
                                    @Bind("gatewayErrorMsg") final String gatewayErrorMsg,
                                    @InternalTenantContextBinder final InternalCallContext context);

    @SqlQuery
    PaymentAttemptModelDao getPaymentAttempt(@Bind("id") final String attemptId,
                                             @InternalTenantContextBinder final InternalTenantContext context);

    @SqlQuery
    List<PaymentAttemptModelDao> getPaymentAttempts(@Bind("paymentId") final String paymentId,
                                                    @InternalTenantContextBinder final InternalTenantContext context);

    @Override
    @SqlUpdate
    void insertHistoryFromTransaction(@PaymentAttemptHistoryBinder final EntityHistory<PaymentAttemptModelDao> payment,
                                      @InternalTenantContextBinder final InternalCallContext context);

    public static final class PaymentAttemptModelDaoBinder extends BinderBase implements Binder<Bind, PaymentAttemptModelDao> {

        @Override
        public void bind(@SuppressWarnings("rawtypes") final SQLStatement stmt, final Bind bind, final PaymentAttemptModelDao attempt) {
            stmt.bind("id", attempt.getId().toString());
            stmt.bind("paymentId", attempt.getPaymentId().toString());
            stmt.bind("processingStatus", attempt.getPaymentStatus().toString());
            stmt.bind("gatewayErrorCode", attempt.getGatewayErrorCode());
            stmt.bind("gatewayErrorMsg", attempt.getGatewayErrorMsg());
            stmt.bind("requestedAmount", attempt.getRequestedAmount());
        }
    }

    public static class PaymentAttemptModelDaoMapper extends MapperBase implements ResultSetMapper<PaymentAttemptModelDao> {

        @Override
        public PaymentAttemptModelDao map(final int index, final ResultSet rs, final StatementContext ctx)
                throws SQLException {
            final UUID id = getUUID(rs, "id");
            final UUID accountId = getUUID(rs, "account_id");
            final UUID invoiceId = getUUID(rs, "invoice_id");
            final UUID paymentId = getUUID(rs, "payment_id");
            final DateTime effectiveDate = getDateTime(rs, "effective_date");
            final PaymentStatus processingStatus = PaymentStatus.valueOf(rs.getString("processing_status"));
            final String gatewayErrorCode = rs.getString("gateway_error_code");
            final String gatewayErrorMsg = rs.getString("gateway_error_msg");
            final BigDecimal requestedAmount = rs.getBigDecimal("requested_amount");
            return new PaymentAttemptModelDao(id, accountId, invoiceId, paymentId, processingStatus, effectiveDate, requestedAmount, gatewayErrorCode, gatewayErrorMsg);
        }
    }
}
