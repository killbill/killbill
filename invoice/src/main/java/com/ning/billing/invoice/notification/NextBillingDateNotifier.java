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

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.ning.billing.config.InvoiceConfig;
import com.ning.billing.entitlement.exceptions.EntitlementError;
import com.ning.billing.lifecycle.KillbillService;
import com.ning.billing.lifecycle.LifecycleHandlerType;
import com.ning.billing.lifecycle.LifecycleHandlerType.LifecycleLevel;
import com.ning.billing.util.eventbus.Bus;
import com.ning.billing.util.eventbus.Bus.EventBusException;
import com.ning.billing.util.notificationq.NotificationConfig;
import com.ning.billing.util.notificationq.NotificationQueue;
import com.ning.billing.util.notificationq.NotificationQueueService;
import com.ning.billing.util.notificationq.NotificationQueueService.NotficationQueueAlreadyExists;
import com.ning.billing.util.notificationq.NotificationQueueService.NotificationQueueHandler;

public class NextBillingDateNotifier implements KillbillService {
    private final static Logger log = LoggerFactory.getLogger(NextBillingDateNotifier.class);

    private static final String NEXT_BILLING_DATE_NOTIFIER_SERVICE_NAME = "next-billing-date-notifier";
    private static final String NEXT_BILLING_DATE_NOTIFIER_QUEUE = "next-billing-date-queue";
    
    private final Bus eventBus;
	private boolean stoppedNotificationThread;
	private boolean startedNotificationThread;
    private final NotificationQueueService notificationQueueService;
    private NotificationQueue nextBillingQueue;
	private InvoiceConfig config;

	@Inject
	public NextBillingDateNotifier(NotificationQueueService notificationQueueService, Bus eventBus, InvoiceConfig config){
		this.notificationQueueService = notificationQueueService;
		this.config = config;
		this.eventBus = eventBus;
	}
	
	
	@LifecycleHandlerType(LifecycleLevel.INIT_SERVICE)
    public void initialize() {
		try {
            this.stoppedNotificationThread = false;
            this.startedNotificationThread = false;
            nextBillingQueue = notificationQueueService.createNotificationQueue(NEXT_BILLING_DATE_NOTIFIER_SERVICE_NAME,
            		NEXT_BILLING_DATE_NOTIFIER_QUEUE,
                    new NotificationQueueHandler() {
                @Override
                public void handleReadyNotification(String notificationKey) {
                	UUID subscriptionId;
                	try {
                		subscriptionId = UUID.fromString(notificationKey);
                	} catch (IllegalArgumentException e) {
                		log.error("The key returned from the NextBillingNotificationQueue is not a valid UUID",e);
                		return;
                	}
                    
                    processEventReady(subscriptionId);
                }
            },
            new NotificationConfig() {
                @Override
                public boolean isNotificationProcessingOff() {
                    return config.isEventProcessingOff();
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
                    return config.getDaoMaxReadyEvents();
                }
            });
        } catch (NotficationQueueAlreadyExists e) {
            throw new RuntimeException(e);
        }
    }

    @LifecycleHandlerType(LifecycleLevel.START_SERVICE)
    public void start() {
    	nextBillingQueue.startQueue();
    }

    @LifecycleHandlerType(LifecycleLevel.STOP_SERVICE)
    public void stop() {
        if (nextBillingQueue != null) {
        	nextBillingQueue.stopQueue();
        }
        startedNotificationThread = false;
    }
     
	@Override
	public String getName() {
		return NEXT_BILLING_DATE_NOTIFIER_SERVICE_NAME;
	}
	
    public void processEventReady(UUID subscriptionId) {
        try {
            eventBus.post(new NextBillingDateEvent(subscriptionId));
        } catch (EventBusException e) {
            log.error("Failed to post entitlement event " + subscriptionId, e);
        }
    }

}
