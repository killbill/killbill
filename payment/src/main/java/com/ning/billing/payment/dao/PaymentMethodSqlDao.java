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

import com.ning.billing.payment.api.PaymentMethod;
import com.ning.billing.util.audit.ChangeType;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.dao.MapperBase;
import com.ning.billing.util.entity.dao.Audited;
import com.ning.billing.util.entity.dao.EntitySqlDao;
import com.ning.billing.util.entity.dao.EntitySqlDaoStringTemplate;

@EntitySqlDaoStringTemplate
@RegisterMapper(PaymentMethodSqlDao.PaymentMethodDaoMapper.class)
public interface PaymentMethodSqlDao extends EntitySqlDao<PaymentMethodModelDao, PaymentMethod> {

    @SqlUpdate
    @Audited(ChangeType.UPDATE)
    void markPaymentMethodAsDeleted(@Bind("id") final String paymentMethodId,
                                    @BindBean final InternalCallContext context);

    @SqlUpdate
    @Audited(ChangeType.UPDATE)
    void unmarkPaymentMethodAsDeleted(@Bind("id") final String paymentMethodId,
                                      @BindBean final InternalCallContext context);

    @SqlQuery
    PaymentMethodModelDao getPaymentMethodIncludedDelete(@Bind("id") final String paymentMethodId,
                                                         @BindBean final InternalTenantContext context);

    @SqlQuery
    List<PaymentMethodModelDao> getByAccountId(@Bind("accountId") final String accountId, @BindBean final InternalCallContext context);

    public static class PaymentMethodDaoMapper extends MapperBase implements ResultSetMapper<PaymentMethodModelDao> {

        @Override
        public PaymentMethodModelDao map(final int index, final ResultSet rs, final StatementContext ctx)
                throws SQLException {
            final UUID id = getUUID(rs, "id");
            final UUID accountId = getUUID(rs, "account_id");
            final String pluginName = rs.getString("plugin_name");
            final Boolean isActive = rs.getBoolean("is_active");
            final String externalId = rs.getString("external_id");
            final DateTime createdDate = getDateTime(rs, "created_date");
            final DateTime updatedDate = getDateTime(rs, "updated_date");
            return new PaymentMethodModelDao(id, createdDate, updatedDate, accountId, pluginName, isActive, externalId);
        }
    }
}
