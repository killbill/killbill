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

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.util.config.NotificationConfig;
import com.ning.billing.util.Hostname;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.notificationq.NotificationQueueService.NotificationQueueHandler;
import com.ning.billing.util.queue.PersistentQueueBase;

public abstract class NotificationQueueBase extends PersistentQueueBase implements NotificationQueue {
    private static final Logger log = LoggerFactory.getLogger(NotificationQueueBase.class);

    public static final int CLAIM_TIME_MS = (5 * 60 * 1000); // 5 minutes

    private static final String NOTIFICATION_THREAD_PREFIX = "Notification-";
    private static final int NB_THREADS = 1;

    private final String svcName;
    private final String queueName;
    private final NotificationQueueHandler handler;
    private final NotificationConfig config;
    private final Clock clock;
    private final String hostname;
    private final AtomicLong nbProcessedEvents;

    // Package visibility on purpose
    NotificationQueueBase(final Clock clock, final String svcName, final String queueName, final NotificationQueueHandler handler, final NotificationConfig config) {
        super(svcName, Executors.newFixedThreadPool(1, new ThreadFactory() {
            @Override
            public Thread newThread(final Runnable r) {
                final Thread th = new Thread(r);
                th.setName(NOTIFICATION_THREAD_PREFIX + svcName + "-" + queueName);
                th.setUncaughtExceptionHandler(new UncaughtExceptionHandler() {
                    @Override
                    public void uncaughtException(final Thread t, final Throwable e) {
                        log.error("Uncaught exception for thread " + t.getName(), e);
                    }
                });
                return th;
            }
        }), NB_THREADS, config);

        this.clock = clock;
        this.svcName = svcName;
        this.queueName = queueName;
        this.handler = handler;
        this.config = config;
        this.hostname = Hostname.get();
        this.nbProcessedEvents = new AtomicLong();
    }

    @Override
    public void startQueue() {
        if (config.isNotificationProcessingOff()) {
            return;
        }
        super.startQueue();
    }

    @Override
    public void stopQueue() {
        if (config.isNotificationProcessingOff()) {
            return;
        }
        super.stopQueue();
    }

    @Override
    public int processReadyNotification() {
        return doProcessEvents();
    }

    @Override
    public String toString() {
        return getFullQName();
    }

    @Override
    public String getServiceName() {
        return svcName;
    }

    @Override
    public String getQueueName() {
        return queueName;
    }

    public String getFullQName() {
        return NotificationQueueServiceBase.getCompositeName(svcName, queueName);
    }

    public AtomicLong getNbProcessedEvents() {
        return nbProcessedEvents;
    }

    public String getHostname() {
        return hostname;
    }

    public NotificationQueueHandler getHandler() {
        return handler;
    }

    public Clock getClock() {
        return clock;
    }

    @Override
    public abstract int doProcessEvents();
}
