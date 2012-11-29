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

import java.io.IOException;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.junction.api.Blockable;
import com.ning.billing.overdue.service.DefaultOverdueService;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.notificationq.NotificationKey;
import com.ning.billing.util.notificationq.NotificationQueue;
import com.ning.billing.util.notificationq.NotificationQueueService;
import com.ning.billing.util.notificationq.NotificationQueueService.NoSuchNotificationQueue;

import com.google.inject.Inject;

public class DefaultOverdueCheckPoster implements OverdueCheckPoster {
    private static final Logger log = LoggerFactory.getLogger(DefaultOverdueCheckNotifier.class);

    private final NotificationQueueService notificationQueueService;

    @Inject
    public DefaultOverdueCheckPoster(
            final NotificationQueueService notificationQueueService) {
        super();
        this.notificationQueueService = notificationQueueService;
    }

    @Override
    public void insertOverdueCheckNotification(final Blockable overdueable, final DateTime futureNotificationTime, final InternalCallContext context) {
        final NotificationQueue checkOverdueQueue;
        try {
            checkOverdueQueue = notificationQueueService.getNotificationQueue(DefaultOverdueService.OVERDUE_SERVICE_NAME,
                                                                              DefaultOverdueCheckNotifier.OVERDUE_CHECK_NOTIFIER_QUEUE);
            log.info("Queuing overdue check notification. id: {}, timestamp: {}", overdueable.getId().toString(), futureNotificationTime.toString());

            checkOverdueQueue.recordFutureNotification(futureNotificationTime, new OverdueCheckNotificationKey(overdueable.getId(), Blockable.Type.get(overdueable)), context);
        } catch (NoSuchNotificationQueue e) {
            log.error("Attempting to put items on a non-existent queue (DefaultOverdueCheck).", e);
        } catch (IOException e) {
            log.error("Failed to serialize notifcationKey for {}", overdueable.toString());
        }
    }


    @Override
    public void clearNotificationsFor(final Blockable overdueable, final InternalCallContext context) {
        final NotificationQueue checkOverdueQueue;
        try {
            checkOverdueQueue = notificationQueueService.getNotificationQueue(DefaultOverdueService.OVERDUE_SERVICE_NAME,
                                                                              DefaultOverdueCheckNotifier.OVERDUE_CHECK_NOTIFIER_QUEUE);
            final NotificationKey key = new NotificationKey() {
                @Override
                public String toString() {
                    return overdueable.getId().toString();
                }
            };
            checkOverdueQueue.removeNotificationsByKey(key, context);
        } catch (NoSuchNotificationQueue e) {
            log.error("Attempting to clear items from a non-existent queue (DefaultOverdueCheck).", e);
        }
    }

}
