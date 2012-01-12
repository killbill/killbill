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

package com.ning.billing.util.notificationq;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.apache.commons.io.IOUtils;
import org.joda.time.DateTime;
import org.skife.config.ConfigurationObjectFactory;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;
import org.skife.jdbi.v2.tweak.HandleCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.testng.annotations.AfterSuite;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.ning.billing.dbi.DBIProvider;
import com.ning.billing.dbi.DbiConfig;
import com.ning.billing.dbi.MysqlTestingHelper;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.ClockMock;
import com.ning.billing.util.notificationq.NotificationQueueService.NotificationQueueHandler;
import com.ning.billing.util.notificationq.dao.NotificationSqlDao;

@Guice(modules = TestNotificationQueue.TestNotificationQueueModule.class)
public class TestNotificationQueue {

    private final static Logger log = LoggerFactory.getLogger(TestNotificationQueue.class);

    @Inject
    private DBI dbi;

    @Inject
    MysqlTestingHelper helper;

    @Inject
    private Clock clock;

    private DummySqlTest dao;

   // private NotificationQueue queue;

    private void startMysql() throws IOException, ClassNotFoundException, SQLException {
        final String ddl = IOUtils.toString(NotificationSqlDao.class.getResourceAsStream("/com/ning/billing/util/ddl.sql"));
        final String testDdl = IOUtils.toString(NotificationSqlDao.class.getResourceAsStream("/com/ning/billing/util/ddl_test.sql"));
        helper.startMysql();
        helper.initDb(ddl);
        helper.initDb(testDdl);
    }

    @BeforeSuite(alwaysRun = true)
    public void setup() throws Exception {
        startMysql();
        dao = dbi.onDemand(DummySqlTest.class);
    }

    @BeforeTest
    public void beforeTest() {
        dbi.withHandle(new HandleCallback<Void>() {

            @Override
            public Void withHandle(Handle handle) throws Exception {
                handle.execute("delete from notifications");
                handle.execute("delete from claimed_notifications");
                handle.execute("delete from dummy");
                return null;
            }
        });
        // Reset time to real value
        ((ClockMock) clock).resetDeltaFromReality();
    }



    /**
     * Verify that we can call start/stop on a disabled queue and that both start/stop callbacks are called
     *
     * @throws InterruptedException
     */
    @Test
    public void testSimpleQueueDisabled() throws InterruptedException {

        final TestStartStop testStartStop = new TestStartStop(false, false);
        NotificationQueue queue = new NotificationQueue(dbi, clock, "test-svc", "dead",
                new NotificationQueueHandler() {
                    @Override
                    public void handleReadyNotification(String notificationKey) {
                    }
                    @Override
                    public void completedQueueStop() {
                        testStartStop.stopped();
                    }
                    @Override
                    public void completedQueueStart() {
                        testStartStop.started();
                    }
                },
                getNotificationConfig(true, 100, 1, 10000));

        executeTest(testStartStop, queue, new WithTest() {
            @Override
            public void test(final NotificationQueue readyQueue) throws InterruptedException {
                // Do nothing
            }
        });
        assertTrue(true);
    }

