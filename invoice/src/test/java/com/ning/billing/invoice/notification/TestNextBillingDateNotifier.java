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

package com.ning.billing.invoice.notification;

import static com.jayway.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.MINUTES;

import java.io.IOException;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.apache.commons.io.IOUtils;
import org.joda.time.DateTime;
import org.skife.config.ConfigurationObjectFactory;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.google.common.eventbus.Subscribe;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import com.ning.billing.config.InvoiceConfig;
import com.ning.billing.dbi.MysqlTestingHelper;
import com.ning.billing.lifecycle.KillbillService.ServiceException;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.ClockMock;
import com.ning.billing.util.eventbus.Bus;
import com.ning.billing.util.eventbus.MemoryEventBus;
import com.ning.billing.util.notificationq.DefaultNotificationQueueService;
import com.ning.billing.util.notificationq.DummySqlTest;
import com.ning.billing.util.notificationq.NotificationQueueService;
import com.ning.billing.util.notificationq.dao.NotificationSqlDao;

public class TestNextBillingDateNotifier {

	private Clock clock;
	private DefaultNextBillingDateNotifier notifier;
	private DummySqlTest dao;
	private Bus eventBus;
	private MysqlTestingHelper helper;
	
	@BeforeClass(groups={"setup"})
	public void setup() throws ServiceException, IOException, ClassNotFoundException, SQLException {
		//TestApiBase.loadSystemPropertiesFromClasspath("/entitlement.properties");
        final Injector g = Guice.createInjector(Stage.PRODUCTION,  new AbstractModule() {
			protected void configure() {
				 bind(Clock.class).to(ClockMock.class).asEagerSingleton();
				 bind(Bus.class).to(MemoryEventBus.class).asEagerSingleton();
				 bind(NotificationQueueService.class).to(DefaultNotificationQueueService.class).asEagerSingleton();
				 final InvoiceConfig config = new ConfigurationObjectFactory(System.getProperties()).build(InvoiceConfig.class);
				 bind(InvoiceConfig.class).toInstance(config);
				 final MysqlTestingHelper helper = new MysqlTestingHelper();
				 bind(MysqlTestingHelper.class).toInstance(helper);
				 DBI dbi = helper.getDBI();
				 bind(DBI.class).toInstance(dbi);

			}  	
        });

        clock = g.getInstance(Clock.class);
        DBI dbi = g.getInstance(DBI.class);
        dao = dbi.onDemand(DummySqlTest.class);
        eventBus = g.getInstance(Bus.class);
        helper = g.getInstance(MysqlTestingHelper.class);
        notifier = new DefaultNextBillingDateNotifier(g.getInstance(NotificationQueueService.class), eventBus, g.getInstance(InvoiceConfig.class));
        startMysql();
	}
	
	private void startMysql() throws IOException, ClassNotFoundException, SQLException {
		final String ddl = IOUtils.toString(NotificationSqlDao.class.getResourceAsStream("/com/ning/billing/util/ddl.sql"));
		final String testDdl = IOUtils.toString(NotificationSqlDao.class.getResourceAsStream("/com/ning/billing/util/ddl_test.sql"));
		helper.startMysql();
		helper.initDb(ddl);
		helper.initDb(testDdl);
	}

	public static class NextBillingEventListener {
		private int eventCount=0;

		public int getEventCount() {
			return eventCount;
		}

		@Subscribe
		public synchronized void processEvent(NextBillingDateEvent event) {
			eventCount++;
			//log.debug("Got event {} {}", event.name, event.value);
		}
	}
	
	@Test(enabled=true, groups="slow")
	public void test() throws Exception {
		final UUID subscriptionId = new UUID(0L,1L);
		final DateTime now = new DateTime();
		final DateTime readyTime = now.plusMillis(2000);

		final NextBillingEventListener listener = new NextBillingEventListener();
		eventBus.start();
		notifier.initialize();
		notifier.start();
		
        eventBus.register(listener);
		dao.inTransaction(new Transaction<Void, DummySqlTest>() {
			@Override
			public Void inTransaction(DummySqlTest transactional,
					TransactionStatus status) throws Exception {

				notifier.insertNextBillingNotification(transactional, subscriptionId, readyTime);
				return null;
			}
		});

		// Move time in the future after the notification effectiveDate
		((ClockMock) clock).setDeltaFromReality(3000);


	    await().atMost(1, MINUTES).until(new Callable<Boolean>() {
	            @Override
	            public Boolean call() throws Exception {
	                return listener.getEventCount() == 1;
	            }
	        });

		Assert.assertEquals(listener.getEventCount(), 1);
	}
}
