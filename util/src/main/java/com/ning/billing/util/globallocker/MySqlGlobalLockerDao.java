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

package com.ning.billing.util.globallocker;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

@RegisterMapper(MySqlGlobalLockerDao.LockMapper.class)
public interface MySqlGlobalLockerDao {

    @SqlQuery("Select GET_LOCK(:lockName, :timeout);")
    public Boolean lock(@Bind("lockName") final String lockName, @Bind("timeout") final long timeout);

    @SqlQuery("Select RELEASE_LOCK(:lockName);")
    public Boolean releaseLock(@Bind("lockName") final String lockName);

    @SqlQuery("Select IS_FREE_LOCK(:lockName);")
    public Boolean isFree(@Bind("lockName") final String lockName);

    class LockMapper implements ResultSetMapper<Boolean> {
        @Override
        public Boolean map(final int index, final ResultSet r, final StatementContext ctx) throws SQLException {
            return (r.getByte(1) == 1);
        }
    }
}
