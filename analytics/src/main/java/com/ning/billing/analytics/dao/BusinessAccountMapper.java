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

package com.ning.billing.analytics.dao;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import com.ning.billing.analytics.model.BusinessAccountModelDao;
import com.ning.billing.util.dao.MapperBase;

public class BusinessAccountMapper extends MapperBase implements ResultSetMapper<BusinessAccountModelDao> {

    @Override
    public BusinessAccountModelDao map(final int index, final ResultSet r, final StatementContext ctx) throws SQLException {
        return new BusinessAccountModelDao(
                UUID.fromString(r.getString(1)),
                r.getString(2),
                r.getString(6),
                BigDecimal.valueOf(r.getDouble(5)),
                getDate(r, "last_invoice_date"),
                BigDecimal.valueOf(r.getDouble(8)),
                r.getString(9),
                r.getString(10),
                r.getString(11),
                r.getString(12),
                r.getString(13),
                new DateTime(r.getLong(3), DateTimeZone.UTC),
                new DateTime(r.getLong(4), DateTimeZone.UTC)
        );
    }
}
