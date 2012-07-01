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

package com.ning.billing.util.notificationq;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import org.skife.jdbi.v2.SQLStatement;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.Binder;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.Mapper;
import org.skife.jdbi.v2.sqlobject.mixins.CloseMe;
import org.skife.jdbi.v2.sqlobject.mixins.Transactional;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;
import org.skife.jdbi.v2.sqlobject.stringtemplate.ExternalizedSqlViaStringTemplate3;
import org.skife.jdbi.v2.tweak.ResultSetMapper;


@ExternalizedSqlViaStringTemplate3()
public interface DummySqlTest extends Transactional<DummySqlTest>, Transmogrifier, CloseMe {

    @SqlUpdate
    public void insertDummy(@Bind(binder = DummySqlTestBinder.class) DummyObject dummy);

    @SqlQuery
    @Mapper(DummySqlTestMapper.class)
    public DummyObject getDummyFromId(@Bind("dummy_id") String dummyId);

    public static class DummySqlTestBinder implements Binder<Bind, DummyObject> {
        @Override
        public void bind(@SuppressWarnings("rawtypes") final SQLStatement stmt, final Bind bind, final DummyObject dummy) {
            stmt.bind("dummy_id", dummy.getKey().toString());
            stmt.bind("value", dummy.getValue());
        }
    }

    public static class DummySqlTestMapper implements ResultSetMapper<DummyObject> {
        @Override
        public DummyObject map(final int index, final ResultSet r, final StatementContext ctx)
                throws SQLException {
            final UUID key = UUID.fromString(r.getString("dummy_id"));
            final String value = r.getString("value");
            return new DummyObject(value, key);
        }
    }
}
