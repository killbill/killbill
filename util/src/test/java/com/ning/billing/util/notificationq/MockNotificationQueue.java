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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;

import org.joda.time.DateTime;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;

import com.ning.billing.config.NotificationConfig;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.notificationq.NotificationLifecycle.NotificationLifecycleState;
import com.ning.billing.util.notificationq.NotificationQueueService.NotificationQueueHandler;

public class MockNotificationQueue extends NotificationQueueBase implements NotificationQueue {
    private final TreeSet<Notification> notifications;

    public MockNotificationQueue(final Clock clock,  final String svcName, final String queueName, final NotificationQueueHandler handler, final NotificationConfig config) {
        super(clock, svcName, queueName, handler, config);
        notifications = new TreeSet<Notification>(new Comparator<Notification>() {
            @Override
            public int compare(Notification o1, Notification o2) {
                if (o1.getEffectiveDate().equals(o2.getEffectiveDate())) {
                    return o1.getNotificationKey().compareTo(o2.getNotificationKey());
                } else {
                    return o1.getEffectiveDate().compareTo(o2.getEffectiveDate());
                }
            }
        });
    }

    @Override
    public void recordFutureNotification(DateTime futureNotificationTime, NotificationKey notificationKey) {
        Notification notification = new DefaultNotification("MockQueue", notificationKey.toString(), futureNotificationTime);
        synchronized(notifications) {
            notifications.add(notification);
        }
    }

    @Override
    public void recordFutureNotificationFromTransaction(
            Transmogrifier transactionalDao, DateTime futureNotificationTime,
            NotificationKey notificationKey) {
        recordFutureNotification(futureNotificationTime, notificationKey);
    }

    public List<Notification> getPendingEvents() {
        List<Notification> result = new ArrayList<Notification>();

        for (Notification notification : notifications) {
            if (notification.getProcessingState() == NotificationLifecycleState.AVAILABLE) {
                result.add(notification);
            }
        }
        return result;
    }

    @Override
    protected int doProcessEvents(int sequenceId) {

        int result = 0;

        List<Notification> processedNotifications = new ArrayList<Notification>();
        List<Notification> oldNotifications = new ArrayList<Notification>();

        List<Notification> readyNotifications = new ArrayList<Notification>();
        synchronized(notifications) {
            Iterator<Notification> it = notifications.iterator();
            while (it.hasNext()) {
                Notification cur = it.next();
                if (cur.isAvailableForProcessing(clock.getUTCNow())) {
                    readyNotifications.add(cur);
                }
            }
        }

        result = readyNotifications.size();
        for (Notification cur : readyNotifications) {
            handler.handleReadyNotification(cur.getNotificationKey(), cur.getEffectiveDate());
            DefaultNotification processedNotification = new DefaultNotification(-1L, cur.getUUID(), hostname, "MockQueue", clock.getUTCNow().plus(config.getDaoClaimTimeMs()), NotificationLifecycleState.PROCESSED, cur.getNotificationKey(), cur.getEffectiveDate());
            oldNotifications.add(cur);
            processedNotifications.add(processedNotification);
        }
        synchronized(notifications) {
            if (oldNotifications.size() > 0) {
                notifications.removeAll(oldNotifications);
            }

            if (processedNotifications.size() > 0) {
                notifications.addAll(processedNotifications);
            }
        }
        return result;
    }
}
