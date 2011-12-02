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
import com.ning.billing.account.api.user.AccountBuilder;
import com.ning.billing.catalog.api.Currency;
import org.skife.jdbi.v2.SQLStatement;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.sqlobject.*;
import org.skife.jdbi.v2.sqlobject.customizers.Mapper;
import org.skife.jdbi.v2.sqlobject.mixins.CloseMe;
import org.skife.jdbi.v2.sqlobject.mixins.Transactional;
import org.skife.jdbi.v2.sqlobject.stringtemplate.ExternalizedSqlViaStringTemplate3;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import java.lang.annotation.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

@ExternalizedSqlViaStringTemplate3()
public interface IAccountDaoSql extends IEntityDao<IAccount>, Transactional<IAccountDaoSql>, CloseMe {
    @SqlUpdate
    public void insertAccount(@AccountBinder IAccount account);

    @SqlUpdate
    public void updateAccount(@AccountBinder IAccount account);

    @SqlQuery
    @Mapper(AccountMapper.class)
    public IAccount getAccountByKey(@Bind("externalKey") String externalKey);

    @SqlQuery
    @Mapper(AccountMapper.class)
    public IAccount getAccountById(@Bind("id") String id);

    @SqlQuery
    @Mapper(AccountMapper.class)
    public List<IAccount> getAccounts();

    @SqlUpdate
    public void test();

    public static class AccountMapper implements ResultSetMapper<IAccount> {
        @Override
        public IAccount map(int index, ResultSet result, StatementContext context) throws SQLException {
            UUID id = UUID.fromString(result.getString("id"));
            String externalKey = result.getString("external_key");
            String email = result.getString("email");
            String firstName = result.getString("first_name");
            String lastName = result.getString("last_name");
            String phone = result.getString("phone");
            Currency currency = Currency.valueOf(result.getString("currency"));

            return new AccountBuilder(id).externalKey(externalKey).email(email)
                                         .firstName(firstName).lastName(lastName)
                                         .phone(phone).currency(currency).build();
        }
    }

    @BindingAnnotation(AccountBinder.AccountBinderFactory.class)
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.PARAMETER})
    public @interface AccountBinder {
        public static class AccountBinderFactory implements BinderFactory {
            public Binder build(Annotation annotation) {
                return new Binder<AccountBinder, Account>() {
                    public void bind(SQLStatement q, AccountBinder bind, Account account) {
                        q.bind("id", account.getId().toString());
                        q.bind("externalKey", account.getExternalKey());
                        q.bind("email", account.getEmail());
                        q.bind("firstName", account.getFirstName());
                        q.bind("lastName", account.getLastName());
                        q.bind("phone", account.getPhone());
                        q.bind("currency", account.getCurrency().toString());
                    }
                };
            }
        }
    }
}
