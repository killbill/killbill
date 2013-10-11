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

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.skife.jdbi.v2.IDBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.callcontext.InternalCallContext;
import com.ning.billing.clock.Clock;
import com.ning.billing.notificationq.api.NotificationEventWithMetadata;
import com.ning.billing.notificationq.api.NotificationQueue;
import com.ning.billing.notificationq.api.NotificationQueueService;
import com.ning.billing.notificationq.api.NotificationQueueService.NoSuchNotificationQueue;
import com.ning.billing.overdue.service.DefaultOverdueService;
import com.ning.billing.util.cache.CacheControllerDispatcher;
import com.ning.billing.util.dao.NonEntityDao;
import com.ning.billing.util.entity.dao.EntitySqlDao;
import com.ning.billing.util.entity.dao.EntitySqlDaoTransactionWrapper;
import com.ning.billing.util.entity.dao.EntitySqlDaoTransactionalJdbiWrapper;
import com.ning.billing.util.entity.dao.EntitySqlDaoWrapperFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;

public abstract class DefaultOverduePosterBase implements OverduePoster {

    private static final Logger log = LoggerFactory.getLogger(DefaultOverduePosterBase.class);

    private final NotificationQueueService notificationQueueService;
    private final EntitySqlDaoTransactionalJdbiWrapper transactionalSqlDao;

    public DefaultOverduePosterBase(final NotificationQueueService notificationQueueService,
                                    final IDBI dbi, final Clock clock,
                                    final CacheControllerDispatcher cacheControllerDispatcher, final NonEntityDao nonEntityDao) {
        this.notificationQueueService = notificationQueueService;
        this.transactionalSqlDao = new EntitySqlDaoTransactionalJdbiWrapper(dbi, clock, cacheControllerDispatcher, nonEntityDao);
    }

    @Override
    public <T extends OverdueCheckNotificationKey> void insertOverdueNotification(final UUID accountId, final DateTime futureNotificationTime, final String overdueQueueName, final T notificationKey, final InternalCallContext context) {

        try {
            final NotificationQueue overdueQueue = notificationQueueService.getNotificationQueue(DefaultOverdueService.OVERDUE_SERVICE_NAME,
                                                                                                 overdueQueueName);

            transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<Void>() {
                @Override
                public Void inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {

                    // Check if we already have notifications for that key
                    final Class<T> clazz = (Class<T>) notificationKey.getClass();
                    final Collection<NotificationEventWithMetadata<T>> futureNotifications = getFutureNotificationsForAccountInTransaction(entitySqlDaoWrapperFactory, overdueQueue, accountId,
                                                                                                                                           clazz, context);

                    boolean shouldInsertNewNotification = cleanupFutureNotificationsFormTransaction(entitySqlDaoWrapperFactory, futureNotifications, futureNotificationTime, overdueQueue);
                    if (shouldInsertNewNotification) {
                        log.debug("Queuing overdue check notification. Account id: {}, timestamp: {}", accountId.toString(), futureNotificationTime.toString());
                        overdueQueue.recordFutureNotificationFromTransaction(entitySqlDaoWrapperFactory.getSqlDao(), futureNotificationTime, notificationKey, context.getUserToken(), context.getAccountRecordId(), context.getTenantRecordId());
                    } else {
                        log.debug("Skipping queuing overdue check notification. Account id: {}, timestamp: {}", accountId.toString(), futureNotificationTime.toString());
                    }
                    return null;
                }
            });
        } catch (NoSuchNotificationQueue e) {
            log.error("Attempting to put items on a non-existent queue (DefaultOverdueCheck).", e);
            return;
        }
    }


    @Override
    public <T extends OverdueCheckNotificationKey> void clearOverdueCheckNotifications(final UUID accountId, final String overdueQueueName, final Class<T> clazz, final InternalCallContext context) {
        try {
            final NotificationQueue checkOverdueQueue = notificationQueueService.getNotificationQueue(DefaultOverdueService.OVERDUE_SERVICE_NAME,
                                                                                                      overdueQueueName);
            transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<Void>() {
                @Override
                public Void inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                    final Collection<NotificationEventWithMetadata<T>> futureNotifications = getFutureNotificationsForAccountInTransaction(entitySqlDaoWrapperFactory, checkOverdueQueue, accountId,
                                                                                                                                           clazz, context);
                    for (final NotificationEventWithMetadata<T> notification : futureNotifications) {
                        checkOverdueQueue.removeNotificationFromTransaction(entitySqlDaoWrapperFactory.getSqlDao(), notification.getRecordId());
                    }
                    return null;
                }
            });
        } catch (NoSuchNotificationQueue e) {
            log.error("Attempting to clear items from a non-existent queue (DefaultOverdueCheck).", e);
        }
    }

    @VisibleForTesting
    <T extends OverdueCheckNotificationKey> Collection<NotificationEventWithMetadata<T>> getFutureNotificationsForAccountInTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory,
                                                                                                                                       final NotificationQueue checkOverdueQueue,
                                                                                                                                       final UUID accountId,
                                                                                                                                       final Class<T> clazz,
                                                                                                                                       final InternalCallContext context) {

        final List<NotificationEventWithMetadata<T>> notifications = checkOverdueQueue.getFutureNotificationFromTransactionForSearchKey1(clazz, context.getAccountRecordId(), entitySqlDaoWrapperFactory.getSqlDao());

        /*
        final Collection<NotificationEventWithMetadata<T>> notificationsFiltered = Collections2.filter(notifications, new Predicate<NotificationEventWithMetadata<T>>() {
            @Override
            public boolean apply(@Nullable final NotificationEventWithMetadata<T> input) {
                final OverdueCheckNotificationKey notificationKey = input.getEvent();
                return (accountId.equals(notificationKey.getUuidKey()));
            }
        });
        return notificationsFiltered;
        */
        return notifications;
    }


    protected abstract <T extends OverdueCheckNotificationKey> boolean cleanupFutureNotificationsFormTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory,
                                                                                                      final Collection<NotificationEventWithMetadata<T>> futureNotifications,
                                                                                                      final DateTime futureNotificationTime, final NotificationQueue overdueQueue);


}
