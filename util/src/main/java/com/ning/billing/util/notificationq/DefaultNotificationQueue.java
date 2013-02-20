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
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.skife.jdbi.v2.IDBI;

import com.ning.billing.util.Hostname;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.entity.dao.EntitySqlDao;
import com.ning.billing.util.entity.dao.EntitySqlDaoWrapperFactory;
import com.ning.billing.util.notificationq.NotificationQueueService.NotificationQueueHandler;
import com.ning.billing.util.notificationq.dao.NotificationSqlDao;

import com.fasterxml.jackson.databind.ObjectMapper;

public class DefaultNotificationQueue implements NotificationQueue {

    private final NotificationSqlDao dao;
    private final String hostname;
    private final String svcName;
    private final String queueName;
    private final NotificationQueueHandler handler;
    private final NotificationQueueService notificationQueueService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    private volatile boolean isStarted;

    public DefaultNotificationQueue(final String svcName, final String queueName, final NotificationQueueHandler handler,
                                    final IDBI dbi, final NotificationQueueService notificationQueueService,
                                    final Clock clock) {
        this.svcName = svcName;
        this.queueName = queueName;
        this.handler = handler;
        this.dao = dbi.onDemand(NotificationSqlDao.class);
        this.hostname = Hostname.get();
        this.notificationQueueService = notificationQueueService;
        this.objectMapper = new ObjectMapper();
        this.clock = clock;
    }

    @Override
    public void recordFutureNotification(final DateTime futureNotificationTime,
                                         final NotificationKey notificationKey,
                                         final InternalCallContext context) throws IOException {
        recordFutureNotificationInternal(futureNotificationTime, notificationKey, dao, context);
    }

    @Override
    public void recordFutureNotificationFromTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> transactionalDao,
                                                        final DateTime futureNotificationTime,
                                                        final NotificationKey notificationKey,
                                                        final InternalCallContext context) throws IOException {
        final NotificationSqlDao transactionalNotificationDao = transactionalDao.transmogrify(NotificationSqlDao.class);
        recordFutureNotificationInternal(futureNotificationTime, notificationKey, transactionalNotificationDao, context);
    }

    private void recordFutureNotificationInternal(final DateTime futureNotificationTime,
                                                  final NotificationKey notificationKey,
                                                  final NotificationSqlDao thisDao,
                                                  final InternalCallContext context) throws IOException {
        final String json = objectMapper.writeValueAsString(notificationKey);
        final UUID futureUserToken = UUID.randomUUID();

        final Notification notification = new DefaultNotification(getFullQName(), hostname, notificationKey.getClass().getName(), json,
                                                                  context.getUserToken(), futureUserToken, futureNotificationTime, context.getAccountRecordId(), context.getTenantRecordId());
        thisDao.insertNotification(notification, context);
    }

    @Override
    public List<Notification> getFutureNotificationsForAccount(final InternalCallContext context) {
        return getFutureNotificationsForAccountInternal(dao, context);
    }

    @Override
    public List<Notification> getFutureNotificationsForAccountFromTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> transactionalDao,
                                                                              final InternalCallContext context) {
        final NotificationSqlDao transactionalNotificationDao = transactionalDao.transmogrify(NotificationSqlDao.class);
        return getFutureNotificationsForAccountInternal(transactionalNotificationDao, context);
    }

    private List<Notification> getFutureNotificationsForAccountInternal(final NotificationSqlDao transactionalDao, final InternalCallContext context) {
        return transactionalDao.getFutureNotificationsForAccount(clock.getUTCNow().toDate(), getFullQName(), context);
    }

    @Override
    public void removeNotification(final UUID notificationId, final InternalCallContext context) {
        dao.removeNotification(notificationId.toString(), context);
    }

    @Override
    public void removeNotificationFromTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> transactionalDao, final UUID notificationId, final InternalCallContext context) {
        final NotificationSqlDao transactionalNotificationDao = transactionalDao.transmogrify(NotificationSqlDao.class);
        transactionalNotificationDao.removeNotification(notificationId.toString(), context);
    }

    @Override
    public String getFullQName() {
        return NotificationQueueServiceBase.getCompositeName(svcName, queueName);
    }

    @Override
    public String getServiceName() {
        return svcName;
    }

    @Override
    public String getQueueName() {
        return queueName;
    }

    @Override
    public NotificationQueueHandler getHandler() {
        return handler;
    }

    @Override
    public void startQueue() {
        notificationQueueService.startQueue();
        isStarted = true;
    }

    @Override
    public void stopQueue() {
        // Order matters...
        isStarted = false;
        notificationQueueService.stopQueue();
    }

    @Override
    public boolean isStarted() {
        return isStarted;
    }

}
