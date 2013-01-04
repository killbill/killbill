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

package com.ning.billing.ovedue.notification;

import java.util.UUID;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.overdue.OverdueProperties;
import com.ning.billing.overdue.listener.OverdueListener;
import com.ning.billing.overdue.service.DefaultOverdueService;
import com.ning.billing.util.notificationq.NotificationKey;
import com.ning.billing.util.notificationq.NotificationQueue;
import com.ning.billing.util.notificationq.NotificationQueueService;
import com.ning.billing.util.notificationq.NotificationQueueService.NoSuchNotificationQueue;
import com.ning.billing.util.notificationq.NotificationQueueService.NotificationQueueAlreadyExists;
import com.ning.billing.util.notificationq.NotificationQueueService.NotificationQueueHandler;

import com.google.inject.Inject;

public class DefaultOverdueCheckNotifier implements OverdueCheckNotifier {

    private static final Logger log = LoggerFactory.getLogger(DefaultOverdueCheckNotifier.class);

    public static final String OVERDUE_CHECK_NOTIFIER_QUEUE = "overdue-check-queue";

    private final NotificationQueueService notificationQueueService;
    private final OverdueProperties config;
    private final OverdueListener listener;

    private NotificationQueue overdueQueue;

    @Inject
    public DefaultOverdueCheckNotifier(final NotificationQueueService notificationQueueService, final OverdueProperties config,
                                       final OverdueListener listener) {
        this.notificationQueueService = notificationQueueService;
        this.config = config;
        this.listener = listener;
    }

    @Override
    public void initialize() {
        final NotificationQueueHandler notificationQueueHandler = new NotificationQueueHandler() {
            @Override
            public void handleReadyNotification(final NotificationKey notificationKey, final DateTime eventDate, final UUID userToken, final Long accountRecordId, final Long tenantRecordId) {
                try {
                    if (!(notificationKey instanceof OverdueCheckNotificationKey)) {
                        log.error("Overdue service received Unexpected notificationKey {}", notificationKey.getClass().getName());
                        return;
                    }

                    final OverdueCheckNotificationKey key = (OverdueCheckNotificationKey) notificationKey;
                    listener.handleNextOverdueCheck(key, userToken, accountRecordId, tenantRecordId);
                } catch (IllegalArgumentException e) {
                    log.error("The key returned from the NextBillingNotificationQueue is not a valid UUID", e);
                }

            }
        };

        try {
            overdueQueue = notificationQueueService.createNotificationQueue(DefaultOverdueService.OVERDUE_SERVICE_NAME,
                                                                            OVERDUE_CHECK_NOTIFIER_QUEUE,
                                                                            notificationQueueHandler);
        } catch (NotificationQueueAlreadyExists e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void start() {
        overdueQueue.startQueue();
    }

    @Override
    public void stop() {
        if (overdueQueue != null) {
            overdueQueue.stopQueue();
            try {
                notificationQueueService.deleteNotificationQueue(overdueQueue.getServiceName(), overdueQueue.getQueueName());
            } catch (NoSuchNotificationQueue e) {
                log.error("Error deleting a queue by its own name - this should never happen", e);
            }
        }
    }
}
