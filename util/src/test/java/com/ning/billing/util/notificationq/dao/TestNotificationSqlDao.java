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

import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.tweak.HandleCallback;
import org.testng.Assert;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import com.ning.billing.KillbillTestSuiteWithEmbeddedDB;
import com.ning.billing.dbi.MysqlTestingHelper;
import com.ning.billing.util.UtilTestSuiteWithEmbeddedDB;
import com.ning.billing.util.notificationq.DefaultNotification;
import com.ning.billing.util.notificationq.Notification;
import com.ning.billing.util.notificationq.dao.NotificationSqlDao.NotificationSqlMapper;
import com.ning.billing.util.queue.PersistentQueueEntryLifecycle.PersistentQueueEntryLifecycleState;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

@Guice(modules = TestNotificationSqlDao.TestNotificationSqlDaoModule.class)
public class TestNotificationSqlDao extends UtilTestSuiteWithEmbeddedDB {
    private static final UUID accountId = UUID.randomUUID();
    private static final String hostname = "Yop";

    @Inject
    private IDBI dbi;

    @Inject
    MysqlTestingHelper helper;

    private NotificationSqlDao dao;

    @BeforeSuite(groups = "slow")
    public void setup() {
        dao = dbi.onDemand(NotificationSqlDao.class);
    }

    @BeforeTest(groups = "slow")
    public void cleanupDb() {
        dbi.withHandle(new HandleCallback<Void>() {
            @Override
            public Void withHandle(final Handle handle) throws Exception {
                handle.execute("delete from notifications");
                handle.execute("delete from claimed_notifications");
                return null;
            }
        });
    }

    @Test(groups = "slow")
    public void testBasic() throws InterruptedException {
        final String ownerId = UUID.randomUUID().toString();

        final String notificationKey = UUID.randomUUID().toString();
        final DateTime effDt = new DateTime();
        final Notification notif = new DefaultNotification("testBasic", hostname, notificationKey.getClass().getName(), notificationKey, accountId, effDt,
                                                           null, internalCallContext.getTenantRecordId());
        dao.insertNotification(notif, internalCallContext);

        Thread.sleep(1000);
        final DateTime now = new DateTime();
        final List<Notification> notifications = dao.getReadyNotifications(now.toDate(), hostname, 3, internalCallContext);
        assertNotNull(notifications);
        assertEquals(notifications.size(), 1);

        Notification notification = notifications.get(0);
        assertEquals(notification.getNotificationKey(), notificationKey);
        validateDate(notification.getEffectiveDate(), effDt);
        assertEquals(notification.getOwner(), null);
        assertEquals(notification.getProcessingState(), PersistentQueueEntryLifecycleState.AVAILABLE);
        assertEquals(notification.getNextAvailableDate(), null);

        final DateTime nextAvailable = now.plusMinutes(5);
        final int res = dao.claimNotification(ownerId, nextAvailable.toDate(), notification.getId().toString(), now.toDate(), internalCallContext);
        assertEquals(res, 1);
        dao.insertClaimedHistory(ownerId, now.toDate(), notification.getId().toString(), internalCallContext);

        notification = fetchNotification(notification.getId().toString());
        assertEquals(notification.getNotificationKey(), notificationKey);
        validateDate(notification.getEffectiveDate(), effDt);
        assertEquals(notification.getOwner(), ownerId);
        assertEquals(notification.getProcessingState(), PersistentQueueEntryLifecycleState.IN_PROCESSING);
        validateDate(notification.getNextAvailableDate(), nextAvailable);

        dao.clearNotification(notification.getId().toString(), ownerId, internalCallContext);

        notification = fetchNotification(notification.getId().toString());
        assertEquals(notification.getNotificationKey(), notificationKey);
        validateDate(notification.getEffectiveDate(), effDt);
        //assertEquals(notification.getOwner(), null);
        assertEquals(notification.getProcessingState(), PersistentQueueEntryLifecycleState.PROCESSED);
        validateDate(notification.getNextAvailableDate(), nextAvailable);
    }

    @Test(groups = "slow")
    public void testGetByAccountAndDate() throws InterruptedException {
        final String notificationKey = UUID.randomUUID().toString();
        final DateTime effDt = new DateTime();
        final Notification notif1 = new DefaultNotification("testBasic1", hostname, notificationKey.getClass().getName(), notificationKey, accountId, effDt,
                                                            null, internalCallContext.getTenantRecordId());
        dao.insertNotification(notif1, internalCallContext);

        final Notification notif2 = new DefaultNotification("testBasic2", hostname, notificationKey.getClass().getName(), notificationKey, accountId, effDt,
                                                            null, internalCallContext.getTenantRecordId());
        dao.insertNotification(notif2, internalCallContext);

        List<Notification> notifications = dao.getNotificationForAccountAndDate(accountId.toString(), effDt.toDate(), internalCallContext);
        assertEquals(notifications.size(), 2);
        for (final Notification cur : notifications) {
            Assert.assertEquals(cur.getProcessingState(), PersistentQueueEntryLifecycleState.AVAILABLE);
            dao.removeNotification(cur.getId().toString(), internalCallContext);
        }

        notifications = dao.getNotificationForAccountAndDate(accountId.toString(), effDt.toDate(), internalCallContext);
        assertEquals(notifications.size(), 2);
        for (final Notification cur : notifications) {
            Assert.assertEquals(cur.getProcessingState(), PersistentQueueEntryLifecycleState.REMOVED);
        }
    }

    private Notification fetchNotification(final String notificationId) {
        return dbi.withHandle(new HandleCallback<Notification>() {
            @Override
            public Notification withHandle(final Handle handle) throws Exception {
                return handle.createQuery("   select" +
                                                  " record_id " +
                                                  ", id" +
                                                  ", class_name" +
                                                  ", account_id" +
                                                  ", notification_key" +
                                                  ", created_date" +
                                                  ", creating_owner" +
                                                  ", effective_date" +
                                                  ", queue_name" +
                                                  ", processing_owner" +
                                                  ", processing_available_date" +
                                                  ", processing_state" +
                                                  ", account_record_id" +
                                                  ", tenant_record_id" +
                                                  "    from notifications " +
                                                  " where " +
                                                  " id = '" + notificationId + "';")
                             .map(new NotificationSqlMapper())
                             .first();
            }
        });
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

    private DateTime truncateAndUTC(final DateTime input) {
        if (input == null) {
            return null;
        }
        final DateTime result = input.minus(input.getMillisOfSecond());
        return result.toDateTime(DateTimeZone.UTC);
    }

    public static class TestNotificationSqlDaoModule extends AbstractModule {
        @Override
        protected void configure() {
            final MysqlTestingHelper helper = KillbillTestSuiteWithEmbeddedDB.getMysqlTestingHelper();
            bind(MysqlTestingHelper.class).toInstance(helper);
            final IDBI dbi = helper.getDBI();
            bind(IDBI.class).toInstance(dbi);
        }
    }
}
