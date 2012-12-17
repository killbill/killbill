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

package com.ning.billing.util.globallocker;

import java.io.IOException;
import java.util.UUID;

import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.TransactionCallback;
import org.skife.jdbi.v2.TransactionStatus;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import com.ning.billing.util.UtilTestSuiteWithEmbeddedDB;
import com.ning.billing.util.globallocker.GlobalLocker.LockerType;
import com.ning.billing.util.io.IOUtils;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;

@Guice(modules = TestMysqlGlobalLocker.TestMysqlGlobalLockerModule.class)
public class TestMysqlGlobalLocker extends UtilTestSuiteWithEmbeddedDB {

    @Inject
    private IDBI dbi;

    @BeforeMethod(groups = "mysql")
    public void setup() throws IOException {
        final String testDdl = IOUtils.toString(TestMysqlGlobalLocker.class.getResourceAsStream("/com/ning/billing/util/ddl_test.sql"));
        helper.initDb(testDdl);
    }

    // Used as a manual test to validate the simple DAO by stepping through that locking is done and release correctly
    @Test(groups = "mysql")
    public void testSimpleLocking() {
        final String lockName = UUID.randomUUID().toString();

        final GlobalLocker locker = new MySqlGlobalLocker(dbi);
        final GlobalLock lock = locker.lockWithNumberOfTries(LockerType.ACCOUNT_FOR_INVOICE_PAYMENTS, lockName, 3);

        dbi.inTransaction(new TransactionCallback<Void>() {
            @Override
            public Void inTransaction(final Handle conn, final TransactionStatus status)
                    throws Exception {
                conn.execute("insert into dummy2 (dummy_id) values ('" + UUID.randomUUID().toString() + "')");
                return null;
            }
        });
        Assert.assertEquals(locker.isFree(LockerType.ACCOUNT_FOR_INVOICE_PAYMENTS, lockName), Boolean.FALSE);

        boolean gotException = false;
        try {
            locker.lockWithNumberOfTries(LockerType.ACCOUNT_FOR_INVOICE_PAYMENTS, lockName, 1);
        } catch (LockFailedException e) {
            gotException = true;
        }
        Assert.assertTrue(gotException);

        lock.release();

        Assert.assertEquals(locker.isFree(LockerType.ACCOUNT_FOR_INVOICE_PAYMENTS, lockName), Boolean.TRUE);
    }

    public static final class TestMysqlGlobalLockerModule extends AbstractModule {

        @Override
        protected void configure() {
            final IDBI dbi = getDBI();
            bind(IDBI.class).toInstance(dbi);
        }
    }
}
