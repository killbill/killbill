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

package com.ning.billing.payment;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.invoice.api.InvoiceCreationEvent;
import com.ning.billing.payment.api.Either;
import com.ning.billing.payment.api.PaymentApi;
import com.ning.billing.payment.api.PaymentErrorEvent;
import com.ning.billing.payment.api.PaymentInfoEvent;
import com.ning.billing.payment.provider.PaymentProviderPluginRegistry;
import com.ning.billing.util.bus.Bus;
import com.ning.billing.util.bus.Bus.EventBusException;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.DefaultCallContext;
import com.ning.billing.util.callcontext.UserType;
import com.ning.billing.util.clock.Clock;

public class RequestProcessor {
    public static final String PAYMENT_PROVIDER_KEY = "paymentProvider";
    private final AccountUserApi accountUserApi;
    private final PaymentApi paymentApi;
    private final Bus eventBus;
    private final Clock clock;

    private static final Logger log = LoggerFactory.getLogger(RequestProcessor.class);

    @Inject
    public RequestProcessor(Clock clock,
                            AccountUserApi accountUserApi,
                            PaymentApi paymentApi,
                            PaymentProviderPluginRegistry pluginRegistry,
                            Bus eventBus) {
        this.clock = clock;
        this.accountUserApi = accountUserApi;
        this.paymentApi = paymentApi;
        this.eventBus = eventBus;
    }

    @Subscribe
    public void receiveInvoice(InvoiceCreationEvent event) {
        log.info("Received invoice creation notification for account {} and invoice {}", event.getAccountId(), event.getInvoiceId());
        try {
            final Account account = accountUserApi.getAccountById(event.getAccountId());

            if (account == null) {
                log.info("could not process invoice payment: could not find a valid account for event {}", event);
            }
            else {
                CallContext context = new DefaultCallContext("PaymentRequestProcessor", CallOrigin.INTERNAL, UserType.SYSTEM, clock);
                List<Either<PaymentErrorEvent, PaymentInfoEvent>> results = paymentApi.createPayment(account, Arrays.asList(event.getInvoiceId().toString()), context);
                if (!results.isEmpty()) {
                    Either<PaymentErrorEvent, PaymentInfoEvent> result = results.get(0);
                    try {
                        eventBus.post(result.isLeft() ? result.getLeft() : result.getRight());
                    } catch (EventBusException e) {
                        log.error("Failed to post Payment event event for account {} ", account.getId(), e);
                    }
                }
            }
        } catch(AccountApiException e) {
            log.warn("could not process invoice payment", e);
        }
    }
}
