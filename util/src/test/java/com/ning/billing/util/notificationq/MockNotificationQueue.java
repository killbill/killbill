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

package com.ning.billing.util.notificationq;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;

import org.joda.time.DateTime;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;

import com.ning.billing.util.Hostname;
import com.ning.billing.util.config.NotificationConfig;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.notificationq.NotificationQueueService.NotificationQueueHandler;
import com.ning.billing.util.queue.PersistentQueueEntryLifecycle.PersistentQueueEntryLifecycleState;

import com.fasterxml.jackson.databind.ObjectMapper;

public class MockNotificationQueue implements NotificationQueue {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final String hostname;
    private final TreeSet<Notification> notifications;
    private final Clock clock;
    private final String svcName;
    private final String queueName;
    private final NotificationQueueHandler handler;

    private volatile boolean isStarted;

    public MockNotificationQueue(final Clock clock, final String svcName, final String queueName, final NotificationQueueHandler handler) {

        this.svcName = svcName;
        this.queueName = queueName;
        this.handler = handler;
        this.clock = clock;
        this.hostname = Hostname.get();

        notifications = new TreeSet<Notification>(new Comparator<Notification>() {
            @Override
            public int compare(final Notification o1, final Notification o2) {
                if (o1.getEffectiveDate().equals(o2.getEffectiveDate())) {
                    return o1.getNotificationKey().compareTo(o2.getNotificationKey());
                } else {
                    return o1.getEffectiveDate().compareTo(o2.getEffectiveDate());
                }
            }
        });
    }

    @Override
    public void recordFutureNotification(final DateTime futureNotificationTime, final UUID accountId,
                                         final NotificationKey notificationKey, final InternalCallContext context) throws IOException {
        final String json = objectMapper.writeValueAsString(notificationKey);
        final Notification notification = new DefaultNotification("MockQueue", hostname, notificationKey.getClass().getName(), json, accountId, futureNotificationTime,
                                                                  null, 0L);
        synchronized (notifications) {
            notifications.add(notification);
        }
    }

    @Override
    public void recordFutureNotificationFromTransaction(final Transmogrifier transactionalDao, final DateTime futureNotificationTime,
                                                        final UUID accountId, final NotificationKey notificationKey, final InternalCallContext context) throws IOException {
        recordFutureNotification(futureNotificationTime, accountId, notificationKey, context);
    }


    @Override
    public void removeNotificationsByKey(final NotificationKey key, final InternalCallContext context) {
        final List<Notification> toClearNotifications = new ArrayList<Notification>();
        for (final Notification notification : notifications) {
            if (notification.getNotificationKey().equals(key.toString())) {
                toClearNotifications.add(notification);
            }
        }

        synchronized (notifications) {
            if (toClearNotifications.size() > 0) {
                notifications.removeAll(toClearNotifications);
            }
        }
    }

    @Override
    public List<Notification> getNotificationForAccountAndDate(final UUID accountId, final DateTime effectiveDate, final InternalCallContext context) {

        final List<Notification> result = new ArrayList<Notification>();
        synchronized (notifications) {
            for (Notification cur : notifications) {
                if (cur.getAccountId().equals(accountId) || cur.getEffectiveDate().compareTo(effectiveDate) == 0) {
                    result.add(cur);
                }
            }
        }
        return result;
    }

    @Override
    public void removeNotification(final UUID notificationId, final InternalCallContext context) {
        synchronized (notifications) {
            for (Notification cur : notifications) {
                if (cur.getId().equals(notificationId)) {
                    notifications.remove(cur);
                    break;
                }
            }
        }

    }

    @Override
    public String getFullQName() {
        return NotificationQueueDispatcher.getCompositeName(svcName, queueName);
    }

    @Override
    public String getServiceName() {
        return svcName;
    }

    @Override
    public String getQueueName() {
        return queueName;
    }

    @Override
    public NotificationQueueHandler getHandler() {
        return handler;
    }

    @Override
    public void startQueue() {
        isStarted = true;
    }

    @Override
    public void stopQueue() {
        isStarted = false;
    }

    @Override
    public boolean isStarted() {
        return isStarted;
    }


    public List<Notification> getReadyNotifications() {
        final int result;
        final List<Notification> processedNotifications = new ArrayList<Notification>();
        final List<Notification> oldNotifications = new ArrayList<Notification>();

        final List<Notification> readyNotifications = new ArrayList<Notification>();
        synchronized (notifications) {
            for (final Notification cur : notifications) {
                if (cur.isAvailableForProcessing(clock.getUTCNow())) {
                    readyNotifications.add(cur);
                }
            }
        }
        return readyNotifications;
    }

    public void markProcessedNotifications(final List<Notification> toBeremoved, final List<Notification> toBeAdded ) {
        synchronized (notifications) {
            notifications.removeAll(toBeremoved);
            notifications.addAll(toBeAdded);
        }
    }

}
