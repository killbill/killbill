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

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.subscription.api.user.SubscriptionBaseApiException;
import com.ning.billing.subscription.api.SubscriptionBase;
import com.ning.billing.invoice.InvoiceListener;
import com.ning.billing.invoice.api.DefaultInvoiceService;
import com.ning.billing.notificationq.api.NotificationEvent;
import com.ning.billing.notificationq.api.NotificationQueue;
import com.ning.billing.notificationq.api.NotificationQueueService;
import com.ning.billing.notificationq.api.NotificationQueueService.NoSuchNotificationQueue;
import com.ning.billing.notificationq.api.NotificationQueueService.NotificationQueueAlreadyExists;
import com.ning.billing.notificationq.api.NotificationQueueService.NotificationQueueHandler;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.config.InvoiceConfig;
import com.ning.billing.util.svcapi.subscription.SubscriptionBaseInternalApi;

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
