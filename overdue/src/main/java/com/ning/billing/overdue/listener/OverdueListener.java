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

import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;
import com.ning.billing.payment.api.PaymentApi;
import com.ning.billing.payment.api.PaymentApiException;
import com.ning.billing.payment.api.PaymentAttempt;
import com.ning.billing.payment.api.PaymentErrorEvent;
import com.ning.billing.payment.api.PaymentInfoEvent;

public class OverdueListener {
    OverdueDispatcher dispatcher;

    private final static Logger log = LoggerFactory.getLogger(OverdueListener.class);
    private final PaymentApi paymentApi;

    @Inject
    public OverdueListener(OverdueDispatcher dispatcher, PaymentApi paymentApi) {
        this.dispatcher = dispatcher;
        this.paymentApi = paymentApi;
    }

    @Subscribe
    public void handlePaymentInfoEvent(final PaymentInfoEvent event) {
        log.info(String.format("Received PaymentInfo event %s", event.toString()));
        try {
            UUID paymentId = event.getId();
            PaymentAttempt attempt = paymentApi.getPaymentAttemptForPaymentId(paymentId);
            UUID accountId = attempt.getAccountId();
            dispatcher.processOverdueForAccount(accountId);
        } catch (PaymentApiException e) {
            log.error("Payment exception encountered when trying process Overdue against payement: " + event.getId(), e);
        }
    }
 
    @Subscribe
    public void handlePaymentErrorEvent(final PaymentErrorEvent event) {
        log.info(String.format("Received PaymentError event %s", event.toString()));
        UUID accountId = event.getAccountId();
        dispatcher.processOverdueForAccount(accountId);
    }

    public void handleNextOverdueCheck(UUID overdueableId) { 
        log.info(String.format("Received OD checkup notification for %s", overdueableId));
        dispatcher.processOverdue(overdueableId);
    }


}
