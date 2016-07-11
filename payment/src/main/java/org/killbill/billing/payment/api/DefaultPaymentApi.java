/*
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

package org.killbill.billing.payment.api;

import java.math.BigDecimal;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.payment.core.PaymentMethodProcessor;
import org.killbill.billing.payment.core.PaymentProcessor;
import org.killbill.billing.payment.core.PluginControlPaymentProcessor;
import org.killbill.billing.util.UUIDs;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.config.PaymentConfig;
import org.killbill.billing.util.entity.Pagination;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public class DefaultPaymentApi extends DefaultApiBase implements PaymentApi {

    private static final Joiner JOINER = Joiner.on(",");

    private static final boolean SHOULD_LOCK_ACCOUNT = true;
    private static final boolean IS_API_PAYMENT = true;
    private static final UUID NULL_ATTEMPT_ID = null;

    private static final Logger log = LoggerFactory.getLogger(DefaultPaymentApi.class);

    private final PaymentProcessor paymentProcessor;
    private final PaymentMethodProcessor paymentMethodProcessor;
    private final PluginControlPaymentProcessor pluginControlPaymentProcessor;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public DefaultPaymentApi(final PaymentConfig paymentConfig, final PaymentProcessor paymentProcessor, final PaymentMethodProcessor paymentMethodProcessor, final PluginControlPaymentProcessor pluginControlPaymentProcessor, final InternalCallContextFactory internalCallContextFactory) {
        super(paymentConfig);
        this.paymentProcessor = paymentProcessor;
        this.paymentMethodProcessor = paymentMethodProcessor;
        this.pluginControlPaymentProcessor = pluginControlPaymentProcessor;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Override
    public Payment createAuthorization(final Account account, final UUID paymentMethodId, @Nullable final UUID paymentId, final BigDecimal amount, final Currency currency, @Nullable final String paymentExternalKey, @Nullable final String paymentTransactionExternalKey,
                                       final Iterable<PluginProperty> properties, final CallContext callContext) throws PaymentApiException {
        checkNotNullParameter(account, "account");
        checkNotNullParameter(paymentMethodId, "paymentMethodId");
        if (paymentId == null) {
            checkNotNullParameter(amount, "amount");
            checkNotNullParameter(currency, "currency");
        }
        checkNotNullParameter(properties, "plugin properties");

        final String transactionType = TransactionType.AUTHORIZE.name();
        Payment payment = null;
        PaymentTransaction paymentTransaction = null;
        try {
            logEnterAPICall(transactionType, account, paymentMethodId, paymentId, null, amount, currency, paymentExternalKey, paymentTransactionExternalKey, null, null);

            final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
            payment = paymentProcessor.createAuthorization(IS_API_PAYMENT, NULL_ATTEMPT_ID, account, paymentMethodId, paymentId, amount, currency, paymentExternalKey, paymentTransactionExternalKey,
                                                                         SHOULD_LOCK_ACCOUNT, properties, callContext, internalCallContext);

            paymentTransaction = findPaymentTransaction(payment, paymentTransactionExternalKey);

            return payment;
        } finally {
            logExitAPICall(transactionType,
                           account,
                           payment != null ? payment.getPaymentMethodId() : null,
                           payment != null ? payment.getId() : null,
                           paymentTransaction != null ? paymentTransaction.getId() : null,
                           paymentTransaction != null ? paymentTransaction.getProcessedAmount() : null,
                           paymentTransaction != null ? paymentTransaction.getProcessedCurrency() : null,
                           payment != null ? payment.getExternalKey() : null,
                           paymentTransaction != null ? paymentTransaction.getExternalKey() : null,
                           paymentTransaction != null ? paymentTransaction.getTransactionStatus() : null,
                           null);
        }
    }

    @Override
    public Payment createAuthorizationWithPaymentControl(final Account account, final UUID paymentMethodId, @Nullable final UUID paymentId, final BigDecimal amount, final Currency currency,
                                                         @Nullable final String paymentExternalKey, @Nullable final String paymentTransactionExternalKey,
                                                         final Iterable<PluginProperty> properties, final PaymentOptions paymentOptions, final CallContext callContext) throws PaymentApiException {
        final List<String> paymentControlPluginNames = toPaymentControlPluginNames(paymentOptions);
        if (paymentControlPluginNames.isEmpty()) {
            return createAuthorization(account, paymentMethodId, paymentId, amount, currency, paymentExternalKey, paymentTransactionExternalKey, properties, callContext);
        }

        checkNotNullParameter(account, "account");
        checkNotNullParameter(paymentMethodId, "paymentMethodId");
        if (paymentId == null) {
            checkNotNullParameter(amount, "amount");
            checkNotNullParameter(currency, "currency");
        }
        checkNotNullParameter(properties, "plugin properties");

        final String transactionType = TransactionType.AUTHORIZE.name();

        Payment payment = null;
        PaymentTransaction paymentTransaction = null;
        try {
            logEnterAPICall(transactionType, account, paymentMethodId, paymentId, null, amount, currency, paymentExternalKey, paymentTransactionExternalKey, null, paymentControlPluginNames);

            final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
            payment = pluginControlPaymentProcessor.createAuthorization(IS_API_PAYMENT, account, paymentMethodId, paymentId, amount, currency, paymentExternalKey, paymentTransactionExternalKey,
                                                                                      properties, paymentControlPluginNames, callContext, internalCallContext);

            paymentTransaction = findPaymentTransaction(payment, paymentTransactionExternalKey);

            return payment;
        } finally {
            logExitAPICall(transactionType,
                           account,
                           payment != null ? payment.getPaymentMethodId() : null,
                           payment != null ? payment.getId() : null,
                           paymentTransaction != null ? paymentTransaction.getId() : null,
                           paymentTransaction != null ? paymentTransaction.getProcessedAmount() : null,
                           paymentTransaction != null ? paymentTransaction.getProcessedCurrency() : null,
                           payment != null ? payment.getExternalKey() : null,
                           paymentTransaction != null ? paymentTransaction.getExternalKey() : null,
                           paymentTransaction != null ? paymentTransaction.getTransactionStatus() : null,
                           paymentControlPluginNames);
        }
    }

    @Override
    public Payment createCapture(final Account account, final UUID paymentId, final BigDecimal amount, final Currency currency, @Nullable final String paymentTransactionExternalKey,
                                 final Iterable<PluginProperty> properties, final CallContext callContext) throws PaymentApiException {

        checkNotNullParameter(account, "account");
        checkNotNullParameter(paymentId, "paymentId");
        checkNotNullParameter(amount, "amount");
        checkNotNullParameter(currency, "currency");
        checkNotNullParameter(properties, "plugin properties");

        final String transactionType = TransactionType.CAPTURE.name();
        Payment payment = null;
        PaymentTransaction paymentTransaction = null;
        try {
            logEnterAPICall(transactionType, account, null, paymentId, null, amount, currency, null, paymentTransactionExternalKey, null, null);

            final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
            payment = paymentProcessor.createCapture(IS_API_PAYMENT, NULL_ATTEMPT_ID, account, paymentId, amount, currency, paymentTransactionExternalKey,
                                                                   SHOULD_LOCK_ACCOUNT, properties, callContext, internalCallContext);

            paymentTransaction = findPaymentTransaction(payment, paymentTransactionExternalKey);

            return payment;
        } finally {
            logExitAPICall(transactionType,
                           account,
                           payment != null ? payment.getPaymentMethodId() : null,
                           payment != null ? payment.getId() : null,
                           paymentTransaction != null ? paymentTransaction.getId() : null,
                           paymentTransaction != null ? paymentTransaction.getProcessedAmount() : null,
                           paymentTransaction != null ? paymentTransaction.getProcessedCurrency() : null,
                           payment != null ? payment.getExternalKey() : null,
                           paymentTransaction != null ? paymentTransaction.getExternalKey() : null,
                           paymentTransaction != null ? paymentTransaction.getTransactionStatus() : null,
                           null);
        }
    }

    @Override
    public Payment createCaptureWithPaymentControl(final Account account, final UUID paymentId, final BigDecimal amount, final Currency currency, @Nullable final String paymentTransactionExternalKey,
                                                   final Iterable<PluginProperty> properties, final PaymentOptions paymentOptions, final CallContext callContext) throws PaymentApiException {
        final List<String> paymentControlPluginNames = toPaymentControlPluginNames(paymentOptions);
        if (paymentControlPluginNames.isEmpty()) {
            return createCapture(account, paymentId, amount, currency, paymentTransactionExternalKey, properties, callContext);
        }

        checkNotNullParameter(account, "account");
        checkNotNullParameter(paymentId, "paymentId");
        checkNotNullParameter(amount, "amount");
        checkNotNullParameter(currency, "currency");
        checkNotNullParameter(properties, "plugin properties");

        final String transactionType = TransactionType.CAPTURE.name();
        Payment payment = null;
        PaymentTransaction paymentTransaction = null;
        try {
            logEnterAPICall(transactionType, account, null, paymentId, null, amount, currency, null, paymentTransactionExternalKey, null, paymentControlPluginNames);

            final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
            payment = pluginControlPaymentProcessor.createCapture(IS_API_PAYMENT, account, paymentId, amount, currency, paymentTransactionExternalKey,
                                                                                properties, paymentControlPluginNames, callContext, internalCallContext);

            paymentTransaction = findPaymentTransaction(payment, paymentTransactionExternalKey);

            return payment;
        } finally {
            logExitAPICall(transactionType,
                           account,
                           payment != null ? payment.getPaymentMethodId() : null,
                           payment != null ? payment.getId() : null,
                           paymentTransaction != null ? paymentTransaction.getId() : null,
                           paymentTransaction != null ? paymentTransaction.getProcessedAmount() : null,
                           paymentTransaction != null ? paymentTransaction.getProcessedCurrency() : null,
                           payment != null ? payment.getExternalKey() : null,
                           paymentTransaction != null ? paymentTransaction.getExternalKey() : null,
                           paymentTransaction != null ? paymentTransaction.getTransactionStatus() : null,
                           paymentControlPluginNames);
        }

    }

    @Override
    public Payment createPurchase(final Account account, final UUID paymentMethodId, @Nullable final UUID paymentId, final BigDecimal amount, final Currency currency, @Nullable final String paymentExternalKey, @Nullable final String paymentTransactionExternalKey,
                                  final Iterable<PluginProperty> properties, final CallContext callContext) throws PaymentApiException {
        checkNotNullParameter(account, "account");
        checkNotNullParameter(paymentMethodId, "paymentMethodId");
        if (paymentId == null) {
            checkNotNullParameter(amount, "amount");
            checkNotNullParameter(currency, "currency");
        }
        checkNotNullParameter(properties, "plugin properties");

        final String transactionType = TransactionType.PURCHASE.name();
        Payment payment = null;
        PaymentTransaction paymentTransaction = null;
        try {
            logEnterAPICall(transactionType, account, paymentMethodId, paymentId, null, amount, currency, paymentExternalKey, paymentTransactionExternalKey, null, null);

            final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
            payment = paymentProcessor.createPurchase(IS_API_PAYMENT, NULL_ATTEMPT_ID, account, paymentMethodId, paymentId, amount, currency, paymentExternalKey, paymentTransactionExternalKey,
                                                                    SHOULD_LOCK_ACCOUNT, properties, callContext, internalCallContext);

            paymentTransaction = findPaymentTransaction(payment, paymentTransactionExternalKey);

            return payment;
        } finally {
            logExitAPICall(transactionType,
                           account,
                           payment != null ? payment.getPaymentMethodId() : null,
                           payment != null ? payment.getId() : null,
                           paymentTransaction != null ? paymentTransaction.getId() : null,
                           paymentTransaction != null ? paymentTransaction.getProcessedAmount() : null,
                           paymentTransaction != null ? paymentTransaction.getProcessedCurrency() : null,
                           payment != null ? payment.getExternalKey() : null,
                           paymentTransaction != null ? paymentTransaction.getExternalKey() : null,
                           paymentTransaction != null ? paymentTransaction.getTransactionStatus() : null,
                           null);
        }
    }

    @Override
    public Payment createPurchaseWithPaymentControl(final Account account, @Nullable final UUID paymentMethodId, @Nullable final UUID paymentId, final BigDecimal amount, final Currency currency, @Nullable final String paymentExternalKey, final String paymentTransactionExternalKey,
                                                    final Iterable<PluginProperty> properties, final PaymentOptions paymentOptions, final CallContext callContext) throws PaymentApiException {
        final List<String> paymentControlPluginNames = toPaymentControlPluginNames(paymentOptions);
        if (paymentControlPluginNames.isEmpty()) {
            return createPurchase(account, paymentMethodId, paymentId, amount, currency, paymentExternalKey, paymentTransactionExternalKey, properties, callContext);
        }

        checkNotNullParameter(account, "account");
        if (paymentId == null) {
            checkNotNullParameter(amount, "amount");
            checkNotNullParameter(currency, "currency");
        }
        checkNotNullParameter(paymentTransactionExternalKey, "paymentTransactionExternalKey");
        checkNotNullParameter(properties, "plugin properties");

        if (paymentMethodId == null && !paymentOptions.isExternalPayment()) {
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_DEFAULT_PAYMENT_METHOD, "paymentMethodId", "should not be null");
        }
        final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
        final UUID nonNulPaymentMethodId = (paymentMethodId != null) ?
                                           paymentMethodId :
                                           paymentMethodProcessor.createOrGetExternalPaymentMethod(UUIDs.randomUUID().toString(), account, properties, callContext, internalCallContext);

        final String transactionType = TransactionType.PURCHASE.name();
        Payment payment = null;
        PaymentTransaction paymentTransaction = null;

        try {
            logEnterAPICall(transactionType, account, paymentMethodId, paymentId, null, amount, currency, paymentExternalKey, paymentTransactionExternalKey, null, paymentControlPluginNames);

            payment = pluginControlPaymentProcessor.createPurchase(IS_API_PAYMENT, account, nonNulPaymentMethodId, paymentId, amount, currency, paymentExternalKey, paymentTransactionExternalKey,
                                                                                 properties, paymentControlPluginNames, callContext, internalCallContext);

            paymentTransaction = findPaymentTransaction(payment, paymentTransactionExternalKey);

            return payment;
        } finally {
            logExitAPICall(transactionType,
                           account,
                           payment != null ? payment.getPaymentMethodId() : null,
                           payment != null ? payment.getId() : null,
                           paymentTransaction != null ? paymentTransaction.getId() : null,
                           paymentTransaction != null ? paymentTransaction.getProcessedAmount() : null,
                           paymentTransaction != null ? paymentTransaction.getProcessedCurrency() : null,
                           payment != null ? payment.getExternalKey() : null,
                           paymentTransaction != null ? paymentTransaction.getExternalKey() : null,
                           paymentTransaction != null ? paymentTransaction.getTransactionStatus() : null,
                           paymentControlPluginNames);
        }
    }

    @Override
    public Payment createVoid(final Account account, final UUID paymentId, @Nullable final String paymentTransactionExternalKey, final Iterable<PluginProperty> properties,
                              final CallContext callContext) throws PaymentApiException {

        checkNotNullParameter(account, "account");
        checkNotNullParameter(paymentId, "paymentId");
        checkNotNullParameter(properties, "plugin properties");

        final String transactionType = TransactionType.VOID.name();
        Payment payment = null;
        PaymentTransaction paymentTransaction = null;
        try {
            logEnterAPICall(transactionType, account, null, paymentId, null, null, null, null, paymentTransactionExternalKey, null, null);

            final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
            payment = paymentProcessor.createVoid(IS_API_PAYMENT, NULL_ATTEMPT_ID, account, paymentId, paymentTransactionExternalKey,
                                                                SHOULD_LOCK_ACCOUNT, properties, callContext, internalCallContext);

            paymentTransaction = findPaymentTransaction(payment, paymentTransactionExternalKey);

            return payment;
        } finally {
            logExitAPICall(transactionType,
                           account,
                           payment != null ? payment.getPaymentMethodId() : null,
                           payment != null ? payment.getId() : null,
                           paymentTransaction != null ? paymentTransaction.getId() : null,
                           paymentTransaction != null ? paymentTransaction.getProcessedAmount() : null,
                           paymentTransaction != null ? paymentTransaction.getProcessedCurrency() : null,
                           payment != null ? payment.getExternalKey() : null,
                           paymentTransaction != null ? paymentTransaction.getExternalKey() : null,
                           paymentTransaction != null ? paymentTransaction.getTransactionStatus() : null,
                           null);
        }

    }

    @Override
    public Payment createVoidWithPaymentControl(final Account account, final UUID paymentId, final String paymentTransactionExternalKey, final Iterable<PluginProperty> properties, final PaymentOptions paymentOptions, final CallContext callContext) throws PaymentApiException {
        final List<String> paymentControlPluginNames = toPaymentControlPluginNames(paymentOptions);
        if (paymentControlPluginNames.isEmpty()) {
            return createVoid(account, paymentId, paymentTransactionExternalKey, properties, callContext);
        }

        checkNotNullParameter(account, "account");
        checkNotNullParameter(paymentId, "paymentId");
        checkNotNullParameter(properties, "plugin properties");

        final String transactionType = TransactionType.VOID.name();
        Payment payment = null;
        PaymentTransaction paymentTransaction = null;
        try {
            logEnterAPICall(transactionType, account, null, paymentId, null, null, null, null, paymentTransactionExternalKey, null, paymentControlPluginNames);

            final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
            payment = pluginControlPaymentProcessor.createVoid(IS_API_PAYMENT, account, paymentId, paymentTransactionExternalKey,
                                                                             properties, paymentControlPluginNames, callContext, internalCallContext);

            paymentTransaction = findPaymentTransaction(payment, paymentTransactionExternalKey);

            return payment;
        } finally {
            logExitAPICall(transactionType,
                           account,
                           payment != null ? payment.getPaymentMethodId() : null,
                           payment != null ? payment.getId() : null,
                           paymentTransaction != null ? paymentTransaction.getId() : null,
                           paymentTransaction != null ? paymentTransaction.getProcessedAmount() : null,
                           paymentTransaction != null ? paymentTransaction.getProcessedCurrency() : null,
                           payment != null ? payment.getExternalKey() : null,
                           paymentTransaction != null ? paymentTransaction.getExternalKey() : null,
                           paymentTransaction != null ? paymentTransaction.getTransactionStatus() : null,
                           paymentControlPluginNames);
        }
    }

    @Override
    public Payment createRefund(final Account account, @Nullable final UUID paymentId, final BigDecimal amount, final Currency currency, @Nullable final String paymentTransactionExternalKey, final Iterable<PluginProperty> properties,
                                final CallContext callContext) throws PaymentApiException {
        checkNotNullParameter(account, "account");
        if (paymentId == null) {
            checkNotNullParameter(currency, "currency");
        }
        checkNotNullParameter(paymentId, "paymentId");
        checkNotNullParameter(properties, "plugin properties");

        final String transactionType = TransactionType.REFUND.name();
        Payment payment = null;
        PaymentTransaction paymentTransaction = null;
        try {
            logEnterAPICall(transactionType, account, null, paymentId, null, amount, currency, null, paymentTransactionExternalKey, null, null);

            final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
            payment = paymentProcessor.createRefund(IS_API_PAYMENT, NULL_ATTEMPT_ID, account, paymentId, amount, currency, paymentTransactionExternalKey,
                                                                  SHOULD_LOCK_ACCOUNT, properties, callContext, internalCallContext);

            paymentTransaction = findPaymentTransaction(payment, paymentTransactionExternalKey);

            return payment;
        } finally {
            logExitAPICall(transactionType,
                           account,
                           payment != null ? payment.getPaymentMethodId() : null,
                           payment != null ? payment.getId() : null,
                           paymentTransaction != null ? paymentTransaction.getId() : null,
                           paymentTransaction != null ? paymentTransaction.getProcessedAmount() : null,
                           paymentTransaction != null ? paymentTransaction.getProcessedCurrency() : null,
                           payment != null ? payment.getExternalKey() : null,
                           paymentTransaction != null ? paymentTransaction.getExternalKey() : null,
                           paymentTransaction != null ? paymentTransaction.getTransactionStatus() : null,
                           null);
        }
    }

    @Override
    public Payment createRefundWithPaymentControl(final Account account, @Nullable final UUID paymentId, @Nullable final BigDecimal amount, final Currency currency, final String paymentTransactionExternalKey, final Iterable<PluginProperty> properties,
                                                  final PaymentOptions paymentOptions, final CallContext callContext) throws PaymentApiException {
        final List<String> paymentControlPluginNames = toPaymentControlPluginNames(paymentOptions);
        if (paymentControlPluginNames.isEmpty()) {
            return createRefund(account, paymentId, amount, currency, paymentTransactionExternalKey, properties, callContext);
        }

        checkNotNullParameter(account, "account");
        if (paymentId == null) {
            checkNotNullParameter(currency, "currency");
        }
        checkNotNullParameter(paymentId, "paymentId");
        checkNotNullParameter(paymentTransactionExternalKey, "paymentTransactionExternalKey");
        checkNotNullParameter(properties, "plugin properties");

        final String transactionType = TransactionType.REFUND.name();
        Payment payment = null;
        PaymentTransaction paymentTransaction = null;
        try {
            logEnterAPICall(transactionType, account, null, paymentId, null, amount, currency, null, paymentTransactionExternalKey, null, paymentControlPluginNames);

            final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
            payment = pluginControlPaymentProcessor.createRefund(IS_API_PAYMENT, account, paymentId, amount, currency, paymentTransactionExternalKey,
                                                                               properties, paymentControlPluginNames, callContext, internalCallContext);

            paymentTransaction = findPaymentTransaction(payment, paymentTransactionExternalKey);

            return payment;
        } finally {
            logExitAPICall(transactionType,
                           account,
                           payment != null ? payment.getPaymentMethodId() : null,
                           payment != null ? payment.getId() : null,
                           paymentTransaction != null ? paymentTransaction.getId() : null,
                           paymentTransaction != null ? paymentTransaction.getProcessedAmount() : null,
                           paymentTransaction != null ? paymentTransaction.getProcessedCurrency() : null,
                           payment != null ? payment.getExternalKey() : null,
                           paymentTransaction != null ? paymentTransaction.getExternalKey() : null,
                           paymentTransaction != null ? paymentTransaction.getTransactionStatus() : null,
                           paymentControlPluginNames);
        }
    }

    @Override
    public Payment createCredit(final Account account, final UUID paymentMethodId, @Nullable final UUID paymentId, final BigDecimal amount, final Currency currency,
                                @Nullable final String paymentExternalKey, @Nullable final String paymentTransactionExternalKey,
                                final Iterable<PluginProperty> properties, final CallContext callContext) throws PaymentApiException {
        checkNotNullParameter(account, "account");
        checkNotNullParameter(paymentMethodId, "paymentMethodId");
        if (paymentId == null) {
            checkNotNullParameter(amount, "amount");
            checkNotNullParameter(currency, "currency");
        }
        checkNotNullParameter(properties, "plugin properties");

        final String transactionType = TransactionType.CREDIT.name();
        Payment payment = null;
        PaymentTransaction paymentTransaction = null;
        try {
            logEnterAPICall(transactionType, account, paymentMethodId, paymentId, null, amount, currency, paymentExternalKey, paymentTransactionExternalKey, null, null);

            final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
            payment = paymentProcessor.createCredit(IS_API_PAYMENT, NULL_ATTEMPT_ID, account, paymentMethodId, paymentId, amount, currency, paymentExternalKey, paymentTransactionExternalKey,
                                                                  SHOULD_LOCK_ACCOUNT, properties, callContext, internalCallContext);

            paymentTransaction = findPaymentTransaction(payment, paymentTransactionExternalKey);

            return payment;
        } finally {
            logExitAPICall(transactionType,
                           account,
                           payment != null ? payment.getPaymentMethodId() : null,
                           payment != null ? payment.getId() : null,
                           paymentTransaction != null ? paymentTransaction.getId() : null,
                           paymentTransaction != null ? paymentTransaction.getProcessedAmount() : null,
                           paymentTransaction != null ? paymentTransaction.getProcessedCurrency() : null,
                           payment != null ? payment.getExternalKey() : null,
                           paymentTransaction != null ? paymentTransaction.getExternalKey() : null,
                           paymentTransaction != null ? paymentTransaction.getTransactionStatus() : null,
                           null);
        }
    }

    @Override
    public Payment createCreditWithPaymentControl(final Account account, final UUID paymentMethodId, @Nullable final UUID paymentId, final BigDecimal amount, final Currency currency,
                                                  @Nullable final String paymentExternalKey, @Nullable final String paymentTransactionExternalKey,
                                                  final Iterable<PluginProperty> properties, final PaymentOptions paymentOptions, final CallContext callContext) throws PaymentApiException {
        final List<String> paymentControlPluginNames = toPaymentControlPluginNames(paymentOptions);
        if (paymentControlPluginNames.isEmpty()) {
            return createCredit(account, paymentMethodId, paymentId, amount, currency, paymentExternalKey, paymentTransactionExternalKey, properties, callContext);
        }

        checkNotNullParameter(account, "account");
        checkNotNullParameter(paymentMethodId, "paymentMethodId");
        if (paymentId == null) {
            checkNotNullParameter(amount, "amount");
            checkNotNullParameter(currency, "currency");
        }
        checkNotNullParameter(properties, "plugin properties");

        final String transactionType = TransactionType.CREDIT.name();
        Payment payment = null;
        PaymentTransaction paymentTransaction = null;
        try {
            logEnterAPICall(transactionType, account, paymentMethodId, paymentId, null, amount, currency, paymentExternalKey, paymentTransactionExternalKey, null, paymentControlPluginNames);

            final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
            payment = pluginControlPaymentProcessor.createCredit(IS_API_PAYMENT, account, paymentMethodId, paymentId, amount, currency, paymentExternalKey, paymentTransactionExternalKey,
                                                                               properties, paymentControlPluginNames, callContext, internalCallContext);

            paymentTransaction = findPaymentTransaction(payment, paymentTransactionExternalKey);

            return payment;
        } finally {
            logExitAPICall(transactionType,
                           account,
                           payment != null ? payment.getPaymentMethodId() : null,
                           payment != null ? payment.getId() : null,
                           paymentTransaction != null ? paymentTransaction.getId() : null,
                           paymentTransaction != null ? paymentTransaction.getProcessedAmount() : null,
                           paymentTransaction != null ? paymentTransaction.getProcessedCurrency() : null,
                           payment != null ? payment.getExternalKey() : null,
                           paymentTransaction != null ? paymentTransaction.getExternalKey() : null,
                           paymentTransaction != null ? paymentTransaction.getTransactionStatus() : null,
                           paymentControlPluginNames);
        }
    }

    @Override
    public Payment notifyPendingTransactionOfStateChanged(final Account account, final UUID paymentTransactionId, final boolean isSuccess, final CallContext callContext) throws PaymentApiException {
        checkNotNullParameter(account, "account");
        checkNotNullParameter(paymentTransactionId, "paymentTransactionId");

        final String transactionType = "NOTIFY_STATE_CHANGE";
        Payment payment = null;
        PaymentTransaction paymentTransaction = null;
        try {
            logEnterAPICall(transactionType, account, null, null, paymentTransactionId, null, null, null, null, null, null);

            final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
            payment = paymentProcessor.notifyPendingPaymentOfStateChanged(account, paymentTransactionId, isSuccess, callContext, internalCallContext);

            paymentTransaction = Iterables.<PaymentTransaction>tryFind(payment.getTransactions(),
                                                                                                new Predicate<PaymentTransaction>() {
                                                                                                    @Override
                                                                                                    public boolean apply(final PaymentTransaction transaction) {
                                                                                                        return transaction.getId().equals(paymentTransactionId);
                                                                                                    }
                                                                                                }).orNull();
            return payment;
        } finally {
            logExitAPICall(transactionType,
                           account,
                           payment != null ? payment.getPaymentMethodId() : null,
                           payment != null ? payment.getId() : null,
                           paymentTransaction != null ? paymentTransaction.getId() : null,
                           paymentTransaction != null ? paymentTransaction.getProcessedAmount() : null,
                           paymentTransaction != null ? paymentTransaction.getProcessedCurrency() : null,
                           payment != null ? payment.getExternalKey() : null,
                           paymentTransaction != null ? paymentTransaction.getExternalKey() : null,
                           paymentTransaction != null ? paymentTransaction.getTransactionStatus() : null,
                           null);
        }
    }

    @Override
    public Payment notifyPendingTransactionOfStateChangedWithPaymentControl(final Account account, final UUID paymentTransactionId, final boolean isSuccess, final PaymentOptions paymentOptions, final CallContext callContext) throws PaymentApiException {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public Payment createChargeback(final Account account, final UUID paymentId, final BigDecimal amount, final Currency currency, final String paymentTransactionExternalKey, final CallContext callContext) throws PaymentApiException {
        checkNotNullParameter(account, "account");
        checkNotNullParameter(amount, "amount");
        checkNotNullParameter(currency, "currency");
        checkNotNullParameter(paymentId, "paymentId");

        final String transactionType = TransactionType.CHARGEBACK.name();
        Payment payment = null;
        PaymentTransaction paymentTransaction = null;
        try {
            logEnterAPICall(transactionType, account, null, paymentId, null, amount, currency, null, paymentTransactionExternalKey, null, null);

            final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
            payment = paymentProcessor.createChargeback(IS_API_PAYMENT, NULL_ATTEMPT_ID, account, paymentId, paymentTransactionExternalKey, amount, currency, true,
                                                                      callContext, internalCallContext);

            paymentTransaction = findPaymentTransaction(payment, paymentTransactionExternalKey);

            return payment;
        } finally {
            logExitAPICall(transactionType,
                           account,
                           payment != null ? payment.getPaymentMethodId() : null,
                           payment != null ? payment.getId() : null,
                           paymentTransaction != null ? paymentTransaction.getId() : null,
                           paymentTransaction != null ? paymentTransaction.getProcessedAmount() : null,
                           paymentTransaction != null ? paymentTransaction.getProcessedCurrency() : null,
                           payment != null ? payment.getExternalKey() : null,
                           paymentTransaction != null ? paymentTransaction.getExternalKey() : null,
                           paymentTransaction != null ? paymentTransaction.getTransactionStatus() : null,
                           null);
        }
    }

    @Override
    public Payment createChargebackWithPaymentControl(final Account account, final UUID paymentId, final BigDecimal amount, final Currency currency, final String paymentTransactionExternalKey, final PaymentOptions paymentOptions, final CallContext callContext) throws PaymentApiException {
        final List<String> paymentControlPluginNames = toPaymentControlPluginNames(paymentOptions);
        if (paymentControlPluginNames.isEmpty()) {
            return createChargeback(account, paymentId, amount, currency, paymentTransactionExternalKey, callContext);
        }

        checkNotNullParameter(account, "account");
        checkNotNullParameter(amount, "amount");
        checkNotNullParameter(currency, "currency");
        checkNotNullParameter(paymentId, "paymentId");

        final String transactionType = TransactionType.CHARGEBACK.name();
        Payment payment = null;
        PaymentTransaction paymentTransaction = null;
        try {
            logEnterAPICall(transactionType, account, null, paymentId, null, amount, currency, null, paymentTransactionExternalKey, null, paymentControlPluginNames);

            final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
            payment = pluginControlPaymentProcessor.createChargeback(IS_API_PAYMENT, account, paymentId, paymentTransactionExternalKey, amount, currency,
                                                                                   paymentControlPluginNames, callContext, internalCallContext);

            paymentTransaction = findPaymentTransaction(payment, paymentTransactionExternalKey);

            return payment;
        } finally {
            logExitAPICall(transactionType,
                           account,
                           payment != null ? payment.getPaymentMethodId() : null,
                           payment != null ? payment.getId() : null,
                           paymentTransaction != null ? paymentTransaction.getId() : null,
                           paymentTransaction != null ? paymentTransaction.getProcessedAmount() : null,
                           paymentTransaction != null ? paymentTransaction.getProcessedCurrency() : null,
                           payment != null ? payment.getExternalKey() : null,
                           paymentTransaction != null ? paymentTransaction.getExternalKey() : null,
                           paymentTransaction != null ? paymentTransaction.getTransactionStatus() : null,
                           paymentControlPluginNames);
        }
    }

    //@Override TODO 0.17
    public Payment createChargebackReversal(final Account account, final UUID paymentId, final String paymentTransactionExternalKey, final CallContext callContext) throws PaymentApiException {
        checkNotNullParameter(account, "account");
        checkNotNullParameter(paymentId, "paymentId");
        checkNotNullParameter(paymentTransactionExternalKey, "paymentTransactionExternalKey");

        final String transactionType = TransactionType.CHARGEBACK.name();
        Payment payment = null;
        PaymentTransaction paymentTransaction = null;
        try {
            logEnterAPICall(transactionType, account, null, paymentId, null, null, null, null, paymentTransactionExternalKey, null, null);

            final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
            payment = paymentProcessor.createChargebackReversal(IS_API_PAYMENT, NULL_ATTEMPT_ID, account, paymentId, paymentTransactionExternalKey, null, null, true, callContext, internalCallContext);

            paymentTransaction = findPaymentTransaction(payment, paymentTransactionExternalKey);

            return payment;
        } finally {
            logExitAPICall(transactionType,
                           account,
                           payment != null ? payment.getPaymentMethodId() : null,
                           payment != null ? payment.getId() : null,
                           paymentTransaction != null ? paymentTransaction.getId() : null,
                           paymentTransaction != null ? paymentTransaction.getProcessedAmount() : null,
                           paymentTransaction != null ? paymentTransaction.getProcessedCurrency() : null,
                           payment != null ? payment.getExternalKey() : null,
                           paymentTransaction != null ? paymentTransaction.getExternalKey() : null,
                           paymentTransaction != null ? paymentTransaction.getTransactionStatus() : null,
                           null);
        }
    }

    @Override
    public List<Payment> getAccountPayments(final UUID accountId, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext tenantContext) throws PaymentApiException {
        return paymentProcessor.getAccountPayments(accountId, withPluginInfo, tenantContext, internalCallContextFactory.createInternalTenantContext(accountId, tenantContext));
    }

    @Override
    public Pagination<Payment> getPayments(final Long offset, final Long limit, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext context) {
        return paymentProcessor.getPayments(offset, limit, withPluginInfo, properties, context, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public Pagination<Payment> getPayments(final Long offset, final Long limit, final String pluginName, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext tenantContext) throws PaymentApiException {
        return paymentProcessor.getPayments(offset, limit, pluginName, withPluginInfo, properties, tenantContext, internalCallContextFactory.createInternalTenantContext(tenantContext));
    }

    @Override
    public Payment getPayment(final UUID paymentId, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext context) throws PaymentApiException {
        final Payment payment = paymentProcessor.getPayment(paymentId, withPluginInfo, properties, context, internalCallContextFactory.createInternalTenantContext(paymentId, ObjectType.PAYMENT, context));
        if (payment == null) {
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_PAYMENT, paymentId);
        }
        return payment;
    }

    @Override
    public Payment getPaymentByExternalKey(final String paymentExternalKey, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext tenantContext)
            throws PaymentApiException {
        final Payment payment = paymentProcessor.getPaymentByExternalKey(paymentExternalKey, withPluginInfo, properties, tenantContext, internalCallContextFactory.createInternalTenantContext(tenantContext));
        if (payment == null) {
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_PAYMENT, paymentExternalKey);
        }
        return payment;
    }

    @Override
    public Pagination<Payment> searchPayments(final String searchKey, final Long offset, final Long limit, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext context) {
        return paymentProcessor.searchPayments(searchKey, offset, limit, withPluginInfo, properties, context, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public Pagination<Payment> searchPayments(final String searchKey, final Long offset, final Long limit, final String pluginName, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext context) throws PaymentApiException {
        return paymentProcessor.searchPayments(searchKey, offset, limit, pluginName, withPluginInfo, properties, context, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public UUID addPaymentMethod(final Account account, final String paymentMethodExternalKey, final String pluginName,
                                 final boolean setDefault, final PaymentMethodPlugin paymentMethodInfo,
                                 final Iterable<PluginProperty> properties, final CallContext context)
            throws PaymentApiException {
        return paymentMethodProcessor.addPaymentMethod(paymentMethodExternalKey, pluginName, account, setDefault, paymentMethodInfo, properties,
                                                       context, internalCallContextFactory.createInternalCallContext(account.getId(), context));
    }

    @Override
    public List<PaymentMethod> getAccountPaymentMethods(final UUID accountId, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext context)
            throws PaymentApiException {
        return paymentMethodProcessor.getPaymentMethods(withPluginInfo, properties, context, internalCallContextFactory.createInternalTenantContext(accountId, context));
    }

    @Override
    public PaymentMethod getPaymentMethodById(final UUID paymentMethodId, final boolean includedDeleted, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext context)
            throws PaymentApiException {
        return paymentMethodProcessor.getPaymentMethodById(paymentMethodId, includedDeleted, withPluginInfo, properties, context, internalCallContextFactory.createInternalTenantContext(paymentMethodId, ObjectType.PAYMENT_METHOD, context));
    }

    @Override
    public PaymentMethod getPaymentMethodByExternalKey(final String paymentMethodExternalKey, final boolean includedInactive, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext context)
            throws PaymentApiException {
        return paymentMethodProcessor.getPaymentMethodByExternalKey(paymentMethodExternalKey, includedInactive, withPluginInfo, properties, context, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public Pagination<PaymentMethod> getPaymentMethods(final Long offset, final Long limit, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext context) {
        return paymentMethodProcessor.getPaymentMethods(offset, limit, withPluginInfo, properties, context, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public Pagination<PaymentMethod> getPaymentMethods(final Long offset, final Long limit, final String pluginName, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext context) throws PaymentApiException {
        return paymentMethodProcessor.getPaymentMethods(offset, limit, pluginName, withPluginInfo, properties, context, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public Pagination<PaymentMethod> searchPaymentMethods(final String searchKey, final Long offset, final Long limit, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext context) {
        return paymentMethodProcessor.searchPaymentMethods(searchKey, offset, limit, withPluginInfo, properties, context, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public Pagination<PaymentMethod> searchPaymentMethods(final String searchKey, final Long offset, final Long limit, final String pluginName, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext context) throws PaymentApiException {
        return paymentMethodProcessor.searchPaymentMethods(searchKey, offset, limit, pluginName, withPluginInfo, properties, context, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public void deletePaymentMethod(final Account account, final UUID paymentMethodId, final boolean deleteDefaultPaymentMethodWithAutoPayOff, final boolean forceDefaultPaymentMethodDeletion, final Iterable<PluginProperty> properties, final CallContext context)
            throws PaymentApiException {
        paymentMethodProcessor.deletedPaymentMethod(account, paymentMethodId, deleteDefaultPaymentMethodWithAutoPayOff, forceDefaultPaymentMethodDeletion, properties, context, internalCallContextFactory.createInternalCallContext(account.getId(), context));
    }

    @Override
    public void setDefaultPaymentMethod(final Account account, final UUID paymentMethodId, final Iterable<PluginProperty> properties, final CallContext context)
            throws PaymentApiException {
        paymentMethodProcessor.setDefaultPaymentMethod(account, paymentMethodId, properties, context, internalCallContextFactory.createInternalCallContext(account.getId(), context));
    }

    @Override
    public List<PaymentMethod> refreshPaymentMethods(final Account account, final String pluginName, final Iterable<PluginProperty> properties, final CallContext context)
            throws PaymentApiException {
        return paymentMethodProcessor.refreshPaymentMethods(pluginName, account, properties, context, internalCallContextFactory.createInternalCallContext(account.getId(), context));
    }

    @Override
    public List<PaymentMethod> refreshPaymentMethods(final Account account, final Iterable<PluginProperty> properties, final CallContext context)
            throws PaymentApiException {
        final InternalCallContext callContext = internalCallContextFactory.createInternalCallContext(account.getId(), context);

        final List<PaymentMethod> paymentMethods = new LinkedList<PaymentMethod>();
        for (final String pluginName : paymentMethodProcessor.getAvailablePlugins()) {
            paymentMethods.addAll(paymentMethodProcessor.refreshPaymentMethods(pluginName, account, properties, context, callContext));
        }

        return paymentMethods;
    }

    private PaymentTransaction findPaymentTransaction(final Payment payment, @Nullable final String paymentTransactionExternalKey) {
        // By design, the payment transactions are already correctly sorted (by effective date asc)
        if (paymentTransactionExternalKey == null) {
            return Iterables.getLast(payment.getTransactions());
        } else {
            return Iterables.<PaymentTransaction>find(Lists.<PaymentTransaction>reverse(payment.getTransactions()),
                                                      new Predicate<PaymentTransaction>() {
                                                          @Override
                                                          public boolean apply(final PaymentTransaction input) {
                                                              return paymentTransactionExternalKey.equals(input.getExternalKey());
                                                          }
                                                      });
        }
    }

    private void logEnterAPICall(final String transactionType,
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
        logAPICallInternal("ENTERING ",
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
                           paymentControlPluginNames);
    }

    private void logExitAPICall(final String transactionType,
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
        logAPICallInternal("EXITING ",
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
                           paymentControlPluginNames);
    }

    private void logAPICallInternal(final String prefixMsg,
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
                                    @Nullable final List<String> paymentControlPluginNames) {
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
            log.info(logLine.toString());
        }
    }
}
