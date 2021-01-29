/*
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

package org.killbill.billing.util.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.TimeZone;

import javax.sql.DataSource;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.chrono.GregorianChronology;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/*
* Those are experiments we can run when we get confused about mysql dates and our binding/mapping function.
*
* The tests are disabled by default, and they are using the very mysql connector -- nothing in between.
* There is a description page https://github.com/killbill/killbill/wiki/Date,-Datetime,-Timezone-and-time-Granularity-in-Kill-Bill.
* that summarizes the conclusions.
 */
public class TestDateInvestigation /* extends UtilTestSuiteWithEmbeddedDB */ {

    private static final TimeZone TZ__GMT = TimeZone.getTimeZone("GMT");
    private static final DateTimeZone DATE_TZ_GMT = DateTimeZone.forTimeZone(TZ__GMT);
    private static final Calendar GMT_CALENDAR = Calendar.getInstance();

    private static final TimeZone TZ_PLUS_8_GMT = TimeZone.getTimeZone("GMT+8:00");
    private static final DateTimeZone DATE_TZ_PLUS_8_GMT = DateTimeZone.forTimeZone(TZ_PLUS_8_GMT);
    private static final Calendar CALENDAR_PLUS_8_GMT = Calendar.getInstance(TZ_PLUS_8_GMT);

    private static final TimeZone TZ_MINUS_20_GMT = TimeZone.getTimeZone("GMT-20:00");
    private static final DateTimeZone DATE_TZ_MINUS_20_GMT = DateTimeZone.forTimeZone(TZ_MINUS_20_GMT);
    private static final Calendar CALENDAR_MINUS_20_GMT = Calendar.getInstance(TZ_MINUS_20_GMT);

    private Connection connection;
    private DataSource rawSource;

    private enum DataSourceType {
        MYSQL_JDBC2,
        MYSQL_MARIADB
    }

    @BeforeTest(groups = "slow")
    public void beforeTest() throws SQLException {
        rawSource = getRawSource(DataSourceType.MYSQL_MARIADB, "killbill", "root", "root");
    }

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws SQLException {
        connection = rawSource.getConnection();
        cleanup();
    }

    @AfterMethod(groups = "slow")
    public void afterMethod() throws SQLException {
        if (connection != null) {
            connection.close();
            connection = null;
        }

    }

    @Test(groups = "slow", enabled = false)
    public void testWithGMTPlus8() throws SQLException {

        final LocalDate date1_1 = new LocalDate(2014, 10, 1, GregorianChronology.getInstance(DATE_TZ_PLUS_8_GMT));
        // We chose a time such that it moves to next day
        final DateTime date2_1 = new DateTime(2014, 10, 1, 22, 48, 56, DATE_TZ_PLUS_8_GMT);

        insertData(date1_1, date2_1, date2_1);

        final FullOfDates result = readData();
        assertEquals(result.getDate1().compareTo(date1_1), 0);
        assertEquals(result.getDate2().compareTo(date2_1), 0);
        assertEquals(result.getDate2().getZone().toString(), "UTC");
    }

    @Test(groups = "slow", enabled = false)
    public void testWithGMTMinus20() throws SQLException {

        final LocalDate date1_1 = new LocalDate(2014, 10, 1, GregorianChronology.getInstance(DATE_TZ_MINUS_20_GMT));
        // We chose a time such that it moves to next day
        final DateTime date2_1 = new DateTime(2014, 10, 1, 16, 48, 56, DATE_TZ_MINUS_20_GMT);

        insertData(date1_1, date2_1, date2_1);

        final FullOfDates result = readData();
        assertEquals(result.getDate1().compareTo(date1_1), 0);
        assertEquals(result.getDate2().compareTo(date2_1), 0);
        assertEquals(result.getDate2().getZone().toString(), "UTC");
    }

