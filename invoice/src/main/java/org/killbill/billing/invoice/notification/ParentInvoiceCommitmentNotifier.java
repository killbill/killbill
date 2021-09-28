/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2020 Groupon, Inc
 * Copyright 2020-2021 Equinix, Inc
 * Copyright 2014-2021 The Billing Project, LLC
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
import org.killbill.billing.platform.api.KillbillService.KILLBILL_SERVICES;
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

public class ParentInvoiceCommitmentNotifier implements NextBillingDateNotifier {

    private static final Logger log = LoggerFactory.getLogger(ParentInvoiceCommitmentNotifier.class);

    public static final String PARENT_INVOICE_COMMITMENT_NOTIFIER_QUEUE = "parent-invoice-commitment-queue";

    private final NotificationQueueService notificationQueueService;
    private final InvoiceListener listener;

    private NotificationQueue commitInvoiceQueue;

    @Inject
    public ParentInvoiceCommitmentNotifier(final NotificationQueueService notificationQueueService,
                                           final InvoiceListener listener) {
        this.notificationQueueService = notificationQueueService;
        this.listener = listener;
    }

    @Override
    public void initialize() throws NotificationQueueAlreadyExists {

        final NotificationQueueHandler notificationQueueHandler = new NotificationQueueHandler() {
            @Override
            public void handleReadyNotification(final NotificationEvent notificationKey, final DateTime eventDate, final UUID userToken, final Long accountRecordId, final Long tenantRecordId) {
                try {
                    if (!(notificationKey instanceof ParentInvoiceCommitmentNotificationKey)) {
                        log.error("Invoice service received an unexpected event type {}", notificationKey.getClass().getName());
                        return;
                    }

                    final ParentInvoiceCommitmentNotificationKey key = (ParentInvoiceCommitmentNotificationKey) notificationKey;

                    listener.handleParentInvoiceCommitmentEvent(key.getUuidKey(), userToken, accountRecordId, tenantRecordId);

                } catch (IllegalArgumentException e) {
                    log.error("The key returned from the ParentInvoiceCommitmentQueue is not a valid UUID", e);
                }
            }
        };

        commitInvoiceQueue = notificationQueueService.createNotificationQueue(KILLBILL_SERVICES.INVOICE_SERVICE.getServiceName(),
                                                                              PARENT_INVOICE_COMMITMENT_NOTIFIER_QUEUE,
                                                                              notificationQueueHandler);
    }

    @Override
    public void start() {
        commitInvoiceQueue.startQueue();
    }

    @Override
    public void stop() throws NoSuchNotificationQueue {
        if (commitInvoiceQueue != null) {
            if (!commitInvoiceQueue.stopQueue()) {
                log.warn("Timed out while shutting down {} queue: IN_PROCESSING entries might be left behind", commitInvoiceQueue.getFullQName());
            }
            notificationQueueService.deleteNotificationQueue(commitInvoiceQueue.getServiceName(), commitInvoiceQueue.getQueueName());
        }
    }

}
