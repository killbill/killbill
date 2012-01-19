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

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.apache.commons.io.IOUtils;
import org.joda.time.DateTime;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;
import org.skife.jdbi.v2.tweak.HandleCallback;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.ning.billing.dbi.MysqlTestingHelper;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.ClockMock;
import com.ning.billing.util.notificationq.NotificationQueueService.NotificationQueueHandler;
import com.ning.billing.util.notificationq.dao.NotificationSqlDao;

import static org.testng.Assert.assertEquals;

@Guice(modules = TestNotificationQueue.TestNotificationQueueModule.class)
public class TestNotificationQueue {
	@Inject
	private IDBI dbi;

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
	 * Test that we can post a notification in the future from a transaction and get the notification
	 * callback with the correct key when the time is ready
	 *
	 * @throws InterruptedException
	 */
	@Test
	public void testSimpleNotification() throws InterruptedException {

		final Map<String, Boolean> expectedNotifications = new TreeMap<String, Boolean>();

		final DefaultNotificationQueue queue = new DefaultNotificationQueue(dbi, clock, "test-svc", "foo",
				new NotificationQueueHandler() {
			@Override
			public void handleReadyNotification(String notificationKey) {
				synchronized (expectedNotifications) {
					expectedNotifications.put(notificationKey, Boolean.TRUE);
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
				return null;
			}
		});

		// Move time in the future after the notification effectiveDate
		((ClockMock) clock).setDeltaFromReality(3000);

		// Notification should have kicked but give it at least a sec' for thread scheduling
		  
	}

	@Test
	public void testManyNotifications() throws InterruptedException {
		final Map<String, Boolean> expectedNotifications = new TreeMap<String, Boolean>();

		final DefaultNotificationQueue queue = new DefaultNotificationQueue(dbi, clock, "test-svc", "many",
				new NotificationQueueHandler() {
			@Override
			public void handleReadyNotification(String notificationKey) {
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
		assertEquals(success, true);

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


	public static class TestNotificationQueueModule extends AbstractModule {
		@Override
		protected void configure() {

			bind(Clock.class).to(ClockMock.class);

			final MysqlTestingHelper helper = new MysqlTestingHelper();
			bind(MysqlTestingHelper.class).toInstance(helper);
			IDBI dbi = helper.getDBI();
			bind(IDBI.class).toInstance(dbi);
			/*
            bind(DBI.class).toProvider(DBIProvider.class).asEagerSingleton();
            final DbiConfig config = new ConfigurationObjectFactory(System.getProperties()).build(DbiConfig.class);
            bind(DbiConfig.class).toInstance(config);
			 */
		}
	}


}
