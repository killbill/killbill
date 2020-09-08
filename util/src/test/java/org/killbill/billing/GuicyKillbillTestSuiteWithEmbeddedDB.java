/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

package org.killbill.billing;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.List;

import javax.annotation.Nullable;
import javax.cache.CacheManager;
import javax.inject.Named;
import javax.sql.DataSource;

import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.commons.embeddeddb.EmbeddedDB;
import org.redisson.api.RedissonClient;
import org.skife.jdbi.v2.IDBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;

import static org.killbill.billing.util.glue.CacheModule.REDIS_CACHE_CLIENT;
import static org.killbill.billing.util.glue.IDBISetup.MAIN_RO_IDBI_NAMED;

public class GuicyKillbillTestSuiteWithEmbeddedDB extends GuicyKillbillTestSuite {

    private static final Logger log = LoggerFactory.getLogger(GuicyKillbillTestSuiteWithEmbeddedDB.class);

    @Inject
    protected EmbeddedDB helper;

    @Inject
    protected DataSource dataSource;

    @Inject
    protected IDBI dbi;

    @Inject
    @Named(MAIN_RO_IDBI_NAMED)
    protected IDBI roDbi;

    @Inject
    protected CacheControllerDispatcher controlCacheDispatcher;

    @Nullable
    @Inject(optional = true)
    protected CacheManager cacheManager;

    @Nullable
    @Inject(optional = true)
    @Named(REDIS_CACHE_CLIENT)
    protected RedissonClient redissonCachingClient;

    @BeforeSuite(groups = "slow")
    public void beforeSuite() throws Exception {
        if (hasFailed()) {
            return;
        }

        // Hack to configure log4jdbc -- properties used by tests will be properly setup in @BeforeClass
        getConfigSource(ImmutableMap.<String, String>of());
        DBTestingHelper.get().start();
    }

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        cleanupAllTables();
        if (callContext != null) {
            callContext.setDelegate(null, internalCallContext);
        }
        if (controlCacheDispatcher != null) {
            controlCacheDispatcher.clearAll();
        }
    }

    protected void cleanupAllTables() {
        // Work around tests flakiness (see also RetryableDataSource)
        for (int i = 0; i < 5; i++) {
            try {
                DBTestingHelper.get().getInstance().cleanupAllTables();
                break;
            } catch (final Exception e) {
                if (i == 4) {
                    Assert.fail("Unable to clean database", e);
                }
            }
        }
    }

    @AfterSuite(groups = "slow")
    public void afterSuite() throws Exception {
        if (hasFailed()) {
            threadDump();
            dumpDB();
            log.error("**********************************************************************************************");
            log.error("*** TESTS HAVE FAILED - LEAVING DB RUNNING FOR DEBUGGING - MAKE SURE TO KILL IT ONCE DONE ****");
            log.error(DBTestingHelper.get().getInstance().getCmdLineConnectionString());
            log.error("**********************************************************************************************");
            return;
        }

        if (cacheManager != null) {
            cacheManager.close();
        }

        if (redissonCachingClient != null) {
            redissonCachingClient.shutdown();
        }

        try {
            DBTestingHelper.get().getInstance().stop();
        } catch (final Exception ignored) {
        }
    }

    private void dumpDB() {
        log.error("*********************************************");
        log.error("*** TESTS HAVE FAILED - DUMPING DATABASE ****");
        log.error("*********************************************\n");

        try {
            final EmbeddedDB embeddedDB = DBTestingHelper.get().getInstance();
            final List<String> tables = embeddedDB.getAllTables();

            final Connection connection = embeddedDB.getDataSource().getConnection();
            try {
                for (final String table : tables) {
                    final StringBuilder tableDump = new StringBuilder("Table ").append(table).append("\n");
                    boolean hasData = false;

                    Statement statement = null;
                    try {
                        statement = connection.createStatement();
                        final ResultSet rs = statement.executeQuery("select * from " + table);

                        final ResultSetMetaData metadata = rs.getMetaData();
                        final int columnCount = metadata.getColumnCount();
                        for (int i = 1; i <= columnCount; i++) {
                            tableDump.append(metadata.getColumnName(i)).append(",");
                        }
                        tableDump.append("\n");

                        while (rs.next()) {
                            hasData = true;
                            for (int i = 1; i <= columnCount; i++) {
                                tableDump.append(rs.getString(i)).append(",");
                            }
                            tableDump.append("\n");
                        }
                    } finally {
                        if (statement != null) {
                            statement.close();
                        }
                    }

                    if (hasData) {
                        log.error(tableDump.toString());
                    }
                }
            } finally {
                connection.close();
            }
            log.error("*********************************************");
        } catch (final Exception e) {
            log.error("Unable to dump DB");
        }
    }

    private void threadDump() {
        final ThreadInfo[] threads = ManagementFactory.getThreadMXBean().dumpAllThreads(true, true);
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, Charset.forName("UTF-8")));

        for (int ti = threads.length - 1; ti >= 0; ti--) {
            final ThreadInfo t = threads[ti];
            writer.printf("\"%s\" id=%d state=%s",
                          t.getThreadName(),
                          t.getThreadId(),
                          t.getThreadState());
            final LockInfo lock = t.getLockInfo();
            if (lock != null && t.getThreadState() != Thread.State.BLOCKED) {
                writer.printf("%n    - waiting on <0x%08x> (a %s)",
                              lock.getIdentityHashCode(),
                              lock.getClassName());
                writer.printf("%n    - locked <0x%08x> (a %s)",
                              lock.getIdentityHashCode(),
                              lock.getClassName());
            } else if (lock != null && t.getThreadState() == Thread.State.BLOCKED) {
                writer.printf("%n    - waiting to lock <0x%08x> (a %s)",
                              lock.getIdentityHashCode(),
                              lock.getClassName());
            }

            if (t.isSuspended()) {
                writer.print(" (suspended)");
            }

            if (t.isInNative()) {
                writer.print(" (running in native)");
            }

            writer.println();
            if (t.getLockOwnerName() != null) {
                writer.printf("     owned by %s id=%d%n", t.getLockOwnerName(), t.getLockOwnerId());
            }

            final StackTraceElement[] elements = t.getStackTrace();
            final MonitorInfo[] monitors = t.getLockedMonitors();

            for (int i = 0; i < elements.length; i++) {
                final StackTraceElement element = elements[i];
                writer.printf("    at %s%n", element);
                for (int j = 1; j < monitors.length; j++) {
                    final MonitorInfo monitor = monitors[j];
                    if (monitor.getLockedStackDepth() == i) {
                        writer.printf("      - locked %s%n", monitor);
                    }
                }
            }
            writer.println();

            final LockInfo[] locks = t.getLockedSynchronizers();
            if (locks.length > 0) {
                writer.printf("    Locked synchronizers: count = %d%n", locks.length);
                for (LockInfo l : locks) {
                    writer.printf("      - %s%n", l);
                }
                writer.println();
            }
        }

        writer.println();
        writer.flush();

        log.error("********************************************");
        log.error("*** TESTS HAVE FAILED - DUMPING THREADS ****");
        log.error("********************************************\n");
        log.error(out.toString());
        log.error("********************************************");
    }
}
