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

import com.ning.billing.config.NotificationConfig;
import com.ning.billing.util.Hostname;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.notificationq.NotificationQueueService.NotificationQueueHandler;
import com.ning.billing.util.queue.PersistentQueueBase;


public abstract class NotificationQueueBase extends PersistentQueueBase implements NotificationQueue {

    protected final static Logger log = LoggerFactory.getLogger(NotificationQueueBase.class);

    protected static final String NOTIFICATION_THREAD_PREFIX = "Notification-";
    protected static final long STOP_WAIT_TIMEOUT_MS = 60000;

    protected final String svcName;
    protected final String queueName;
    protected final NotificationQueueHandler handler;
    protected final NotificationConfig config;

    protected final Clock clock;
    protected final String hostname;

    protected AtomicLong nbProcessedEvents;

    // Package visibility on purpose
    NotificationQueueBase(final Clock clock,  final String svcName, final String queueName, final NotificationQueueHandler handler, final NotificationConfig config) {
        super(svcName, Executors.newFixedThreadPool(1, new ThreadFactory() {

            @Override
            public Thread newThread(Runnable r) {
                Thread th = new Thread(r);
                th.setName(NOTIFICATION_THREAD_PREFIX + svcName + "-" + queueName);
                th.setUncaughtExceptionHandler(new UncaughtExceptionHandler() {
                    @Override
                    public void uncaughtException(Thread t, Throwable e) {
                        log.error("Uncaught exception for thread " + t.getName(), e);
                    }
                });
                return th;
            }
        }), 1, STOP_WAIT_TIMEOUT_MS, config.getNotificationSleepTimeMs());

        this.clock = clock;
        this.svcName = svcName;
        this.queueName = queueName;
        this.handler = handler;
        this.config = config;
        this.hostname = Hostname.get();
        this.nbProcessedEvents = new AtomicLong();
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
    public String getFullQName() {
        return svcName + ":" +  queueName;
    }

    @Override
    public abstract int doProcessEvents();
}
