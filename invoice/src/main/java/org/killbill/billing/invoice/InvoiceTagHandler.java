/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

package org.killbill.billing.invoice;

import java.util.UUID;

import org.killbill.billing.ObjectType;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.events.ControlTagDeletionInternalEvent;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.platform.api.KillbillService;
import org.killbill.billing.platform.api.LifecycleHandlerType;
import org.killbill.billing.platform.api.LifecycleHandlerType.LifecycleLevel;
import org.killbill.billing.util.callcontext.CallOrigin;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.UserType;
import org.killbill.billing.util.tag.ControlTagType;
import org.killbill.clock.Clock;
import org.killbill.notificationq.api.NotificationQueueService;
import org.killbill.notificationq.api.NotificationQueueService.NoSuchNotificationQueue;
import org.killbill.queue.retry.RetryableService;
import org.killbill.queue.retry.RetryableSubscriber;
import org.killbill.queue.retry.RetryableSubscriber.SubscriberAction;
import org.killbill.queue.retry.RetryableSubscriber.SubscriberQueueHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.AllowConcurrentEvents;
import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;

@SuppressWarnings("TypeMayBeWeakened")
public class InvoiceTagHandler extends RetryableService implements KillbillService {

    private static final String INVOICE_TAG_HANDLER_SERVICE_NAME = "invoice-tag-handler-service";

    private static final Logger log = LoggerFactory.getLogger(InvoiceTagHandler.class);

    private final InvoiceDispatcher dispatcher;
    private final RetryableSubscriber retryableSubscriber;

    private final SubscriberQueueHandler subscriberQueueHandler = new SubscriberQueueHandler();

    @Inject
    public InvoiceTagHandler(final Clock clock,
                             final InvoiceDispatcher dispatcher,
                             final NotificationQueueService notificationQueueService,
                             final InternalCallContextFactory internalCallContextFactory) {
        super(notificationQueueService);
        this.dispatcher = dispatcher;

        final SubscriberAction<ControlTagDeletionInternalEvent> action = new SubscriberAction<ControlTagDeletionInternalEvent>() {
            @Override
            public void run(final ControlTagDeletionInternalEvent event) {
                if (event.getTagDefinition().getName().equals(ControlTagType.AUTO_INVOICING_OFF.toString()) && event.getObjectType() == ObjectType.ACCOUNT) {
                    final UUID accountId = event.getObjectId();
                    final InternalCallContext context = internalCallContextFactory.createInternalCallContext(event.getSearchKey2(), event.getSearchKey1(), "InvoiceTagHandler", CallOrigin.INTERNAL, UserType.SYSTEM, event.getUserToken());
                    processUnpaid_AUTO_INVOICING_OFF_invoices(accountId, context);
                }
            }
        };
        subscriberQueueHandler.subscribe(ControlTagDeletionInternalEvent.class, action);
        this.retryableSubscriber = new RetryableSubscriber(clock, this, subscriberQueueHandler);
    }

    @Override
    public String getName() {
        return INVOICE_TAG_HANDLER_SERVICE_NAME;
    }

    @LifecycleHandlerType(LifecycleLevel.INIT_SERVICE)
    public void initialize() {
        super.initialize("invoice-tag-handler", subscriberQueueHandler);
    }

    @AllowConcurrentEvents
    @Subscribe
    public void process_AUTO_INVOICING_OFF_removal(final ControlTagDeletionInternalEvent event) {
        retryableSubscriber.handleEvent(event);
    }

    @LifecycleHandlerType(LifecycleLevel.START_SERVICE)
    public void start() {
        super.start();
    }

    @LifecycleHandlerType(LifecycleLevel.STOP_SERVICE)
    public void stop() throws NoSuchNotificationQueue {
        super.stop();
    }

    private void processUnpaid_AUTO_INVOICING_OFF_invoices(final UUID accountId, final InternalCallContext context) {
        try {
            dispatcher.processAccountFromNotificationOrBusEvent(accountId, null, null, false, context);
        } catch (final InvoiceApiException e) {
            log.warn("Failed to process tag removal AUTO_INVOICING_OFF for accountId='{}'", accountId, e);
        }
    }
}
