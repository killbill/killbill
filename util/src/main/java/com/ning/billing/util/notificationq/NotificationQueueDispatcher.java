/*
 * Copyright 2010-2012 Ning, Inc.
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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nullable;

import org.skife.jdbi.v2.IDBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.util.Hostname;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.callcontext.UserType;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.config.NotificationConfig;
import com.ning.billing.util.notificationq.NotificationQueueService.NotificationQueueHandler;
import com.ning.billing.util.notificationq.dao.NotificationSqlDao;
import com.ning.billing.util.queue.PersistentQueueBase;

public class NotificationQueueDispatcher extends PersistentQueueBase {

    protected static final Logger log = LoggerFactory.getLogger(NotificationQueueDispatcher.class);

    public static final int CLAIM_TIME_MS = (5 * 60 * 1000); // 5 minutes

    private static final String NOTIFICATION_THREAD_NAME = "Notification-queue-dispatch";
    private static final int NB_THREADS = 1;

    private final NotificationConfig config;
    private final String hostname;
    private final AtomicLong nbProcessedEvents;
    private final NotificationSqlDao dao;

    protected final InternalCallContextFactory internalCallContextFactory;
    protected final Clock clock;
    protected final Map<String, NotificationQueue> queues;

    private AtomicBoolean isStarted;

    // Package visibility on purpose
    NotificationQueueDispatcher(final Clock clock, final NotificationQueueConfig config, final IDBI dbi, final InternalCallContextFactory internalCallContextFactory) {
        super("NotificationQ", Executors.newFixedThreadPool(1, new ThreadFactory() {
            @Override
            public Thread newThread(final Runnable r) {
                final Thread th = new Thread(r);
                th.setName(NOTIFICATION_THREAD_NAME);
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
        this.config = config;
        this.dao = (dbi != null) ? dbi.onDemand(NotificationSqlDao.class) : null;
        this.internalCallContextFactory = internalCallContextFactory;
        this.hostname = Hostname.get();
        this.nbProcessedEvents = new AtomicLong();

        this.queues = new TreeMap<String, NotificationQueue>();

        this.isStarted = new AtomicBoolean(false);

    }

    @Override
    public void startQueue() {
        if (config.isNotificationProcessingOff()) {
            return;
        }
        // The first startQueue from any queue will trigger starting the processing for all queues
        if (isStarted.compareAndSet(false, true)) {
            super.startQueue();
        }
    }

    @Override
    public void stopQueue() {
        if (config.isNotificationProcessingOff()) {
            return;
        }

        // If there are no active queues left, stop the processing for the queues
        // (This is not intended to be robust against a system that would stop and start queues at the same time,
        // for a a normal shutdown sequence)
        //
        if (isStarted.get()) {
            int nbQueueStarted = 0;
            synchronized (queues) {
                for (NotificationQueue cur : queues.values()) {
                    if (cur.isStarted()) {
                        nbQueueStarted++;
                    }
                }
            }
            if (nbQueueStarted == 0 && (isStarted.compareAndSet(true, false))) {
                super.stopQueue();
            }
        }
    }

    @Override
    public boolean isStarted() {
        return isStarted.get();
    }

    public AtomicLong getNbProcessedEvents() {
        return nbProcessedEvents;
    }

    public String getHostname() {
        return hostname;
    }

    public Clock getClock() {
        return clock;
    }

    protected NotificationQueueHandler getHandlerForActiveQueue(final String compositeName) {
        synchronized (queues) {
            final NotificationQueue queue = queues.get(compositeName);
            if (queue == null || !queue.isStarted()) {
                return null;
            }
            return queue.getHandler();
        }
    }

    @Override
    public int doProcessEvents() {
        return doProcessEventsWithLimit(-1);
    }

    protected int doProcessEventsWithLimit(int limit) {
        logDebug("ENTER doProcessEvents");
        // Finding and claiming notifications is not done per tenant (yet?)
        final List<Notification> notifications = getReadyNotifications(createCallContext(null, null));
        if (notifications.size() == 0) {
            logDebug("EXIT doProcessEvents");
            return 0;
        }
        logDebug("doProcessEventsWithLimit date = %s, got %s", getClock().getUTCNow().toDate(), notifications.size());

        if (limit > 0) {
            while (notifications.size() > limit) {
                notifications.remove(notifications.size() -1 );
            }
        }

        logDebug("START processing %d events at time %s", notifications.size(), getClock().getUTCNow().toDate());

        int result = 0;
        for (final Notification cur : notifications) {
            getNbProcessedEvents().incrementAndGet();
            final NotificationKey key = deserializeEvent(cur.getNotificationKeyClass(), cur.getNotificationKey());

            NotificationQueueHandler handler = getHandlerForActiveQueue(cur.getQueueName());
            if (handler == null) {
                continue;
            }
            handler.handleReadyNotification(key, cur.getEffectiveDate(), cur.getFutureUserToken(), cur.getAccountRecordId(), cur.getTenantRecordId());

            result++;
            clearNotification(cur, createCallContext(cur.getTenantRecordId(), cur.getAccountRecordId()));
            logDebug("done handling notification %s, key = %s for time %s", cur.getId(), cur.getNotificationKey(), cur.getEffectiveDate());
        }

        return result;
    }

    private void clearNotification(final Notification cleared, final InternalCallContext context) {
        dao.clearNotification(cleared.getId().toString(), getHostname(), context);
    }

    private List<Notification> getReadyNotifications(final InternalCallContext context) {
        final Date now = getClock().getUTCNow().toDate();
        final Date nextAvailable = getClock().getUTCNow().plus(CLAIM_TIME_MS).toDate();

        final List<Notification> input = dao.getReadyNotifications(now, getHostname(), getMaxNotifications() , context);

        final List<Notification> claimedNotifications = new ArrayList<Notification>();
        for (final Notification cur : input) {

            // Skip non active queues...
            final NotificationQueue queue = queues.get(cur.getQueueName());
            if (queue == null || !queue.isStarted()) {
                continue;
            }

            logDebug("about to claim notification %s,  key = %s for time %s",
                     cur.getId(), cur.getNotificationKey(), cur.getEffectiveDate());

            final boolean claimed = (dao.claimNotification(getHostname(), nextAvailable, cur.getId().toString(), now, context) == 1);
            logDebug("claimed notification %s, key = %s for time %s result = %s",
                     cur.getId(), cur.getNotificationKey(), cur.getEffectiveDate(), claimed);

            if (claimed) {
                claimedNotifications.add(cur);
                dao.insertClaimedHistory(getHostname(), now, cur.getId().toString(), context);
            }
        }

        for (final Notification cur : claimedNotifications) {
            if (cur.getOwner() != null && !cur.getOwner().equals(getHostname())) {
                log.warn("NotificationQueue stealing notification {} from {}", new Object[]{ cur, cur.getOwner()});
            }
        }

        return claimedNotifications;
    }

    private void logDebug(final String format, final Object... args) {
        if (log.isDebugEnabled()) {
            final String realDebug = String.format(format, args);
            log.debug(String.format("Thread %d  %s", Thread.currentThread().getId(), realDebug));
        }
    }

    private InternalCallContext createCallContext(@Nullable final Long tenantRecordId, @Nullable final Long accountRecordId) {
        return internalCallContextFactory.createInternalCallContext(tenantRecordId, accountRecordId, "NotificationQueue", CallOrigin.INTERNAL, UserType.SYSTEM, null);
    }


    public static String getCompositeName(final String svcName, final String queueName) {
        return svcName + ":" + queueName;
    }


    private int getMaxNotifications() {
        return queues.size() * 3;
    }

}
