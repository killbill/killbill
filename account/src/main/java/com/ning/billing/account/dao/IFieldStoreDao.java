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

import com.ning.billing.account.api.CustomField;
import com.ning.billing.account.api.ICustomField;
import org.skife.jdbi.v2.SQLStatement;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.sqlobject.*;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.sqlobject.stringtemplate.ExternalizedSqlViaStringTemplate3;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

@ExternalizedSqlViaStringTemplate3
public interface IFieldStoreDao {
    @SqlBatch
    public void saveFields(@Bind("objectId") final String objectId,
                           @Bind("objectType") final String objectType,
                           @BindBean final List<ICustomField> fields);

    @SqlBatch
    public void createFields(@Bind("objectId") final String objectId,
                            @Bind("objectType") final String objectType,
                            @BindBean final List<ICustomField> fields);

    @SqlQuery
    @RegisterMapper(CustomFieldMapper.class)
    public List<ICustomField> getFields(@Bind("objectId") final String objectId,
                                        @Bind("objectType") final String objectType);

    @SqlUpdate
    public void test();

    public static class CustomFieldBinder implements Binder<Bind, ICustomField> {
        @Override
        public void bind(@SuppressWarnings("rawtypes") SQLStatement stmt, Bind bind, ICustomField field) {
            stmt.bind("id", field.getId().toString());
            stmt.bind("fieldName", field.getName());
            stmt.bind("fieldValue", field.getValue());
        }
    }

    public static class CustomFieldMapper implements ResultSetMapper<ICustomField> {
        @Override
        public ICustomField map(int index, ResultSet result, StatementContext context)
                throws SQLException {
            UUID id = UUID.fromString(result.getString("id"));
            String fieldName = result.getString("field_name");
            String fieldValue = result.getString("field_value");
            return new CustomField(id, fieldName, fieldValue);
        }
    }
}
