/*
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

package org.killbill.billing.server.notifications;

import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.server.DefaultServerService;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.notificationq.api.NotificationEvent;
import org.killbill.notificationq.api.NotificationQueue;
import org.killbill.notificationq.api.NotificationQueueService;
import org.killbill.notificationq.api.NotificationQueueService.NoSuchNotificationQueue;
import org.killbill.notificationq.api.NotificationQueueService.NotificationQueueAlreadyExists;
import org.killbill.notificationq.api.NotificationQueueService.NotificationQueueHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.inject.Inject;

public class PushNotificationRetryService {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationRetryService.class);
    public static final String QUEUE_NAME = "push-notification-queue";
    private static final String retryService = DefaultServerService.SERVER_SERVICE + "-" + QUEUE_NAME;

    private final NotificationQueueService notificationQueueService;
    private final InternalCallContextFactory internalCallContextFactory;
    private final PushNotificationListener pushNotificationListener;

    private NotificationQueue retryQueue;

    @Inject
    public PushNotificationRetryService(final NotificationQueueService notificationQueueService,
                                        final InternalCallContextFactory internalCallContextFactory,
                                        final PushNotificationListener pushNotificationListener) {
        this.notificationQueueService = notificationQueueService;
        this.internalCallContextFactory = internalCallContextFactory;
        this.pushNotificationListener = pushNotificationListener;
    }

    public void initialize() throws NotificationQueueAlreadyExists {
        retryQueue = notificationQueueService.createNotificationQueue(DefaultServerService.SERVER_SERVICE,
                                                                      QUEUE_NAME,
                                                                      new NotificationQueueHandler() {
                                                                          @Override
                                                                          public void handleReadyNotification(final NotificationEvent notificationKey, final DateTime eventDateTime, final UUID userToken, final Long accountRecordId, final Long tenantRecordId) {
                                                                              if (!(notificationKey instanceof PushNotificationKey)) {
                                                                                  log.error("Push Notification service got an unexpected notification type {}", notificationKey.getClass().getName());
                                                                                  return;
                                                                              }
                                                                              final PushNotificationKey key = (PushNotificationKey) notificationKey;
                                                                              try {
                                                                                  pushNotificationListener.resendPushNotification(key);
                                                                              } catch (JsonProcessingException e) {
                                                                                  log.error("Failed to push notification url='{}', tenantId='{}'", key.getUrl(), key.getTenantId(), e);
                                                                              }
                                                                          }
                                                                      }
                                                                     );
    }

    public void start() {
        retryQueue.startQueue();
    }

    public void stop() throws NoSuchNotificationQueue {
        if (retryQueue != null) {
            retryQueue.stopQueue();
            notificationQueueService.deleteNotificationQueue(retryQueue.getServiceName(), retryQueue.getQueueName());
        }
    }

}