    /**
     * Test that we can post a notification in the future from a transaction and get the notification
     * callback with the correct key when the time is ready
     *
     * @throws InterruptedException
     */
    @Test
    public void testSimpleNotification() throws InterruptedException {

        final Map<String, Boolean> expectedNotifications = new TreeMap<String, Boolean>();

        final TestStartStop testStartStop = new TestStartStop(false, false);
        NotificationQueue queue = new NotificationQueue(dbi, clock, "test-svc", "foo",
                new NotificationQueueHandler() {
                    @Override
                    public void handleReadyNotification(String notificationKey) {
                        synchronized (expectedNotifications) {
                            expectedNotifications.put(notificationKey, Boolean.TRUE);
                            expectedNotifications.notify();
                        }
                    }
                    @Override
                    public void completedQueueStop() {
                        testStartStop.stopped();
                    }
                    @Override
                    public void completedQueueStart() {
                        testStartStop.started();
                    }
                },
                getNotificationConfig(false, 100, 1, 10000));


        executeTest(testStartStop, queue, new WithTest() {
            @Override
            public void test(final NotificationQueue readyQueue) throws InterruptedException {

                final UUID key = UUID.randomUUID();
                final DummyObject obj = new DummyObject("foo", key);
                final DateTime now = new DateTime();
                final DateTime readyTime = now.plusMillis(2000);
                final NotificationKey notificationKey = new NotificationKey() {
                    @Override
                    public String toString() {
                        return key.toString();
                    }
                };
                expectedNotifications.put(notificationKey.toString(), Boolean.FALSE);


                // Insert dummy to be processed in 2 sec'
                dao.inTransaction(new Transaction<Void, DummySqlTest>() {
                    @Override
                    public Void inTransaction(DummySqlTest transactional,
                            TransactionStatus status) throws Exception {

                        transactional.insertDummy(obj);
                        readyQueue.recordFutureNotificationFromTransaction(transactional,
                                readyTime, notificationKey);
                        return null;
                    }
                });

                // Move time in the future after the notification effectiveDate
                ((ClockMock) clock).setDeltaFromReality(3000);

                // Notification should have kicked but give it at least a sec' for thread scheduling
                int nbTry = 1;
                boolean success = false;
                do {
                    synchronized(expectedNotifications) {
                        if (expectedNotifications.get(notificationKey.toString())) {
                            success = true;
                            break;
                        }
                        expectedNotifications.wait(1000);
                    }
                } while (nbTry-- > 0);
                assertEquals(success, true);
            }
        });
    }

    @Test
    public void testManyNotifications() throws InterruptedException {
        final Map<String, Boolean> expectedNotifications = new TreeMap<String, Boolean>();

        final TestStartStop testStartStop = new TestStartStop(false, false);
        NotificationQueue queue = new NotificationQueue(dbi, clock, "test-svc", "many",
                new NotificationQueueHandler() {
                    @Override
                    public void handleReadyNotification(String notificationKey) {
                        synchronized (expectedNotifications) {
                            expectedNotifications.put(notificationKey, Boolean.TRUE);
                            expectedNotifications.notify();
                        }
                    }
                    @Override
                    public void completedQueueStop() {
                        testStartStop.stopped();
                    }
                    @Override
                    public void completedQueueStart() {
                        testStartStop.started();
                    }
                },
                getNotificationConfig(false, 100, 10, 10000));


        executeTest(testStartStop, queue, new WithTest() {
            @Override
            public void test(final NotificationQueue readyQueue) throws InterruptedException {

                final DateTime now = clock.getUTCNow();
                final int MAX_NOTIFICATIONS = 100;
                for (int i = 0; i < MAX_NOTIFICATIONS; i++) {

                    final int nextReadyTimeIncrementMs = 1000;

                    final UUID key = UUID.randomUUID();
                    final DummyObject obj = new DummyObject("foo", key);
                    final int currentIteration = i;

                    final NotificationKey notificationKey = new NotificationKey() {
                        @Override
                        public String toString() {
                            return key.toString();
                        }
                    };
                    expectedNotifications.put(notificationKey.toString(), Boolean.FALSE);

                    dao.inTransaction(new Transaction<Void, DummySqlTest>() {
                        @Override
                        public Void inTransaction(DummySqlTest transactional,
                                TransactionStatus status) throws Exception {

                            transactional.insertDummy(obj);
                            readyQueue.recordFutureNotificationFromTransaction(transactional,
                                    now.plus((currentIteration + 1) * nextReadyTimeIncrementMs), notificationKey);
                            return null;
                        }
                    });

                    // Move time in the future after the notification effectiveDate
                    if (i == 0) {
                        ((ClockMock) clock).setDeltaFromReality(nextReadyTimeIncrementMs);
                    } else {
                        ((ClockMock) clock).addDeltaFromReality(nextReadyTimeIncrementMs);
                    }
                }

                // Wait a little longer since there are a lot of callback that need to happen
                int nbTry = MAX_NOTIFICATIONS + 1;
                boolean success = false;
                do {
                    synchronized(expectedNotifications) {

                        Collection<Boolean> completed =  Collections2.filter(expectedNotifications.values(), new Predicate<Boolean>() {
                            @Override
                            public boolean apply(Boolean input) {
                                return input;
                            }
                        });

                        if (completed.size() == MAX_NOTIFICATIONS) {
                            success = true;
                            break;
                        }
                        //log.debug(String.format("BEFORE WAIT : Got %d notifications at time %s (real time %s)", completed.size(), clock.getUTCNow(), new DateTime()));
                        expectedNotifications.wait(1000);
                    }
                } while (nbTry-- > 0);
                assertEquals(success, true);
            }
        });
    }


