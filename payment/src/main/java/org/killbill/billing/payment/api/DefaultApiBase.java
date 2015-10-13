/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

package org.killbill.billing.payment.api;

import java.math.BigDecimal;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.payment.invoice.InvoicePaymentControlPluginApi;
import org.killbill.billing.util.config.PaymentConfig;
import org.killbill.commons.request.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

public class DefaultApiBase {

    private static final Logger log = LoggerFactory.getLogger(DefaultApiBase.class);

    private final PaymentConfig paymentConfig;

    public DefaultApiBase(final PaymentConfig paymentConfig) {
        this.paymentConfig = paymentConfig;
    }

    protected void logAPICall(final String transactionType, final Account account, final UUID paymentMethodId, @Nullable final UUID paymentId, @Nullable final UUID transactionId, @Nullable final BigDecimal amount, @Nullable final Currency currency, @Nullable final String paymentExternalKey, @Nullable final String paymentTransactionExternalKey) {
        if (log.isInfoEnabled()) {
            final StringBuilder logLine = new StringBuilder();
            logLine.append("PaymentApi : ")
                   .append(transactionType)
                   .append(", account = ")
                   .append(account.getId());
            if (paymentMethodId != null) {
                logLine.append(", paymentMethodId = ")
                       .append(paymentMethodId);
            }
            if (paymentExternalKey != null) {
                logLine.append(", paymentExternalKey = ")
                       .append(paymentExternalKey);
            }
            if (paymentTransactionExternalKey != null) {
                logLine.append(", paymentTransactionExternalKey = ")
                       .append(paymentTransactionExternalKey);
            }
            if (paymentId != null) {
                logLine.append(", paymentId = ")
                       .append(paymentId);
            }
            if (transactionId != null) {
                logLine.append(", transactionId = ")
                       .append(transactionId);
            }
            if (amount != null) {
                logLine.append(", amount = ")
                       .append(amount);
            }
            if (currency != null) {
                logLine.append(", currency = ")
                       .append(currency);
            }

            final String requestId = Request.getPerThreadRequestData() != null
                                     ? Request.getPerThreadRequestData().getRequestId() : null;
            if (requestId != null) {
                logLine.append(", requestId = ")
                       .append(requestId);
            }
            log.info(logLine.toString());
        }
    }

    protected List<String> toPaymentControlPluginNames(final PaymentOptions paymentOptions) {
        // Special path for JAX-RS InvoicePayment endpoints (see JaxRsResourceBase)
        if (paymentConfig.getPaymentControlPluginNames() != null &&
            paymentOptions.getPaymentControlPluginNames() != null &&
            paymentOptions.getPaymentControlPluginNames().size() == 1 &&
            InvoicePaymentControlPluginApi.PLUGIN_NAME.equals(paymentOptions.getPaymentControlPluginNames().get(0))) {
            final List<String> paymentControlPluginNames = new LinkedList<String>(paymentOptions.getPaymentControlPluginNames());
            paymentControlPluginNames.addAll(paymentConfig.getPaymentControlPluginNames());
            return paymentControlPluginNames;
        } else if (paymentOptions.getPaymentControlPluginNames() != null && !paymentOptions.getPaymentControlPluginNames().isEmpty()) {
            return paymentOptions.getPaymentControlPluginNames();
        } else if (paymentConfig.getPaymentControlPluginNames() != null && !paymentConfig.getPaymentControlPluginNames().isEmpty()) {
            return paymentConfig.getPaymentControlPluginNames();
        } else {
            return ImmutableList.<String>of();
        }
    }

    protected void checkNotNullParameter(final Object parameter, final String parameterName) throws PaymentApiException {
        if (parameter == null) {
            throw new PaymentApiException(ErrorCode.PAYMENT_INVALID_PARAMETER, parameterName, "should not be null");
        }
    }

    protected void checkPositiveAmount(final BigDecimal amount) throws PaymentApiException {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentApiException(ErrorCode.PAYMENT_INVALID_PARAMETER, "amount", "should be greater than 0");
        }
    }

}
