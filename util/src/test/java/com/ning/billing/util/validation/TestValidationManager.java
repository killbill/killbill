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
import com.ning.billing.util.globallocker.TestMysqlGlobalLocker;
import com.ning.billing.util.validation.dao.DatabaseSchemaDao;

import org.apache.commons.io.IOUtils;
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
    
    @BeforeClass(groups = "slow")
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
        final String testDdl = IOUtils.toString(TestMysqlGlobalLocker.class.getResourceAsStream("/com/ning/billing/util/ddl_test.sql"));
        helper.startMysql();
        helper.initDb(testDdl);
    }

    @AfterClass(groups = "slow")
    public void tearDown() {
        stopDatabase();
    }

    private void stopDatabase() {
        helper.stopMysql();
    }

    @Test(groups = "slow")
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

    @Test(groups = "slow")
    public void testSimpleConfiguration() {
        String STRING_FIELD_2 = "column2";
        String STRING_FIELD_2_PROPERTY = "stringField2";

        SimpleTestClass testObject = new SimpleTestClass(null, null, 7.9, new DateTime());

        vm.setConfiguration(testObject.getClass(), STRING_FIELD_2_PROPERTY, vm.getColumnInfo(TABLE_NAME, STRING_FIELD_2));

        assertTrue(vm.hasConfiguration(testObject.getClass()));
        assertFalse(vm.hasConfiguration(ValidationManager.class));

        ValidationConfiguration configuration = vm.getConfiguration(SimpleTestClass.class);
        assertNotNull(configuration);
        assertTrue(configuration.hasMapping(STRING_FIELD_2_PROPERTY));

        // set char field to value that is too short
        assertFalse(vm.validate(testObject));
        testObject.setStringField2("a");
        assertFalse(vm.validate(testObject));

        // set char to excessively long string
        testObject.setStringField2("abc");
        assertFalse(vm.validate(testObject));

        // set char to proper length
        testObject.setStringField2("ab");
        assertTrue(vm.validate(testObject));

        // add the first string field and add a string that exceeds the length
        final String STRING_FIELD_1 = "column1";
        final String STRING_FIELD_1_PROPERTY = "stringField1";
        vm.setConfiguration(testObject.getClass(), STRING_FIELD_1_PROPERTY, vm.getColumnInfo(TABLE_NAME, STRING_FIELD_1));

        assertTrue(vm.validate(testObject));
        testObject.setStringField1("This is a long string that exceeds the length limit for column 1.");
        assertFalse(vm.validate(testObject));
        testObject.setStringField1("This is a short string.");
        assertTrue(vm.validate(testObject));

        // verify numeric values
        final String NUMERIC_FIELD = "column3";
        final String NUMERIC_FIELD_PROPERTY = "numericField1";
        vm.setConfiguration(testObject.getClass(), NUMERIC_FIELD_PROPERTY, vm.getColumnInfo(TABLE_NAME, NUMERIC_FIELD));
        assertTrue(vm.validate(testObject));

        // set the value to have more than 4 decimal places
        testObject.setNumericField1(0.123456);
        assertFalse(vm.validate(testObject));

        // set the value to have more than 10 digits
        testObject.setNumericField1(12345678901234D);
        assertFalse(vm.validate(testObject));

        // set to a valid value
        testObject.setNumericField1(1234567890);
        assertTrue(vm.validate(testObject));

        // check another valid number
        testObject.setNumericField1(123456.7891);
        assertTrue(vm.validate(testObject));

        // check another valid number
        testObject.setNumericField1(12345678.91);
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
