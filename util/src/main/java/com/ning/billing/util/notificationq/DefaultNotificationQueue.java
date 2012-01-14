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
import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.joda.time.DateTime;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;

import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.notificationq.NotificationQueueService.NotificationQueueHandler;
import com.ning.billing.util.notificationq.dao.NotificationSqlDao;

public class DefaultNotificationQueue extends NotificationQueueBase {

    protected final NotificationSqlDao dao;

    public DefaultNotificationQueue(final DBI dbi, final Clock clock,  final String svcName, final String queueName, final NotificationQueueHandler handler, final NotificationConfig config) {
        super(clock, svcName, queueName, handler, config);
        this.dao = dbi.onDemand(NotificationSqlDao.class);
    }

    @Override
    protected void doProcessEvents(int sequenceId) {
        List<Notification> notifications = getReadyNotifications(sequenceId);
        for (Notification cur : notifications) {
            nbProcessedEvents.incrementAndGet();
            handler.handleReadyNotification(cur.getNotificationKey());
        }
        // If anything happens before we get to clear those notifications, somebody else will pick them up
        clearNotifications(notifications);
    }

    @Override
    public void recordFutureNotificationFromTransaction(final Transmogrifier transactionalDao,
            final DateTime futureNotificationTime, final NotificationKey notificationKey) {
        NotificationSqlDao transactionalNotificationDao =  transactionalDao.become(NotificationSqlDao.class);
        Notification notification = new DefaultNotification(notificationKey.toString(), futureNotificationTime);
        transactionalNotificationDao.insertNotification(notification);
    }


    private void clearNotifications(final Collection<Notification> cleared) {

        log.debug(String.format("NotificationQueue %s clearEventsReady START cleared size = %d",
                getFullQName(),
                cleared.size()));

        dao.inTransaction(new Transaction<Void, NotificationSqlDao>() {

            @Override
            public Void inTransaction(NotificationSqlDao transactional,
                    TransactionStatus status) throws Exception {
                for (Notification cur : cleared) {
                    transactional.clearNotification(cur.getId().toString(), hostname);
                    log.debug(String.format("NotificationQueue %s cleared events %s", getFullQName(), cur.getId()));
                }
                return null;
            }
        });
    }

    private List<Notification> getReadyNotifications(final int seqId) {

        final Date now = clock.getUTCNow().toDate();
        final Date nextAvailable = clock.getUTCNow().plus(config.getDaoClaimTimeMs()).toDate();

        log.debug(String.format("NotificationQueue %s getEventsReady START effectiveNow =  %s",  getFullQName(), now));

        List<Notification> result = dao.inTransaction(new Transaction<List<Notification>, NotificationSqlDao>() {

            @Override
            public List<Notification> inTransaction(NotificationSqlDao transactionalDao,
                    TransactionStatus status) throws Exception {

                List<Notification> claimedNotifications = new ArrayList<Notification>();
                List<Notification> input = transactionalDao.getReadyNotifications(now, config.getDaoMaxReadyEvents());
                for (Notification cur : input) {
                    final boolean claimed = (transactionalDao.claimNotification(hostname, nextAvailable, cur.getId().toString(), now) == 1);
                    if (claimed) {
                        claimedNotifications.add(cur);
                        transactionalDao.insertClaimedHistory(seqId, hostname, now, cur.getId().toString());
                    }
                }
                return claimedNotifications;
            }
        });

        for (Notification cur : result) {
            log.debug(String.format("NotificationQueue %sclaimed events %s",
                    getFullQName(), cur.getId()));
            if (cur.getOwner() != null && !cur.getOwner().equals(hostname)) {
                log.warn(String.format("NotificationQueue %s stealing notification %s from %s",
                        getFullQName(), cur, cur.getOwner()));
            }
        }
        return result;
    }
}
