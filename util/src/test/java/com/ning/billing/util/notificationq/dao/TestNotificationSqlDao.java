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

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.io.IOUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.tweak.HandleCallback;
import org.testng.Assert;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.ning.billing.dbi.MysqlTestingHelper;
import com.ning.billing.util.notificationq.DefaultNotification;
import com.ning.billing.util.notificationq.Notification;
import com.ning.billing.util.notificationq.NotificationLifecycle.NotificationLifecycleState;
import com.ning.billing.util.notificationq.dao.NotificationSqlDao.NotificationSqlMapper;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

@Test(groups = "slow")
@Guice(modules = TestNotificationSqlDao.TestNotificationSqlDaoModule.class)
public class TestNotificationSqlDao {

    private static AtomicInteger sequenceId = new AtomicInteger();
    private final static String hostname = "Yop";
    
    @Inject
    private IDBI dbi;

    @Inject
    MysqlTestingHelper helper;

    private NotificationSqlDao dao;

    private void startMysql() throws IOException, ClassNotFoundException, SQLException {
        final String ddl = IOUtils.toString(NotificationSqlDao.class.getResourceAsStream("/com/ning/billing/util/ddl.sql"));
        helper.startMysql();
        helper.initDb(ddl);
    }

    @BeforeSuite(groups = "slow")
    public void setup()  {
        try {
            startMysql();
            dao = dbi.onDemand(NotificationSqlDao.class);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @AfterSuite(groups = "slow")
    public void stopMysql()
    {
        if (helper != null) {
            helper.stopMysql();
        }
    }


    @BeforeTest(groups = "slow")
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
        Notification notif = new DefaultNotification("testBasic", hostname, notificationKey, effDt);
        dao.insertNotification(notif);

        Thread.sleep(1000);
        DateTime now = new DateTime();
        List<Notification> notifications = dao.getReadyNotifications(now.toDate(), hostname, 3, "testBasic");
        assertNotNull(notifications);
        assertEquals(notifications.size(), 1);

        Notification notification = notifications.get(0);
        assertEquals(notification.getNotificationKey(), notificationKey);
        validateDate(notification.getEffectiveDate(), effDt);
        assertEquals(notification.getOwner(), null);
        assertEquals(notification.getProcessingState(), NotificationLifecycleState.AVAILABLE);
        assertEquals(notification.getNextAvailableDate(), null);

        DateTime nextAvailable = now.plusMinutes(5);
        int res = dao.claimNotification(ownerId, nextAvailable.toDate(), notification.getId(), now.toDate());
        assertEquals(res, 1);
        dao.insertClaimedHistory(sequenceId.incrementAndGet(), ownerId, now.toDate(), notification.getUUID().toString());

        notification = fetchNotification(notification.getUUID().toString());
        assertEquals(notification.getNotificationKey(), notificationKey);
        validateDate(notification.getEffectiveDate(), effDt);
        assertEquals(notification.getOwner().toString(), ownerId);
        assertEquals(notification.getProcessingState(), NotificationLifecycleState.IN_PROCESSING);
        validateDate(notification.getNextAvailableDate(), nextAvailable);

        dao.clearNotification(notification.getId(), ownerId);

        notification = fetchNotification(notification.getUUID().toString());
        assertEquals(notification.getNotificationKey(), notificationKey);
        validateDate(notification.getEffectiveDate(), effDt);
        //assertEquals(notification.getOwner(), null);
        assertEquals(notification.getProcessingState(), NotificationLifecycleState.PROCESSED);
        validateDate(notification.getNextAvailableDate(), nextAvailable);

    }

    private Notification fetchNotification(final String notificationId) {
        Notification res =  dbi.withHandle(new HandleCallback<Notification>() {

            @Override
            public Notification withHandle(Handle handle) throws Exception {
                Notification res = handle.createQuery("   select" +
                        " id " +
                		", notification_id" +
                		", notification_key" +
                		", created_dt" +
                		", creating_owner" +
                		", effective_dt" +
                		", queue_name" +
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

            final MysqlTestingHelper helper = new MysqlTestingHelper();
            bind(MysqlTestingHelper.class).toInstance(helper);
            IDBI dbi = helper.getDBI();
            bind(IDBI.class).toInstance(dbi);
        }
    }
}
