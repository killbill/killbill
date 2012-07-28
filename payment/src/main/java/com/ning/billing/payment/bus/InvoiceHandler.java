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

import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;
import com.ning.billing.ErrorCode;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.invoice.api.InvoiceCreationEvent;
import com.ning.billing.payment.api.PaymentApiException;
import com.ning.billing.payment.core.PaymentProcessor;
import com.ning.billing.util.api.TagUserApi;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.DefaultCallContext;
import com.ning.billing.util.callcontext.UserType;
import com.ning.billing.util.clock.Clock;


public class InvoiceHandler {

    public static final String PAYMENT_PROVIDER_KEY = "paymentProvider";

    private final PaymentProcessor paymentProcessor;
    private final AccountUserApi accountUserApi;
    private final Clock clock;


    private static final Logger log = LoggerFactory.getLogger(InvoiceHandler.class);

    @Inject
    public InvoiceHandler(final Clock clock,
                          final AccountUserApi accountUserApi,
                          final PaymentProcessor paymentProcessor,
                          final TagUserApi tagUserApi) {
        this.clock = clock;
        this.accountUserApi = accountUserApi;
        this.paymentProcessor = paymentProcessor;
    }


    @Subscribe
    public void processInvoiceEvent(final InvoiceCreationEvent event) {

        log.info("Received invoice creation notification for account {} and invoice {}",
                 event.getAccountId(), event.getInvoiceId());

        Account account = null;
        try {

            final CallContext context = new DefaultCallContext("PaymentRequestProcessor", CallOrigin.INTERNAL, UserType.SYSTEM, event.getUserToken(), clock);
            account = accountUserApi.getAccountById(event.getAccountId());
            paymentProcessor.createPayment(account, event.getInvoiceId(), null, context, false, false);
        } catch (AccountApiException e) {
            log.error("Failed to process invoice payment", e);
        } catch (PaymentApiException e) {
            if (e.getCode() != ErrorCode.PAYMENT_NULL_INVOICE.getCode()) {
                log.error("Failed to process invoice payment", e);
            }
        }
    }
}


