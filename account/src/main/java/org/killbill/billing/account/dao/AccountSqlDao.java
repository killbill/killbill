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

package org.killbill.billing.account.dao;

import java.util.List;
import java.util.UUID;

import org.killbill.billing.account.api.Account;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.util.audit.ChangeType;
import org.killbill.billing.util.entity.dao.Audited;
import org.killbill.billing.util.entity.dao.EntitySqlDao;
import org.killbill.billing.util.entity.dao.EntitySqlDaoStringTemplate;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.BindBean;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;

@EntitySqlDaoStringTemplate
public interface AccountSqlDao extends EntitySqlDao<AccountModelDao, Account> {

    @SqlQuery
    public AccountModelDao getAccountByKey(@Bind("externalKey") final String key,
                                           @BindBean final InternalTenantContext context);

    @SqlQuery
    public UUID getIdFromKey(@Bind("externalKey") final String key,
                             @BindBean final InternalTenantContext context);

    @SqlQuery
    public Integer getBCD(@Bind("id") String accountId,
                       @BindBean final InternalTenantContext context);

    @SqlUpdate
    @Audited(ChangeType.UPDATE)
    public void update(@BindBean final AccountModelDao account,
                       @BindBean final InternalCallContext context);

    @SqlUpdate
    @Audited(ChangeType.UPDATE)
    public void updatePaymentMethod(@Bind("id") String accountId,
                                    @Bind("paymentMethodId") String paymentMethodId,
                                    @BindBean final InternalCallContext context);

    @SqlQuery
    List<AccountModelDao> getAccountsByParentId(@Bind("parentAccountId") UUID parentAccountId,
                                                @BindBean final InternalTenantContext context);
}
