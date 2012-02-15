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
import java.util.Date;
import java.util.List;
import org.joda.time.DateTime;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;

import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.notificationq.NotificationQueueService.NotificationQueueHandler;
import com.ning.billing.util.notificationq.dao.NotificationSqlDao;

public class DefaultNotificationQueue extends NotificationQueueBase {

    protected final NotificationSqlDao dao;

    public DefaultNotificationQueue(final IDBI dbi, final Clock clock,  final String svcName, final String queueName, final NotificationQueueHandler handler, final NotificationConfig config) {

        super(clock, svcName, queueName, handler, config);
        this.dao = dbi.onDemand(NotificationSqlDao.class);
    }

    @Override
    protected void doProcessEvents(final int sequenceId) {

        logDebug("ENTER doProcessEvents");
        List<Notification> notifications = getReadyNotifications(sequenceId);
        if (notifications.size() == 0) {
            logDebug("EXIT doProcessEvents");
            return;
        }

        logDebug("START processing %d events at time %s", notifications.size(), clock.getUTCNow().toDate());

        for (final Notification cur : notifications) {
            nbProcessedEvents.incrementAndGet();
            logDebug("handling notification %s, key = %s for time %s",
                    cur.getUUID(), cur.getNotificationKey(), cur.getEffectiveDate());
            handler.handleReadyNotification(cur.getNotificationKey(), cur.getEffectiveDate());
            clearNotification(cur);
            logDebug("done handling notification %s, key = %s for time %s",
                    cur.getUUID(), cur.getNotificationKey(), cur.getEffectiveDate());
        }
    }

    @Override
    public void recordFutureNotificationFromTransaction(final Transmogrifier transactionalDao,
            final DateTime futureNotificationTime, final NotificationKey notificationKey) {
        NotificationSqlDao transactionalNotificationDao =  transactionalDao.become(NotificationSqlDao.class);
        Notification notification = new DefaultNotification(getFullQName(), notificationKey.toString(), futureNotificationTime);
        transactionalNotificationDao.insertNotification(notification);
    }


    private void clearNotification(final Notification cleared) {
        dao.clearNotification(cleared.getId(), hostname);
    }

    private List<Notification> getReadyNotifications(final int seqId) {

        final Date now = clock.getUTCNow().toDate();
        final Date nextAvailable = clock.getUTCNow().plus(config.getDaoClaimTimeMs()).toDate();

        List<Notification> input = dao.getReadyNotifications(now, config.getDaoMaxReadyEvents(), getFullQName());

        List<Notification> claimedNotifications = new ArrayList<Notification>();
        for (Notification cur : input) {
            logDebug("about to claim notification %s,  key = %s for time %s",
                    cur.getUUID(), cur.getNotificationKey(), cur.getEffectiveDate());
            final boolean claimed = (dao.claimNotification(hostname, nextAvailable, cur.getId(), now) == 1);
            logDebug("claimed notification %s, key = %s for time %s result = %s",
                    cur.getUUID(), cur.getNotificationKey(), cur.getEffectiveDate(), Boolean.valueOf(claimed));
            if (claimed) {
                claimedNotifications.add(cur);
                dao.insertClaimedHistory(seqId, hostname, now, cur.getUUID().toString());
            }
        }

        for (Notification cur : claimedNotifications) {
            if (cur.getOwner() != null && !cur.getOwner().equals(hostname)) {
                log.warn(String.format("NotificationQueue %s stealing notification %s from %s",
                        getFullQName(), cur, cur.getOwner()));
            }
        }
        return claimedNotifications;
    }

    private void logDebug(String format, Object...args) {
        if (log.isDebugEnabled()) {
            String realDebug = String.format(format, args);
            log.debug(String.format("Thread %d [queue = %s] %s", Thread.currentThread().getId(), getFullQName(), realDebug));
        }
    }
}