    private void cleanup() throws SQLException {
        final PreparedStatement stmt = connection.prepareStatement("delete from full_of_dates;");
        try {
            stmt.execute();
        } finally {
            if (stmt != null) {
                stmt.close();
            }
        }
    }

    private FullOfDates readData() throws SQLException {
        final PreparedStatement stmt = connection.prepareStatement("select * from full_of_dates");
        try {
            final ResultSet rs = stmt.executeQuery();
            rs.next();

            // Read using String -- this will just read without any interpretation
            final String dateString = rs.getString(2);
            final LocalDate d1 = new LocalDate(dateString, DateTimeZone.UTC);

            // Read as a timestamp
            final Timestamp t2 = rs.getTimestamp(3);
            final DateTime d2 = new DateTime(t2.getTime(), DateTimeZone.UTC);
            return new FullOfDates(d1, d2, null);
        } finally {
            if (stmt != null) {
                stmt.close();
            }
        }
    }

    private void insertData(final LocalDate date1, DateTime date2, DateTime date3) throws SQLException {

        final PreparedStatement stmt = connection.prepareStatement("insert into full_of_dates (date1, datetime1, timestamp1) VALUES (?, ?, ?)");
        try {
            // See https://github.com/killbill/killbill-commons/blob/master/jdbi/src/main/java/org/killbill/commons/jdbi/argument/LocalDateArgumentFactory.java
            if (date1 != null) {
                stmt.setString(1, date1.toString());
            }
            // See https://github.com/killbill/killbill-commons/blob/master/jdbi/src/main/java/org/killbill/commons/jdbi/argument/DateTimeArgumentFactory.java
            if (date2 != null) {
                stmt.setTimestamp(2, new Timestamp(date2.toDate().getTime()));
            }
            // We do not use
            stmt.setTimestamp(3, new Timestamp(new DateTime().toDate().getTime()));
            stmt.execute();
        } finally {
            if (stmt != null) {
                stmt.close();
            }
        }
    }

    private DataSource getRawSource(final DataSourceType type, final String dbName, final String user, final String pwd) throws SQLException {
        if (type == DataSourceType.MYSQL_JDBC2) {
            return getRawMysqlDataSource(dbName, user, pwd);
        } else if (type == DataSourceType.MYSQL_MARIADB) {
            return getRawMariaDBDataSource(dbName, user, pwd);
        } else {
            throw new IllegalStateException("Unknow data source " + type);
        }
    }

    private DataSource getRawMysqlDataSource(final String dbName, final String user, final String pwd) {
        final com.mysql.cj.jdbc.MysqlDataSource rawSource = new com.mysql.cj.jdbc.MysqlDataSource();
        rawSource.setDatabaseName(dbName);
        rawSource.setUser(user);
        rawSource.setPassword(pwd);
        rawSource.setPort(3306);
        rawSource.setURL("jdbc:mysql://localhost:3306/killbill?createDatabaseIfNotExist=true&allowMultiQueries=true");
        return rawSource;
    }

    private DataSource getRawMariaDBDataSource(final String dbName, final String user, final String pwd) throws SQLException {
        final org.mariadb.jdbc.MariaDbDataSource rawSource = new org.mariadb.jdbc.MariaDbDataSource();
        rawSource.setUrl("jdbc:mysql://localhost:3306/killbill?createDatabaseIfNotExist=true&allowMultiQueries=true");
        rawSource.setDatabaseName(dbName);
        rawSource.setUser(user);
        rawSource.setPassword(pwd);
        rawSource.setPort(3306);
        return rawSource;
    }

    private static class FullOfDates {

        private final LocalDate date1;
        private final DateTime date2;
        private final DateTime date3;

        public FullOfDates(final LocalDate date1, final DateTime date2, final DateTime date3) {
            this.date1 = date1;
            this.date2 = date2;
            this.date3 = date3;
        }

        public LocalDate getDate1() {
            return date1;
        }

        public DateTime getDate2() {
            return date2;
        }

        public DateTime getDate3() {
            return date3;
        }
    }

}
