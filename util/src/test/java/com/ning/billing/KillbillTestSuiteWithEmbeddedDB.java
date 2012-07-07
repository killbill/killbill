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

package com.ning.billing;

import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.SQLException;

import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;

import com.ning.billing.dbi.MysqlTestingHelper;

public class KillbillTestSuiteWithEmbeddedDB extends KillbillTestSuite {
    protected static final MysqlTestingHelper helper = new MysqlTestingHelper();

    public static MysqlTestingHelper getMysqlTestingHelper() {
        return helper;
    }

    @BeforeSuite(groups = "slow")
    public void startMysql() throws IOException, ClassNotFoundException, SQLException, URISyntaxException {
        helper.startMysql();
        helper.initDb();
        helper.cleanupAllTables();
    }

    @BeforeMethod(groups = "slow")
    public void cleanup() {
        try {
            helper.cleanupAllTables();
        } catch (Exception ignored) {
        }
    }

    @AfterSuite(groups = "slow")
    public void shutdownMysql() throws IOException, ClassNotFoundException, SQLException, URISyntaxException {
        try {
            helper.cleanupAllTables();
            helper.stopMysql();
        } catch (Exception ignored) {
        }
    }
}
