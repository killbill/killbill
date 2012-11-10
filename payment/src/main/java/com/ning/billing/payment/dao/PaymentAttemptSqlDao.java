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
import org.skife.jdbi.v2.sqlobject.BindBean;
import org.skife.jdbi.v2.sqlobject.Binder;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import com.ning.billing.payment.api.Payment.PaymentAttempt;
import com.ning.billing.payment.api.PaymentStatus;
import com.ning.billing.util.audit.ChangeType;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.dao.BinderBase;
import com.ning.billing.util.dao.MapperBase;
import com.ning.billing.util.entity.dao.Audited;
import com.ning.billing.util.entity.dao.EntitySqlDao;
import com.ning.billing.util.entity.dao.EntitySqlDaoStringTemplate;

@EntitySqlDaoStringTemplate
@RegisterMapper(PaymentAttemptSqlDao.PaymentAttemptModelDaoMapper.class)
public interface PaymentAttemptSqlDao extends EntitySqlDao<PaymentAttemptModelDao> {


    @SqlUpdate
    @Audited(ChangeType.UPDATE)
    void updatePaymentAttemptStatus(@Bind("id") final String attemptId,
                                    @Bind("processingStatus") final String processingStatus,
                                    @Bind("gatewayErrorCode") final String gatewayErrorCode,
                                    @Bind("gatewayErrorMsg") final String gatewayErrorMsg,
                                    @BindBean final InternalCallContext context);

    @SqlQuery
    List<PaymentAttemptModelDao> getByPaymentId(@Bind("paymentId") final String paymentId,
                                                @BindBean final InternalTenantContext context);


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
            final DateTime createdDate = getDateTime(rs, "created_date");
            final DateTime updatedDate = getDateTime(rs, "updated_date");

            return new PaymentAttemptModelDao(id, createdDate, updatedDate, accountId, invoiceId, paymentId, processingStatus,
                                              effectiveDate, requestedAmount, gatewayErrorCode, gatewayErrorMsg);
        }
    }
}
