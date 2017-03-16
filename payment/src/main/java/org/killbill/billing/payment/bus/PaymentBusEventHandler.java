/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.events.InvoiceCreationInternalEvent;
import org.killbill.billing.events.PaymentInternalEvent;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PaymentTransaction;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.core.PluginControlPaymentProcessor;
import org.killbill.billing.payment.core.janitor.Janitor;
import org.killbill.billing.payment.invoice.InvoicePaymentControlPluginApi;
import org.killbill.billing.util.UUIDs;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.CallOrigin;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.UserType;
import org.killbill.billing.util.config.definition.PaymentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.eventbus.AllowConcurrentEvents;
import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;

import static org.killbill.billing.payment.logging.PaymentLoggingHelper.logEnterAPICall;
import static org.killbill.billing.payment.logging.PaymentLoggingHelper.logExitAPICall;

public class PaymentBusEventHandler {

    private static final Logger log = LoggerFactory.getLogger(PaymentBusEventHandler.class);

    private final AccountInternalApi accountApi;
    private final InternalCallContextFactory internalCallContextFactory;
    private final PluginControlPaymentProcessor pluginControlPaymentProcessor;
    private final PaymentConfig paymentConfig;
    private final Janitor janitor;

    @Inject
    public PaymentBusEventHandler(final PaymentConfig paymentConfig,
                                  final AccountInternalApi accountApi,
                                  final PluginControlPaymentProcessor pluginControlPaymentProcessor,
                                  final Janitor janitor,
                                  final InternalCallContextFactory internalCallContextFactory) {
        this.paymentConfig = paymentConfig;
        this.accountApi = accountApi;
        this.janitor = janitor;
        this.internalCallContextFactory = internalCallContextFactory;
        this.pluginControlPaymentProcessor = pluginControlPaymentProcessor;
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

        final Collection<PluginProperty> properties = new ArrayList<PluginProperty>();
        final PluginProperty propertyInvoiceId = new PluginProperty(InvoicePaymentControlPluginApi.PROP_IPCD_INVOICE_ID, event.getInvoiceId().toString(), false);
        properties.add(propertyInvoiceId);

        final InternalCallContext internalContext = internalCallContextFactory.createInternalCallContext(event.getSearchKey2(), event.getSearchKey1(), "PaymentRequestProcessor", CallOrigin.INTERNAL, UserType.SYSTEM, event.getUserToken());
        final CallContext callContext = internalCallContextFactory.createCallContext(internalContext);

        final BigDecimal amountToBePaid = null; // We let the plugin compute how much should be paid
        final List<String> paymentControlPluginNames = paymentConfig.getPaymentControlPluginNames(internalContext) != null ? new LinkedList<String>(paymentConfig.getPaymentControlPluginNames(internalContext)) : new LinkedList<String>();
        paymentControlPluginNames.add(InvoicePaymentControlPluginApi.PLUGIN_NAME);

        final String transactionType = TransactionType.PURCHASE.name();
        Account account = null;
        Payment payment = null;
        PaymentTransaction paymentTransaction = null;
        try {
            account = accountApi.getAccountById(event.getAccountId(), internalContext);

            logEnterAPICall(log,
                            transactionType,
                            account,
                            account.getPaymentMethodId(),
                            null,
                            null,
                            amountToBePaid,
                            account.getCurrency(),
                            null,
                            null,
                            null,
                            paymentControlPluginNames);

            payment = pluginControlPaymentProcessor.createPurchase(false, account, account.getPaymentMethodId(), null, amountToBePaid, account.getCurrency(), null, null, properties, paymentControlPluginNames, callContext, internalContext);

            paymentTransaction = payment.getTransactions().get(payment.getTransactions().size() - 1);
        } catch (final AccountApiException e) {
            log.warn("Failed to process invoice payment", e);
        } catch (final PaymentApiException e) {
            // Log as warn unless nothing left to be paid
            if (e.getCode() != ErrorCode.PAYMENT_PLUGIN_API_ABORTED.getCode()) {
                log.warn("Failed to process invoice payment {}", e.toString());
            }
        } finally {
            logExitAPICall(log,
                           transactionType,
                           account,
                           payment != null ? payment.getPaymentMethodId() : null,
                           payment != null ? payment.getId() : null,
                           paymentTransaction != null ? paymentTransaction.getId() : null,
                           paymentTransaction != null ? paymentTransaction.getProcessedAmount() : null,
                           paymentTransaction != null ? paymentTransaction.getProcessedCurrency() : null,
                           payment != null ? payment.getExternalKey() : null,
                           paymentTransaction != null ? paymentTransaction.getExternalKey() : null,
                           paymentTransaction != null ? paymentTransaction.getTransactionStatus() : null,
                           paymentControlPluginNames,
                           null);
        }
    }
}


