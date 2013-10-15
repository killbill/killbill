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

package com.ning.billing.overdue.notification;

import java.util.UUID;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.callcontext.InternalCallContext;
import com.ning.billing.notificationq.api.NotificationEvent;
import com.ning.billing.notificationq.api.NotificationQueue;
import com.ning.billing.notificationq.api.NotificationQueueService;
import com.ning.billing.notificationq.api.NotificationQueueService.NoSuchNotificationQueue;
import com.ning.billing.notificationq.api.NotificationQueueService.NotificationQueueAlreadyExists;
import com.ning.billing.notificationq.api.NotificationQueueService.NotificationQueueHandler;
import com.ning.billing.overdue.OverdueProperties;
import com.ning.billing.overdue.listener.OverdueDispatcher;
import com.ning.billing.overdue.service.DefaultOverdueService;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.callcontext.UserType;

public abstract class DefaultOverdueNotifierBase implements OverdueNotifier {


    private static final Logger log = LoggerFactory.getLogger(DefaultOverdueNotifierBase.class);

    private final InternalCallContextFactory internalCallContextFactory;
    protected final NotificationQueueService notificationQueueService;
    protected final OverdueProperties config;
    protected final OverdueDispatcher dispatcher;
    protected NotificationQueue overdueQueue;

    public abstract String getQueueName();

    public abstract void handleReadyNotification(final NotificationEvent notificationKey, final DateTime eventDate, final UUID userToken, final Long accountRecordId, final Long tenantRecordId);

    public DefaultOverdueNotifierBase(final NotificationQueueService notificationQueueService,
                                      final OverdueProperties config,
                                      final InternalCallContextFactory internalCallContextFactory,
                                      final OverdueDispatcher dispatcher) {
        this.notificationQueueService = notificationQueueService;
        this.config = config;
        this.dispatcher = dispatcher;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Override
    public void initialize() {

        final OverdueNotifier myself = this;

        final NotificationQueueHandler notificationQueueHandler = new NotificationQueueHandler() {
            @Override
            public void handleReadyNotification(final NotificationEvent notificationKey, final DateTime eventDate, final UUID userToken, final Long accountRecordId, final Long tenantRecordId) {
                myself.handleReadyNotification(notificationKey, eventDate, userToken, accountRecordId, tenantRecordId);
            }
        };

        try {
            overdueQueue = notificationQueueService.createNotificationQueue(DefaultOverdueService.OVERDUE_SERVICE_NAME,
                                                                            getQueueName(),
                                                                            notificationQueueHandler);
        } catch (NotificationQueueAlreadyExists e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void start() {
        overdueQueue.startQueue();
    }

    @Override
    public void stop() {
        if (overdueQueue != null) {
            overdueQueue.stopQueue();
            try {
                notificationQueueService.deleteNotificationQueue(overdueQueue.getServiceName(), overdueQueue.getQueueName());
            } catch (NoSuchNotificationQueue e) {
                log.error("Error deleting a queue by its own name - this should never happen", e);
            }
        }
    }

    protected InternalCallContext createCallContext(final UUID userToken, final Long accountRecordId, final Long tenantRecordId) {
        return internalCallContextFactory.createInternalCallContext(tenantRecordId, accountRecordId, "OverdueService", CallOrigin.INTERNAL, UserType.SYSTEM, userToken);
    }

}
