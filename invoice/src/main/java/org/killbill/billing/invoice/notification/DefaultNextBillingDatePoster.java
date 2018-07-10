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

package org.killbill.billing.invoice.notification;

import java.io.IOException;
import java.util.Iterator;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.invoice.api.DefaultInvoiceService;
import org.killbill.billing.util.entity.dao.EntitySqlDaoWrapperFactory;
import org.killbill.notificationq.api.NotificationEventWithMetadata;
import org.killbill.notificationq.api.NotificationQueue;
import org.killbill.notificationq.api.NotificationQueueService;
import org.killbill.notificationq.api.NotificationQueueService.NoSuchNotificationQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.inject.Inject;

public class DefaultNextBillingDatePoster implements NextBillingDatePoster {

    private static final Logger log = LoggerFactory.getLogger(DefaultNextBillingDatePoster.class);

    private static Joiner JOINER = Joiner.on(",");

    private final NotificationQueueService notificationQueueService;

    @Inject
    public DefaultNextBillingDatePoster(final NotificationQueueService notificationQueueService) {
        this.notificationQueueService = notificationQueueService;
    }

    @Override
    public void insertNextBillingNotificationFromTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory,
                                                             final UUID accountId,
                                                             final Iterable<UUID> subscriptionIds,
                                                             final DateTime futureNotificationTime,
                                                             final boolean isRescheduled,
                                                             final InternalCallContext internalCallContext) {
        insertNextBillingFromTransactionInternal(entitySqlDaoWrapperFactory, subscriptionIds, Boolean.FALSE, isRescheduled, futureNotificationTime, futureNotificationTime, internalCallContext);
    }

    @Override
    public void insertNextBillingDryRunNotificationFromTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory,
                                                                   final UUID accountId,
                                                                   final Iterable<UUID> subscriptionIds,
                                                                   final DateTime futureNotificationTime,
                                                                   final DateTime targetDate,
                                                                   final InternalCallContext internalCallContext) {
        insertNextBillingFromTransactionInternal(entitySqlDaoWrapperFactory, subscriptionIds, Boolean.TRUE, null, futureNotificationTime, targetDate, internalCallContext);
    }

    private void insertNextBillingFromTransactionInternal(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory,
                                                          final Iterable<UUID> subscriptionIds,
                                                          final Boolean isDryRunForInvoiceNotification,
                                                          final Boolean isRescheduled,
                                                          final DateTime futureNotificationTime,
                                                          final DateTime targetDate,
                                                          final InternalCallContext internalCallContext) {
        final NotificationQueue nextBillingQueue;
        try {
            nextBillingQueue = notificationQueueService.getNotificationQueue(DefaultInvoiceService.INVOICE_SERVICE_NAME,
                                                                             DefaultNextBillingDateNotifier.NEXT_BILLING_DATE_NOTIFIER_QUEUE);

            // If we see existing notification for the same date (and isDryRunForInvoiceNotification mode), we don't insert a new notification
            final Iterable<NotificationEventWithMetadata<NextBillingDateNotificationKey>> futureNotifications = nextBillingQueue.getFutureNotificationFromTransactionForSearchKeys(internalCallContext.getAccountRecordId(), internalCallContext.getTenantRecordId(), entitySqlDaoWrapperFactory.getHandle().getConnection());

            NotificationEventWithMetadata<NextBillingDateNotificationKey> existingNotificationForEffectiveDate = null;
            final Iterator<NotificationEventWithMetadata<NextBillingDateNotificationKey>> iterator = futureNotifications.iterator();
            try {
                while (iterator.hasNext()) {
                    final NotificationEventWithMetadata<NextBillingDateNotificationKey> input = iterator.next();
                    final boolean isEventDryRunForNotifications = input.getEvent().isDryRunForInvoiceNotification() != null ?
                                                                  input.getEvent().isDryRunForInvoiceNotification() : false;

                    final LocalDate notificationEffectiveLocaleDate = internalCallContext.toLocalDate(futureNotificationTime);
                    final LocalDate eventEffectiveLocaleDate = internalCallContext.toLocalDate(input.getEffectiveDate());

                    if (notificationEffectiveLocaleDate.compareTo(eventEffectiveLocaleDate) == 0 &&
                        ((isDryRunForInvoiceNotification && isEventDryRunForNotifications) ||
                         (!isDryRunForInvoiceNotification && !isEventDryRunForNotifications))) {
                        existingNotificationForEffectiveDate = input;
                    }
                }
            } finally {
                // Go through all results to close the connection
                while (iterator.hasNext()) {
                    iterator.next();
                }
            }

            if (existingNotificationForEffectiveDate == null) {
                log.info("Queuing next billing date notification at {} for subscriptionId {}", futureNotificationTime.toString(), JOINER.join(subscriptionIds));

                final NextBillingDateNotificationKey newNotificationEvent = new NextBillingDateNotificationKey(null, subscriptionIds, targetDate, isDryRunForInvoiceNotification, isRescheduled);
                nextBillingQueue.recordFutureNotificationFromTransaction(entitySqlDaoWrapperFactory.getHandle().getConnection(), futureNotificationTime,
                                                                         newNotificationEvent, internalCallContext.getUserToken(),
                                                                         internalCallContext.getAccountRecordId(), internalCallContext.getTenantRecordId());
            } else {
                log.info("Updating next billing date notification event at {} for subscriptionId {}", futureNotificationTime.toString(), JOINER.join(subscriptionIds));
                final NextBillingDateNotificationKey updateNotificationEvent = new NextBillingDateNotificationKey(existingNotificationForEffectiveDate.getEvent(), subscriptionIds);
                nextBillingQueue.updateFutureNotificationFromTransaction(entitySqlDaoWrapperFactory.getHandle().getConnection(), existingNotificationForEffectiveDate.getRecordId(), updateNotificationEvent, internalCallContext.getAccountRecordId(), internalCallContext.getTenantRecordId());
            }

        } catch (final NoSuchNotificationQueue e) {
            log.error("Attempting to put items on a non-existent queue (NextBillingDateNotifier).", e);
        } catch (final IOException e) {
            log.error("Failed to serialize notificationKey for subscriptionId {}", subscriptionIds);
        }
    }

}
