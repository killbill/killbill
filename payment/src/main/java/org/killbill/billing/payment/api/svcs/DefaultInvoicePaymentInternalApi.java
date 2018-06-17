/*
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

package org.killbill.billing.payment.api.svcs;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.InvoiceInternalApi;
import org.killbill.billing.invoice.api.InvoicePayment;
import org.killbill.billing.payment.api.InvoicePaymentInternalApi;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PaymentOptions;
import org.killbill.billing.payment.api.PaymentTransaction;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.core.PluginControlPaymentProcessor;
import org.killbill.billing.util.UUIDs;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.inject.Inject;

import static org.killbill.billing.payment.logging.PaymentLoggingHelper.logEnterAPICall;
import static org.killbill.billing.payment.logging.PaymentLoggingHelper.logExitAPICall;

public class DefaultInvoicePaymentInternalApi implements InvoicePaymentInternalApi {

    private static final Logger log = LoggerFactory.getLogger(DefaultInvoicePaymentInternalApi.class);

    private final InvoiceInternalApi invoiceInternalApi;
    private final PluginControlPaymentProcessor pluginControlPaymentProcessor;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public DefaultInvoicePaymentInternalApi(final InvoiceInternalApi invoiceInternalApi,
                                            final PluginControlPaymentProcessor pluginControlPaymentProcessor,
                                            final InternalCallContextFactory internalCallContextFactory) {
        this.invoiceInternalApi = invoiceInternalApi;
        this.pluginControlPaymentProcessor = pluginControlPaymentProcessor;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Override
    public InvoicePayment createPurchaseForInvoice(final boolean isApiPayment,
                                                   final Account account,
                                                   final UUID invoiceId,
                                                   final UUID paymentMethodId,
                                                   final UUID paymentId,
                                                   final BigDecimal amount,
                                                   final Currency currency,
                                                   final DateTime effectiveDate,
                                                   final String paymentExternalKey,
                                                   final String originalPaymentTransactionExternalKey,
                                                   final Iterable<PluginProperty> originalProperties,
                                                   final PaymentOptions paymentOptions,
                                                   final InternalCallContext internalCallContext) throws PaymentApiException {
        final Collection<PluginProperty> pluginProperties = new LinkedList<PluginProperty>();
        if (originalProperties != null) {
            for (final PluginProperty pluginProperty : originalProperties) {
                pluginProperties.add(pluginProperty);
            }
        }
        pluginProperties.add(new PluginProperty("IPCD_INVOICE_ID", invoiceId.toString(), false));

        final String paymentTransactionExternalKey = MoreObjects.firstNonNull(originalPaymentTransactionExternalKey, UUIDs.randomUUID().toString());
        final CallContext callContext = internalCallContextFactory.createCallContext(internalCallContext);

        final List<String> paymentControlPluginNames = InvoicePaymentPaymentOptions.create(paymentOptions).getPaymentControlPluginNames();

        final String transactionType = TransactionType.PURCHASE.name();
        Payment payment = null;
        PaymentTransaction paymentTransaction = null;
        PaymentApiException exception = null;
        try {
            logEnterAPICall(log, transactionType, account, paymentMethodId, paymentId, null, amount, currency, paymentExternalKey, paymentTransactionExternalKey, null, paymentControlPluginNames);

            payment = pluginControlPaymentProcessor.createPurchase(isApiPayment,
                                                                   account,
                                                                   paymentMethodId,
                                                                   paymentId,
                                                                   amount,
                                                                   currency,
                                                                   effectiveDate,
                                                                   paymentExternalKey,
                                                                   paymentTransactionExternalKey,
                                                                   pluginProperties,
                                                                   paymentControlPluginNames,
                                                                   callContext,
                                                                   internalCallContext);

            paymentTransaction = payment.getTransactions().get(payment.getTransactions().size() - 1);
        } catch (final PaymentApiException e) {
            exception = e;
            throw e;
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
                           exception);
        }

        return getInvoicePayment(payment.getId(), paymentTransactionExternalKey, callContext);
    }

    private InvoicePayment getInvoicePayment(final UUID paymentId, final String paymentTransactionExternalKey, final TenantContext context) {
        for (final InvoicePayment invoicePayment : invoiceInternalApi.getInvoicePayments(paymentId, context)) {
            if (invoicePayment.getPaymentCookieId().compareTo(paymentTransactionExternalKey) == 0) {
                return invoicePayment;
            }
        }
        return null;
    }
}
