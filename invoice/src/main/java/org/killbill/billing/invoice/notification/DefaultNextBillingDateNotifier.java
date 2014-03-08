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

package org.killbill.billing.invoice.notification;

import java.util.UUID;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.killbill.billing.subscription.api.user.SubscriptionBaseApiException;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.invoice.InvoiceListener;
import org.killbill.billing.invoice.api.DefaultInvoiceService;
import org.killbill.notificationq.api.NotificationEvent;
import org.killbill.notificationq.api.NotificationQueue;
import org.killbill.notificationq.api.NotificationQueueService;
import org.killbill.notificationq.api.NotificationQueueService.NoSuchNotificationQueue;
import org.killbill.notificationq.api.NotificationQueueService.NotificationQueueAlreadyExists;
import org.killbill.notificationq.api.NotificationQueueService.NotificationQueueHandler;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.config.InvoiceConfig;
import org.killbill.billing.subscription.api.SubscriptionBaseInternalApi;

import com.google.inject.Inject;

public class DefaultNextBillingDateNotifier implements NextBillingDateNotifier {

    private static final Logger log = LoggerFactory.getLogger(DefaultNextBillingDateNotifier.class);

    public static final String NEXT_BILLING_DATE_NOTIFIER_QUEUE = "next-billing-date-queue";

    private final NotificationQueueService notificationQueueService;
    private final InvoiceConfig config;
    private final SubscriptionBaseInternalApi subscriptionApi;
    private final InvoiceListener listener;
    private final InternalCallContextFactory callContextFactory;

    private NotificationQueue nextBillingQueue;

    @Inject
    public DefaultNextBillingDateNotifier(final NotificationQueueService notificationQueueService,
                                          final InvoiceConfig config,
                                          final SubscriptionBaseInternalApi subscriptionApi,
                                          final InvoiceListener listener,
                                          final InternalCallContextFactory callContextFactory) {
        this.notificationQueueService = notificationQueueService;
        this.config = config;
        this.subscriptionApi = subscriptionApi;
        this.listener = listener;
        this.callContextFactory = callContextFactory;
    }

    @Override
    public void initialize() throws NotificationQueueAlreadyExists {

        final NotificationQueueHandler notificationQueueHandler = new NotificationQueueHandler() {
            @Override
            public void handleReadyNotification(final NotificationEvent notificationKey, final DateTime eventDate, final UUID userToken, final Long accountRecordId, final Long tenantRecordId) {
                try {
                    if (!(notificationKey instanceof NextBillingDateNotificationKey)) {
                        log.error("Invoice service received an unexpected event type {}", notificationKey.getClass().getName());
                        return;
                    }

                    final NextBillingDateNotificationKey key = (NextBillingDateNotificationKey) notificationKey;
                    try {
                        final SubscriptionBase subscription = subscriptionApi.getSubscriptionFromId(key.getUuidKey(), callContextFactory.createInternalTenantContext(tenantRecordId, accountRecordId));
                        if (subscription == null) {
                            log.warn("Next Billing Date Notification Queue handled spurious notification (key: " + key + ")");
                        } else {
                            processEvent(key.getUuidKey(), eventDate, userToken, accountRecordId, tenantRecordId);
                        }
                    } catch (SubscriptionBaseApiException e) {
                        log.warn("Next Billing Date Notification Queue handled spurious notification (key: " + key + ")", e);
                    }
                } catch (IllegalArgumentException e) {
                    log.error("The key returned from the NextBillingNotificationQueue is not a valid UUID", e);
                }
            }
        };

        nextBillingQueue = notificationQueueService.createNotificationQueue(DefaultInvoiceService.INVOICE_SERVICE_NAME,
                                                                            NEXT_BILLING_DATE_NOTIFIER_QUEUE,
                                                                            notificationQueueHandler);
    }

    @Override
    public void start() {
        nextBillingQueue.startQueue();
    }

    @Override
    public void stop() throws NoSuchNotificationQueue {
        if (nextBillingQueue != null) {
            nextBillingQueue.stopQueue();
            notificationQueueService.deleteNotificationQueue(nextBillingQueue.getServiceName(), nextBillingQueue.getQueueName());
        }
    }

    private void processEvent(final UUID subscriptionId, final DateTime eventDateTime, final UUID userToken, final Long accountRecordId, final Long tenantRecordId) {
        listener.handleNextBillingDateEvent(subscriptionId, eventDateTime, userToken, accountRecordId, tenantRecordId);
    }
}
