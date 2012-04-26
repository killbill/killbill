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

package com.ning.billing.invoice.api;

import com.google.inject.Inject;
import com.ning.billing.invoice.InvoiceListener;
import com.ning.billing.invoice.notification.NextBillingDateNotifier;
import com.ning.billing.lifecycle.LifecycleHandlerType;
import com.ning.billing.lifecycle.LifecycleHandlerType.LifecycleLevel;
import com.ning.billing.util.bus.Bus;

public class DefaultInvoiceService implements InvoiceService {

    public static final String INVOICE_SERVICE_NAME = "invoice-service";
    private final NextBillingDateNotifier dateNotifier;
    private final InvoiceListener invoiceListener;
    private final Bus eventBus;

    @Inject
    public DefaultInvoiceService(InvoiceListener invoiceListener, Bus eventBus, NextBillingDateNotifier dateNotifier) {
        this.invoiceListener = invoiceListener;
        this.eventBus = eventBus;
        this.dateNotifier = dateNotifier;
    }


    @Override
    public String getName() {
        return INVOICE_SERVICE_NAME;
    }

    @LifecycleHandlerType(LifecycleHandlerType.LifecycleLevel.INIT_SERVICE)
    public void initialize() {
        dateNotifier.initialize();
    }

    @LifecycleHandlerType(LifecycleLevel.START_SERVICE)
    public void start() {
        dateNotifier.start();
    }

    @LifecycleHandlerType(LifecycleHandlerType.LifecycleLevel.REGISTER_EVENTS)
    public void registerForNotifications() {
        try {
            eventBus.register(invoiceListener);
        } catch (Bus.EventBusException e) {
            throw new RuntimeException("Unable to register to the EventBus!", e);
        }
    }

    @LifecycleHandlerType(LifecycleHandlerType.LifecycleLevel.UNREGISTER_EVENTS)
    public void unregisterForNotifications() {
        try {
            eventBus.unregister(invoiceListener);
        } catch (Bus.EventBusException e) {
            throw new RuntimeException("Unable to unregister to the EventBus!", e);
        }
    }

    @LifecycleHandlerType(LifecycleLevel.STOP_SERVICE)
    public void stop() {
        dateNotifier.stop();
    }
}
