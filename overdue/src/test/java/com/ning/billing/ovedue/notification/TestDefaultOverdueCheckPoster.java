/*
 * Copyright 2010-2013 Ning, Inc.
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

package com.ning.billing.ovedue.notification;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.junction.api.Blockable;
import com.ning.billing.junction.api.Blockable.Type;
import com.ning.billing.overdue.OverdueTestSuiteWithEmbeddedDB;
import com.ning.billing.overdue.service.DefaultOverdueService;
import com.ning.billing.util.jackson.ObjectMapper;
import com.ning.billing.util.notificationq.Notification;
import com.ning.billing.util.notificationq.NotificationQueue;

public class TestDefaultOverdueCheckPoster extends OverdueTestSuiteWithEmbeddedDB {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private NotificationQueue overdueQueue;
    private DateTime testReferenceTime;

    @BeforeMethod(groups = "slow")
    public void setUp() throws Exception {
        overdueQueue = notificationQueueService.getNotificationQueue(DefaultOverdueService.OVERDUE_SERVICE_NAME,
                                                                     DefaultOverdueCheckNotifier.OVERDUE_CHECK_NOTIFIER_QUEUE);
        Assert.assertTrue(overdueQueue.isStarted());

        testReferenceTime = clock.getUTCNow();
    }

    @Test(groups = "slow")
    public void testShouldntInsertMultipleNotificationsPerOverdueable() throws Exception {
        final UUID subscriptionId = UUID.randomUUID();
        final Blockable overdueable = Mockito.mock(Subscription.class);
        Mockito.when(overdueable.getId()).thenReturn(subscriptionId);

        verifyQueueContent(overdueable, 10, 10);
        verifyQueueContent(overdueable, 5, 5);
        verifyQueueContent(overdueable, 15, 5);

        // Check we don't conflict with other overdueables
        final UUID bundleId = UUID.randomUUID();
        final Blockable otherOverdueable = Mockito.mock(SubscriptionBundle.class);
        Mockito.when(otherOverdueable.getId()).thenReturn(bundleId);

        verifyQueueContent(otherOverdueable, 10, 10);
        verifyQueueContent(otherOverdueable, 5, 5);
        verifyQueueContent(otherOverdueable, 15, 5);

        // Verify the final content of the queue for each key
        final OverdueCheckNotificationKey notificationKey = new OverdueCheckNotificationKey(subscriptionId, Type.SUBSCRIPTION);
        Assert.assertEquals(overdueQueue.getFutureNotificationsForKey(notificationKey, internalCallContext).size(), 1);
        final OverdueCheckNotificationKey otherNotificationKey = new OverdueCheckNotificationKey(bundleId, Type.SUBSCRIPTION_BUNDLE);
        Assert.assertEquals(overdueQueue.getFutureNotificationsForKey(otherNotificationKey, internalCallContext).size(), 1);
    }

    private void verifyQueueContent(final Blockable overdueable, final int nbDaysInFuture, final int expectedNbDaysInFuture) throws IOException {
        final DateTime futureNotificationTime = testReferenceTime.plusDays(nbDaysInFuture);
        poster.insertOverdueCheckNotification(overdueable, futureNotificationTime, internalCallContext);

        final OverdueCheckNotificationKey notificationKey = new OverdueCheckNotificationKey(overdueable.getId(), Blockable.Type.get(overdueable));
        final List<Notification> notificationsForKey = overdueQueue.getFutureNotificationsForKey(notificationKey, internalCallContext);
        Assert.assertEquals(notificationsForKey.size(), 1);
        Assert.assertEquals(notificationsForKey.get(0).getNotificationKey(), objectMapper.writeValueAsString(notificationKey));
        Assert.assertEquals(notificationsForKey.get(0).getEffectiveDate(), testReferenceTime.plusDays(expectedNbDaysInFuture));
    }
}
