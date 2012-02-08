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

package com.ning.billing.util.globalLocker;

import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.IDBI;
import org.testng.annotations.Test;

public class TestMysqlGlobalLocker {

    // Used as a manual test to validate the simple DAO by stepping through that locking is done and release correctly
    @Test(enabled=false)
    public void testSimpleLocking() {
        IDBI dbi = new DBI("jdbc:mysql://localhost:3306/killbill?createDatabaseIfNotExist=true", "root", "root");
        GlobalLocker lock = new  MySqlGlobalLocker(dbi);
        lock.lockWithNumberOfTries("test-lock", 3);
        System.out.println("youpihh!");
    }
}
