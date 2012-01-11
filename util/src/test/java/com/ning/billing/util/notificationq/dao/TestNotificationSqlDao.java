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

package com.ning.billing.util.notificationq.dao;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.skife.config.ConfigurationObjectFactory;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.tweak.HandleCallback;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.ning.billing.dbi.DBIProvider;
import com.ning.billing.dbi.DbiConfig;
import com.ning.billing.util.notificationq.DefaultNotification;
import com.ning.billing.util.notificationq.Notification;
import com.ning.billing.util.notificationq.NotificationLifecycle.NotificationLifecycleState;
import com.ning.billing.util.notificationq.dao.NotificationSqlDao.NotificationSqlMapper;

@Guice(modules = TestNotificationSqlDao.TestNotificationSqlDaoModule.class)
public class TestNotificationSqlDao {

    private static AtomicInteger sequenceId = new AtomicInteger();

    @Inject
    private DBI dbi;

    private NotificationSqlDao dao;

    @BeforeClass(alwaysRun = true)
    public void setup() {
       dao = dbi.onDemand(NotificationSqlDao.class);
    }


    @BeforeTest
    public void cleanupDb() {
        dbi.withHandle(new HandleCallback<Void>() {

            @Override
            public Void withHandle(Handle handle) throws Exception {
                handle.execute("delete from notifications");
                handle.execute("delete from claimed_notifications");
                return null;
            }
        });
    }

    @Test
    public void testBasic() throws InterruptedException {

        final String ownerId = UUID.randomUUID().toString();

        String notificationKey = UUID.randomUUID().toString();
        DateTime effDt = new DateTime();
        Notification notif = new DefaultNotification(notificationKey, effDt);
        dao.insertNotification(notif);

        Thread.sleep(1000);
        DateTime now = new DateTime();
        List<Notification> notifications = dao.getReadyNotifications(now.toDate(), 3);
        assertNotNull(notifications);
        assertEquals(notifications.size(), 1);

        Notification notification = notifications.get(0);
        assertEquals(notification.getNotificationKey(), notificationKey);
        validateDate(notification.getEffectiveDate(), effDt);
        assertEquals(notification.getOwner(), null);
        assertEquals(notification.getProcessingState(), NotificationLifecycleState.AVAILABLE);
        assertEquals(notification.getNextAvailableDate(), null);

        DateTime nextAvailable = now.plusMinutes(5);
        int res = dao.claimNotification(ownerId, nextAvailable.toDate(), notification.getId().toString(), now.toDate());
        assertEquals(res, 1);
        dao.insertClaimedHistory(sequenceId.incrementAndGet(), ownerId, now.toDate(), notification.getId().toString());

        notification = fetchNotification(notification.getId().toString());
        assertEquals(notification.getNotificationKey(), notificationKey);
        validateDate(notification.getEffectiveDate(), effDt);
        assertEquals(notification.getOwner().toString(), ownerId);
        assertEquals(notification.getProcessingState(), NotificationLifecycleState.IN_PROCESSING);
        validateDate(notification.getNextAvailableDate(), nextAvailable);

        dao.clearNotification(notification.getId().toString(), ownerId);

        notification = fetchNotification(notification.getId().toString());
        assertEquals(notification.getNotificationKey(), notificationKey);
        validateDate(notification.getEffectiveDate(), effDt);
        assertEquals(notification.getOwner(), null);
        assertEquals(notification.getProcessingState(), NotificationLifecycleState.PROCESSED);
        validateDate(notification.getNextAvailableDate(), nextAvailable);

    }

    private Notification fetchNotification(final String notificationId) {
        Notification res =  dbi.withHandle(new HandleCallback<Notification>() {

            @Override
            public Notification withHandle(Handle handle) throws Exception {
                Notification res = handle.createQuery("   select" +
                		" notification_id" +
                		", notification_key" +
                		", created_dt" +
                		", effective_dt" +
                		", processing_owner" +
                		", processing_available_dt" +
                		", processing_state" +
                		"    from notifications " +
                		" where " +
                		" notification_id = '" + notificationId + "';")
                		.map(new NotificationSqlMapper())
                		.first();
                return res;
            }
        });
        return res;
    }

    private void validateDate(DateTime input, DateTime expected) {
        if (input == null && expected != null) {
            Assert.fail("Got input date null");
        }
        if (input != null && expected == null) {
            Assert.fail("Was expecting null date");
        }
        expected = truncateAndUTC(expected);
        input = truncateAndUTC(input);
        Assert.assertEquals(input, expected);
    }

    private DateTime truncateAndUTC(DateTime input) {
        if (input == null) {
            return null;
        }
        DateTime result = input.minus(input.getMillisOfSecond());
        return result.toDateTime(DateTimeZone.UTC);
    }

    public static class TestNotificationSqlDaoModule extends AbstractModule {
        @Override
        protected void configure() {
            bind(DBI.class).toProvider(DBIProvider.class).asEagerSingleton();
            final DbiConfig config = new ConfigurationObjectFactory(System.getProperties()).build(DbiConfig.class);
            bind(DbiConfig.class).toInstance(config);
        }
    }
}
