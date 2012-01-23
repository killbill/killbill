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

import org.joda.time.DateTime;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.ning.billing.config.InvoiceConfig;
import com.ning.billing.invoice.api.DefaultInvoiceService;
import com.ning.billing.lifecycle.KillbillService;
import com.ning.billing.lifecycle.LifecycleHandlerType;
import com.ning.billing.lifecycle.LifecycleHandlerType.LifecycleLevel;
import com.ning.billing.util.eventbus.Bus;
import com.ning.billing.util.eventbus.Bus.EventBusException;
import com.ning.billing.util.notificationq.NotificationConfig;
import com.ning.billing.util.notificationq.NotificationKey;
import com.ning.billing.util.notificationq.NotificationQueue;
import com.ning.billing.util.notificationq.NotificationQueueService;
import com.ning.billing.util.notificationq.NotificationQueueService.NotificationQueueAlreadyExists;
import com.ning.billing.util.notificationq.NotificationQueueService.NotificationQueueHandler;

public class DefaultNextBillingDateNotifier implements  NextBillingDateNotifier {

    private final static Logger log = LoggerFactory.getLogger(DefaultNextBillingDateNotifier.class);

    private static final String NEXT_BILLING_DATE_NOTIFIER_QUEUE = "next-billing-date-queue";

    private final Bus eventBus;
    private final NotificationQueueService notificationQueueService;
	private final InvoiceConfig config;

    private NotificationQueue nextBillingQueue;

    @Inject
	public DefaultNextBillingDateNotifier(NotificationQueueService notificationQueueService, Bus eventBus, InvoiceConfig config){
		this.notificationQueueService = notificationQueueService;
		this.config = config;
		this.eventBus = eventBus;
	}


    @Override
    public void initialize() {
		try {
            nextBillingQueue = notificationQueueService.createNotificationQueue(DefaultInvoiceService.INVOICE_SERVICE_NAME,
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

                    processEvent(subscriptionId);
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
        } catch (NotificationQueueAlreadyExists e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void start() {
    	nextBillingQueue.startQueue();
    }

    @Override
    public void stop() {
        if (nextBillingQueue != null) {
        	nextBillingQueue.stopQueue();
        }
    }

    private void processEvent(UUID subscriptionId) {
        try {
            eventBus.post(new NextBillingDateEvent(subscriptionId));
        } catch (EventBusException e) {
            log.error("Failed to post entitlement event " + subscriptionId, e);
        }
    }

    @Override
    public void insertNextBillingNotification(Transmogrifier transactionalDao, final UUID subscriptionId, DateTime futureNotificationTime) {
    	if (nextBillingQueue != null) {
            log.info("Queuing next billing date notification. id: {}, timestamp: {}", subscriptionId.toString(), futureNotificationTime.toString());
            nextBillingQueue.recordFutureNotificationFromTransaction(transactionalDao, futureNotificationTime, new NotificationKey(){
                @Override
                public String toString() {
                    return subscriptionId.toString();
                }
    	    });
        } else {
            log.error("Attempting to put items on a non-existent queue (NextBillingDateNotifier).");
        }
    }
}
