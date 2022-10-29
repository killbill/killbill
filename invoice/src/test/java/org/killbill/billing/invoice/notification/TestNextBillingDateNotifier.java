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

package org.killbill.billing.invoice.notification;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.joda.time.DateTime;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.invoice.InvoiceTestSuiteWithEmbeddedDB;
import org.killbill.billing.platform.api.KillbillService.KILLBILL_SERVICES;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.notificationq.api.NotificationQueue;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.MINUTES;

public class TestNextBillingDateNotifier extends InvoiceTestSuiteWithEmbeddedDB {

    @Test(groups = "slow")
    public void testInvoiceNotifier() throws Exception {
        final Account account = invoiceUtil.createAccount(callContext);
        final Long accountRecordId = nonEntityDao.retrieveAccountRecordIdFromObject(account.getId(), ObjectType.ACCOUNT, null);

        final SubscriptionBase subscription = invoiceUtil.createSubscription();
        final UUID subscriptionId = subscription.getId();
        final DateTime now = clock.getUTCNow();

        final NotificationQueue nextBillingQueue = notificationQueueService.getNotificationQueue(KILLBILL_SERVICES.INVOICE_SERVICE.getServiceName(), DefaultNextBillingDateNotifier.NEXT_BILLING_DATE_NOTIFIER_QUEUE);

        nextBillingQueue.recordFutureNotification(now, new NextBillingDateNotificationKey(null, List.of(subscriptionId), now, Boolean.FALSE, Boolean.FALSE), internalCallContext.getUserToken(), accountRecordId, internalCallContext.getTenantRecordId());

        // Move time in the future after the notification effectiveDate
        clock.setDeltaFromReality(3000);

        await().atMost(1, MINUTES).until(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return testInvoiceNotificationQListener.getEventCount() == 1;
            }
        });

        Assert.assertEquals(testInvoiceNotificationQListener.getEventCount(), 1);
    }
}
