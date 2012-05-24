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

package com.ning.billing.payment.glue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.ning.billing.lifecycle.LifecycleHandlerType;
import com.ning.billing.lifecycle.LifecycleHandlerType.LifecycleLevel;
import com.ning.billing.payment.RequestProcessor;
import com.ning.billing.payment.api.PaymentApi;
import com.ning.billing.payment.api.PaymentService;
import com.ning.billing.payment.retry.FailedPaymentRetryService;
import com.ning.billing.payment.retry.TimedoutPaymentRetryService;
import com.ning.billing.util.bus.Bus;
import com.ning.billing.util.notificationq.NotificationQueueService.NoSuchNotificationQueue;
import com.ning.billing.util.notificationq.NotificationQueueService.NotificationQueueAlreadyExists;

public class DefaultPaymentService implements PaymentService {
    private static final Logger log = LoggerFactory.getLogger(DefaultPaymentService.class);

    private static final String SERVICE_NAME = "payment-service";

    private final RequestProcessor requestProcessor;
    private final Bus eventBus;
    private final PaymentApi api;
    private final FailedPaymentRetryService failedRetryService;
    private final TimedoutPaymentRetryService timedoutRetryService;

    @Inject
    public DefaultPaymentService(final RequestProcessor requestProcessor, final PaymentApi api, final Bus eventBus,
            final FailedPaymentRetryService failedRetryService, final TimedoutPaymentRetryService timedoutRetryService) {
        this.requestProcessor = requestProcessor;
        this.eventBus = eventBus;
        this.api = api;
        this.failedRetryService = failedRetryService;
        this.timedoutRetryService = timedoutRetryService;
    }

    @Override
    public String getName() {
        return SERVICE_NAME;
    }

    @LifecycleHandlerType(LifecycleLevel.INIT_SERVICE)
    public void initialize() throws NotificationQueueAlreadyExists {
        failedRetryService.initialize(SERVICE_NAME);
        timedoutRetryService.initialize(SERVICE_NAME);
    }
    
    @LifecycleHandlerType(LifecycleHandlerType.LifecycleLevel.REGISTER_EVENTS)
    public void registerForNotifications() {
        try {
            eventBus.register(requestProcessor);
        }
        catch (Bus.EventBusException e) {
            log.error("Unable to register with the EventBus!", e);
        }
    }
    
    @LifecycleHandlerType(LifecycleLevel.START_SERVICE)
    public void start() {
        failedRetryService.start();
        timedoutRetryService.start();
    }

    @LifecycleHandlerType(LifecycleLevel.STOP_SERVICE)
    public void stop() throws NoSuchNotificationQueue {
        failedRetryService.stop();
        timedoutRetryService.stop();
    }

    @Override
    public PaymentApi getPaymentApi() {
        return api;
    }
}
