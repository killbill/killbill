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

import static com.jayway.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.testng.Assert.assertEquals;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;
import org.joda.time.DateTime;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;
import org.skife.jdbi.v2.tweak.HandleCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.name.Names;
import com.ning.billing.dbi.MysqlTestingHelper;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.ClockMock;
import com.ning.billing.util.notificationq.NotificationQueueService.NotificationQueueHandler;
import com.ning.billing.util.notificationq.dao.NotificationSqlDao;

@Test(groups = "slow")
@Guice(modules = TestNotificationQueue.TestNotificationQueueModule.class)
public class TestNotificationQueue {
    Logger log = LoggerFactory.getLogger(TestNotificationQueue.class);
    @Inject
    private IDBI dbi;

    @Inject
    MysqlTestingHelper helper;

    @Inject
    private Clock clock;

    private DummySqlTest dao;

    private int eventsReceived;

    // private NotificationQueue queue;

    private void startMysql() throws IOException, ClassNotFoundException, SQLException {
        final String ddl = IOUtils.toString(NotificationSqlDao.class.getResourceAsStream("/com/ning/billing/util/ddl.sql"));
        final String testDdl = IOUtils.toString(NotificationSqlDao.class.getResourceAsStream("/com/ning/billing/util/ddl_test.sql"));
        helper.startMysql();
        helper.initDb(ddl);
        helper.initDb(testDdl);
    }

    @BeforeSuite(groups="slow")
    public void setup() throws Exception {
        startMysql();
        dao = dbi.onDemand(DummySqlTest.class);
    }

    @AfterClass(groups="slow")
    public void tearDown() {
        helper.stopMysql();
    }

