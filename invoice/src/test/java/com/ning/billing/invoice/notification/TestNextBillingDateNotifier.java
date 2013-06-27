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

package com.ning.billing.invoice.notification;

import java.util.UUID;
import java.util.concurrent.Callable;

import org.joda.time.DateTime;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.invoice.InvoiceTestSuiteWithEmbeddedDB;
import com.ning.billing.invoice.api.DefaultInvoiceService;
import com.ning.billing.notificationq.NotificationQueue;
import com.ning.billing.util.clock.ClockMock;

import static com.jayway.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.MINUTES;

public class TestNextBillingDateNotifier extends InvoiceTestSuiteWithEmbeddedDB {

    @Test(groups = "slow")
    public void testInvoiceNotifier() throws Exception {

        final UUID accountId = UUID.randomUUID();
        final Subscription subscription = invoiceUtil.createSubscription();
        final UUID subscriptionId = subscription.getId();
        final DateTime now = clock.getUTCNow();


        final NotificationQueue nextBillingQueue = notificationQueueService.getNotificationQueue(DefaultInvoiceService.INVOICE_SERVICE_NAME, DefaultNextBillingDateNotifier.NEXT_BILLING_DATE_NOTIFIER_QUEUE);


        nextBillingQueue.recordFutureNotification(now, new NextBillingDateNotificationKey(subscriptionId), internalCallContext.getUserToken(), internalCallContext.getAccountRecordId(), internalCallContext.getTenantRecordId());

        // Move time in the future after the notification effectiveDate
        ((ClockMock) clock).setDeltaFromReality(3000);

        await().atMost(1, MINUTES).until(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return testInvoiceNotificationQListener.getEventCount() == 1;
            }
        });

        Assert.assertEquals(testInvoiceNotificationQListener.getEventCount(), 1);
        Assert.assertEquals(testInvoiceNotificationQListener.getLatestSubscriptionId(), subscriptionId);
    }
}
