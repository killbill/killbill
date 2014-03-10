/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.payment.glue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.killbill.bus.api.PersistentBus;
import org.killbill.billing.lifecycle.LifecycleHandlerType;
import org.killbill.billing.lifecycle.LifecycleHandlerType.LifecycleLevel;
import org.killbill.notificationq.api.NotificationQueueService.NoSuchNotificationQueue;
import org.killbill.notificationq.api.NotificationQueueService.NotificationQueueAlreadyExists;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.billing.payment.api.PaymentService;
import org.killbill.billing.payment.bus.InvoiceHandler;
import org.killbill.billing.payment.bus.PaymentTagHandler;
import org.killbill.billing.payment.retry.AutoPayRetryService;
import org.killbill.billing.payment.retry.FailedPaymentRetryService;
import org.killbill.billing.payment.retry.PluginFailureRetryService;

import com.google.inject.Inject;

public class DefaultPaymentService implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(DefaultPaymentService.class);

    public static final String SERVICE_NAME = "payment-service";

    private final InvoiceHandler invoiceHandler;
    private final PaymentTagHandler tagHandler;
    private final PersistentBus eventBus;
    private final PaymentApi api;
    private final FailedPaymentRetryService failedRetryService;
    private final PluginFailureRetryService timedoutRetryService;
    private final AutoPayRetryService autoPayoffRetryService;

    @Inject
    public DefaultPaymentService(final InvoiceHandler invoiceHandler,
                                 final PaymentTagHandler tagHandler,
                                 final PaymentApi api, final PersistentBus eventBus,
                                 final FailedPaymentRetryService failedRetryService,
                                 final PluginFailureRetryService timedoutRetryService,
                                 final AutoPayRetryService autoPayoffRetryService) {
        this.invoiceHandler = invoiceHandler;
        this.tagHandler = tagHandler;
        this.eventBus = eventBus;
        this.api = api;
        this.failedRetryService = failedRetryService;
        this.timedoutRetryService = timedoutRetryService;
        this.autoPayoffRetryService = autoPayoffRetryService;
    }

    @Override
    public String getName() {
        return SERVICE_NAME;
    }

    @LifecycleHandlerType(LifecycleLevel.INIT_SERVICE)
    public void initialize() throws NotificationQueueAlreadyExists {
        try {
            eventBus.register(invoiceHandler);
            eventBus.register(tagHandler);
        } catch (PersistentBus.EventBusException e) {
            log.error("Unable to register with the EventBus!", e);
        }
        failedRetryService.initialize(SERVICE_NAME);
        timedoutRetryService.initialize(SERVICE_NAME);
        autoPayoffRetryService.initialize(SERVICE_NAME);
    }

    @LifecycleHandlerType(LifecycleLevel.START_SERVICE)
    public void start() {
        failedRetryService.start();
        timedoutRetryService.start();
        autoPayoffRetryService.start();
    }

    @LifecycleHandlerType(LifecycleLevel.STOP_SERVICE)
    public void stop() throws NoSuchNotificationQueue {
        try {
            eventBus.unregister(invoiceHandler);
            eventBus.unregister(tagHandler);
        } catch (PersistentBus.EventBusException e) {
            throw new RuntimeException("Unable to unregister to the EventBus!", e);
        }
        failedRetryService.stop();
        timedoutRetryService.stop();
        autoPayoffRetryService.stop();
    }

    @Override
    public PaymentApi getPaymentApi() {
        return api;
    }
}
