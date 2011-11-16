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

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.IAccount;
import org.skife.jdbi.v2.SQLStatement;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.Binder;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.Mapper;
import org.skife.jdbi.v2.sqlobject.mixins.CloseMe;
import org.skife.jdbi.v2.sqlobject.mixins.Transactional;
import org.skife.jdbi.v2.sqlobject.stringtemplate.ExternalizedSqlViaStringTemplate3;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

@ExternalizedSqlViaStringTemplate3()
public interface IAccountDaoSql extends Transactional<IAccountDaoSql>, CloseMe {

    @SqlUpdate
    public void insertAccount(@Bind(binder = IAccountSqlBinder.class) IAccount account);

    @SqlQuery
    @Mapper(IAccountSqlMapper.class)
    public IAccount getAccountByKey(@Bind("key_name") String key);

    @SqlQuery
    @Mapper(IAccountSqlMapper.class)
    public IAccount getAccountFromId(@Bind("id") String id);

    @SqlQuery
    @Mapper(IAccountSqlMapper.class)
    public List<IAccount> getAccounts();

    @SqlUpdate
    public void test();

    public static class IAccountSqlBinder implements Binder<Bind, IAccount> {

        @Override
        public void bind(SQLStatement stmt, Bind bind, IAccount account) {
            stmt.bind("id", account.getId().toString());
            stmt.bind("key_name", account.getKey());
        }
    }

    public static class IAccountSqlMapper implements ResultSetMapper<IAccount> {

        @Override
        public IAccount map(int index, ResultSet r, StatementContext ctx)
                throws SQLException {
            UUID id = UUID.fromString(r.getString("id"));
            String key = r.getString("key_name");
            return new Account(id).withKey(key);
        }
    }
}
