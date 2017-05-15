/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.util.validation.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import javax.annotation.Nullable;

import org.killbill.commons.jdbi.template.KillBillSqlDaoStringTemplate;
import org.killbill.billing.util.validation.DefaultColumnInfo;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

@KillBillSqlDaoStringTemplate
public interface DatabaseSchemaSqlDao {

    @SqlQuery
    List<DefaultColumnInfo> getSchemaInfo(@Nullable @Bind("schemaName") final String schemaName);

    class ColumnInfoMapper implements ResultSetMapper<DefaultColumnInfo> {

        @Override
        public DefaultColumnInfo map(final int index, final ResultSet r, final StatementContext ctx) throws SQLException {
            final String tableName = r.getString("table_name");
            final String columnName = r.getString("column_name");
            final Long scale = r.getLong("numeric_scale");
            final Long precision = r.getLong("numeric_precision");

            // Special handling for PostgreSQL - the implementation of AbstractJdbc2ResultSet#getBoolean doesn't support YES/NO
            final String isNullableString = r.getString("is_nullable");
            final boolean isNullable;
            if ("YES".equalsIgnoreCase(isNullableString)) {
                isNullable = true;
            } else if ("NO".equalsIgnoreCase(isNullableString)) {
                isNullable = false;
            } else {
                isNullable = r.getBoolean("is_nullable");
            }

            final Long maximumLength = r.getLong("character_maximum_length");
            final String dataType = r.getString("data_type");

            return new DefaultColumnInfo(tableName, columnName, scale, precision, isNullable, maximumLength, dataType);
        }
    }
}