    @BeforeTest(groups="slow")
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
        eventsReceived=0;
    }



    /**
     * Test that we can post a notification in the future from a transaction and get the notification
     * callback with the correct key when the time is ready
     * @throws Exception
     */
    @Test(groups={"slow"}, enabled = true)
    public void testSimpleNotification() throws Exception {

        final Map<String, Boolean> expectedNotifications = new TreeMap<String, Boolean>();

        final DefaultNotificationQueue queue = new DefaultNotificationQueue(dbi, clock, "test-svc", "foo",
                new NotificationQueueHandler() {
            @Override
            public void handleReadyNotification(String notificationKey, DateTime eventDateTime) {
                synchronized (expectedNotifications) {
                    log.info("Handler received key: " + notificationKey);

                    expectedNotifications.put(notificationKey.toString(), Boolean.TRUE);
                    expectedNotifications.notify();
                }
            }
        },
        getNotificationConfig(false, 100, 1, 10000));


        queue.startQueue();

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
                queue.recordFutureNotificationFromTransaction(transactional,
                        readyTime, notificationKey);
                log.info("Posted key: " + notificationKey);

                return null;
            }
        });

        // Move time in the future after the notification effectiveDate
        ((ClockMock) clock).setDeltaFromReality(3000);

        // Notification should have kicked but give it at least a sec' for thread scheduling
        await().atMost(1, MINUTES).until(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return expectedNotifications.get(notificationKey.toString());
            }
        });

        queue.stopQueue();
	    Assert.assertTrue(expectedNotifications.get(notificationKey.toString()));
	}

    @Test(groups="slow")
    public void testManyNotifications() throws InterruptedException {
        final Map<String, Boolean> expectedNotifications = new TreeMap<String, Boolean>();

        final DefaultNotificationQueue queue = new DefaultNotificationQueue(dbi, clock, "test-svc", "many",
                new NotificationQueueHandler() {
            @Override
            public void handleReadyNotification(String notificationKey, DateTime eventDateTime) {
                synchronized (expectedNotifications) {
                    expectedNotifications.put(notificationKey, Boolean.TRUE);
                    expectedNotifications.notify();
                }
            }
        },
        getNotificationConfig(false, 100, 10, 10000));


        queue.startQueue();

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
                    queue.recordFutureNotificationFromTransaction(transactional,
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

        queue.stopQueue();
		assertEquals(success, true);
	}

    /**
     * Test that we can post a notification in the future from a transaction and get the notification
     * callback with the correct key when the time is ready
     * @throws Exception
     */
    @Test(groups={"slow"}, enabled = true)
    public void testMultipleHandlerNotification() throws Exception {

        final Map<String, Boolean> expectedNotificationsFred = new TreeMap<String, Boolean>();
        final Map<String, Boolean> expectedNotificationsBarney = new TreeMap<String, Boolean>();

        NotificationQueueService notificationQueueService = new DefaultNotificationQueueService(dbi,  clock);

        NotificationConfig config=new NotificationConfig() {
            @Override
            public boolean isNotificationProcessingOff() {
                return false;
            }
            @Override
            public long getNotificationSleepTimeMs() {
                return 10;
            }
            @Override
            public int getDaoMaxReadyEvents() {
                return 1;
            }
            @Override
            public long getDaoClaimTimeMs() {
                return 60000;
            }
        };


        final NotificationQueue queueFred = notificationQueueService.createNotificationQueue("UtilTest", "Fred", new NotificationQueueHandler() {
            @Override
            public void handleReadyNotification(String notificationKey, DateTime eventDateTime)  {
                log.info("Fred received key: " + notificationKey);
                expectedNotificationsFred.put(notificationKey, Boolean.TRUE);
                eventsReceived++;
            }
        },
        config);

        final NotificationQueue queueBarney = notificationQueueService.createNotificationQueue("UtilTest", "Barney", new NotificationQueueHandler() {
            @Override
            public void handleReadyNotification(String notificationKey, DateTime eventDateTime) {
                log.info("Barney received key: " + notificationKey);
                expectedNotificationsBarney.put(notificationKey, Boolean.TRUE);
                eventsReceived++;
            }
        },
        config);

        queueFred.startQueue();
        //		We don't start Barney so it can never pick up notifications


        final UUID key = UUID.randomUUID();
        final DummyObject obj = new DummyObject("foo", key);
        final DateTime now = new DateTime();
        final DateTime readyTime = now.plusMillis(2000);
        final NotificationKey notificationKeyFred = new NotificationKey() {
            @Override
            public String toString() {
                return "Fred" ;
            }
        };


        final NotificationKey notificationKeyBarney = new NotificationKey() {
            @Override
            public String toString() {
                return "Barney" ;
            }
        };

        expectedNotificationsFred.put(notificationKeyFred.toString(), Boolean.FALSE);
        expectedNotificationsFred.put(notificationKeyBarney.toString(), Boolean.FALSE);


        // Insert dummy to be processed in 2 sec'
        dao.inTransaction(new Transaction<Void, DummySqlTest>() {
            @Override
            public Void inTransaction(DummySqlTest transactional,
                    TransactionStatus status) throws Exception {

                transactional.insertDummy(obj);
                queueFred.recordFutureNotificationFromTransaction(transactional,
                        readyTime, notificationKeyFred);
                log.info("posted key: " + notificationKeyFred.toString());
                queueBarney.recordFutureNotificationFromTransaction(transactional,
                        readyTime, notificationKeyBarney);
                log.info("posted key: " + notificationKeyBarney.toString());

                return null;
            }
        });

        // Move time in the future after the notification effectiveDate
        ((ClockMock) clock).setDeltaFromReality(3000);

        // Note the timeout is short on this test, but expected behaviour is that it times out.
        // We are checking that the Fred queue does not pick up the Barney event
        try {
            await().atMost(5, TimeUnit.SECONDS).until(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    return eventsReceived >= 2;
                }
            });
            Assert.fail("There should only have been one event for the queue to pick up - it got more than that");
        } catch (Exception e) {
            // expected behavior
        }

        queueFred.stopQueue();
		Assert.assertTrue(expectedNotificationsFred.get(notificationKeyFred.toString()));
		Assert.assertFalse(expectedNotificationsFred.get(notificationKeyBarney.toString()));
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
    
    
    @Test(groups="slow")
    public void testRemoveNotifications() throws InterruptedException {
        
        final UUID key = UUID.randomUUID();
        final NotificationKey notificationKey = new NotificationKey() {
            @Override
            public String toString() {
                return key.toString();
            }
        };        
        final UUID key2 = UUID.randomUUID();
        final NotificationKey notificationKey2 = new NotificationKey() {
            @Override
            public String toString() {
                return key2.toString();
            }
        };        

        final DefaultNotificationQueue queue = new DefaultNotificationQueue(dbi, clock, "test-svc", "many",
                new NotificationQueueHandler() {
            @Override
            public void handleReadyNotification(String key, DateTime eventDateTime) {
                    if(key.equals(notificationKey) || key.equals(notificationKey2)) { //ig nore stray events from other tests
                        log.info("Received notification with key: " + notificationKey);
                        eventsReceived++;
                    }
            }
        },
        getNotificationConfig(false, 100, 10, 10000));


        queue.startQueue();

        final DateTime start = clock.getUTCNow().plusHours(1);
        final int nextReadyTimeIncrementMs = 1000;
 
        // add 3 events

        dao.inTransaction(new Transaction<Void, DummySqlTest>() {
            @Override
            public Void inTransaction(DummySqlTest transactional,
                    TransactionStatus status) throws Exception {

                queue.recordFutureNotificationFromTransaction(transactional,
                        start.plus(nextReadyTimeIncrementMs), notificationKey);
                queue.recordFutureNotificationFromTransaction(transactional,
                        start.plus(2 *nextReadyTimeIncrementMs), notificationKey);
                queue.recordFutureNotificationFromTransaction(transactional,
                        start.plus(3 * nextReadyTimeIncrementMs), notificationKey2);
                return null;
            }
        });
    
    
      queue.removeNotificationsByKey(key); // should remove 2 of the 3

    // Move time in the future after the notification effectiveDate
        ((ClockMock) clock).setDeltaFromReality(4000000 + nextReadyTimeIncrementMs * 3 );
        
        try {
            await().atMost(10, TimeUnit.SECONDS).until(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    return eventsReceived >= 2;
                }
            });
            Assert.fail("There should only have been only one event left in the queue we got: " + eventsReceived);
        } catch (Exception e) {
            // expected behavior
        }
        log.info("Received " + eventsReceived + " events");
        queue.stopQueue();
    }



    public static class TestNotificationQueueModule extends AbstractModule {
        @Override
        protected void configure() {

            bind(Clock.class).to(ClockMock.class);

            final MysqlTestingHelper helper = new MysqlTestingHelper();
            bind(MysqlTestingHelper.class).toInstance(helper);
            IDBI dbi = helper.getDBI();
            bind(IDBI.class).toInstance(dbi);
            IDBI otherDbi = helper.getDBI();
            bind(IDBI.class).annotatedWith(Names.named("global-lock")).toInstance(otherDbi);
            /*
            bind(DBI.class).toProvider(DBIProvider.class).asEagerSingleton();
            final DbiConfig config = new ConfigurationObjectFactory(System.getProperties()).build(DbiConfig.class);
            bind(DbiConfig.class).toInstance(config);
             */
        }
    }


}
