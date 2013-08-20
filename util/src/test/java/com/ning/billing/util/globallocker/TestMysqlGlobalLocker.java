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
import org.skife.jdbi.v2.TransactionCallback;
import org.skife.jdbi.v2.TransactionStatus;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.commons.locker.GlobalLock;
import com.ning.billing.commons.locker.GlobalLocker;
import com.ning.billing.commons.locker.LockFailedException;
import com.ning.billing.commons.locker.mysql.MySqlGlobalLocker;
import com.ning.billing.util.UtilTestSuiteWithEmbeddedDB;

public class TestMysqlGlobalLocker extends UtilTestSuiteWithEmbeddedDB {

    // Used as a manual test to validate the simple DAO by stepping through that locking is done and release correctly
    @Test(groups = "mysql")
    public void testSimpleLocking() throws IOException, LockFailedException {
        final String lockName = UUID.randomUUID().toString();

        final GlobalLocker locker = new MySqlGlobalLocker(dataSource);
        final GlobalLock lock = locker.lockWithNumberOfTries(LockerType.ACCOUNT_FOR_INVOICE_PAYMENTS.toString(), lockName, 3);

        dbi.inTransaction(new TransactionCallback<Void>() {
            @Override
            public Void inTransaction(final Handle conn, final TransactionStatus status)
                    throws Exception {
                conn.execute("insert into dummy2 (dummy_id) values ('" + UUID.randomUUID().toString() + "')");
                return null;
            }
        });
        Assert.assertEquals(locker.isFree(LockerType.ACCOUNT_FOR_INVOICE_PAYMENTS.toString(), lockName), false);

        boolean gotException = false;
        try {
            locker.lockWithNumberOfTries(LockerType.ACCOUNT_FOR_INVOICE_PAYMENTS.toString(), lockName, 1);
        } catch (LockFailedException e) {
            gotException = true;
        }
        Assert.assertTrue(gotException);

        lock.release();

        Assert.assertEquals(locker.isFree(LockerType.ACCOUNT_FOR_INVOICE_PAYMENTS.toString(), lockName), true);
    }
}
