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

package com.ning.billing.analytics.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import com.ning.billing.analytics.model.BusinessAccountFieldModelDao;

public class BusinessAccountFieldMapper implements ResultSetMapper<BusinessAccountFieldModelDao> {

    @Override
    public BusinessAccountFieldModelDao map(final int index, final ResultSet r, final StatementContext ctx) throws SQLException {
        final UUID accountId = UUID.fromString(r.getString(1));
        final String accountKey = r.getString(2);
        final String name = r.getString(3);
        final String value = r.getString(4);
        return new BusinessAccountFieldModelDao(accountId, accountKey, name, value);
    }
}
