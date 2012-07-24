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

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.payment.dao.RefundModelDao.RefundStatus;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallContextBinder;
import com.ning.billing.util.dao.BinderBase;
import com.ning.billing.util.dao.EntityHistory;
import com.ning.billing.util.dao.MapperBase;
import com.ning.billing.util.entity.dao.UpdatableEntitySqlDao;

@ExternalizedSqlViaStringTemplate3()
@RegisterMapper(RefundSqlDao.RefundModelDaoMapper.class)
public interface RefundSqlDao extends Transactional<RefundSqlDao>, UpdatableEntitySqlDao<RefundModelDao>, Transmogrifier, CloseMe {

    @SqlUpdate
    void insertRefund(@Bind(binder = RefundModelDaoBinder.class) final RefundModelDao refundInfo,
                      @CallContextBinder final CallContext context);

    @SqlUpdate
    void updateStatus(@Bind("id") final String refundId, @Bind("refundStatus") final String status);

    @SqlQuery
    RefundModelDao getRefund(@Bind("id") final String refundId);

    @SqlQuery
    List<RefundModelDao> getRefundsForPayment(@Bind("paymentId") final String paymentId);

    @SqlQuery
    List<RefundModelDao> getRefundsForAccount(@Bind("accountId") final String accountId);

    @Override
    @SqlUpdate
    public void insertHistoryFromTransaction(@RefundHistoryBinder final EntityHistory<RefundModelDao> payment,
                                             @CallContextBinder final CallContext context);

    public static final class RefundModelDaoBinder extends BinderBase implements Binder<Bind, RefundModelDao> {

        @Override
        public void bind(final SQLStatement stmt, final Bind bind, final RefundModelDao refund) {
            stmt.bind("id", refund.getId().toString());
            stmt.bind("accountId", refund.getAccountId().toString());
            stmt.bind("paymentId", refund.getPaymentId().toString());
            stmt.bind("amount", refund.getAmount());
            stmt.bind("currency", refund.getCurrency().toString());
            stmt.bind("isAdjusted", refund.isAdjsuted());
            stmt.bind("refundStatus", refund.getRefundStatus().toString());
            // createdDate and updatedDate are populated by the @CallContextBinder
        }
    }

    public static class RefundModelDaoMapper extends MapperBase implements ResultSetMapper<RefundModelDao> {

        @Override
        public RefundModelDao map(final int index, final ResultSet rs, final StatementContext ctx)
                throws SQLException {
            final UUID id = getUUID(rs, "id");
            final UUID accountId = getUUID(rs, "account_id");
            final UUID paymentId = getUUID(rs, "payment_id");
            final BigDecimal amount = rs.getBigDecimal("amount");
            final boolean isAdjusted = rs.getBoolean("is_adjusted");
            final Currency currency = Currency.valueOf(rs.getString("currency"));
            final RefundStatus refundStatus = RefundStatus.valueOf(rs.getString("refund_status"));
            final DateTime createdDate = getDateTime(rs, "created_date");
            final DateTime updatedDate = getDateTime(rs, "updated_date");
            return new RefundModelDao(id, accountId, paymentId, amount, currency, isAdjusted, refundStatus, createdDate, updatedDate);
        }
    }
}
