/*
 * Copyright 2010-2012 Ning, Inc.
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

package org.killbill.billing.util.dao;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

import org.joda.time.DateTimeZone;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.Argument;
import org.skife.jdbi.v2.tweak.ArgumentFactory;

public class DateTimeZoneArgumentFactory implements ArgumentFactory<DateTimeZone> {

    @Override
    public boolean accepts(final Class<?> expectedType, final Object value, final StatementContext ctx) {
        return value instanceof DateTimeZone;
    }

    @Override
    public Argument build(final Class<?> expectedType, final DateTimeZone value, final StatementContext ctx) {
        return new DateTimeZoneArgument(value);
    }

    public class DateTimeZoneArgument implements Argument {

        private final DateTimeZone value;

        public DateTimeZoneArgument(final DateTimeZone value) {
            this.value = value;
        }

        @Override
        public void apply(final int position, final PreparedStatement statement, final StatementContext ctx) throws SQLException {
            if (value != null) {
                statement.setString(position, value.toString());
            } else {
                statement.setNull(position, Types.VARCHAR);
            }
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append("DateTimeZoneArgument");
            sb.append("{value=").append(value);
            sb.append('}');
            return sb.toString();
        }
    }
}
