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
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.ning.billing.dbi.MysqlTestingHelper;
import com.ning.billing.util.globallocker.GlobalLock;
import com.ning.billing.util.globallocker.GlobalLocker;
import com.ning.billing.util.globallocker.LockFailedException;
import com.ning.billing.util.globallocker.MySqlGlobalLocker;
import com.ning.billing.util.globallocker.GlobalLocker.LockerService;

@Guice(modules=TestMysqlGlobalLocker.TestMysqlGlobalLockerModule.class)
public class TestMysqlGlobalLocker {

    @Inject
    private IDBI dbi;

    @Inject
    private MysqlTestingHelper helper;

    @BeforeClass(alwaysRun=true)
    public void setup() throws IOException  {
        helper.startMysql();
        createSimpleTable(dbi);
    }

    @AfterClass(alwaysRun=true)
    public void tearDown() {
        helper.stopMysql();
    }

    // Used as a manual test to validate the simple DAO by stepping through that locking is done and release correctly
    @Test(groups= "slow", enabled = true)
    public void testSimpleLocking() {

        final String lockName = UUID.randomUUID().toString();

        GlobalLocker locker = new MySqlGlobalLocker(dbi);
        GlobalLock lock = locker.lockWithNumberOfTries(LockerService.INVOICE, lockName, 3);

        dbi.inTransaction(new TransactionCallback<Void>() {
            @Override
            public Void inTransaction(Handle conn, TransactionStatus status)
                    throws Exception {
                conn.execute("insert into dummy (dummy_id) values ('" + UUID.randomUUID().toString()  + "')");
                return null;
            }
        });
        Assert.assertEquals(locker.isFree(LockerService.INVOICE, lockName), Boolean.FALSE);

        boolean gotException = false;
        try {
            locker.lockWithNumberOfTries(LockerService.INVOICE, lockName, 1);
        } catch (LockFailedException e) {
            gotException = true;
        }
        Assert.assertTrue(gotException);

        lock.release();

        Assert.assertEquals(locker.isFree(LockerService.INVOICE, lockName), Boolean.TRUE);
    }

    private void createSimpleTable(IDBI dbi) {
        dbi.inTransaction(new TransactionCallback<Void>() {

            @Override
            public Void inTransaction(Handle h, TransactionStatus status)
                    throws Exception {
                h.execute("create table dummy " +
                        "(id int(11) unsigned NOT NULL AUTO_INCREMENT, " +
                        "dummy_id char(36) NOT NULL, " +
                        "PRIMARY KEY(id)" +
                		") ENGINE=innodb;");
                return null;
            }
        });
    }

    public final static class TestMysqlGlobalLockerModule extends AbstractModule {

        @Override
        protected void configure() {
            MysqlTestingHelper helper = new MysqlTestingHelper();
            bind(MysqlTestingHelper.class).toInstance(helper);
            final IDBI dbi = helper.getDBI();
            bind(IDBI.class).toInstance(dbi);
        }
    }
}
