/*
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.invoice.InvoiceDispatcher.FutureAccountNotifications;
import org.killbill.billing.invoice.InvoiceDispatcher.FutureAccountNotifications.FutureAccountNotificationsBuilder;
import org.killbill.billing.invoice.InvoiceTestSuiteWithEmbeddedDB;
import org.killbill.billing.invoice.api.DefaultInvoiceService;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.notificationq.api.NotificationEventWithMetadata;
import org.killbill.notificationq.api.NotificationQueue;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

public class TestNextBillingDatePoster extends InvoiceTestSuiteWithEmbeddedDB {

    protected KillbillConfigSource getConfigSource() {
        final ImmutableMap<String, String> extraProperties = ImmutableMap.<String, String>builder()
                .put("org.killbill.invoice.dryRunNotificationSchedule", "48h")
                .build();
        return getConfigSource("/resource.properties", extraProperties);
    }

    @Test(groups = "slow")
    public void testDryRunReInsertion() throws Exception {
        final Account account = invoiceUtil.createAccount(callContext);
        final Long accountRecordId = nonEntityDao.retrieveAccountRecordIdFromObject(account.getId(), ObjectType.ACCOUNT, null);

        final LocalDate notificationDate = clock.getUTCToday().plusDays(30);

        final SubscriptionBase subscription = invoiceUtil.createSubscription();
        final UUID subscriptionId = subscription.getId();

        final FutureAccountNotifications futureAccountNotifications = createFutureAccountNotifications(subscriptionId, notificationDate);

        invoiceDao.setFutureAccountNotificationsForEmptyInvoice(account.getId(), futureAccountNotifications, internalCallContext);
        invoiceDao.setFutureAccountNotificationsForEmptyInvoice(account.getId(), futureAccountNotifications, internalCallContext);

        final NotificationQueue nextBillingQueue = notificationQueueService.getNotificationQueue(DefaultInvoiceService.INVOICE_SERVICE_NAME,
                                                                                                 DefaultNextBillingDateNotifier.NEXT_BILLING_DATE_NOTIFIER_QUEUE);
        final Iterable<NotificationEventWithMetadata<NextBillingDateNotificationKey>> futureNotifications = nextBillingQueue.getFutureNotificationForSearchKeys(accountRecordId, internalCallContext.getTenantRecordId());
        final ImmutableList<NotificationEventWithMetadata<NextBillingDateNotificationKey>> futureNotificationsList = ImmutableList.copyOf(futureNotifications);
        Assert.assertEquals(futureNotificationsList.size(), 1);

        // We expect only one notification for which effectiveDate matches our original effectiveDate (conversion DateTime -> LocalDate -> DateTime)
        final NotificationEventWithMetadata<NextBillingDateNotificationKey> notification = futureNotificationsList.get(0);
        Assert.assertEquals(notification.getEffectiveDate(), internalCallContext.toUTCDateTime(notificationDate));

        final Iterable<UUID> uuidKeys = notification.getEvent().getUuidKeys();
        Assert.assertFalse(Iterables.isEmpty(uuidKeys));
        final List<UUID> uuidKeysList = ImmutableList.copyOf(uuidKeys);
        Assert.assertEquals(uuidKeysList.size(), 1);
        Assert.assertEquals(uuidKeysList.get(0), subscriptionId);
    }

    @Test(groups = "slow")
    public void testDryRunUpdateWithNewSubscription() throws Exception {
        final Account account = invoiceUtil.createAccount(callContext);
        final Long accountRecordId = nonEntityDao.retrieveAccountRecordIdFromObject(account.getId(), ObjectType.ACCOUNT, null);

        final LocalDate notificationDate = clock.getUTCToday().plusDays(30);

        final SubscriptionBase subscription1 = invoiceUtil.createSubscription();
        final UUID subscriptionId1 = subscription1.getId();

        final FutureAccountNotifications futureAccountNotifications1 = createFutureAccountNotifications(subscriptionId1, notificationDate);
        invoiceDao.setFutureAccountNotificationsForEmptyInvoice(account.getId(), futureAccountNotifications1, internalCallContext);

        final SubscriptionBase subscription2 = invoiceUtil.createSubscription();
        final UUID subscriptionId2 = subscription2.getId();

        final FutureAccountNotifications futureAccountNotifications2 = createFutureAccountNotifications(subscriptionId2, notificationDate);
        invoiceDao.setFutureAccountNotificationsForEmptyInvoice(account.getId(), futureAccountNotifications2, internalCallContext);


        final NotificationQueue nextBillingQueue = notificationQueueService.getNotificationQueue(DefaultInvoiceService.INVOICE_SERVICE_NAME,
                                                                                                 DefaultNextBillingDateNotifier.NEXT_BILLING_DATE_NOTIFIER_QUEUE);

        final Iterable<NotificationEventWithMetadata<NextBillingDateNotificationKey>> futureNotifications = nextBillingQueue.getFutureNotificationForSearchKeys(accountRecordId, internalCallContext.getTenantRecordId());
        final ImmutableList<NotificationEventWithMetadata<NextBillingDateNotificationKey>> futureNotificationsList = ImmutableList.copyOf(futureNotifications);
        Assert.assertEquals(futureNotificationsList.size(), 1);

        // We expect only one notification but this time we should see a list with both subscriptionIds.
        final NotificationEventWithMetadata<NextBillingDateNotificationKey> notification = futureNotificationsList.get(0);
        Assert.assertEquals(notification.getEffectiveDate(), internalCallContext.toUTCDateTime(notificationDate));

        final Iterable<UUID> uuidKeys = notification.getEvent().getUuidKeys();
        Assert.assertFalse(Iterables.isEmpty(uuidKeys));
        final List<UUID> uuidKeysList = ImmutableList.copyOf(uuidKeys);
        Assert.assertEquals(uuidKeysList.size(), 2);
        Assert.assertEquals(uuidKeysList.get(0), subscriptionId1);
        Assert.assertEquals(uuidKeysList.get(1), subscriptionId2);
    }


    @Test(groups = "slow")
    public void testDryRunWithSameSubscriptionLater() throws Exception {
        final Account account = invoiceUtil.createAccount(callContext);
        final Long accountRecordId = nonEntityDao.retrieveAccountRecordIdFromObject(account.getId(), ObjectType.ACCOUNT, null);

        final LocalDate notificationDate1 = clock.getUTCToday().plusDays(30);

        final SubscriptionBase subscription = invoiceUtil.createSubscription();
        final UUID subscriptionId = subscription.getId();

        final FutureAccountNotifications futureAccountNotifications1 = createFutureAccountNotifications(subscriptionId, notificationDate1);

        // Add 3 seconds to make it more interesting
        clock.addDeltaFromReality(3000);

        invoiceDao.setFutureAccountNotificationsForEmptyInvoice(account.getId(), futureAccountNotifications1, internalCallContext);

        clock.addDays(1);
        final LocalDate notificationDate2 = clock.getUTCToday().plusDays(30);

        final FutureAccountNotifications futureAccountNotifications2 = createFutureAccountNotifications(subscriptionId, notificationDate2);


        invoiceDao.setFutureAccountNotificationsForEmptyInvoice(account.getId(), futureAccountNotifications2, internalCallContext);

        final NotificationQueue nextBillingQueue = notificationQueueService.getNotificationQueue(DefaultInvoiceService.INVOICE_SERVICE_NAME,
                                                                                                 DefaultNextBillingDateNotifier.NEXT_BILLING_DATE_NOTIFIER_QUEUE);
        final Iterable<NotificationEventWithMetadata<NextBillingDateNotificationKey>> futureNotifications = nextBillingQueue.getFutureNotificationForSearchKeys(accountRecordId, internalCallContext.getTenantRecordId());
        final ImmutableList<NotificationEventWithMetadata<NextBillingDateNotificationKey>> futureNotificationsList = ImmutableList.copyOf(futureNotifications);
        // We expect only two notifications, one for each date
        Assert.assertEquals(futureNotificationsList.size(), 2);

        final NotificationEventWithMetadata<NextBillingDateNotificationKey> notification1 = futureNotificationsList.get(0);
        Assert.assertEquals(notification1.getEffectiveDate(), internalCallContext.toUTCDateTime(notificationDate1));

        final Iterable<UUID> uuidKeys1 = notification1.getEvent().getUuidKeys();
        Assert.assertFalse(Iterables.isEmpty(uuidKeys1));
        final List<UUID> uuidKeysList1 = ImmutableList.copyOf(uuidKeys1);
        Assert.assertEquals(uuidKeysList1.size(), 1);
        Assert.assertEquals(uuidKeysList1.get(0), subscriptionId);


        final NotificationEventWithMetadata<NextBillingDateNotificationKey> notification2 = futureNotificationsList.get(1);
        Assert.assertEquals(notification2.getEffectiveDate(), internalCallContext.toUTCDateTime(notificationDate2));

        final Iterable<UUID> uuidKeys2 = notification2.getEvent().getUuidKeys();
        Assert.assertFalse(Iterables.isEmpty(uuidKeys2));
        final List<UUID> uuidKeysList2 = ImmutableList.copyOf(uuidKeys2);
        Assert.assertEquals(uuidKeysList2.size(), 1);
        Assert.assertEquals(uuidKeysList2.get(0), subscriptionId);

    }

    private FutureAccountNotifications createFutureAccountNotifications(final UUID subscriptionId, final LocalDate notificationDate) {
        final Map<LocalDate, Set<UUID>> notificationListForDryRun = new HashMap<LocalDate, Set<UUID>>();
        notificationListForDryRun.put(notificationDate, ImmutableSet.<UUID>of(subscriptionId));

        return new FutureAccountNotificationsBuilder()
                .setNotificationListForDryRun(notificationListForDryRun)
                .build();

    }

}
