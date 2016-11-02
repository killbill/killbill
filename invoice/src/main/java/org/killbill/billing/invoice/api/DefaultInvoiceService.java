/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

package org.killbill.billing.invoice.api;

import org.killbill.billing.invoice.notification.ParentInvoiceCommitmentNotifier;
import org.killbill.bus.api.PersistentBus;
import org.killbill.billing.invoice.InvoiceListener;
import org.killbill.billing.invoice.InvoiceTagHandler;
import org.killbill.billing.invoice.notification.NextBillingDateNotifier;
import org.killbill.billing.platform.api.LifecycleHandlerType;
import org.killbill.billing.platform.api.LifecycleHandlerType.LifecycleLevel;
import org.killbill.notificationq.api.NotificationQueueService.NoSuchNotificationQueue;
import org.killbill.notificationq.api.NotificationQueueService.NotificationQueueAlreadyExists;

import com.google.inject.Inject;

public class DefaultInvoiceService implements InvoiceService {

    public static final String INVOICE_SERVICE_NAME = "invoice-service";
    private final NextBillingDateNotifier dateNotifier;
    private final InvoiceListener invoiceListener;
    private final InvoiceTagHandler tagHandler;
    private final PersistentBus eventBus;
    private final ParentInvoiceCommitmentNotifier parentInvoiceNotifier;

    @Inject
    public DefaultInvoiceService(final InvoiceListener invoiceListener, final InvoiceTagHandler tagHandler, final PersistentBus eventBus,
                                 final NextBillingDateNotifier dateNotifier, final ParentInvoiceCommitmentNotifier parentInvoiceNotifier) {
        this.invoiceListener = invoiceListener;
        this.tagHandler = tagHandler;
        this.eventBus = eventBus;
        this.dateNotifier = dateNotifier;
        this.parentInvoiceNotifier = parentInvoiceNotifier;
    }

    @Override
    public String getName() {
        return INVOICE_SERVICE_NAME;
    }

    @LifecycleHandlerType(LifecycleHandlerType.LifecycleLevel.INIT_SERVICE)
    public void initialize() throws NotificationQueueAlreadyExists {
        try {
            eventBus.register(invoiceListener);
            eventBus.register(tagHandler);
        } catch (PersistentBus.EventBusException e) {
            throw new RuntimeException("Failed to register bus handlers", e);
        }
        dateNotifier.initialize();
        parentInvoiceNotifier.initialize();
    }

    @LifecycleHandlerType(LifecycleLevel.START_SERVICE)
    public void start() {
        dateNotifier.start();
        parentInvoiceNotifier.start();
    }

    @LifecycleHandlerType(LifecycleLevel.STOP_SERVICE)
    public void stop() throws NoSuchNotificationQueue {
        try {
            eventBus.unregister(invoiceListener);
            eventBus.unregister(tagHandler);
        } catch (PersistentBus.EventBusException e) {
            throw new RuntimeException("Failed to unregister bus handlers", e);
        }
        dateNotifier.stop();
        parentInvoiceNotifier.stop();
    }
}
