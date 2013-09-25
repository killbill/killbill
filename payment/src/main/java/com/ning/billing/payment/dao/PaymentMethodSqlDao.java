/*
 * Copyright 2010-2013 Ning, Inc.
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

import java.util.List;

import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.BindBean;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;

import com.ning.billing.payment.api.PaymentMethod;
import com.ning.billing.util.audit.ChangeType;
import com.ning.billing.callcontext.InternalCallContext;
import com.ning.billing.callcontext.InternalTenantContext;
import com.ning.billing.util.entity.dao.Audited;
import com.ning.billing.util.entity.dao.EntitySqlDao;
import com.ning.billing.util.entity.dao.EntitySqlDaoStringTemplate;

@EntitySqlDaoStringTemplate
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
    List<PaymentMethodModelDao> getByAccountId(@Bind("accountId") final String accountId, @BindBean final InternalTenantContext context);

    @SqlQuery
    List<PaymentMethodModelDao> getByAccountIdIncludedDelete(@Bind("accountId") final String accountId, @BindBean final InternalTenantContext context);
}
