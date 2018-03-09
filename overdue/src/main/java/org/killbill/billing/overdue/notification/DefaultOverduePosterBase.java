/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.overdue.notification;

import java.util.Iterator;
import java.util.UUID;

import javax.inject.Named;

import org.joda.time.DateTime;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.overdue.service.DefaultOverdueService;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.billing.util.entity.dao.EntitySqlDaoTransactionWrapper;
import org.killbill.billing.util.entity.dao.EntitySqlDaoTransactionalJdbiWrapper;
import org.killbill.billing.util.entity.dao.EntitySqlDaoWrapperFactory;
import org.killbill.clock.Clock;
import org.killbill.notificationq.api.NotificationEventWithMetadata;
import org.killbill.notificationq.api.NotificationQueue;
import org.killbill.notificationq.api.NotificationQueueService;
import org.killbill.notificationq.api.NotificationQueueService.NoSuchNotificationQueue;
import org.skife.jdbi.v2.IDBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

import static org.killbill.billing.util.glue.IDBISetup.MAIN_RO_IDBI_NAMED;

public abstract class DefaultOverduePosterBase implements OverduePoster {

    private static final Logger log = LoggerFactory.getLogger(DefaultOverduePosterBase.class);

    private final NotificationQueueService notificationQueueService;
    private final EntitySqlDaoTransactionalJdbiWrapper transactionalSqlDao;

    public DefaultOverduePosterBase(final NotificationQueueService notificationQueueService,
                                    final IDBI dbi, @Named(MAIN_RO_IDBI_NAMED) final IDBI roDbi, final Clock clock, final CacheControllerDispatcher cacheControllerDispatcher,
                                    final NonEntityDao nonEntityDao, final InternalCallContextFactory internalCallContextFactory) {
        this.notificationQueueService = notificationQueueService;
        this.transactionalSqlDao = new EntitySqlDaoTransactionalJdbiWrapper(dbi, roDbi, clock, cacheControllerDispatcher, nonEntityDao, internalCallContextFactory);
    }

    @Override
    public <T extends OverdueCheckNotificationKey> void insertOverdueNotification(final UUID accountId, final DateTime futureNotificationTime, final String overdueQueueName, final T notificationKey, final InternalCallContext context) {
        try {
            final NotificationQueue overdueQueue = notificationQueueService.getNotificationQueue(DefaultOverdueService.OVERDUE_SERVICE_NAME,
                                                                                                 overdueQueueName);

            transactionalSqlDao.execute(false, new EntitySqlDaoTransactionWrapper<Void>() {
                @Override
                public Void inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                    // Check if we already have notifications for that key
                    final Class<T> clazz = (Class<T>) notificationKey.getClass();
                    final Iterable<NotificationEventWithMetadata<T>> futureNotifications = getFutureNotificationsForAccountInTransaction(entitySqlDaoWrapperFactory, overdueQueue,
                                                                                                                                         clazz, context);

                    final boolean shouldInsertNewNotification = cleanupFutureNotificationsFormTransaction(entitySqlDaoWrapperFactory, futureNotifications, futureNotificationTime, overdueQueue);
                    if (shouldInsertNewNotification) {
                        log.debug("Queuing overdue check notification. Account id: {}, timestamp: {}", accountId.toString(), futureNotificationTime.toString());
                        overdueQueue.recordFutureNotificationFromTransaction(entitySqlDaoWrapperFactory.getHandle().getConnection(), futureNotificationTime, notificationKey, context.getUserToken(), context.getAccountRecordId(), context.getTenantRecordId());
                    } else {
                        log.debug("Skipping queuing overdue check notification. Account id: {}, timestamp: {}", accountId.toString(), futureNotificationTime.toString());
                    }
                    return null;
                }
            });
        } catch (final NoSuchNotificationQueue e) {
            log.error("Attempting to put items on a non-existent queue (DefaultOverdueCheck).", e);
        }
    }

    @Override
    public <T extends OverdueCheckNotificationKey> void clearOverdueCheckNotifications(final UUID accountId, final String overdueQueueName, final Class<T> clazz, final InternalCallContext context) {
        try {
            final NotificationQueue checkOverdueQueue = notificationQueueService.getNotificationQueue(DefaultOverdueService.OVERDUE_SERVICE_NAME,
                                                                                                      overdueQueueName);
            transactionalSqlDao.execute(false, new EntitySqlDaoTransactionWrapper<Void>() {
                @Override
                public Void inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                    final Iterable<NotificationEventWithMetadata<T>> futureNotifications = getFutureNotificationsForAccountInTransaction(entitySqlDaoWrapperFactory, checkOverdueQueue,
                                                                                                                                         clazz, context);
                    final Iterator<NotificationEventWithMetadata<T>> iterator = futureNotifications.iterator();
                    try {
                        while (iterator.hasNext()) {
                            final NotificationEventWithMetadata<T> notification = iterator.next();
                            checkOverdueQueue.removeNotificationFromTransaction(entitySqlDaoWrapperFactory.getHandle().getConnection(), notification.getRecordId());
                        }
                    } finally {
                        // Go through all results to close the connection
                        while (iterator.hasNext()) {
                            iterator.next();
                        }
                    }
                    return null;
                }
            });
        } catch (final NoSuchNotificationQueue e) {
            log.error("Attempting to clear items from a non-existent queue (DefaultOverdueCheck).", e);
        }
    }

    @VisibleForTesting
    <T extends OverdueCheckNotificationKey> Iterable<NotificationEventWithMetadata<T>> getFutureNotificationsForAccountInTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory,
                                                                                                                                     final NotificationQueue checkOverdueQueue,
                                                                                                                                     final Class<T> clazz,
                                                                                                                                     final InternalCallContext context) {
        return checkOverdueQueue.getFutureNotificationFromTransactionForSearchKeys(context.getAccountRecordId(), context.getTenantRecordId(), entitySqlDaoWrapperFactory.getHandle().getConnection());
    }

    protected abstract <T extends OverdueCheckNotificationKey> boolean cleanupFutureNotificationsFormTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory,
                                                                                                                 final Iterable<NotificationEventWithMetadata<T>> futureNotifications,
                                                                                                                 final DateTime futureNotificationTime, final NotificationQueue overdueQueue);

}
