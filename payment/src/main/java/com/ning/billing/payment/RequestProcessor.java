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
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.invoice.api.InvoiceCreationEvent;
import com.ning.billing.payment.api.DefaultPaymentErrorEvent;
import com.ning.billing.payment.api.Either;
import com.ning.billing.payment.api.PaymentApi;
import com.ning.billing.payment.api.PaymentApiException;
import com.ning.billing.payment.api.PaymentErrorEvent;
import com.ning.billing.payment.api.PaymentInfoEvent;
import com.ning.billing.payment.provider.PaymentProviderPluginRegistry;
import com.ning.billing.util.bus.Bus;
import com.ning.billing.util.bus.Bus.EventBusException;
import com.ning.billing.util.bus.BusEvent.BusEventType;
import com.ning.billing.util.bus.BusEvent;
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
    
    private void postPaymentEvent(BusEvent ev, UUID accountId) {
        if (ev == null) {
            return;
        }
        try {
            eventBus.post(ev);
        } catch (EventBusException e) {
            log.error("Failed to post Payment event event for account {} ", accountId, e);
        }
    }

    @Subscribe
    public void receiveInvoice(InvoiceCreationEvent event) {


        log.info("Received invoice creation notification for account {} and invoice {}", event.getAccountId(), event.getInvoiceId());
        PaymentErrorEvent errorEvent = null;
        try {
            final Account account = accountUserApi.getAccountById(event.getAccountId());
            if (account != null) {
                CallContext context = new DefaultCallContext("PaymentRequestProcessor", CallOrigin.INTERNAL, UserType.SYSTEM, clock);
                List<PaymentInfoEvent> results = paymentApi.createPayment(account, Arrays.asList(event.getInvoiceId().toString()), context);
                PaymentInfoEvent infoEvent = (!results.isEmpty()) ?  results.get(0) : null;
                postPaymentEvent(infoEvent, account.getId());
                return;
            } else {
                errorEvent = new DefaultPaymentErrorEvent(null, "Failed to retrieve account", event.getAccountId(), null, null);
            }
        } catch(AccountApiException e) {
            log.error("Failed to process invoice payment", e);
            errorEvent = new DefaultPaymentErrorEvent(null, e.getMessage(), event.getAccountId(), null, null);            
        } catch (PaymentApiException e) {
            log.error("Failed to process invoice payment", e);
            errorEvent = new DefaultPaymentErrorEvent(null, e.getMessage(), event.getAccountId(), null, null);                        
        }
        postPaymentEvent(errorEvent, event.getAccountId());
    }
}
