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

import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
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
    public NotificationQueue createNotificationQueue(String svcName,
            String queueName, NotificationQueueHandler handler,
            NotificationConfig config) throws NotficationQueueAlreadyExists {
        if (svcName == null || queueName == null || handler == null || config == null) {
            throw new RuntimeException("Need to specify all parameters");
        }

        String compositeName = getCompositeName(svcName, queueName);
        NotificationQueue result = null;
        synchronized(queues) {
            result = queues.get(compositeName);
            if (result != null) {
                throw new NotficationQueueAlreadyExists(String.format("Queue for svc %s and name %s already exist",
                        svcName, queueName));
            }
            result = createNotificationQueueInternal(svcName, queueName, handler, config);
            queues.put(compositeName, result);
        }
        return result;
    }

    @Override
    public NotificationQueue getNotificationQueue(String svcName,
            String queueName) throws NoSuchNotificationQueue {

        NotificationQueue result = null;
        String compositeName = getCompositeName(svcName, queueName);
        synchronized(queues) {
            result = queues.get(compositeName);
            if (result == null) {
                throw new NoSuchNotificationQueue(String.format("Queue for svc %s and name %s does not exist",
                        svcName, queueName));
            }
        }
        return result;
    }


    protected abstract NotificationQueue createNotificationQueueInternal(String svcName,
            String queueName, NotificationQueueHandler handler,
            NotificationConfig config);


    private String getCompositeName(String svcName, String queueName) {
        return svcName + ":" + queueName;
    }
}
