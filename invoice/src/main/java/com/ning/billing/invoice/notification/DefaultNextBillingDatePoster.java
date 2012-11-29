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

package com.ning.billing.invoice.notification;

import java.io.IOException;
import java.util.UUID;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.invoice.api.DefaultInvoiceService;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.callcontext.UserType;
import com.ning.billing.util.entity.dao.EntitySqlDao;
import com.ning.billing.util.entity.dao.EntitySqlDaoWrapperFactory;
import com.ning.billing.util.notificationq.NotificationQueue;
import com.ning.billing.util.notificationq.NotificationQueueService;
import com.ning.billing.util.notificationq.NotificationQueueService.NoSuchNotificationQueue;

import com.google.inject.Inject;

public class DefaultNextBillingDatePoster implements NextBillingDatePoster {

    private static final Logger log = LoggerFactory.getLogger(DefaultNextBillingDatePoster.class);

    private final NotificationQueueService notificationQueueService;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public DefaultNextBillingDatePoster(final NotificationQueueService notificationQueueService,
                                        final InternalCallContextFactory internalCallContextFactory) {
        this.notificationQueueService = notificationQueueService;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Override
    public void insertNextBillingNotification(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory, final UUID accountId,
                                              final UUID subscriptionId, final DateTime futureNotificationTime, final UUID userToken) {
        final InternalCallContext context = createCallContext(accountId, userToken);

        final NotificationQueue nextBillingQueue;
        try {
            nextBillingQueue = notificationQueueService.getNotificationQueue(DefaultInvoiceService.INVOICE_SERVICE_NAME,
                                                                             DefaultNextBillingDateNotifier.NEXT_BILLING_DATE_NOTIFIER_QUEUE);
            log.info("Queuing next billing date notification at {} for subscriptionId {}", futureNotificationTime.toString(), subscriptionId.toString());

            nextBillingQueue.recordFutureNotificationFromTransaction(entitySqlDaoWrapperFactory, futureNotificationTime,
                                                                     new NextBillingDateNotificationKey(subscriptionId), context);
        } catch (NoSuchNotificationQueue e) {
            log.error("Attempting to put items on a non-existent queue (NextBillingDateNotifier).", e);
        } catch (IOException e) {
            log.error("Failed to serialize notificationKey for subscriptionId {}", subscriptionId);
        }
    }

    private InternalCallContext createCallContext(final UUID accountId, final UUID userToken) {
        return internalCallContextFactory.createInternalCallContext(accountId, "NextBillingDatePoster", CallOrigin.INTERNAL, UserType.SYSTEM, userToken);
    }
}
