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

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.ning.billing.invoice.api.DefaultInvoiceService;
import com.ning.billing.util.notificationq.Notification;
import com.ning.billing.util.notificationq.NotificationQueue;
import com.ning.billing.util.notificationq.NotificationQueueService;
import com.ning.billing.util.notificationq.NotificationQueueService.NoSuchNotificationQueue;

public class DefaultNextBillingDatePoster implements NextBillingDatePoster {

    private static final Logger log = LoggerFactory.getLogger(DefaultNextBillingDatePoster.class);

    private final NotificationQueueService notificationQueueService;

    @Inject
    public DefaultNextBillingDatePoster(
            final NotificationQueueService notificationQueueService) {
        super();
        this.notificationQueueService = notificationQueueService;
    }

    @Override
    public void insertNextBillingNotification(final Transmogrifier transactionalDao, final UUID accountId, final UUID subscriptionId, final DateTime futureNotificationTime) {
        final NotificationQueue nextBillingQueue;
        try {


            nextBillingQueue = notificationQueueService.getNotificationQueue(DefaultInvoiceService.INVOICE_SERVICE_NAME,
                                                                             DefaultNextBillingDateNotifier.NEXT_BILLING_DATE_NOTIFIER_QUEUE);
            log.info("Queuing next billing date notification. id: {}, timestamp: {}", subscriptionId.toString(), futureNotificationTime.toString());


            List<Notification> existingNotifications =  nextBillingQueue.getNotificationForAccountAndDate(accountId, futureNotificationTime);
            if (existingNotifications.size() > 0) {
                log.info(String.format("%s : notification for account %s and date %s already exist, skip...",
                        DefaultNextBillingDateNotifier.NEXT_BILLING_DATE_NOTIFIER_QUEUE, accountId, futureNotificationTime));
                return;
            }

            nextBillingQueue.recordFutureNotificationFromTransaction(transactionalDao, futureNotificationTime, accountId, new NextBillingDateNotificationKey(subscriptionId));
        } catch (NoSuchNotificationQueue e) {
            log.error("Attempting to put items on a non-existent queue (NextBillingDateNotifier).", e);
        } catch (IOException e) {
            log.error("Failed to serialize notficationKey for subscriptionId {}", subscriptionId);
        }
    }
}
