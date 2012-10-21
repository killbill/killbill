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

package com.ning.billing.payment.bus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.ErrorCode;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.payment.api.PaymentApiException;
import com.ning.billing.payment.core.PaymentProcessor;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.DefaultCallContext;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.callcontext.UserType;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.events.InvoiceCreationInternalEvent;
import com.ning.billing.util.svcapi.account.AccountInternalApi;

import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;

public class InvoiceHandler {

    private final PaymentProcessor paymentProcessor;
    private final AccountInternalApi accountApi;
    private final Clock clock;
    private final InternalCallContextFactory internalCallContextFactory;

    private static final Logger log = LoggerFactory.getLogger(InvoiceHandler.class);

    @Inject
    public InvoiceHandler(final Clock clock,
                          final AccountInternalApi accountApi,
                          final PaymentProcessor paymentProcessor,
                          final InternalCallContextFactory internalCallContextFactory) {
        this.clock = clock;
        this.accountApi = accountApi;
        this.paymentProcessor = paymentProcessor;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Subscribe
    public void processInvoiceEvent(final InvoiceCreationInternalEvent event) {

        log.info("Received invoice creation notification for account {} and invoice {}",
                 event.getAccountId(), event.getInvoiceId());

        final Account account;
        try {
            // API_FIX
            final CallContext context = new DefaultCallContext(null, "PaymentRequestProcessor", CallOrigin.INTERNAL, UserType.SYSTEM, event.getUserToken(), clock);
            final InternalTenantContext internalContext = internalCallContextFactory.createInternalCallContext(event.getAccountId(), context);
            account = accountApi.getAccountById(event.getAccountId(), internalContext);
            paymentProcessor.createPayment(account, event.getInvoiceId(), null, internalCallContextFactory.createInternalCallContext(event.getAccountId(), context), false, false);
        } catch (AccountApiException e) {
            log.error("Failed to process invoice payment", e);
        } catch (PaymentApiException e) {
            // Log as error unless:
            if (e.getCode() != ErrorCode.PAYMENT_NULL_INVOICE.getCode() /*  Nothing to left be paid*/ &&
                    e.getCode() != ErrorCode.PAYMENT_CREATE_PAYMENT.getCode() /* User payment error */) {
                log.error("Failed to process invoice payment {}", e.toString());
            }
        }
    }
}


