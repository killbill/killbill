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
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.config.NotificationConfig;
import com.ning.billing.util.Hostname;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.notificationq.NotificationQueueService.NotificationQueueHandler;


public abstract class NotificationQueueBase implements NotificationQueue {

    protected final static Logger log = LoggerFactory.getLogger(NotificationQueueBase.class);

    private static final long MAX_NOTIFICATION_THREAD_WAIT_MS = 10000; // 10 secs
    private static final long NOTIFICATION_THREAD_WAIT_INCREMENT_MS = 1000; // 1 sec
    private static final long NANO_TO_MS = (1000 * 1000);

    protected static final String NOTIFICATION_THREAD_PREFIX = "Notification-";
    protected final long STOP_WAIT_TIMEOUT_MS = 60000;

    protected final String svcName;
    protected final String queueName;
    protected final NotificationQueueHandler handler;
    protected final NotificationConfig config;

    protected final Executor executor;
    protected final Clock clock;
    protected final String hostname;

    protected static final AtomicInteger sequenceId = new AtomicInteger();

    protected AtomicLong nbProcessedEvents;

    // Use this object's monitor for synchronization (no need for volatile)
    protected boolean isProcessingEvents;

    private boolean startedComplete = false;
    private boolean stoppedComplete = false;

    // Package visibility on purpose
    NotificationQueueBase(final Clock clock,  final String svcName, final String queueName, final NotificationQueueHandler handler, final NotificationConfig config) {
        this.clock = clock;
        this.svcName = svcName;
        this.queueName = queueName;
        this.handler = handler;
        this.config = config;
        this.hostname = Hostname.get();

        this.executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
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
        });
    }


    @Override
    public int processReadyNotification() {
        return doProcessEvents(sequenceId.incrementAndGet());
    }


    @Override
    public void stopQueue() {
        if (config.isNotificationProcessingOff()) {
            completedQueueStop();
            return;
        }

        synchronized(this) {
            isProcessingEvents = false;
            try {
                log.info("NotificationQueue requested to stop");
                wait(STOP_WAIT_TIMEOUT_MS);
                log.info("NotificationQueue requested should have exited");
            } catch (InterruptedException e) {
                log.warn("NotificationQueue got interrupted exception when stopping notifications", e);
            }
        }
        waitForNotificationStopCompletion();
    }

    @Override
    public void startQueue() {

        this.isProcessingEvents = true;
        this.nbProcessedEvents = new AtomicLong();


        if (config.isNotificationProcessingOff()) {
            log.warn(String.format("KILLBILL NOTIFICATION PROCESSING FOR SVC %s IS OFF !!!", getFullQName()));
            completedQueueStart();
            return;
        }
        final NotificationQueueBase notificationQueue = this;

        executor.execute(new Runnable() {
            @Override
            public void run() {

                log.info(String.format("NotificationQueue thread %s [%d] started",
                        Thread.currentThread().getName(),
                        Thread.currentThread().getId()));

                // Thread is now started, notify the listener
                completedQueueStart();

                try {
                    while (true) {

                        synchronized (notificationQueue) {
                            if (!isProcessingEvents) {
                                log.info(String.format("NotificationQueue has been requested to stop, thread  %s  [%d] stopping...",
                                        Thread.currentThread().getName(),
                                        Thread.currentThread().getId()));
                                notificationQueue.notify();
                                break;
                            }
                        }

                        // Callback may trigger exceptions in user code so catch anything here and live with it.
                        try {
                            doProcessEvents(sequenceId.getAndIncrement());
                        } catch (Exception e) {
                            log.error(String.format("NotificationQueue thread  %s  [%d] got an exception..",
                                    Thread.currentThread().getName(),
                                    Thread.currentThread().getId()), e);
                        }
                        sleepALittle();
                    }
                } catch (InterruptedException e) {
                    log.warn(Thread.currentThread().getName() + " got interrupted ", e);
                } catch (Throwable e) {
                    log.error(Thread.currentThread().getName() + " got an exception exiting...", e);
                    // Just to make it really obvious in the log
                    e.printStackTrace();
                } finally {
                    completedQueueStop();
                    log.info(String.format("NotificationQueue thread  %s  [%d] exited...",
                            Thread.currentThread().getName(),
                            Thread.currentThread().getId()));
                }
            }

            private void sleepALittle() throws InterruptedException {
                Thread.sleep(config.getNotificationSleepTimeMs());
            }
        });
        waitForNotificationStartCompletion();
    }

    @Override
    public String toString() {
        return getFullQName();
    }

    private void completedQueueStop() {
    	synchronized (this) {
    		stoppedComplete = true;
            this.notifyAll();
        }
    }

    private void completedQueueStart() {
        synchronized (this) {
        	startedComplete = true;
            this.notifyAll();
        }
    }

    private void waitForNotificationStartCompletion() {
        waitForNotificationEventCompletion(true);
    }

    private void waitForNotificationStopCompletion() {
        waitForNotificationEventCompletion(false);
    }

    private void waitForNotificationEventCompletion(boolean startEvent) {

        long ini = System.nanoTime();
        synchronized(this) {
            do {
                if ((startEvent ? startedComplete : stoppedComplete)) {
                    break;
                }
                try {
                    this.wait(NOTIFICATION_THREAD_WAIT_INCREMENT_MS);
                } catch (InterruptedException e ) {
                    Thread.currentThread().interrupt();
                    throw new NotificationError(e);
                }
            } while (!(startEvent ? startedComplete : stoppedComplete) &&
                    (System.nanoTime() - ini) / NANO_TO_MS < MAX_NOTIFICATION_THREAD_WAIT_MS);

            if (!(startEvent ? startedComplete : stoppedComplete)) {
                log.error("Could not {} notification thread in {} msec !!!",
                        (startEvent ? "start" : "stop"),
                        MAX_NOTIFICATION_THREAD_WAIT_MS);
                throw new NotificationError("Failed to start service!!");
            }
            log.info("Notification thread has been {} in {} ms",
                    (startEvent ? "started" : "stopped"),
                    (System.nanoTime() - ini) / NANO_TO_MS);
        }
    }

    @Override
    public String getFullQName() {
        return svcName + ":" +  queueName;
    }

    protected abstract int doProcessEvents(int sequenceId);
}
