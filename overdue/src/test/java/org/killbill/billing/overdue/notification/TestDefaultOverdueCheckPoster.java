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

package org.killbill.billing.overdue.notification;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.overdue.OverdueTestSuiteWithEmbeddedDB;
import org.killbill.billing.overdue.service.DefaultOverdueService;
import org.killbill.commons.utils.collect.Iterables;
import org.killbill.billing.util.entity.dao.EntitySqlDaoTransactionalJdbiWrapper;
import org.killbill.notificationq.api.NotificationEventWithMetadata;
import org.killbill.notificationq.api.NotificationQueue;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class TestDefaultOverdueCheckPoster extends OverdueTestSuiteWithEmbeddedDB {

    private EntitySqlDaoTransactionalJdbiWrapper entitySqlDaoTransactionalJdbiWrapper;
    private NotificationQueue overdueQueue;
    private DateTime testReferenceTime;

    @Override
    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeMethod();
        entitySqlDaoTransactionalJdbiWrapper = new EntitySqlDaoTransactionalJdbiWrapper(dbi, roDbi, clock, cacheControllerDispatcher, nonEntityDao, internalCallContextFactory);

        overdueQueue = notificationQueueService.getNotificationQueue(DefaultOverdueService.OVERDUE_SERVICE_NAME,
                                                                     OverdueCheckNotifier.OVERDUE_CHECK_NOTIFIER_QUEUE);
        Assert.assertTrue(overdueQueue.isStarted());

        testReferenceTime = clock.getUTCNow();
    }

    @Test(groups = "slow")
    public void testShouldntInsertMultipleNotificationsPerOverdueable() throws Exception {
        final UUID accountId = UUID.randomUUID();
        final Account overdueable = Mockito.mock(Account.class);
        Mockito.when(overdueable.getId()).thenReturn(accountId);

        insertOverdueCheckAndVerifyQueueContent(overdueable, 10, 10);
        insertOverdueCheckAndVerifyQueueContent(overdueable, 5, 5);
        insertOverdueCheckAndVerifyQueueContent(overdueable, 15, 5);

        // Verify the final content of the queue
        final Iterable<?> result = overdueQueue.getFutureNotificationForSearchKeys(internalCallContext.getAccountRecordId(), internalCallContext.getTenantRecordId());
        Assert.assertEquals(Iterables.size(result), 1);
    }

    private void insertOverdueCheckAndVerifyQueueContent(final Account account, final int nbDaysInFuture, final int expectedNbDaysInFuture) throws IOException {
        final DateTime futureNotificationTime = testReferenceTime.plusDays(nbDaysInFuture);

        final OverdueCheckNotificationKey notificationKey = new OverdueCheckNotificationKey(account.getId());
        checkPoster.insertOverdueNotification(account.getId(), futureNotificationTime, OverdueCheckNotifier.OVERDUE_CHECK_NOTIFIER_QUEUE, notificationKey, internalCallContext);

        final List<NotificationEventWithMetadata<OverdueCheckNotificationKey>> notificationsForKey = getNotificationsForOverdueable();
        Assert.assertEquals(notificationsForKey.size(), 1);
        final NotificationEventWithMetadata nm = notificationsForKey.get(0);
        Assert.assertEquals(nm.getEvent(), notificationKey);
        Assert.assertEquals(nm.getEffectiveDate().compareTo(testReferenceTime.plusDays(expectedNbDaysInFuture)), 0);
    }

    private List<NotificationEventWithMetadata<OverdueCheckNotificationKey>> getNotificationsForOverdueable() {
        return entitySqlDaoTransactionalJdbiWrapper.execute(true, entitySqlDaoWrapperFactory -> {
            final Iterable<NotificationEventWithMetadata<OverdueCheckNotificationKey>> result =
                    ((OverdueCheckPoster) checkPoster).getFutureNotificationsForAccountInTransaction(entitySqlDaoWrapperFactory,
                                                                                                     overdueQueue,
                                                                                                     OverdueCheckNotificationKey.class,
                                                                                                     internalCallContext);
            // This will go through all results to close the connection
            return Iterables.toUnmodifiableList(result);
        });
    }
}
