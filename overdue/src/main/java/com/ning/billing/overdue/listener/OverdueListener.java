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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.invoice.api.InvoiceAdjustmentEvent;
import com.ning.billing.ovedue.notification.OverdueCheckNotificationKey;
import com.ning.billing.payment.api.PaymentErrorEvent;
import com.ning.billing.payment.api.PaymentInfoEvent;

import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;

public class OverdueListener {

    private final OverdueDispatcher dispatcher;

    private static final Logger log = LoggerFactory.getLogger(OverdueListener.class);

    @Inject
    public OverdueListener(final OverdueDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Subscribe
    public void handlePaymentInfoEvent(final PaymentInfoEvent event) {
        log.info(String.format("Received PaymentInfo event %s", event.toString()));
        dispatcher.processOverdueForAccount(event.getAccountId());
    }

    @Subscribe
    public void handlePaymentErrorEvent(final PaymentErrorEvent event) {
        log.info(String.format("Received PaymentError event %s", event.toString()));
        final UUID accountId = event.getAccountId();
        dispatcher.processOverdueForAccount(accountId);
    }

    @Subscribe
    public void handleInvoiceAdjustmentEvent(final InvoiceAdjustmentEvent event) {
        log.info(String.format("Received InvoiceAdjustment event %s", event.toString()));
        final UUID accountId = event.getAccountId();
        dispatcher.processOverdueForAccount(accountId);
    }

    public void handleNextOverdueCheck(final OverdueCheckNotificationKey notificationKey) {
        log.info(String.format("Received OD checkup notification for type = %s, id = %s",
                notificationKey.getType(), notificationKey.getUuidKey()));
        dispatcher.processOverdue(notificationKey.getType(), notificationKey.getUuidKey());
    }
}
