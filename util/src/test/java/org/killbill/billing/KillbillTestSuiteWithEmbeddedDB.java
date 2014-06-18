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

package org.killbill.billing;

import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;

public class KillbillTestSuiteWithEmbeddedDB extends KillbillTestSuite {

    private static final Logger log = LoggerFactory.getLogger(KillbillTestSuiteWithEmbeddedDB.class);

    @BeforeSuite(groups = "slow")
    public void startMysqlBeforeTestSuite() throws IOException, ClassNotFoundException, SQLException, URISyntaxException {
        DBTestingHelper.get().start();
    }

    @BeforeMethod(groups = "slow")
    public void cleanupTablesBetweenMethods() {
        try {
            DBTestingHelper.get().getInstance().cleanupAllTables();
        } catch (final Exception ignored) {
        }
    }

    @AfterSuite(groups = "slow")
    public void shutdownMysqlAfterTestSuite() throws IOException, ClassNotFoundException, SQLException, URISyntaxException {
        if (hasFailed()) {
            log.error("**********************************************************************************************");
            log.error("*** TESTS HAVE FAILED - LEAVING DB RUNNING FOR DEBUGGING - MAKE SURE TO KILL IT ONCE DONE ****");
            log.error(DBTestingHelper.get().getInstance().getCmdLineConnectionString());
            log.error("**********************************************************************************************");
            return;
        }

        try {
            DBTestingHelper.get().getInstance().stop();
        } catch (final Exception ignored) {
        }
    }
}
