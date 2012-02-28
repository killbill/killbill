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

package com.ning.billing.util.validation.dao;

import com.ning.billing.util.validation.ColumnInfo;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.sqlobject.stringtemplate.ExternalizedSqlViaStringTemplate3;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@ExternalizedSqlViaStringTemplate3
@RegisterMapper(DatabaseSchemaSqlDao.ColumnInfoMapper.class)
public interface DatabaseSchemaSqlDao {
    @SqlQuery
    List<ColumnInfo> getSchemaInfo(@Bind("schemaName") final String schemaName);

    class ColumnInfoMapper implements ResultSetMapper<ColumnInfo> {
        @Override
        public ColumnInfo map(int index, ResultSet r, StatementContext ctx) throws SQLException {
            final String tableName = r.getString("table_name");
            final String columnName = r.getString("column_name");
            final Integer scale = r.getInt("numeric_scale");
            final Integer precision = r.getInt("numeric_precision");
            final boolean isNullable = r.getBoolean("is_nullable");

            return new ColumnInfo(tableName, columnName, scale, precision, isNullable);
        }
    }
}
