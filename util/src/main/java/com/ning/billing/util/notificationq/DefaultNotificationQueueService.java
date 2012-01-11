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

import org.skife.jdbi.v2.DBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.util.clock.Clock;

public class DefaultNotificationQueueService implements NotificationQueueService {

    private final Logger log = LoggerFactory.getLogger(DefaultNotificationQueueService.class);

    private final DBI dbi;
    private final Clock clock;

    private final Map<String, NotificationQueue> queues;

    public DefaultNotificationQueueService(final DBI dbi, final Clock clock) {
        this.dbi = dbi;
        this.clock = clock;
        this.queues = new TreeMap<String, NotificationQueue>();
    }

    @Override
    public NotificationQueue createNotificationQueue(String svcName,
            String queueName, NotificationQueueHandler handler,
            NotificationConfig config) {
        if (svcName == null || queueName == null || handler == null || config == null) {
            throw new RuntimeException("Need to specify all parameters");
        }

        String compositeName = svcName + ":" + queueName;
        NotificationQueue result = null;
        synchronized(queues) {
            result = queues.get(compositeName);
            if (result == null) {
                result = new NotificationQueue(dbi, clock, svcName, queueName, handler, config);
                queues.put(compositeName, result);
            } else {
                log.warn("Queue for svc {} and name {} already exist", svcName, queueName);
            }
        }
        return result;
    }
}
