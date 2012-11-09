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
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.BindBean;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.payment.dao.RefundModelDao.RefundStatus;
import com.ning.billing.util.audit.ChangeType;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.dao.MapperBase;
import com.ning.billing.util.entity.dao.Audited;
import com.ning.billing.util.entity.dao.EntitySqlDao;
import com.ning.billing.util.entity.dao.EntitySqlDaoStringTemplate;

@EntitySqlDaoStringTemplate
@RegisterMapper(RefundSqlDao.RefundModelDaoMapper.class)
public interface RefundSqlDao extends EntitySqlDao<RefundModelDao> {

    @SqlUpdate
    @Audited(ChangeType.INSERT)
    void insertRefund(@BindBean final RefundModelDao refundInfo,
                      @BindBean final InternalCallContext context);

    @SqlUpdate
    @Audited(ChangeType.UPDATE)
    void updateStatus(@Bind("id") final String refundId,
                      @Bind("refundStatus") final String status,
                      @BindBean final InternalCallContext context);

    @SqlQuery
    RefundModelDao getRefund(@Bind("id") final String refundId,
                             @BindBean final InternalTenantContext context);

    @SqlQuery
    List<RefundModelDao> getRefundsForPayment(@Bind("paymentId") final String paymentId,
                                              @BindBean final InternalTenantContext context);

    @SqlQuery
    List<RefundModelDao> getRefundsForAccount(@Bind("accountId") final String accountId,
                                              @BindBean final InternalTenantContext context);


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
