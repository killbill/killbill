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

package com.ning.billing.ovedue.notification;

import java.util.UUID;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.ning.billing.config.NotificationConfig;
import com.ning.billing.overdue.OverdueProperties;
import com.ning.billing.overdue.service.DefaultOverdueService;
import com.ning.billing.util.notificationq.NotificationQueue;
import com.ning.billing.util.notificationq.NotificationQueueService;
import com.ning.billing.util.notificationq.NotificationQueueService.NotificationQueueAlreadyExists;
import com.ning.billing.util.notificationq.NotificationQueueService.NotificationQueueHandler;

public class DefaultOverdueCheckNotifier implements  OverdueCheckNotifier { 

    private final static Logger log = LoggerFactory.getLogger(DefaultOverdueCheckNotifier.class);

    public static final String OVERDUE_CHECK_NOTIFIER_QUEUE = "overdue-check-queue";

    private final NotificationQueueService notificationQueueService;
	private final OverdueProperties config;

    private NotificationQueue overdueQueue;
	private final OverdueListener listener;

    @Inject
	public DefaultOverdueCheckNotifier(NotificationQueueService notificationQueueService,
	        OverdueProperties config, OverdueListener listener){
		this.notificationQueueService = notificationQueueService;
		this.config = config;
        this.listener = listener;
	}

    @Override
    public void initialize() {
		try {
            overdueQueue = notificationQueueService.createNotificationQueue(DefaultOverdueService.OVERDUE_SERVICE_NAME,
            		OVERDUE_CHECK_NOTIFIER_QUEUE,
                    new NotificationQueueHandler() {
                @Override
                public void handleReadyNotification(String notificationKey, DateTime eventDate) {
                	try {
                 		UUID key = UUID.fromString(notificationKey);
                        processEvent(key , eventDate);
                   	} catch (IllegalArgumentException e) {
                		log.error("The key returned from the NextBillingNotificationQueue is not a valid UUID", e);
                		return;
                	}

                }
            },
            new NotificationConfig() {
                @Override
                public boolean isNotificationProcessingOff() {
                    return config.isNotificationProcessingOff();
                }
                @Override
                public long getNotificationSleepTimeMs() {
                    return config.getNotificationSleepTimeMs();
                }
                @Override
                public int getDaoMaxReadyEvents() {
                    return config.getDaoMaxReadyEvents();
                }
                @Override
                public long getDaoClaimTimeMs() {
                    return config.getDaoClaimTimeMs();
                }
            });
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
        }
    }

    private void processEvent(UUID overdueableId, DateTime eventDateTime) {
        listener.handleNextOverdueCheck(overdueableId, eventDateTime); 
    }


}
