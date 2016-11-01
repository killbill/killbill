/*
 * Copyright 2016 Groupon, Inc
 * Copyright 2016 The Billing Project, LLC
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

package org.killbill.billing.payment.logging;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.TransactionStatus;
import org.slf4j.Logger;

import com.google.common.base.Joiner;

public abstract class PaymentLoggingHelper {

    private static final Joiner JOINER = Joiner.on(",");

    public static void logEnterAPICall(final Logger log,
                                       final String transactionType,
                                       final Account account,
                                       @Nullable final UUID paymentMethodId,
                                       @Nullable final UUID paymentId,
                                       @Nullable final UUID transactionId,
                                       @Nullable final BigDecimal amount,
                                       @Nullable final Currency currency,
                                       @Nullable final String paymentExternalKey,
                                       @Nullable final String paymentTransactionExternalKey,
                                       @Nullable final TransactionStatus transactionStatus,
                                       @Nullable final List<String> paymentControlPluginNames) {
        logAPICallInternal(log,
                           "ENTERING ",
                           transactionType,
                           account,
                           paymentMethodId,
                           paymentId,
                           transactionId,
                           amount,
                           currency,
                           paymentExternalKey,
                           paymentTransactionExternalKey,
                           transactionStatus,
                           paymentControlPluginNames,
                           null);
    }

    public static void logExitAPICall(final Logger log,
                                      final String transactionType,
                                      final Account account,
                                      @Nullable final UUID paymentMethodId,
                                      @Nullable final UUID paymentId,
                                      @Nullable final UUID transactionId,
                                      @Nullable final BigDecimal amount,
                                      @Nullable final Currency currency,
                                      @Nullable final String paymentExternalKey,
                                      @Nullable final String paymentTransactionExternalKey,
                                      @Nullable final TransactionStatus transactionStatus,
                                      @Nullable final List<String> paymentControlPluginNames,
                                      @Nullable final PaymentApiException exception) {
        logAPICallInternal(log,
                           "EXITING ",
                           transactionType,
                           account,
                           paymentMethodId,
                           paymentId,
                           transactionId,
                           amount,
                           currency,
                           paymentExternalKey,
                           paymentTransactionExternalKey,
                           transactionStatus,
                           paymentControlPluginNames,
                           exception);
    }

    public static void logAPICallInternal(final Logger log,
                                          final String prefixMsg,
                                          final String transactionType,
                                          final Account account,
                                          final UUID paymentMethodId,
                                          @Nullable final UUID paymentId,
                                          @Nullable final UUID transactionId,
                                          @Nullable final BigDecimal amount,
                                          @Nullable final Currency currency,
                                          @Nullable final String paymentExternalKey,
                                          @Nullable final String paymentTransactionExternalKey,
                                          @Nullable final TransactionStatus transactionStatus,
                                          @Nullable final List<String> paymentControlPluginNames,
                                          @Nullable final PaymentApiException exception) {
        if (log.isInfoEnabled()) {
            final StringBuilder logLine = new StringBuilder(prefixMsg);
            logLine.append("PaymentApi: transactionType='")
                   .append(transactionType)
                   .append("', accountId='")
                   .append(account.getId())
                   .append("'");
            if (paymentMethodId != null) {
                logLine.append(", paymentMethodId='")
                       .append(paymentMethodId)
                       .append("'");
            }
            if (paymentExternalKey != null) {
                logLine.append(", paymentExternalKey='")
                       .append(paymentExternalKey)
                       .append("'");
            }
            if (paymentTransactionExternalKey != null) {
                logLine.append(", paymentTransactionExternalKey='")
                       .append(paymentTransactionExternalKey)
                       .append("'");
            }
            if (paymentId != null) {
                logLine.append(", paymentId='")
                       .append(paymentId)
                       .append("'");
            }
            if (transactionId != null) {
                logLine.append(", transactionId='")
                       .append(transactionId)
                       .append("'");
            }
            if (amount != null) {
                logLine.append(", amount='")
                       .append(amount)
                       .append("'");
            }
            if (currency != null) {
                logLine.append(", currency='")
                       .append(currency)
                       .append("'");
            }
            if (transactionStatus != null) {
                logLine.append(", transactionStatus='")
                       .append(transactionStatus)
                       .append("'");
            }
            if (paymentControlPluginNames != null) {
                logLine.append(", paymentControlPluginNames='")
                       .append(JOINER.join(paymentControlPluginNames))
                       .append("'");
            }
            if (exception != null) {
                final ErrorCode error = ErrorCode.fromCode(exception.getCode());
                if (error == ErrorCode.PAYMENT_PLUGIN_API_ABORTED) {
                    logLine.append(", aborted=true");
                }
                logLine.append(", error='")
                       .append(error)
                       .append("', exception='")
                       .append(exception.getMessage())
                       .append("'");
            }
            log.info(logLine.toString());
        }
    }
}
