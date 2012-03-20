package com.ning.billing.util.entity;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;

public abstract class MapperBase {
    protected DateTime getDate(ResultSet rs, String fieldName) throws SQLException {
        final Timestamp resultStamp = rs.getTimestamp(fieldName);
        return rs.wasNull() ? null : new DateTime(resultStamp).toDateTime(DateTimeZone.UTC);
    }
}
