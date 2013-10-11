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

package com.ning.billing.ovedue.notification;

import java.util.UUID;

import org.joda.time.DateTime;
import org.slf4j.Logger;

import com.ning.billing.notificationq.api.NotificationEvent;
import com.ning.billing.notificationq.api.NotificationQueue;
import com.ning.billing.notificationq.api.NotificationQueueService;
import com.ning.billing.notificationq.api.NotificationQueueService.NoSuchNotificationQueue;
import com.ning.billing.notificationq.api.NotificationQueueService.NotificationQueueAlreadyExists;
import com.ning.billing.notificationq.api.NotificationQueueService.NotificationQueueHandler;
import com.ning.billing.overdue.OverdueProperties;
import com.ning.billing.overdue.listener.OverdueListener;
import com.ning.billing.overdue.service.DefaultOverdueService;

public abstract class DefaultOverdueNotifierBase implements OverdueNotifier {


    protected final NotificationQueueService notificationQueueService;
    protected final OverdueProperties config;
    protected final OverdueListener listener;

    protected NotificationQueue overdueQueue;


    public abstract Logger getLogger();

    public abstract String getQueueName();

    public abstract void handleReadyNotification(final NotificationEvent notificationKey, final DateTime eventDate, final UUID userToken, final Long accountRecordId, final Long tenantRecordId);

    public DefaultOverdueNotifierBase(final NotificationQueueService notificationQueueService, final OverdueProperties config,
                                      final OverdueListener listener) {
        this.notificationQueueService = notificationQueueService;
        this.config = config;
        this.listener = listener;
    }

    @Override
    public void initialize() {

        final OverdueNotifier myself = this;

        final NotificationQueueHandler notificationQueueHandler = new NotificationQueueHandler() {
            @Override
            public void handleReadyNotification(final NotificationEvent notificationKey, final DateTime eventDate, final UUID userToken, final Long accountRecordId, final Long tenantRecordId) {
                myself.handleReadyNotification(notificationKey, eventDate, userToken, accountRecordId, tenantRecordId);
            }
        };

        try {
            overdueQueue = notificationQueueService.createNotificationQueue(DefaultOverdueService.OVERDUE_SERVICE_NAME,
                                                                            getQueueName(),
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
                getLogger().error("Error deleting a queue by its own name - this should never happen", e);
            }
        }
    }
}
