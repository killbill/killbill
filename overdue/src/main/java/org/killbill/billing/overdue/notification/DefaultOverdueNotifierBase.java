/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2020 Groupon, Inc
 * Copyright 2020-2021 Equinix, Inc
 * Copyright 2014-2021 The Billing Project, LLC
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

package org.killbill.billing.overdue.notification;

import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.clock.Clock;
import org.killbill.queue.retry.RetryableHandler;
import org.killbill.queue.retry.RetryableService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.notificationq.api.NotificationEvent;
import org.killbill.notificationq.api.NotificationQueue;
import org.killbill.notificationq.api.NotificationQueueService;
import org.killbill.notificationq.api.NotificationQueueService.NoSuchNotificationQueue;
import org.killbill.notificationq.api.NotificationQueueService.NotificationQueueAlreadyExists;
import org.killbill.notificationq.api.NotificationQueueService.NotificationQueueHandler;
import org.killbill.billing.overdue.OverdueProperties;
import org.killbill.billing.overdue.listener.OverdueDispatcher;
import org.killbill.billing.overdue.service.DefaultOverdueService;
import org.killbill.billing.util.callcontext.CallOrigin;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.UserType;

public abstract class DefaultOverdueNotifierBase extends RetryableService implements OverdueNotifier {


    private static final Logger log = LoggerFactory.getLogger(DefaultOverdueNotifierBase.class);

    private final InternalCallContextFactory internalCallContextFactory;
    protected final NotificationQueueService notificationQueueService;
    protected final OverdueProperties config;
    protected final OverdueDispatcher dispatcher;
    protected NotificationQueue overdueQueue;

    private final Clock clock;

    private final String queueName;

    public abstract String getQueueName();

    public abstract void handleReadyNotification(final NotificationEvent notificationKey, final DateTime eventDate, final UUID userToken, final Long accountRecordId, final Long tenantRecordId);

    public DefaultOverdueNotifierBase(final String queueName,
                                      final NotificationQueueService notificationQueueService,
                                      final OverdueProperties config,
                                      final Clock clock,
                                      final InternalCallContextFactory internalCallContextFactory,
                                      final OverdueDispatcher dispatcher) {
        super(notificationQueueService);
        this.queueName = queueName;
        this.notificationQueueService = notificationQueueService;
        this.config = config;
        this.dispatcher = dispatcher;
        this.clock = clock;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Override
    public void initialize() throws NotificationQueueAlreadyExists {

        final OverdueNotifier myself = this;

        final NotificationQueueHandler originalHandler = new NotificationQueueHandler() {
            @Override
            public void handleReadyNotification(final NotificationEvent notificationKey, final DateTime eventDate, final UUID userToken, final Long accountRecordId, final Long tenantRecordId) {
                myself.handleReadyNotification(notificationKey, eventDate, userToken, accountRecordId, tenantRecordId);
            }
        };

        final RetryableHandler retryQueueHandler = new RetryableHandler(clock, this, originalHandler);
        overdueQueue = notificationQueueService.createNotificationQueue(DefaultOverdueService.OVERDUE_SERVICE_NAME,
                                                                        getQueueName(),
                                                                        retryQueueHandler);
        super.initialize(queueName, originalHandler);
    }

    @Override
    public void start() {
        super.start();
        overdueQueue.startQueue();
    }

    @Override
    public void stop() throws NoSuchNotificationQueue {
        if (overdueQueue != null) {
            try {
                if (!overdueQueue.stopQueue()) {
                    log.warn("Timed out while shutting down {} queue: IN_PROCESSING entries might be left behind", overdueQueue.getFullQName());
                }
                notificationQueueService.deleteNotificationQueue(overdueQueue.getServiceName(), overdueQueue.getQueueName());
            } catch (NoSuchNotificationQueue e) {
                log.error("Error deleting a queue by its own name - this should never happen", e);
            } finally {
                super.stop();
            }
        }
    }

    protected InternalCallContext createCallContext(final UUID userToken, final Long accountRecordId, final Long tenantRecordId) {
        return internalCallContextFactory.createInternalCallContext(tenantRecordId, accountRecordId, "OverdueService", CallOrigin.INTERNAL, UserType.SYSTEM, userToken);
    }

}
