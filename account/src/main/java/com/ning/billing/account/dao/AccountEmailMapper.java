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

import com.ning.billing.account.api.AccountEmail;
import com.ning.billing.account.api.DefaultAccountEmail;
import com.ning.billing.util.dao.MapperBase;
import org.joda.time.DateTime;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class AccountEmailMapper extends MapperBase implements ResultSetMapper<AccountEmail> {
    @Override
    public AccountEmail map(int index, ResultSet result, StatementContext context) throws SQLException {
        UUID id = UUID.fromString(result.getString("id"));
        UUID accountId = UUID.fromString(result.getString("account_id"));
        String email = result.getString("email");

        String createdBy = result.getString("created_by");
        DateTime createdDate = getDate(result, "created_date");
        String updatedBy = result.getString("updated_by");
        DateTime updatedDate = getDate(result, "updated_date");

        return new DefaultAccountEmail(id, accountId, email, createdBy, createdDate, updatedBy, updatedDate);
    }
}