    NotificationConfig getNotificationConfig(final boolean off,
            final long sleepTime, final int maxReadyEvents, final long claimTimeMs) {
        return new NotificationConfig() {
            @Override
            public boolean isNotificationProcessingOff() {
                return off;
            }
            @Override
            public long getNotificationSleepTimeMs() {
                return sleepTime;
            }
            @Override
            public int getDaoMaxReadyEvents() {
                return maxReadyEvents;
            }
            @Override
            public long getDaoClaimTimeMs() {
                return claimTimeMs;
            }
        };
    }

    private static class TestStartStop {
        private boolean started;
        private boolean stopped;

        public TestStartStop(boolean started, boolean stopped) {
            super();
            this.started = started;
            this.stopped = stopped;
        }

        public void started() {
            synchronized(this) {
                started = true;
                notify();
            }
        }

        public void stopped() {
            synchronized(this) {
                stopped = true;
                notify();
            }
        }

        public boolean waitForStartComplete(int timeoutMs) throws InterruptedException {
            return waitForEventCompletion(timeoutMs, true);
        }

        public boolean waitForStopComplete(int timeoutMs) throws InterruptedException {
            return waitForEventCompletion(timeoutMs, false);
        }

        private boolean waitForEventCompletion(int timeoutMs, boolean start) throws InterruptedException {
            DateTime init = new DateTime();
            synchronized(this) {
                while (! ((start ? started : stopped))) {
                    wait(timeoutMs);
                    if (init.plusMillis(timeoutMs).isAfterNow()) {
                        break;
                    }
                }
            }
            return (start ? started : stopped);
        }
    }

    private interface WithTest {
        public void test(NotificationQueue readyQueue) throws InterruptedException;
    }

    private void executeTest(final TestStartStop testStartStop,
            NotificationQueue queue, WithTest test) throws InterruptedException{

        queue.startQueue();
        boolean started = testStartStop.waitForStartComplete(3000);
        assertEquals(started, true);

        test.test(queue);

        queue.stopQueue();
        boolean stopped = testStartStop.waitForStopComplete(3000);
        assertEquals(stopped, true);
    }


    public static class TestNotificationQueueModule extends AbstractModule {
        @Override
        protected void configure() {

            bind(Clock.class).to(ClockMock.class);

            final MysqlTestingHelper helper = new MysqlTestingHelper();
            bind(MysqlTestingHelper.class).toInstance(helper);
            DBI dbi = helper.getDBI();
            bind(DBI.class).toInstance(dbi);
            /*
            bind(DBI.class).toProvider(DBIProvider.class).asEagerSingleton();
            final DbiConfig config = new ConfigurationObjectFactory(System.getProperties()).build(DbiConfig.class);
            bind(DbiConfig.class).toInstance(config);
            */
        }
    }


}
