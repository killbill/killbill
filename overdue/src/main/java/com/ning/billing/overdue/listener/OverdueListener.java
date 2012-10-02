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

package com.ning.billing.overdue.listener;

import java.util.UUID;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.invoice.api.InvoiceAdjustmentEvent;
import com.ning.billing.ovedue.notification.OverdueCheckNotificationKey;
import com.ning.billing.payment.api.PaymentErrorEvent;
import com.ning.billing.payment.api.PaymentInfoEvent;
import com.ning.billing.util.bus.BusEvent;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.callcontext.UserType;

import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;

public class OverdueListener {

    private final OverdueDispatcher dispatcher;
    private final InternalCallContextFactory internalCallContextFactory;

    private static final Logger log = LoggerFactory.getLogger(OverdueListener.class);

    @Inject
    public OverdueListener(final OverdueDispatcher dispatcher,
                           final InternalCallContextFactory internalCallContextFactory) {
        this.dispatcher = dispatcher;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Subscribe
    public void handlePaymentInfoEvent(final PaymentInfoEvent event) {
        log.info(String.format("Received PaymentInfo event %s", event.toString()));
        dispatcher.processOverdueForAccount(event.getAccountId(), createCallContext(event));
    }

    @Subscribe
    public void handlePaymentErrorEvent(final PaymentErrorEvent event) {
        log.info(String.format("Received PaymentError event %s", event.toString()));
        final UUID accountId = event.getAccountId();
        dispatcher.processOverdueForAccount(accountId, createCallContext(event));
    }

    @Subscribe
    public void handleInvoiceAdjustmentEvent(final InvoiceAdjustmentEvent event) {
        log.info(String.format("Received InvoiceAdjustment event %s", event.toString()));
        final UUID accountId = event.getAccountId();
        dispatcher.processOverdueForAccount(accountId, createCallContext(event));
    }

    public void handleNextOverdueCheck(final OverdueCheckNotificationKey notificationKey) {
        log.info(String.format("Received OD checkup notification for type = %s, id = %s",
                notificationKey.getType(), notificationKey.getUuidKey()));
        dispatcher.processOverdue(notificationKey.getType(), notificationKey.getUuidKey(), createCallContext(null));
    }

    private InternalCallContext createCallContext(@Nullable final BusEvent event) {
        return internalCallContextFactory.createInternalCallContext("OverdueService", CallOrigin.INTERNAL, UserType.SYSTEM,
                                                                    event == null ? null : event.getUserToken());
    }
}
