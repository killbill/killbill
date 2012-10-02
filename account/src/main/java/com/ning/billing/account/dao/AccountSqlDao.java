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

package com.ning.billing.account.dao;

import java.util.UUID;

import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.sqlobject.mixins.Transactional;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;
import org.skife.jdbi.v2.sqlobject.stringtemplate.ExternalizedSqlViaStringTemplate3;

import com.ning.billing.account.api.Account;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.callcontext.InternalTenantContextBinder;
import com.ning.billing.util.dao.EntityHistory;
import com.ning.billing.util.dao.UuidMapper;
import com.ning.billing.util.entity.dao.UpdatableEntitySqlDao;

@ExternalizedSqlViaStringTemplate3
@RegisterMapper({UuidMapper.class, AccountMapper.class})
public interface AccountSqlDao extends UpdatableEntitySqlDao<Account>, Transactional<AccountSqlDao>, Transmogrifier {

    @SqlQuery
    public Account getAccountByKey(@Bind("externalKey") final String key,
                                   @InternalTenantContextBinder final InternalTenantContext context);

    @SqlQuery
    public UUID getIdFromKey(@Bind("externalKey") final String key,
                             @InternalTenantContextBinder final InternalTenantContext context);

    @Override
    @SqlUpdate
    public void create(@AccountBinder Account account,
                       @InternalTenantContextBinder final InternalCallContext context);

    @Override
    @SqlUpdate
    public void update(@AccountBinder Account account,
                       @InternalTenantContextBinder final InternalCallContext context);

    @SqlUpdate
    public void updatePaymentMethod(@Bind("id") String accountId,
                                    @Bind("paymentMethodId") String paymentMethodId,
                                    @InternalTenantContextBinder final InternalCallContext context);

    @Override
    @SqlUpdate
    public void insertHistoryFromTransaction(@AccountHistoryBinder final EntityHistory<Account> account,
                                             @InternalTenantContextBinder final InternalCallContext context);
}
