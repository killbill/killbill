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

package org.killbill.billing.payment.bus;

import java.math.BigDecimal;
import java.util.LinkedList;
import java.util.List;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.events.InvoiceCreationInternalEvent;
import org.killbill.billing.events.PaymentInternalEvent;
import org.killbill.billing.payment.api.InvoicePaymentInternalApi;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PaymentOptions;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.core.janitor.Janitor;
import org.killbill.billing.util.callcontext.CallOrigin;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.UserType;
import org.killbill.billing.util.config.definition.PaymentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.eventbus.AllowConcurrentEvents;
import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;

public class PaymentBusEventHandler {

    private static final Logger log = LoggerFactory.getLogger(PaymentBusEventHandler.class);

    private final AccountInternalApi accountApi;
    private final InvoicePaymentInternalApi invoicePaymentInternalApi;
    private final InternalCallContextFactory internalCallContextFactory;
    private final PaymentConfig paymentConfig;
    private final Janitor janitor;

    @Inject
    public PaymentBusEventHandler(final PaymentConfig paymentConfig,
                                  final AccountInternalApi accountApi,
                                  final InvoicePaymentInternalApi invoicePaymentInternalApi,
                                  final Janitor janitor,
                                  final InternalCallContextFactory internalCallContextFactory) {
        this.paymentConfig = paymentConfig;
        this.accountApi = accountApi;
        this.invoicePaymentInternalApi = invoicePaymentInternalApi;
        this.janitor = janitor;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @AllowConcurrentEvents
    @Subscribe
    public void processPaymentEvent(final PaymentInternalEvent event) {
        janitor.processPaymentEvent(event);
    }

    @AllowConcurrentEvents
    @Subscribe
    public void processInvoiceEvent(final InvoiceCreationInternalEvent event) {
        log.info("Received invoice creation notification for accountId='{}', invoiceId='{}'", event.getAccountId(), event.getInvoiceId());

        final InternalCallContext internalContext = internalCallContextFactory.createInternalCallContext(event.getSearchKey2(), event.getSearchKey1(), "PaymentRequestProcessor", CallOrigin.INTERNAL, UserType.SYSTEM, event.getUserToken());

        final BigDecimal amountToBePaid = null; // We let the plugin compute how much should be paid
        final List<String> paymentControlPluginNames = paymentConfig.getPaymentControlPluginNames(internalContext) != null ? new LinkedList<String>(paymentConfig.getPaymentControlPluginNames(internalContext)) : new LinkedList<String>();

        final Account account;
        try {
            account = accountApi.getAccountById(event.getAccountId(), internalContext);

            invoicePaymentInternalApi.createPurchaseForInvoicePayment(false,
                                                                      account,
                                                                      event.getInvoiceId(),
                                                                      account.getPaymentMethodId(),
                                                                      null,
                                                                      amountToBePaid,
                                                                      account.getCurrency(),
                                                                      null,
                                                                      null,
                                                                      null,
                                                                      ImmutableList.<PluginProperty>of(),
                                                                      new PaymentOptions() {
                                                                   @Override
                                                                   public boolean isExternalPayment() {
                                                                       return false;
                                                                   }

                                                                   @Override
                                                                   public List<String> getPaymentControlPluginNames() {
                                                                       return paymentControlPluginNames;
                                                                   }
                                                               },
                                                                      internalContext);
        } catch (final AccountApiException e) {
            log.warn("Failed to process invoice payment", e);
        } catch (final PaymentApiException e) {
            // Log as warn unless nothing left to be paid
            if (e.getCode() != ErrorCode.PAYMENT_PLUGIN_API_ABORTED.getCode()) {
                log.warn("Failed to process invoice payment {}", e.toString());
            }
        }
    }
}


