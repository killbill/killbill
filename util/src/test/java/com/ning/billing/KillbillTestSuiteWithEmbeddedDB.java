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

import org.skife.jdbi.v2.IDBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;

import com.ning.billing.dbi.DBTestingHelper;
import com.ning.billing.dbi.H2TestingHelper;
import com.ning.billing.dbi.MysqlTestingHelper;

public class KillbillTestSuiteWithEmbeddedDB extends KillbillTestSuite {

    private static final Logger log = LoggerFactory.getLogger(KillbillTestSuiteWithEmbeddedDB.class);

    protected static DBTestingHelper helper;

    static {
        if ("true".equals(System.getProperty("com.ning.billing.dbi.test.h2"))) {
            log.info("Using h2 as the embedded database");
            helper = new H2TestingHelper();
        } else {
            log.info("Using MySQL as the embedded database");
            helper = new MysqlTestingHelper();
        }
    }

    public static DBTestingHelper getDBTestingHelper() {
        return helper;
    }

    public static IDBI getDBI() {
        return helper.getDBI();
    }

    @BeforeSuite(groups = {"slow", "mysql"})
    public void startMysqlBeforeTestSuite() throws IOException, ClassNotFoundException, SQLException, URISyntaxException {
        helper.start();
        helper.initDb();
        helper.cleanupAllTables();
    }

    @BeforeMethod(groups = {"slow", "mysql"})
    public void cleanupTablesBetweenMethods() {
        try {
            helper.cleanupAllTables();
        } catch (Exception ignored) {
        }
    }

    @AfterSuite(groups = {"slow", "mysql"})
    public void shutdownMysqlAfterTestSuite() throws IOException, ClassNotFoundException, SQLException, URISyntaxException {
        if (hasFailed()) {
            log.error("**********************************************************************************************");
            log.error("*** TESTS HAVE FAILED - LEAVING DB RUNNING FOR DEBUGGING - MAKE SURE TO KILL IT ONCE DONE ****");
            log.error(helper.getConnectionString());
            log.error("**********************************************************************************************");
            return;
        }

        try {
            helper.stop();
        } catch (Exception ignored) {
        }
    }
}
