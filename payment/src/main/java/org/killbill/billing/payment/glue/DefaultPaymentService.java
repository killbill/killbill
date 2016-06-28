/*
 * Copyright 2010-2014 Ning, Inc.
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

package org.killbill.billing.payment.glue;

import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PaymentService;
import org.killbill.billing.payment.bus.PaymentBusEventHandler;
import org.killbill.billing.payment.caching.StateMachineConfigCache;
import org.killbill.billing.payment.core.PaymentExecutors;
import org.killbill.billing.payment.core.janitor.Janitor;
import org.killbill.billing.payment.invoice.PaymentTagHandler;
import org.killbill.billing.payment.retry.DefaultRetryService;
import org.killbill.billing.platform.api.LifecycleHandlerType;
import org.killbill.billing.platform.api.LifecycleHandlerType.LifecycleLevel;
import org.killbill.bus.api.PersistentBus;
import org.killbill.notificationq.api.NotificationQueueService.NoSuchNotificationQueue;
import org.killbill.notificationq.api.NotificationQueueService.NotificationQueueAlreadyExists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;

public class DefaultPaymentService implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(DefaultPaymentService.class);

    public static final String SERVICE_NAME = "payment-service";

    private final PaymentBusEventHandler paymentBusEventHandler;
    private final PaymentTagHandler tagHandler;
    private final PersistentBus eventBus;
    private final PaymentApi api;
    private final DefaultRetryService retryService;
    private final Janitor janitor;
    private final PaymentExecutors paymentExecutors;
    private final StateMachineConfigCache stateMachineConfigCache;

    @Inject
    public DefaultPaymentService(final PaymentBusEventHandler paymentBusEventHandler,
                                 final PaymentTagHandler tagHandler,
                                 final PaymentApi api,
                                 final DefaultRetryService retryService,
                                 final PersistentBus eventBus,
                                 final Janitor janitor,
                                 final PaymentExecutors paymentExecutors,
                                 final StateMachineConfigCache stateMachineConfigCache) {
        this.paymentBusEventHandler = paymentBusEventHandler;
        this.tagHandler = tagHandler;
        this.eventBus = eventBus;
        this.api = api;
        this.retryService = retryService;
        this.janitor = janitor;
        this.paymentExecutors = paymentExecutors;
        this.stateMachineConfigCache = stateMachineConfigCache;
    }

    @Override
    public String getName() {
        return SERVICE_NAME;
    }

    @LifecycleHandlerType(LifecycleLevel.INIT_SERVICE)
    public void initialize() throws NotificationQueueAlreadyExists {
        try {
            stateMachineConfigCache.loadDefaultPaymentStateMachineConfig(PaymentModule.DEFAULT_STATE_MACHINE_PAYMENT_XML);
        } catch (final PaymentApiException e) {
            log.error("Unable to load default payment state machine");
        }

        try {
            eventBus.register(paymentBusEventHandler);
            eventBus.register(tagHandler);
        } catch (final PersistentBus.EventBusException e) {
            log.error("Failed to register bus handlers", e);
        }
        paymentExecutors.initialize();
        retryService.initialize();
        janitor.initialize();
    }

    @LifecycleHandlerType(LifecycleLevel.START_SERVICE)
    public void start() {
        retryService.start();
        janitor.start();
    }

    @LifecycleHandlerType(LifecycleLevel.STOP_SERVICE)
    public void stop() throws NoSuchNotificationQueue {
        try {
            eventBus.unregister(paymentBusEventHandler);
            eventBus.unregister(tagHandler);
        } catch (final PersistentBus.EventBusException e) {
            throw new RuntimeException("Failed to unregister bus handlers", e);
        }
        retryService.stop();
        janitor.stop();
        try {
            paymentExecutors.stop();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("PaymentService got interrupted", e);
        }
    }

    @Override
    public PaymentApi getPaymentApi() {
        return api;
    }
}
