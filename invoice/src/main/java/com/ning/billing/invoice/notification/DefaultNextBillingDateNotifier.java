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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.ning.billing.config.InvoiceConfig;
import com.ning.billing.config.NotificationConfig;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.invoice.InvoiceListener;
import com.ning.billing.invoice.api.DefaultInvoiceService;
import com.ning.billing.util.notificationq.NotificationQueue;
import com.ning.billing.util.notificationq.NotificationQueueService;
import com.ning.billing.util.notificationq.NotificationQueueService.NotificationQueueAlreadyExists;
import com.ning.billing.util.notificationq.NotificationQueueService.NotificationQueueHandler;

public class DefaultNextBillingDateNotifier implements  NextBillingDateNotifier {

    private final static Logger log = LoggerFactory.getLogger(DefaultNextBillingDateNotifier.class);

    public static final String NEXT_BILLING_DATE_NOTIFIER_QUEUE = "next-billing-date-queue";

    private final NotificationQueueService notificationQueueService;
	private final InvoiceConfig config;
	private final EntitlementUserApi entitlementUserApi;

	
    private NotificationQueue nextBillingQueue;
	private final InvoiceListener listener;

    @Inject
	public DefaultNextBillingDateNotifier(NotificationQueueService notificationQueueService,
			InvoiceConfig config, EntitlementUserApi entitlementUserApi, InvoiceListener listener){
		this.notificationQueueService = notificationQueueService;
		this.config = config;
        this.entitlementUserApi = entitlementUserApi;
        this.listener = listener;
	}

    @Override
    public void initialize() {
		try {
            nextBillingQueue = notificationQueueService.createNotificationQueue(DefaultInvoiceService.INVOICE_SERVICE_NAME,
            		NEXT_BILLING_DATE_NOTIFIER_QUEUE,
                    new NotificationQueueHandler() {
                @Override
                public void handleReadyNotification(String notificationKey, DateTime eventDate) {
                    try {
                        UUID key = UUID.fromString(notificationKey);
                        try {
                            Subscription subscription = entitlementUserApi.getSubscriptionFromId(key);
                            if (subscription == null) {
                                log.warn("Next Billing Date Notification Queue handled spurious notification (key: " + key + ")" );
                            } else {
                                processEvent(key , eventDate);
                            }
                        } catch (EntitlementUserApiException e) {
                            log.warn("Next Billing Date Notification Queue handled spurious notification (key: " + key + ")", e );
                        }
                    } catch (IllegalArgumentException e) {
                		log.error("The key returned from the NextBillingNotificationQueue is not a valid UUID", e);
                		return;
                	}

                }
            },
            new NotificationConfig() {
                
                @Override
                public long getSleepTimeMs() {
                    return config.getSleepTimeMs();
                }
                
                @Override
                public boolean isNotificationProcessingOff() {
                    return config.isNotificationProcessingOff();
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

    private void processEvent(UUID subscriptionId, DateTime eventDateTime) {
        listener.handleNextBillingDateEvent(subscriptionId, eventDateTime);
    }


}
