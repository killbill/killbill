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

import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.invoice.InvoiceListener;
import org.killbill.billing.invoice.api.DefaultInvoiceService;
import org.killbill.billing.subscription.api.SubscriptionBaseInternalApi;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.clock.Clock;
import org.killbill.notificationq.api.NotificationEvent;
import org.killbill.notificationq.api.NotificationQueue;
import org.killbill.notificationq.api.NotificationQueueService;
import org.killbill.notificationq.api.NotificationQueueService.NoSuchNotificationQueue;
import org.killbill.notificationq.api.NotificationQueueService.NotificationQueueAlreadyExists;
import org.killbill.notificationq.api.NotificationQueueService.NotificationQueueHandler;
import org.killbill.queue.retry.RetryableHandler;
import org.killbill.queue.retry.RetryableService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;

public class DefaultNextBillingDateNotifier extends RetryableService implements NextBillingDateNotifier {

    public static final String NEXT_BILLING_DATE_NOTIFIER_QUEUE = "next-billing-date-queue";

    private static final Logger log = LoggerFactory.getLogger(DefaultNextBillingDateNotifier.class);

    private final Clock clock;
    private final NotificationQueueService notificationQueueService;
    private final SubscriptionBaseInternalApi subscriptionApi;
    private final InvoiceListener listener;
    private final InternalCallContextFactory internalCallContextFactory;

    private NotificationQueue nextBillingQueue;

    @Inject
    public DefaultNextBillingDateNotifier(final Clock clock,
                                          final NotificationQueueService notificationQueueService,
                                          final SubscriptionBaseInternalApi subscriptionApi,
                                          final InvoiceListener listener,
                                          final InternalCallContextFactory internalCallContextFactory) {
        super(notificationQueueService);
        this.clock = clock;
        this.notificationQueueService = notificationQueueService;
        this.subscriptionApi = subscriptionApi;
        this.listener = listener;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Override
    public void initialize() throws NotificationQueueAlreadyExists {
        final NotificationQueueHandler notificationQueueHandler = new NotificationQueueHandler() {
            @Override
            public void handleReadyNotification(final NotificationEvent notificationKey, final DateTime eventDate, final UUID userToken, final Long accountRecordId, final Long tenantRecordId) {
                if (!(notificationKey instanceof NextBillingDateNotificationKey)) {
                    log.error("Invoice service received an unexpected event className='{}", notificationKey.getClass());
                    return;
                }

                final NextBillingDateNotificationKey key = (NextBillingDateNotificationKey) notificationKey;

                // Just to ensure compatibility with json that might not have that targetDate field (old versions < 0.13.6)
                final DateTime targetDate = key.getTargetDate() != null ? key.getTargetDate() : eventDate;
                final UUID firstSubscriptionId = key.getUuidKeys().iterator().next();
                if (key.isDryRunForInvoiceNotification() != null && // Just to ensure compatibility with json that might not have that field (old versions < 0.13.6)
                    key.isDryRunForInvoiceNotification()) {
                    processEventForInvoiceNotification(firstSubscriptionId, targetDate, userToken, accountRecordId, tenantRecordId);
                } else {
                    final boolean isRescheduled = key.isRescheduled() == Boolean.TRUE; // Handle null value (old versions < 0.19.7)
                    processEventForInvoiceGeneration(firstSubscriptionId, targetDate, isRescheduled, userToken, accountRecordId, tenantRecordId);
                }
            }
        };

        final NotificationQueueHandler retryableHandler = new RetryableHandler(clock, this, notificationQueueHandler);
        nextBillingQueue = notificationQueueService.createNotificationQueue(DefaultInvoiceService.INVOICE_SERVICE_NAME,
                                                                            NEXT_BILLING_DATE_NOTIFIER_QUEUE,
                                                                            retryableHandler);

        super.initialize(nextBillingQueue, notificationQueueHandler);
    }

    @Override
    public void start() {
        super.start();

        nextBillingQueue.startQueue();
    }

    @Override
    public void stop() throws NoSuchNotificationQueue {
        if (nextBillingQueue != null) {
            nextBillingQueue.stopQueue();
            notificationQueueService.deleteNotificationQueue(nextBillingQueue.getServiceName(), nextBillingQueue.getQueueName());
        }

        super.stop();
    }

    private void processEventForInvoiceGeneration(final UUID subscriptionId, final DateTime eventDateTime, final boolean isRescheduled, final UUID userToken, final Long accountRecordId, final Long tenantRecordId) {
        listener.handleNextBillingDateEvent(subscriptionId, eventDateTime, isRescheduled, userToken, accountRecordId, tenantRecordId);
    }

    private void processEventForInvoiceNotification(final UUID subscriptionId, final DateTime eventDateTime, final UUID userToken, final Long accountRecordId, final Long tenantRecordId) {
        listener.handleEventForInvoiceNotification(subscriptionId, eventDateTime, userToken, accountRecordId, tenantRecordId);
    }
}
