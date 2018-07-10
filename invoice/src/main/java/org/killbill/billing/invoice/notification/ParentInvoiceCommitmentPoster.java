/*
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

import com.google.inject.Inject;

public class ParentInvoiceCommitmentPoster {

    private static final Logger log = LoggerFactory.getLogger(ParentInvoiceCommitmentPoster.class);

    private final NotificationQueueService notificationQueueService;

    @Inject
    public ParentInvoiceCommitmentPoster(final NotificationQueueService notificationQueueService) {
        this.notificationQueueService = notificationQueueService;
    }

    public void insertParentInvoiceFromTransactionInternal(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory,
                                                           final UUID invoiceId,
                                                           final DateTime futureNotificationTime,
                                                           final InternalCallContext internalCallContext) {
        final NotificationQueue commitInvoiceQueue;
        try {
            commitInvoiceQueue = notificationQueueService.getNotificationQueue(DefaultInvoiceService.INVOICE_SERVICE_NAME,
                                                                               ParentInvoiceCommitmentNotifier.PARENT_INVOICE_COMMITMENT_NOTIFIER_QUEUE);

            // If we see existing notification for the same date we don't insert a new notification
            final Iterable<NotificationEventWithMetadata<ParentInvoiceCommitmentNotificationKey>> futureNotifications = commitInvoiceQueue.getFutureNotificationFromTransactionForSearchKeys(internalCallContext.getAccountRecordId(), internalCallContext.getTenantRecordId(), entitySqlDaoWrapperFactory.getHandle().getConnection());

            boolean existingFutureNotificationWithSameDateAndInvoiceId = false;
            final Iterator<NotificationEventWithMetadata<ParentInvoiceCommitmentNotificationKey>> iterator = futureNotifications.iterator();
            try {
                while (iterator.hasNext()) {
                    final NotificationEventWithMetadata<ParentInvoiceCommitmentNotificationKey> input = iterator.next();
                    final LocalDate notificationEffectiveLocaleDate = internalCallContext.toLocalDate(futureNotificationTime);
                    final LocalDate eventEffectiveLocaleDate = internalCallContext.toLocalDate(input.getEffectiveDate());

                    if (notificationEffectiveLocaleDate.compareTo(eventEffectiveLocaleDate) == 0 && input.getEvent().getUuidKey().equals(invoiceId)) {
                        existingFutureNotificationWithSameDateAndInvoiceId = true;
                    }
                }
            } finally {
                // Go through all results to close the connection
                while (iterator.hasNext()) {
                    iterator.next();
                }
            }

            if (!existingFutureNotificationWithSameDateAndInvoiceId) {
                log.info("Queuing parent invoice commitment notification at {} for invoiceId {}", futureNotificationTime.toString(), invoiceId.toString());

                commitInvoiceQueue.recordFutureNotificationFromTransaction(entitySqlDaoWrapperFactory.getHandle().getConnection(), futureNotificationTime,
                                                                         new ParentInvoiceCommitmentNotificationKey(invoiceId), internalCallContext.getUserToken(),
                                                                         internalCallContext.getAccountRecordId(), internalCallContext.getTenantRecordId());
            } else if (log.isDebugEnabled()) {
                log.debug("*********************   SKIPPING Queuing parent invoice commitment notification at {} for invoiceId {} *******************", futureNotificationTime.toString(), invoiceId.toString());
            }

        } catch (final NoSuchNotificationQueue e) {
            log.error("Attempting to put items on a non-existent queue (ParentInvoiceCommitmentNotifier).", e);
        } catch (final IOException e) {
            log.error("Failed to serialize notificationKey for invoiceId {}", invoiceId);
        }
    }

}
