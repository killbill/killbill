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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import com.ning.billing.account.api.AccountEmail;
import com.ning.billing.account.api.DefaultAccountEmail;
import com.ning.billing.util.dao.MapperBase;

public class AccountEmailMapper extends MapperBase implements ResultSetMapper<AccountEmail> {
    @Override
    public AccountEmail map(final int index, final ResultSet result, final StatementContext context) throws SQLException {
        final UUID id = UUID.fromString(result.getString("id"));
        final UUID accountId = UUID.fromString(result.getString("account_id"));
        final String email = result.getString("email");

        return new DefaultAccountEmail(id, accountId, email);
    }
}
