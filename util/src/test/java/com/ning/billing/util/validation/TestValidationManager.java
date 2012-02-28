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

package com.ning.billing.util.validation;

import com.ning.billing.dbi.MysqlTestingHelper;
import com.ning.billing.util.validation.dao.DatabaseSchemaDao;
import org.joda.time.DateTime;
import org.skife.jdbi.v2.IDBI;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.Collection;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class TestValidationManager {
    private final MysqlTestingHelper helper = new MysqlTestingHelper();
    private static final String TABLE_NAME = "validation_test";

    private ValidationManager vm;
    
    @BeforeClass(alwaysRun = true)
    public void setup() throws IOException {
        setupDatabase();
        setupDao();
    }

    private void setupDao() {
        IDBI dbi = helper.getDBI();
        DatabaseSchemaDao dao = new DatabaseSchemaDao(dbi);
        vm = new ValidationManager(dao);
        vm.loadSchemaInformation(helper.getDbName());
    }

    private void setupDatabase() throws IOException {
        helper.startMysql();
        StringBuilder ddl = new StringBuilder();
        ddl.append(String.format("DROP TABLE IF EXISTS %s;", TABLE_NAME));
        ddl.append(String.format("CREATE TABLE %s (column1 varchar(1), column2 char(2) NOT NULL, column3 numeric(10,4), column4 datetime) ENGINE = innodb;", TABLE_NAME));
        helper.initDb(ddl.toString());
    }

    @AfterClass(alwaysRun = true)
    public void tearDown() {
        stopDatabase();
    }

    private void stopDatabase() {
        helper.stopMysql();
    }

    @Test
    public void testRetrievingColumnInfo() {
        Collection<ColumnInfo> columnInfoList = vm.getTableInfo(TABLE_NAME);
        assertEquals(columnInfoList.size(), 4);
        assertNotNull(vm.getColumnInfo(TABLE_NAME, "column1"));
        assertNull(vm.getColumnInfo(TABLE_NAME, "bogus"));

        ColumnInfo numericColumnInfo = vm.getColumnInfo(TABLE_NAME, "column3");
        assertNotNull(numericColumnInfo);
        assertEquals(numericColumnInfo.getScale(), 4);
        assertEquals(numericColumnInfo.getPrecision(), 10);
    }

    @Test
    public void testSimpleConfiguration() {
        String STRING_FIELD_2 = "column2";
        String STRING_FIELD_2_PROPERTY = "stringField2";

        SimpleTestClass testObject = new SimpleTestClass("a", null, 7.9, new DateTime());

        vm.setConfiguration(testObject.getClass(), STRING_FIELD_2_PROPERTY, vm.getColumnInfo(TABLE_NAME, STRING_FIELD_2));

        assertTrue(vm.hasConfiguration(testObject.getClass()));
        assertFalse(vm.hasConfiguration(ValidationManager.class));

        ValidationConfiguration configuration = vm.getConfiguration(SimpleTestClass.class);
        assertNotNull(configuration);
        assertTrue(configuration.hasMapping(STRING_FIELD_2_PROPERTY));

        assertFalse(vm.validate(testObject));
        testObject.setStringField2("ab");
        assertTrue(vm.validate(testObject));

    }

    private class SimpleTestClass {
        private String stringField1;
        private String stringField2;
        private double numericField1;
        private DateTime dateTimeField1;

        public SimpleTestClass(String stringField1, String stringField2, double numericField1, DateTime dateTimeField1) {
            this.stringField1 = stringField1;
            this.stringField2 = stringField2;
            this.numericField1 = numericField1;
            this.dateTimeField1 = dateTimeField1;
        }

        public String getStringField1() {
            return stringField1;
        }

        public void setStringField1(String stringField1) {
            this.stringField1 = stringField1;
        }

        public String getStringField2() {
            return stringField2;
        }

        public void setStringField2(String stringField2) {
            this.stringField2 = stringField2;
        }

        public double getNumericField1() {
            return numericField1;
        }

        public void setNumericField1(double numericField1) {
            this.numericField1 = numericField1;
        }

        public DateTime getDateTimeField1() {
            return dateTimeField1;
        }

        public void setDateTimeField1(DateTime dateTimeField1) {
            this.dateTimeField1 = dateTimeField1;
        }
    }
}
