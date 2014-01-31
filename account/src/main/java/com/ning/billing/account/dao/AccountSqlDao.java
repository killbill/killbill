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

package com.ning.billing.account.dao;

import java.util.Iterator;
import java.util.UUID;

import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.BindBean;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.FetchSize;

import com.ning.billing.account.api.Account;
import com.ning.billing.callcontext.InternalCallContext;
import com.ning.billing.callcontext.InternalTenantContext;
import com.ning.billing.util.audit.ChangeType;
import com.ning.billing.util.entity.dao.Audited;
import com.ning.billing.util.entity.dao.EntitySqlDao;
import com.ning.billing.util.entity.dao.EntitySqlDaoStringTemplate;

@EntitySqlDaoStringTemplate
public interface AccountSqlDao extends EntitySqlDao<AccountModelDao, Account> {

    @SqlQuery
    public AccountModelDao getAccountByKey(@Bind("externalKey") final String key,
                                           @BindBean final InternalTenantContext context);

    @SqlQuery
    // Magic value to force MySQL to stream from the database
    // See http://dev.mysql.com/doc/refman/5.0/en/connector-j-reference-implementation-notes.html (ResultSet)
    @FetchSize(Integer.MIN_VALUE)
    public Iterator<AccountModelDao> searchAccounts(@Bind("searchKey") final String searchKey,
                                                    @Bind("likeSearchKey") final String likeSearchKey,
                                                    @Bind("offset") final Long offset,
                                                    @Bind("rowCount") final Long rowCount,
                                                    @BindBean final InternalTenantContext context);

    @SqlQuery
    public UUID getIdFromKey(@Bind("externalKey") final String key,
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
}
