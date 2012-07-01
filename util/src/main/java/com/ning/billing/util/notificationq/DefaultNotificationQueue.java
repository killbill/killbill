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
import java.util.Date;
import java.util.List;

import org.joda.time.DateTime;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;

import com.ning.billing.config.NotificationConfig;
import com.ning.billing.util.bus.dao.BusEventEntry;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.notificationq.NotificationQueueService.NotificationQueueHandler;
import com.ning.billing.util.notificationq.dao.NotificationSqlDao;

public class DefaultNotificationQueue extends NotificationQueueBase {

    protected final NotificationSqlDao dao;

    public DefaultNotificationQueue(final IDBI dbi, final Clock clock, final String svcName, final String queueName, final NotificationQueueHandler handler, final NotificationConfig config) {

        super(clock, svcName, queueName, handler, config);
        this.dao = dbi.onDemand(NotificationSqlDao.class);
    }

    @Override
    public int doProcessEvents() {

        logDebug("ENTER doProcessEvents");
        final List<Notification> notifications = getReadyNotifications();
        if (notifications.size() == 0) {
            logDebug("EXIT doProcessEvents");
            return 0;
        }

        logDebug("START processing %d events at time %s", notifications.size(), clock.getUTCNow().toDate());

        int result = 0;
        for (final Notification cur : notifications) {
            nbProcessedEvents.incrementAndGet();
            logDebug("handling notification %s, key = %s for time %s",
                     cur.getId(), cur.getNotificationKey(), cur.getEffectiveDate());
            NotificationKey key = deserializeEvent(cur.getNotificationKeyClass(), cur.getNotificationKey()); 
            handler.handleReadyNotification(key, cur.getEffectiveDate());
            result++;
            clearNotification(cur);
            logDebug("done handling notification %s, key = %s for time %s",
                     cur.getId(), cur.getNotificationKey(), cur.getEffectiveDate());
        }
        return result;
    }


    @Override
    public void recordFutureNotification(final DateTime futureNotificationTime, final NotificationKey notificationKey) throws IOException {
        recordFutureNotificationInternal(futureNotificationTime, notificationKey, dao);
    }

    @Override
    public void recordFutureNotificationFromTransaction(final Transmogrifier transactionalDao,
                                                        final DateTime futureNotificationTime, final NotificationKey notificationKey) throws IOException {
        final NotificationSqlDao transactionalNotificationDao = transactionalDao.become(NotificationSqlDao.class);
        recordFutureNotificationInternal(futureNotificationTime, notificationKey, transactionalNotificationDao);
    }
    
    private void recordFutureNotificationInternal(final DateTime futureNotificationTime, final NotificationKey notificationKey, final NotificationSqlDao thisDao) throws IOException {
        final String json = objectMapper.writeValueAsString(notificationKey);
        final Notification notification = new DefaultNotification(getFullQName(), hostname, notificationKey.getClass().getName(), json, futureNotificationTime);
        thisDao.insertNotification(notification);
    }

    private void clearNotification(final Notification cleared) {
        dao.clearNotification(cleared.getId().toString(), hostname);
    }

    private List<Notification> getReadyNotifications() {

        final Date now = clock.getUTCNow().toDate();
        final Date nextAvailable = clock.getUTCNow().plus(CLAIM_TIME_MS).toDate();

        final List<Notification> input = dao.getReadyNotifications(now, hostname, CLAIM_TIME_MS, getFullQName());

        final List<Notification> claimedNotifications = new ArrayList<Notification>();
        for (final Notification cur : input) {
            logDebug("about to claim notification %s,  key = %s for time %s",
                     cur.getId(), cur.getNotificationKey(), cur.getEffectiveDate());
            final boolean claimed = (dao.claimNotification(hostname, nextAvailable, cur.getId().toString(), now) == 1);
            logDebug("claimed notification %s, key = %s for time %s result = %s",
                     cur.getId(), cur.getNotificationKey(), cur.getEffectiveDate(), Boolean.valueOf(claimed));
            if (claimed) {
                claimedNotifications.add(cur);
                dao.insertClaimedHistory(hostname, now, cur.getId().toString());
            }
        }

        for (final Notification cur : claimedNotifications) {
            if (cur.getOwner() != null && !cur.getOwner().equals(hostname)) {
                log.warn(String.format("NotificationQueue %s stealing notification %s from %s",
                                       getFullQName(), cur, cur.getOwner()));
            }
        }
        return claimedNotifications;
    }

    private void logDebug(final String format, final Object... args) {
        if (log.isDebugEnabled()) {
            final String realDebug = String.format(format, args);
            log.debug(String.format("Thread %d [queue = %s] %s", Thread.currentThread().getId(), getFullQName(), realDebug));
        }
    }

    @Override
    public void removeNotificationsByKey(final NotificationKey notificationKey) {
        dao.removeNotificationsByKey(notificationKey.toString());

    }
}
