/*
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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

package org.killbill.billing.util.listener;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.Period;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.invoice.plugin.api.InvoicePluginApiRetryException;
import org.killbill.billing.util.callcontext.CallOrigin;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.UserType;
import org.killbill.billing.util.jackson.ObjectMapper;
import org.killbill.notificationq.api.NotificationEvent;
import org.killbill.notificationq.api.NotificationQueue;
import org.killbill.notificationq.api.NotificationQueueService;
import org.killbill.notificationq.api.NotificationQueueService.NoSuchNotificationQueue;
import org.killbill.notificationq.api.NotificationQueueService.NotificationQueueAlreadyExists;
import org.killbill.notificationq.api.NotificationQueueService.NotificationQueueHandler;
import org.killbill.queue.api.QueueEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class RetryableService {

    public static final String RETRYABLE_SERVICE_NAME = "notifications-retries";

    private static final Logger log = LoggerFactory.getLogger(RetryableService.class);

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final NotificationQueueService notificationQueueService;
    private final InternalCallContextFactory internalCallContextFactory;

    private NotificationQueue retryNotificationQueue;

    public RetryableService(final NotificationQueueService notificationQueueService, final InternalCallContextFactory internalCallContextFactory) {
        this.notificationQueueService = notificationQueueService;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    public void initialize(final NotificationQueue originalQueue, final NotificationQueueHandler originalQueueHandler) {
        initialize(originalQueue.getQueueName(), originalQueueHandler);
    }

    public void initialize(final String queueName, final NotificationQueueHandler originalQueueHandler) {
        try {
            final NotificationQueueHandler notificationQueueHandler = new NotificationQueueHandler() {

                @Override
                public void handleReadyNotification(final NotificationEvent eventJson,
                                                    final DateTime eventDateTime,
                                                    final UUID userToken,
                                                    final Long searchKey1,
                                                    final Long searchKey2) {
                    if (eventJson instanceof RetryNotificationEvent) {
                        final RetryNotificationEvent retryNotificationEvent = (RetryNotificationEvent) eventJson;

                        final NotificationEvent notificationEvent;
                        try {
                            notificationEvent = (NotificationEvent) objectMapper.readValue(retryNotificationEvent.getOriginalEvent(), retryNotificationEvent.getOriginalEventClass());
                        } catch (final IOException e) {
                            throw new RuntimeException(e);
                        }

                        try {
                            originalQueueHandler.handleReadyNotification(notificationEvent,
                                                                         eventDateTime,
                                                                         userToken,
                                                                         searchKey1,
                                                                         searchKey2);
                        } catch (final InvoicePluginApiRetryException e) {
                            final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(searchKey2,
                                                                                                                                 searchKey1,
                                                                                                                                 "RetryableService",
                                                                                                                                 CallOrigin.INTERNAL,
                                                                                                                                 UserType.SYSTEM,
                                                                                                                                 userToken);
                            scheduleRetry(e,
                                          notificationEvent,
                                          retryNotificationEvent.getOriginalEffectiveDate(),
                                          internalCallContext,
                                          retryNotificationEvent.getRetryNb() + 1);
                        }
                    } else {
                        log.error("Retry service received an unexpected event className='{}'", eventJson.getClass());
                    }
                }
            };

            this.retryNotificationQueue = notificationQueueService.createNotificationQueue(RETRYABLE_SERVICE_NAME,
                                                                                           queueName,
                                                                                           notificationQueueHandler);
        } catch (final NotificationQueueAlreadyExists notificationQueueAlreadyExists) {
            throw new RuntimeException(notificationQueueAlreadyExists);
        }
    }

    public void start() {
        retryNotificationQueue.startQueue();
    }

    public void stop() throws NoSuchNotificationQueue {
        if (retryNotificationQueue != null) {
            retryNotificationQueue.stopQueue();
            notificationQueueService.deleteNotificationQueue(retryNotificationQueue.getServiceName(), retryNotificationQueue.getQueueName());
        }
    }

    public void scheduleRetry(final InvoicePluginApiRetryException exception,
                              final QueueEvent originalNotificationEvent,
                              final DateTime originalEffectiveDate,
                              final InternalCallContext context,
                              final int retryNb) {
        final DateTime effectiveDate = computeRetryDate(exception, originalEffectiveDate, retryNb);
        if (effectiveDate == null) {
            log.warn("Error processing event, NOT scheduling retry for event='{}', retryNb='{}'", originalNotificationEvent, retryNb, exception);
            return;
        }
        log.warn("Error processing event, scheduling retry for event='{}', effectiveDate='{}', retryNb='{}'", originalNotificationEvent, effectiveDate, retryNb, exception);

        try {
            final NotificationEvent retryNotificationEvent = new RetryNotificationEvent(objectMapper.writeValueAsString(originalNotificationEvent), originalNotificationEvent.getClass(), originalEffectiveDate, retryNb);
            retryNotificationQueue.recordFutureNotification(effectiveDate, retryNotificationEvent, context.getUserToken(), context.getAccountRecordId(), context.getTenantRecordId());
        } catch (final IOException e) {
            log.error("Unable to schedule retry for event='{}', effectiveDate='{}'", originalNotificationEvent, effectiveDate, e);
        }
    }

    private DateTime computeRetryDate(final InvoicePluginApiRetryException invoicePluginApiRetryException, final DateTime initialEventDateTime, final int retryNb) {
        final List<Period> retrySchedule = invoicePluginApiRetryException.getRetrySchedule();
        if (retrySchedule == null || retryNb > retrySchedule.size()) {
            return null;
        } else {
            final Period nextDelay = retrySchedule.get(retryNb - 1);
            return initialEventDateTime.plus(nextDelay);
        }
    }
}
