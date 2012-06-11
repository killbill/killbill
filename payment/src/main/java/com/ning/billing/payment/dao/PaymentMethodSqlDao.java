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

import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallContextBinder;
import com.ning.billing.util.dao.BinderBase;
import com.ning.billing.util.dao.EntityHistory;
import com.ning.billing.util.dao.MapperBase;
import com.ning.billing.util.entity.dao.UpdatableEntitySqlDao;

@ExternalizedSqlViaStringTemplate3()
@RegisterMapper(PaymentMethodSqlDao.PaymentMethodDaoMapper.class)
public interface PaymentMethodSqlDao extends Transactional<PaymentMethodSqlDao>, UpdatableEntitySqlDao<PaymentMethodModelDao>, Transmogrifier,  CloseMe {

    
    @SqlUpdate
    void insertPaymentMethod(@Bind(binder = PaymentMethodModelDaoBinder.class) final PaymentMethodModelDao paymentMethod,
                           @CallContextBinder final CallContext context);
    
    @SqlUpdate
    void markPaymentMethodAsDeleted(@Bind("id") final String paymentMethodId);
    
    @SqlQuery
    PaymentMethodModelDao getPaymentMethod(@Bind("id") final String paymentMethodId);

    @SqlQuery
    List<PaymentMethodModelDao> getPaymentMethods(@Bind("accountId") final String accountId);


    @Override
    @SqlUpdate
    public void insertHistoryFromTransaction(@PaymentMethodHistoryBinder final EntityHistory<PaymentMethodModelDao> payment,
                                            @CallContextBinder final CallContext context);

    
    public static final class PaymentMethodModelDaoBinder extends BinderBase implements Binder<Bind, PaymentMethodModelDao> {
        @Override
        public void bind(@SuppressWarnings("rawtypes") SQLStatement stmt, Bind bind, PaymentMethodModelDao method) {
            stmt.bind("id", method.getId().toString());
            stmt.bind("accountId", method.getAccountId().toString());            
            stmt.bind("pluginName", method.getPluginName());            
            stmt.bind("isActive", method.isActive());            
            stmt.bind("externalId", method.getExternalId());              
        }
    }
    
    public static class PaymentMethodDaoMapper extends MapperBase implements ResultSetMapper<PaymentMethodModelDao> {

        @Override
        public PaymentMethodModelDao map(int index, ResultSet rs, StatementContext ctx)
                throws SQLException {
            UUID id = getUUID(rs, "id");
            UUID accountId = getUUID(rs, "account_id");
            String pluginName = rs.getString("plugin_name");
            Boolean isActive = rs.getBoolean("is_active");
            String externalId = rs.getString("external_id");
            return new PaymentMethodModelDao(id, accountId, pluginName, isActive, externalId);
        }
    }
}
