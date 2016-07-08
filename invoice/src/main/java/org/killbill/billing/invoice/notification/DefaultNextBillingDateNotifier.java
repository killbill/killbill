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
import org.killbill.billing.invoice.InvoiceListener;
import org.killbill.billing.invoice.api.DefaultInvoiceService;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseInternalApi;
import org.killbill.billing.subscription.api.user.SubscriptionBaseApiException;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.config.definition.InvoiceConfig;
import org.killbill.notificationq.api.NotificationEvent;
import org.killbill.notificationq.api.NotificationQueue;
import org.killbill.notificationq.api.NotificationQueueService;
import org.killbill.notificationq.api.NotificationQueueService.NoSuchNotificationQueue;
import org.killbill.notificationq.api.NotificationQueueService.NotificationQueueAlreadyExists;
import org.killbill.notificationq.api.NotificationQueueService.NotificationQueueHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;

public class DefaultNextBillingDateNotifier implements NextBillingDateNotifier {

    private static final Logger log = LoggerFactory.getLogger(DefaultNextBillingDateNotifier.class);

    public static final String NEXT_BILLING_DATE_NOTIFIER_QUEUE = "next-billing-date-queue";

    private final NotificationQueueService notificationQueueService;
    private final SubscriptionBaseInternalApi subscriptionApi;
    private final InvoiceListener listener;
    private final InternalCallContextFactory callContextFactory;

    private NotificationQueue nextBillingQueue;

    @Inject
    public DefaultNextBillingDateNotifier(final NotificationQueueService notificationQueueService,
                                          final SubscriptionBaseInternalApi subscriptionApi,
                                          final InvoiceListener listener,
                                          final InternalCallContextFactory callContextFactory) {
        this.notificationQueueService = notificationQueueService;
        this.subscriptionApi = subscriptionApi;
        this.listener = listener;
        this.callContextFactory = callContextFactory;
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
                try {
                    final SubscriptionBase subscription = subscriptionApi.getSubscriptionFromId(key.getUuidKey(), callContextFactory.createInternalTenantContext(tenantRecordId, accountRecordId));
                    if (subscription == null) {
                        log.warn("Unable to retrieve subscriptionId='{}' for event {}", key.getUuidKey(), key);
                        return;
                    }
                    if (key.isDryRunForInvoiceNotification() != null && // Just to ensure compatibility with json that might not have that field (old versions < 0.13.6)
                        key.isDryRunForInvoiceNotification()) {
                        processEventForInvoiceNotification(key.getUuidKey(), targetDate, userToken, accountRecordId, tenantRecordId);
                    } else {
                        processEventForInvoiceGeneration(key.getUuidKey(), targetDate, userToken, accountRecordId, tenantRecordId);
                    }
                } catch (SubscriptionBaseApiException e) {
                    log.warn("Error retrieving subscriptionId='{}'", key.getUuidKey(), e);
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

    private void processEventForInvoiceGeneration(final UUID subscriptionId, final DateTime eventDateTime, final UUID userToken, final Long accountRecordId, final Long tenantRecordId) {
        listener.handleNextBillingDateEvent(subscriptionId, eventDateTime, userToken, accountRecordId, tenantRecordId);
    }

    private void processEventForInvoiceNotification(final UUID subscriptionId, final DateTime eventDateTime, final UUID userToken, final Long accountRecordId, final Long tenantRecordId) {
        listener.handleEventForInvoiceNotification(subscriptionId, eventDateTime, userToken, accountRecordId, tenantRecordId);
    }
}
