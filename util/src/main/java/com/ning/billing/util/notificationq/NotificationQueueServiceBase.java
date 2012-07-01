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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.inject.Inject;
import com.ning.billing.config.NotificationConfig;
import com.ning.billing.util.clock.Clock;

public abstract class NotificationQueueServiceBase implements NotificationQueueService {

    protected final Logger log = LoggerFactory.getLogger(DefaultNotificationQueueService.class);

    protected final Clock clock;

    private final Map<String, NotificationQueue> queues;

    @Inject
    public NotificationQueueServiceBase(final Clock clock) {
        this.clock = clock;
        this.queues = new TreeMap<String, NotificationQueue>();
    }


    @Override
    public NotificationQueue createNotificationQueue(final String svcName,
                                                     final String queueName, final NotificationQueueHandler handler,
                                                     final NotificationConfig config) throws NotificationQueueAlreadyExists {
        if (svcName == null || queueName == null || handler == null || config == null) {
            throw new RuntimeException("Need to specify all parameters");
        }

        final String compositeName = getCompositeName(svcName, queueName);
        NotificationQueue result = null;
        synchronized (queues) {
            result = queues.get(compositeName);
            if (result != null) {
                throw new NotificationQueueAlreadyExists(String.format("Queue for svc %s and name %s already exist",
                                                                       svcName, queueName));
            }
            result = createNotificationQueueInternal(svcName, queueName, handler, config);
            queues.put(compositeName, result);
        }
        return result;
    }

    @Override
    public NotificationQueue getNotificationQueue(final String svcName,
                                                  final String queueName) throws NoSuchNotificationQueue {

        NotificationQueue result = null;
        final String compositeName = getCompositeName(svcName, queueName);
        synchronized (queues) {
            result = queues.get(compositeName);
            if (result == null) {
                throw new NoSuchNotificationQueue(String.format("Queue for svc %s and name %s does not exist",
                                                                svcName, queueName));
            }
        }
        return result;
    }


    public void deleteNotificationQueue(final String svcName, final String queueName)
            throws NoSuchNotificationQueue {
        final String compositeName = getCompositeName(svcName, queueName);
        synchronized (queues) {
            final NotificationQueue result = queues.get(compositeName);
            if (result == null) {
                throw new NoSuchNotificationQueue(String.format("Queue for svc %s and name %s does not exist",
                                                                svcName, queueName));
            }
            queues.remove(compositeName);
        }
    }


    //
    // Test ONLY
    //
    @Override
    public int triggerManualQueueProcessing(final String[] services, final Boolean keepRunning) {

        int result = 0;

        List<NotificationQueue> manualQueues = null;
        if (services == null) {
            manualQueues = new ArrayList<NotificationQueue>(queues.values());
        } else {
            final Joiner join = Joiner.on(",");
            join.join(services);

            log.info("Trigger manual processing for services {} ", join.toString());
            manualQueues = new LinkedList<NotificationQueue>();
            synchronized (queues) {
                for (final String svc : services) {
                    addQueuesForService(manualQueues, svc);
                }
            }
        }
        for (final NotificationQueue cur : manualQueues) {
            int processedNotifications = 0;
            do {
                processedNotifications = cur.processReadyNotification();
                log.info("Got {} results from queue {}", processedNotifications, cur.getFullQName());
                result += processedNotifications;
            } while (keepRunning && processedNotifications > 0);
        }
        return result;
    }

    private void addQueuesForService(final List<NotificationQueue> result, final String svcName) {
        for (final String cur : queues.keySet()) {
            if (cur.startsWith(svcName)) {
                result.add(queues.get(cur));
            }
        }
    }

    protected abstract NotificationQueue createNotificationQueueInternal(String svcName,
                                                                         String queueName, NotificationQueueHandler handler,
                                                                         NotificationConfig config);


    public static String getCompositeName(final String svcName, final String queueName) {
        return svcName + ":" + queueName;
    }
}
