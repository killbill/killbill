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

package org.killbill.billing.util.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;

public abstract class MapperBase {
    protected LocalDate getDate(final ResultSet rs, final String fieldName) throws SQLException {
        final Date resultStamp = rs.getDate(fieldName);
        return rs.wasNull() ? null : new LocalDate(resultStamp, DateTimeZone.UTC);
    }

    protected DateTime getDateTime(final ResultSet rs, final String fieldName) throws SQLException {
        final Timestamp resultStamp = rs.getTimestamp(fieldName);
        return rs.wasNull() ? null : new DateTime(resultStamp).toDateTime(DateTimeZone.UTC);
    }

    protected UUID getUUID(final ResultSet resultSet, final String fieldName) throws SQLException {
        final String result = resultSet.getString(fieldName);
        return result == null ? null : UUID.fromString(result);
    }
}
