/*
 * Copyright 2010-2013 Ning, Inc.
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

package com.ning.billing.ovedue.notification;

import java.util.ArrayList;
import java.util.List;

import org.joda.time.DateTime;
import org.skife.jdbi.v2.IDBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.junction.api.Blockable;
import com.ning.billing.junction.api.Type;
import com.ning.billing.overdue.service.DefaultOverdueService;
import com.ning.billing.util.cache.CacheControllerDispatcher;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.dao.NonEntityDao;
import com.ning.billing.util.entity.dao.EntitySqlDao;
import com.ning.billing.util.entity.dao.EntitySqlDaoTransactionWrapper;
import com.ning.billing.util.entity.dao.EntitySqlDaoTransactionalJdbiWrapper;
import com.ning.billing.util.entity.dao.EntitySqlDaoWrapperFactory;
import com.ning.billing.util.notificationq.Notification;
import com.ning.billing.util.notificationq.NotificationQueue;
import com.ning.billing.util.notificationq.NotificationQueueService;
import com.ning.billing.util.notificationq.NotificationQueueService.NoSuchNotificationQueue;
import com.ning.billing.util.queue.PersistentQueueBase;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;

public class DefaultOverdueCheckPoster implements OverdueCheckPoster {

    private static final Logger log = LoggerFactory.getLogger(DefaultOverdueCheckPoster.class);

    private final NotificationQueueService notificationQueueService;
    private final EntitySqlDaoTransactionalJdbiWrapper transactionalSqlDao;

    @Inject
    public DefaultOverdueCheckPoster(final NotificationQueueService notificationQueueService,
                                     final IDBI dbi, final Clock clock,
                                     final CacheControllerDispatcher cacheControllerDispatcher, final NonEntityDao nonEntityDao) {
        this.notificationQueueService = notificationQueueService;
        this.transactionalSqlDao = new EntitySqlDaoTransactionalJdbiWrapper(dbi, clock, cacheControllerDispatcher, nonEntityDao);
    }

    @Override
    public void insertOverdueCheckNotification(final Blockable overdueable, final DateTime futureNotificationTime, final InternalCallContext context) {
        final NotificationQueue checkOverdueQueue;
        try {
            checkOverdueQueue = notificationQueueService.getNotificationQueue(DefaultOverdueService.OVERDUE_SERVICE_NAME,
                                                                              DefaultOverdueCheckNotifier.OVERDUE_CHECK_NOTIFIER_QUEUE);

            transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<Void>() {
                @Override
                public Void inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                    boolean shouldInsertNewNotification = true;

                    // Check if we already have notifications for that key
                    final List<Notification> futureNotifications = getFutureNotificationsForAccountAndOverdueableInTransaction(entitySqlDaoWrapperFactory, checkOverdueQueue, overdueable, context);
                    if (futureNotifications.size() > 0) {
                        // Results are ordered by effective date asc
                        final DateTime earliestExistingNotificationDate = futureNotifications.get(0).getEffectiveDate();

                        final int minIndexToDeleteFrom;
                        if (earliestExistingNotificationDate.isBefore(futureNotificationTime)) {
                            // We don't have to insert a new one. For sanity, delete any other future notification
                            minIndexToDeleteFrom = 1;
                            shouldInsertNewNotification = false;
                        } else {
                            // We win - we are before any other already recorded. Delete all others.
                            minIndexToDeleteFrom = 0;
                        }

                        for (int i = minIndexToDeleteFrom; i < futureNotifications.size(); i++) {
                            checkOverdueQueue.removeNotificationFromTransaction(entitySqlDaoWrapperFactory, futureNotifications.get(i).getId(), context);
                        }
                    }

                    if (shouldInsertNewNotification) {
                        log.debug("Queuing overdue check notification. Overdueable id: {}, timestamp: {}", overdueable.getId().toString(), futureNotificationTime.toString());
                        final OverdueCheckNotificationKey notificationKey = new OverdueCheckNotificationKey(overdueable.getId(), Type.get(overdueable));
                        checkOverdueQueue.recordFutureNotificationFromTransaction(entitySqlDaoWrapperFactory, futureNotificationTime, notificationKey, context);
                    } else {
                        log.debug("Skipping queuing overdue check notification. Overdueable id: {}, timestamp: {}", overdueable.getId().toString(), futureNotificationTime.toString());
                    }

                    return null;
                }
            });
        } catch (NoSuchNotificationQueue e) {
            log.error("Attempting to put items on a non-existent queue (DefaultOverdueCheck).", e);
        }
    }

    @Override
    public void clearNotificationsFor(final Blockable overdueable, final InternalCallContext context) {
        try {
            final NotificationQueue checkOverdueQueue = notificationQueueService.getNotificationQueue(DefaultOverdueService.OVERDUE_SERVICE_NAME,
                                                                                                      DefaultOverdueCheckNotifier.OVERDUE_CHECK_NOTIFIER_QUEUE);
            transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<Void>() {

                @Override
                public Void inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                    final List<Notification> futureNotifications = getFutureNotificationsForAccountAndOverdueableInTransaction(entitySqlDaoWrapperFactory, checkOverdueQueue, overdueable, context);
                    for (final Notification notification : futureNotifications) {
                        checkOverdueQueue.removeNotificationFromTransaction(entitySqlDaoWrapperFactory, notification.getId(), context);
                    }

                    return null;
                }
            });
        } catch (NoSuchNotificationQueue e) {
            log.error("Attempting to clear items from a non-existent queue (DefaultOverdueCheck).", e);
        }
    }

    @VisibleForTesting
    List<Notification> getFutureNotificationsForAccountAndOverdueableInTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory,
                                                                                   final NotificationQueue checkOverdueQueue,
                                                                                   final Blockable overdueable,
                                                                                   final InternalCallContext context) {
        final List<Notification> notifications = new ArrayList<Notification>();

        final List<Notification> candidates = checkOverdueQueue.getFutureNotificationsForAccountFromTransaction(entitySqlDaoWrapperFactory, context);
        for (final Notification candidate : candidates) {
            if (OverdueCheckNotificationKey.class.getName().equals(candidate.getNotificationKeyClass())) {
                final OverdueCheckNotificationKey key = PersistentQueueBase.deserializeEvent(candidate.getNotificationKeyClass(), candidate.getNotificationKey());

                if (Type.get(overdueable).equals(key.getType()) && overdueable.getId().equals(key.getUuidKey())) {
                    notifications.add(candidate);
                }
            }
        }

        return notifications;
    }
}
