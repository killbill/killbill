/*
 * Copyright 2014-2019 Groupon, Inc
 * Copyright 2014-2019 The Billing Project, LLC
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.joda.time.DateTime;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.payment.core.PaymentControlAwareRefresher;
import org.killbill.billing.payment.core.PaymentMethodProcessor;
import org.killbill.billing.payment.core.PaymentProcessor;
import org.killbill.billing.payment.core.PluginControlPaymentProcessor;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.util.UUIDs;
import org.killbill.billing.util.api.AuditLevel;
import org.killbill.billing.util.audit.AuditLogWithHistory;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.commons.utils.collect.Iterables;
import org.killbill.billing.util.config.definition.PaymentConfig;
import org.killbill.billing.util.entity.Pagination;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.killbill.billing.payment.logging.PaymentLoggingHelper.logEnterAPICall;
import static org.killbill.billing.payment.logging.PaymentLoggingHelper.logExitAPICall;

public class DefaultPaymentApi extends DefaultApiBase implements PaymentApi {

    private static final boolean SHOULD_LOCK_ACCOUNT = true;
    private static final boolean IS_API_PAYMENT = true;
    private static final UUID NULL_ATTEMPT_ID = null;

    private static final Logger log = LoggerFactory.getLogger(DefaultPaymentApi.class);

    private final PaymentControlAwareRefresher paymentControlAwareRefresher;
    private final PaymentProcessor paymentProcessor;
    private final PaymentMethodProcessor paymentMethodProcessor;
    private final PluginControlPaymentProcessor pluginControlPaymentProcessor;
    private final PaymentDao paymentDao;

    @Inject
    public DefaultPaymentApi(final PaymentConfig paymentConfig,
                             final PaymentProcessor paymentProcessor,
                             final PaymentMethodProcessor paymentMethodProcessor,
                             final PluginControlPaymentProcessor pluginControlPaymentProcessor,
                             final PaymentDao paymentDao,
                             final InternalCallContextFactory internalCallContextFactory,
                             final PaymentControlAwareRefresher paymentControlAwareRefresher) {
        super(paymentConfig, internalCallContextFactory);
        this.paymentProcessor = paymentProcessor;
        this.paymentMethodProcessor = paymentMethodProcessor;
        this.pluginControlPaymentProcessor = pluginControlPaymentProcessor;
        this.paymentDao = paymentDao;
        this.paymentControlAwareRefresher = paymentControlAwareRefresher;
    }

    @Override
    public Payment createAuthorization(final Account account, final UUID paymentMethodId, @Nullable final UUID paymentId, final BigDecimal amount, final Currency currency, @Nullable final DateTime effectiveDate,  @Nullable final String paymentExternalKey, @Nullable final String paymentTransactionExternalKey,
                                       final Iterable<PluginProperty> properties, final CallContext callContext) throws PaymentApiException {
        checkNotNullParameter(account, "account");
        checkNotNullParameter(paymentMethodId, "paymentMethodId");
        if (paymentId == null) {
            checkNotNullParameter(amount, "amount");
            checkNotNullParameter(currency, "currency");
        }
        checkNotNullParameter(properties, "plugin properties");
        checkExternalKeyLength(paymentExternalKey);

        final String transactionType = TransactionType.AUTHORIZE.name();
        Payment payment = null;
        PaymentTransaction paymentTransaction = null;
        PaymentApiException exception = null;
        try {
            final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
            logEnterAPICall(log, transactionType, account, paymentMethodId, paymentId, null, amount, currency, paymentExternalKey, paymentTransactionExternalKey, null, null);

            payment = paymentProcessor.createAuthorization(IS_API_PAYMENT, NULL_ATTEMPT_ID, account, paymentMethodId, paymentId, amount, currency, effectiveDate, paymentExternalKey, paymentTransactionExternalKey,
                                                           null, null, SHOULD_LOCK_ACCOUNT, properties, callContext, internalCallContext);

            paymentTransaction = findPaymentTransaction(payment, paymentTransactionExternalKey);

            return payment;
        } catch (PaymentApiException e) {
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
                           null,
                           exception);
        }
    }

    @Override
    public Payment createAuthorizationWithPaymentControl(final Account account, @Nullable final UUID paymentMethodId, @Nullable final UUID paymentId, final BigDecimal amount, final Currency currency, @Nullable final DateTime effectiveDate,
                                                         @Nullable final String paymentExternalKey, @Nullable final String paymentTransactionExternalKey,
                                                         final Iterable<PluginProperty> properties, final PaymentOptions paymentOptions, final CallContext callContext) throws PaymentApiException {
        final List<String> paymentControlPluginNames = toPaymentControlPluginNames(paymentOptions, callContext);
        if (paymentControlPluginNames.isEmpty()) {
            return createAuthorization(account, paymentMethodId, paymentId, amount, currency, effectiveDate, paymentExternalKey, paymentTransactionExternalKey, properties, callContext);
        }

        checkNotNullParameter(account, "account");
        checkNotNullParameter(properties, "plugin properties");
        checkExternalKeyLength(paymentExternalKey);

        final String transactionType = TransactionType.AUTHORIZE.name();

        Payment payment = null;
        PaymentTransaction paymentTransaction = null;
        PaymentApiException exception = null;
        try {
            final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
            logEnterAPICall(log, transactionType, account, paymentMethodId, paymentId, null, amount, currency, paymentExternalKey, paymentTransactionExternalKey, null, paymentControlPluginNames);

            payment = pluginControlPaymentProcessor.createAuthorization(IS_API_PAYMENT, account, paymentMethodId, paymentId, amount, currency, effectiveDate, paymentExternalKey, paymentTransactionExternalKey,
                                                                        properties, paymentControlPluginNames, callContext, internalCallContext);

            paymentTransaction = findPaymentTransaction(payment, paymentTransactionExternalKey);

            return payment;
        } catch (PaymentApiException e) {
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
    }

    @Override
    public Payment createCapture(final Account account, final UUID paymentId, final BigDecimal amount, final Currency currency, @Nullable final DateTime effectiveDate, @Nullable final String paymentTransactionExternalKey,
                                 final Iterable<PluginProperty> properties, final CallContext callContext) throws PaymentApiException {

        checkNotNullParameter(account, "account");
        checkNotNullParameter(paymentId, "paymentId");
        checkNotNullParameter(amount, "amount");
        checkNotNullParameter(currency, "currency");
        checkNotNullParameter(properties, "plugin properties");
        checkExternalKeyLength(paymentTransactionExternalKey);

        final String transactionType = TransactionType.CAPTURE.name();
        Payment payment = null;
        PaymentTransaction paymentTransaction = null;
        PaymentApiException exception = null;
        try {
            final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
            logEnterAPICall(log, transactionType, account, null, paymentId, null, amount, currency, null, paymentTransactionExternalKey, null, null);

            payment = paymentProcessor.createCapture(IS_API_PAYMENT, NULL_ATTEMPT_ID, account, paymentId, amount, currency, effectiveDate, paymentTransactionExternalKey,
                                                     null, SHOULD_LOCK_ACCOUNT, properties, callContext, internalCallContext);

            paymentTransaction = findPaymentTransaction(payment, paymentTransactionExternalKey);

            return payment;
        } catch (PaymentApiException e) {
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
                           null,
                           exception);
        }
    }

    @Override
    public Payment createCaptureWithPaymentControl(final Account account, final UUID paymentId, final BigDecimal amount, final Currency currency, @Nullable final DateTime effectiveDate, @Nullable final String paymentTransactionExternalKey,
                                                   final Iterable<PluginProperty> properties, final PaymentOptions paymentOptions, final CallContext callContext) throws PaymentApiException {
        final List<String> paymentControlPluginNames = toPaymentControlPluginNames(paymentOptions, callContext);
        if (paymentControlPluginNames.isEmpty()) {
            return createCapture(account, paymentId, amount, currency, effectiveDate, paymentTransactionExternalKey, properties, callContext);
        }

        checkNotNullParameter(account, "account");
        checkNotNullParameter(paymentId, "paymentId");
        checkNotNullParameter(properties, "plugin properties");
        checkExternalKeyLength(paymentTransactionExternalKey);

        final String transactionType = TransactionType.CAPTURE.name();
        Payment payment = null;
        PaymentTransaction paymentTransaction = null;
        PaymentApiException exception = null;
        try {
            final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
            logEnterAPICall(log, transactionType, account, null, paymentId, null, amount, currency, null, paymentTransactionExternalKey, null, paymentControlPluginNames);

            payment = pluginControlPaymentProcessor.createCapture(IS_API_PAYMENT, account, paymentId, amount, currency, effectiveDate, paymentTransactionExternalKey,
                                                                                properties, paymentControlPluginNames, callContext, internalCallContext);

            paymentTransaction = findPaymentTransaction(payment, paymentTransactionExternalKey);

            return payment;
        } catch (PaymentApiException e) {
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

    }

    @Override
    public Payment createPurchase(final Account account, final UUID paymentMethodId, @Nullable final UUID paymentId, final BigDecimal amount, final Currency currency, @Nullable final DateTime effectiveDate, @Nullable final String paymentExternalKey, @Nullable final String paymentTransactionExternalKey,
                                  final Iterable<PluginProperty> properties, final CallContext callContext) throws PaymentApiException {
        checkNotNullParameter(account, "account");
        checkNotNullParameter(paymentMethodId, "paymentMethodId");
        if (paymentId == null) {
            checkNotNullParameter(amount, "amount");
            checkNotNullParameter(currency, "currency");
        }
        checkNotNullParameter(properties, "plugin properties");
        checkExternalKeyLength(paymentTransactionExternalKey);

        final String transactionType = TransactionType.PURCHASE.name();
        Payment payment = null;
        PaymentTransaction paymentTransaction = null;
        PaymentApiException exception = null;
        try {
            final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
            logEnterAPICall(log, transactionType, account, paymentMethodId, paymentId, null, amount, currency, paymentExternalKey, paymentTransactionExternalKey, null, null);

            payment = paymentProcessor.createPurchase(IS_API_PAYMENT, NULL_ATTEMPT_ID, account, paymentMethodId, paymentId, amount, currency, effectiveDate, paymentExternalKey, paymentTransactionExternalKey,
                                                      null, null, SHOULD_LOCK_ACCOUNT, properties, callContext, internalCallContext);

            paymentTransaction = findPaymentTransaction(payment, paymentTransactionExternalKey);

            return payment;
        } catch (PaymentApiException e) {
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
                           null,
                           exception);
        }
    }

    @Override
    public Payment createPurchaseWithPaymentControl(final Account account, @Nullable final UUID paymentMethodId, @Nullable final UUID paymentId, final BigDecimal amount, final Currency currency, @Nullable final DateTime effectiveDate, @Nullable final String paymentExternalKey, final String paymentTransactionExternalKey,
                                                    final Iterable<PluginProperty> properties, final PaymentOptions paymentOptions, final CallContext callContext) throws PaymentApiException {
        final List<String> paymentControlPluginNames = toPaymentControlPluginNames(paymentOptions, callContext);
        if (paymentControlPluginNames.isEmpty()) {
            return createPurchase(account, paymentMethodId, paymentId, amount, currency, effectiveDate, paymentExternalKey, paymentTransactionExternalKey, properties, callContext);
        }

        checkNotNullParameter(account, "account");
        checkNotNullParameter(properties, "plugin properties");
        checkExternalKeyLength(paymentTransactionExternalKey);

        final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);


        final UUID resolvedPaymentMethodId = (paymentMethodId == null && paymentOptions.isExternalPayment()) ?
                                             paymentMethodProcessor.createOrGetExternalPaymentMethod(UUIDs.randomUUID().toString(), account, properties, callContext, internalCallContext) :
                                             paymentMethodId;

        final String transactionType = TransactionType.PURCHASE.name();
        Payment payment = null;
        PaymentTransaction paymentTransaction = null;
        PaymentApiException exception = null;
        try {
            logEnterAPICall(log, transactionType, account, paymentMethodId, paymentId, null, amount, currency, paymentExternalKey, paymentTransactionExternalKey, null, paymentControlPluginNames);

            payment = pluginControlPaymentProcessor.createPurchase(IS_API_PAYMENT, account, resolvedPaymentMethodId, paymentId, amount, currency, effectiveDate, paymentExternalKey, paymentTransactionExternalKey,
                                                                                 properties, paymentControlPluginNames, callContext, internalCallContext);

            paymentTransaction = findPaymentTransaction(payment, paymentTransactionExternalKey);

            return payment;
        } catch (PaymentApiException e) {
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
    }

    @Override
    public Payment createVoid(final Account account, final UUID paymentId, @Nullable final DateTime effectiveDate, @Nullable final String paymentTransactionExternalKey, final Iterable<PluginProperty> properties,
                              final CallContext callContext) throws PaymentApiException {

        checkNotNullParameter(account, "account");
        checkNotNullParameter(paymentId, "paymentId");
        checkNotNullParameter(properties, "plugin properties");
        checkExternalKeyLength(paymentTransactionExternalKey);

        final String transactionType = TransactionType.VOID.name();
        Payment payment = null;
        PaymentTransaction paymentTransaction = null;
        PaymentApiException exception = null;
        try {
            final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
            logEnterAPICall(log, transactionType, account, null, paymentId, null, null, null, null, paymentTransactionExternalKey, null, null);

            payment = paymentProcessor.createVoid(IS_API_PAYMENT, NULL_ATTEMPT_ID, account, paymentId, effectiveDate, paymentTransactionExternalKey,
                                                  null, SHOULD_LOCK_ACCOUNT, properties, callContext, internalCallContext);

            paymentTransaction = findPaymentTransaction(payment, paymentTransactionExternalKey);

            return payment;
        } catch (PaymentApiException e) {
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
                           null,
                           exception);
        }

    }

    @Override
    public Payment createVoidWithPaymentControl(final Account account, final UUID paymentId, @Nullable final DateTime effectiveDate, final String paymentTransactionExternalKey, final Iterable<PluginProperty> properties, final PaymentOptions paymentOptions, final CallContext callContext) throws PaymentApiException {
        final List<String> paymentControlPluginNames = toPaymentControlPluginNames(paymentOptions, callContext);
        if (paymentControlPluginNames.isEmpty()) {
            return createVoid(account, paymentId, effectiveDate, paymentTransactionExternalKey, properties, callContext);
        }

        checkNotNullParameter(account, "account");
        checkNotNullParameter(paymentId, "paymentId");
        checkNotNullParameter(properties, "plugin properties");
        checkExternalKeyLength(paymentTransactionExternalKey);

        final String transactionType = TransactionType.VOID.name();
        Payment payment = null;
        PaymentTransaction paymentTransaction = null;
        PaymentApiException exception = null;
        try {
            final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
            logEnterAPICall(log, transactionType, account, null, paymentId, null, null, null, null, paymentTransactionExternalKey, null, paymentControlPluginNames);

            payment = pluginControlPaymentProcessor.createVoid(IS_API_PAYMENT, account, paymentId, effectiveDate, paymentTransactionExternalKey,
                                                                             properties, paymentControlPluginNames, callContext, internalCallContext);

            paymentTransaction = findPaymentTransaction(payment, paymentTransactionExternalKey);

            return payment;
        } catch (PaymentApiException e) {
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
    }

    @Override
    public Payment createRefund(final Account account, @Nullable final UUID paymentId, final BigDecimal amount, final Currency currency, @Nullable final DateTime effectiveDate, @Nullable final String paymentTransactionExternalKey, final Iterable<PluginProperty> properties,
                                final CallContext callContext) throws PaymentApiException {
        checkNotNullParameter(account, "account");
        if (paymentId == null) {
            checkNotNullParameter(currency, "currency");
        }
        checkNotNullParameter(paymentId, "paymentId");
        checkNotNullParameter(properties, "plugin properties");
        checkExternalKeyLength(paymentTransactionExternalKey);

        final String transactionType = TransactionType.REFUND.name();
        Payment payment = null;
        PaymentTransaction paymentTransaction = null;
        PaymentApiException exception = null;
        try {
            final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
            logEnterAPICall(log, transactionType, account, null, paymentId, null, amount, currency, null, paymentTransactionExternalKey, null, null);

            payment = paymentProcessor.createRefund(IS_API_PAYMENT, NULL_ATTEMPT_ID, account, paymentId, amount, currency, effectiveDate, paymentTransactionExternalKey,
                                                    null, SHOULD_LOCK_ACCOUNT, properties, callContext, internalCallContext);

            paymentTransaction = findPaymentTransaction(payment, paymentTransactionExternalKey);

            return payment;
        } catch (PaymentApiException e) {
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
                           null,
                           exception);
        }
    }

    @Override
    public Payment createRefundWithPaymentControl(final Account account, @Nullable final UUID paymentId, @Nullable final BigDecimal amount, final Currency currency, @Nullable final DateTime effectiveDate, final String paymentTransactionExternalKey, final Iterable<PluginProperty> properties,
                                                  final PaymentOptions paymentOptions, final CallContext callContext) throws PaymentApiException {
        final List<String> paymentControlPluginNames = toPaymentControlPluginNames(paymentOptions, callContext);
        if (paymentControlPluginNames.isEmpty()) {
            return createRefund(account, paymentId, amount, currency, effectiveDate, paymentTransactionExternalKey, properties, callContext);
        }

        checkNotNullParameter(account, "account");
        checkNotNullParameter(paymentId, "paymentId");
        checkNotNullParameter(properties, "plugin properties");
        checkExternalKeyLength(paymentTransactionExternalKey);

        final String transactionType = TransactionType.REFUND.name();
        Payment payment = null;
        PaymentTransaction paymentTransaction = null;
        PaymentApiException exception = null;
        try {
            final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
            logEnterAPICall(log, transactionType, account, null, paymentId, null, amount, currency, null, paymentTransactionExternalKey, null, paymentControlPluginNames);

            payment = pluginControlPaymentProcessor.createRefund(IS_API_PAYMENT, account, paymentId, amount, currency, effectiveDate, paymentTransactionExternalKey,
                                                                               properties, paymentControlPluginNames, callContext, internalCallContext);

            paymentTransaction = findPaymentTransaction(payment, paymentTransactionExternalKey);

            return payment;
        } catch (PaymentApiException e) {
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
    }

    @Override
    public Payment createCredit(final Account account, final UUID paymentMethodId, @Nullable final UUID paymentId, final BigDecimal amount, final Currency currency, @Nullable final DateTime effectiveDate,
                                @Nullable final String paymentExternalKey, @Nullable final String paymentTransactionExternalKey,
                                final Iterable<PluginProperty> properties, final CallContext callContext) throws PaymentApiException {
        checkNotNullParameter(account, "account");
        if (paymentId == null) {
            checkNotNullParameter(amount, "amount");
            checkNotNullParameter(currency, "currency");
        }
        checkNotNullParameter(paymentMethodId, "paymentMethodId");
        checkNotNullParameter(properties, "plugin properties");
        checkExternalKeyLength(paymentTransactionExternalKey);

        final String transactionType = TransactionType.CREDIT.name();
        Payment payment = null;
        PaymentTransaction paymentTransaction = null;
        PaymentApiException exception = null;
        try {
            final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
            logEnterAPICall(log, transactionType, account, paymentMethodId, paymentId, null, amount, currency, paymentExternalKey, paymentTransactionExternalKey, null, null);

            final UUID nonNullPaymentMethodId = (paymentMethodId != null) ?
                                               paymentMethodId :
                                               paymentMethodProcessor.createOrGetExternalPaymentMethod(UUIDs.randomUUID().toString(), account, properties, callContext, internalCallContext);

            payment = paymentProcessor.createCredit(IS_API_PAYMENT, NULL_ATTEMPT_ID, account, nonNullPaymentMethodId, paymentId, amount, currency, effectiveDate, paymentExternalKey, paymentTransactionExternalKey,
                                                    null, null, SHOULD_LOCK_ACCOUNT, properties, callContext, internalCallContext);

            paymentTransaction = findPaymentTransaction(payment, paymentTransactionExternalKey);

            return payment;
        } catch (PaymentApiException e) {
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
                           null,
                           exception);
        }
    }

    @Override
    public Payment createCreditWithPaymentControl(final Account account, @Nullable final UUID paymentMethodId, @Nullable final UUID paymentId, final BigDecimal amount, final Currency currency, @Nullable final DateTime effectiveDate,
                                                  @Nullable final String paymentExternalKey, @Nullable final String paymentTransactionExternalKey,
                                                  final Iterable<PluginProperty> properties, final PaymentOptions paymentOptions, final CallContext callContext) throws PaymentApiException {
        final List<String> paymentControlPluginNames = toPaymentControlPluginNames(paymentOptions, callContext);
        if (paymentControlPluginNames.isEmpty()) {
            return createCredit(account, paymentMethodId, paymentId, amount, currency, effectiveDate,  paymentExternalKey, paymentTransactionExternalKey, properties, callContext);
        }

        checkNotNullParameter(account, "account");
        checkNotNullParameter(properties, "plugin properties");
        checkExternalKeyLength(paymentTransactionExternalKey);

        final String transactionType = TransactionType.CREDIT.name();
        Payment payment = null;
        PaymentTransaction paymentTransaction = null;
        PaymentApiException exception = null;
        try {
            final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
            logEnterAPICall(log, transactionType, account, paymentMethodId, paymentId, null, amount, currency, paymentExternalKey, paymentTransactionExternalKey, null, paymentControlPluginNames);

            final UUID resolvedPaymentMethodId = (paymentMethodId == null && paymentOptions.isExternalPayment()) ?
                                                                                              paymentMethodProcessor.createOrGetExternalPaymentMethod(UUIDs.randomUUID().toString(), account, properties, callContext, internalCallContext) :
                                                                                              paymentMethodId;

            payment = pluginControlPaymentProcessor.createCredit(IS_API_PAYMENT, account, resolvedPaymentMethodId, paymentId, amount, currency, effectiveDate, paymentExternalKey, paymentTransactionExternalKey,
                                                                               properties, paymentControlPluginNames, callContext, internalCallContext);

            paymentTransaction = findPaymentTransaction(payment, paymentTransactionExternalKey);

            return payment;
        } catch (PaymentApiException e) {
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
    }

    @Override
    public void cancelScheduledPaymentTransaction(final String paymentTransactionExternalKey, final CallContext callContext) throws PaymentApiException {
        checkNotNullParameter(paymentTransactionExternalKey, "paymentTransactionExternalKey");
        paymentProcessor.cancelScheduledPaymentTransaction(null, paymentTransactionExternalKey, callContext);
    }

    @Override
    public void cancelScheduledPaymentTransaction(final UUID paymentTransactionId, final CallContext callContext) throws PaymentApiException {
        checkNotNullParameter(paymentTransactionId, "paymentTransactionId");
        paymentProcessor.cancelScheduledPaymentTransaction(paymentTransactionId, null, callContext);
    }

    private PaymentTransaction filterPaymentTransactionsAndGet(List<PaymentTransaction> transactions, Predicate<? super PaymentTransaction> filter) {
        return transactions.stream()
                .filter(filter)
                .findFirst().orElse(null);
    }

    @Override
    public Payment notifyPendingTransactionOfStateChanged(final Account account, final UUID paymentTransactionId, final boolean isSuccess, final CallContext callContext) throws PaymentApiException {
        checkNotNullParameter(account, "account");
        checkNotNullParameter(paymentTransactionId, "paymentTransactionId");

        final String transactionType = "NOTIFY_STATE_CHANGE";
        Payment payment = null;
        PaymentTransaction paymentTransaction = null;
        PaymentApiException exception = null;
        try {
            final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
            logEnterAPICall(log, transactionType, account, null, null, paymentTransactionId, null, null, null, null, null, null);

            payment = paymentProcessor.notifyPendingPaymentOfStateChanged(account, paymentTransactionId, isSuccess, callContext, internalCallContext);

            paymentTransaction = filterPaymentTransactionsAndGet(payment.getTransactions(),
                                                                 transaction -> transaction.getId().equals(paymentTransactionId));
            return payment;
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
                           null,
                           exception);
        }
    }

    @Override
    public Payment notifyPendingTransactionOfStateChangedWithPaymentControl(final Account account, final UUID paymentTransactionId, final boolean isSuccess, final PaymentOptions paymentOptions, final CallContext callContext) throws PaymentApiException {
        final List<String> paymentControlPluginNames = toPaymentControlPluginNames(paymentOptions, callContext);
        if (paymentControlPluginNames.isEmpty()) {
            return notifyPendingTransactionOfStateChanged(account, paymentTransactionId, isSuccess, callContext);
        }

        checkNotNullParameter(account, "account");
        checkNotNullParameter(paymentTransactionId, "paymentTransactionId");

        final String transactionType = "NOTIFY_STATE_CHANGE";
        Payment payment = null;
        PaymentTransaction paymentTransaction = null;
        PaymentApiException exception = null;
        try {
            final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
            logEnterAPICall(log, transactionType, account, null, null, paymentTransactionId, null, null, null, null, null, paymentControlPluginNames);

            payment = pluginControlPaymentProcessor.notifyPendingPaymentOfStateChanged(IS_API_PAYMENT, account, paymentTransactionId, isSuccess, paymentControlPluginNames, callContext, internalCallContext);

            paymentTransaction = filterPaymentTransactionsAndGet(payment.getTransactions(),
                                                                 transaction -> transaction.getId().equals(paymentTransactionId));
            return payment;
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
    }

    @Override
    public Payment createChargeback(final Account account, final UUID paymentId, final BigDecimal amount, final Currency currency, @Nullable final DateTime effectiveDate, final String paymentTransactionExternalKey, final CallContext callContext) throws PaymentApiException {
        checkNotNullParameter(account, "account");
        checkNotNullParameter(amount, "amount");
        checkNotNullParameter(currency, "currency");
        checkNotNullParameter(paymentId, "paymentId");
        checkExternalKeyLength(paymentTransactionExternalKey);

        final String transactionType = TransactionType.CHARGEBACK.name();
        Payment payment = null;
        PaymentTransaction paymentTransaction = null;
        PaymentApiException exception = null;
        try {
            final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
            logEnterAPICall(log, transactionType, account, null, paymentId, null, amount, currency, null, paymentTransactionExternalKey, null, null);

            payment = paymentProcessor.createChargeback(IS_API_PAYMENT, NULL_ATTEMPT_ID, account, paymentId, paymentTransactionExternalKey, amount, currency, effectiveDate, null, true,
                                                        callContext, internalCallContext);

            paymentTransaction = findPaymentTransaction(payment, paymentTransactionExternalKey);

            return payment;
        } catch (PaymentApiException e) {
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
                           null,
                           exception);
        }
    }

    @Override
    public Payment createChargebackWithPaymentControl(final Account account, final UUID paymentId, final BigDecimal amount, final Currency currency, @Nullable final DateTime effectiveDate, final String paymentTransactionExternalKey, final PaymentOptions paymentOptions, final CallContext callContext) throws PaymentApiException {
        final List<String> paymentControlPluginNames = toPaymentControlPluginNames(paymentOptions, callContext);
        if (paymentControlPluginNames.isEmpty()) {
            return createChargeback(account, paymentId, amount, currency, effectiveDate, paymentTransactionExternalKey, callContext);
        }

        checkNotNullParameter(account, "account");
        checkNotNullParameter(paymentId, "paymentId");

        final String transactionType = TransactionType.CHARGEBACK.name();
        Payment payment = null;
        PaymentTransaction paymentTransaction = null;
        PaymentApiException exception = null;
        try {
            final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
            logEnterAPICall(log, transactionType, account, null, paymentId, null, amount, currency, null, paymentTransactionExternalKey, null, paymentControlPluginNames);

            payment = pluginControlPaymentProcessor.createChargeback(IS_API_PAYMENT, account, paymentId, paymentTransactionExternalKey, amount, currency, effectiveDate,
                                                                                   paymentControlPluginNames, callContext, internalCallContext);

            paymentTransaction = findPaymentTransaction(payment, paymentTransactionExternalKey);

            return payment;
        } catch (PaymentApiException e) {
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
    }

    @Override
    public Payment createChargebackReversal(final Account account, final UUID paymentId, @Nullable final DateTime effectiveDate, final String paymentTransactionExternalKey, final CallContext callContext) throws PaymentApiException {
        checkNotNullParameter(account, "account");
        checkNotNullParameter(paymentId, "paymentId");
        checkExternalKeyLength(paymentTransactionExternalKey);

        final String transactionType = TransactionType.CHARGEBACK.name();
        Payment payment = null;
        PaymentTransaction paymentTransaction = null;
        PaymentApiException exception = null;
        try {
            final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
            logEnterAPICall(log, transactionType, account, null, paymentId, null, null, null, null, paymentTransactionExternalKey, null, null);

            payment = paymentProcessor.createChargebackReversal(IS_API_PAYMENT, NULL_ATTEMPT_ID, account, paymentId, paymentTransactionExternalKey, null, null, effectiveDate, null, true, callContext, internalCallContext);

            paymentTransaction = findPaymentTransaction(payment, paymentTransactionExternalKey);

            return payment;
        } catch (PaymentApiException e) {
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
                           null,
                           exception);
        }
    }

    @Override
    public Payment createChargebackReversalWithPaymentControl(final Account account, final UUID paymentId, @Nullable final DateTime effectiveDate, final String paymentTransactionExternalKey, final PaymentOptions paymentOptions, final CallContext callContext) throws PaymentApiException {
        final List<String> paymentControlPluginNames = toPaymentControlPluginNames(paymentOptions, callContext);
        if (paymentControlPluginNames.isEmpty()) {
            return createChargebackReversal(account, paymentId, effectiveDate, paymentTransactionExternalKey, callContext);
        }

        checkNotNullParameter(account, "account");
        checkNotNullParameter(paymentId, "paymentId");
        checkExternalKeyLength(paymentTransactionExternalKey);

        final String transactionType = TransactionType.CHARGEBACK.name();
        Payment payment = null;
        PaymentTransaction paymentTransaction = null;
        PaymentApiException exception = null;
        try {
            final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
            logEnterAPICall(log, transactionType, account, null, paymentId, null, null, null, null, paymentTransactionExternalKey, null, paymentControlPluginNames);

            payment = pluginControlPaymentProcessor.createChargebackReversal(IS_API_PAYMENT, account, paymentId, effectiveDate, paymentTransactionExternalKey, paymentControlPluginNames, callContext, internalCallContext);

            // See https://github.com/killbill/killbill/issues/552
            final List<PaymentTransaction> reversedPaymentTrx = new ArrayList<>(payment.getTransactions());
            Collections.reverse(reversedPaymentTrx);
            paymentTransaction = filterPaymentTransactionsAndGet(reversedPaymentTrx,
                                                                 input -> paymentTransactionExternalKey.equals(input.getExternalKey()));

            return payment;
        } catch (PaymentApiException e) {
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
    }

    @Override
    public List<Payment> getAccountPayments(final UUID accountId, final boolean withPluginInfo, final boolean withAttempts, final Iterable<PluginProperty> properties, final TenantContext tenantContext) throws PaymentApiException {
        return paymentControlAwareRefresher.getAccountPayments(accountId, withPluginInfo, withAttempts, IS_API_PAYMENT, tenantContext, internalCallContextFactory.createInternalTenantContext(accountId, tenantContext));
    }

    @Override
    public Pagination<Payment> getPayments(final Long offset, final Long limit, final boolean withPluginInfo, final boolean withAttempts, final Iterable<PluginProperty> properties, final TenantContext context) {
        return paymentControlAwareRefresher.getPayments(offset, limit, withPluginInfo, withAttempts, IS_API_PAYMENT, properties, context, internalCallContextFactory.createInternalTenantContextWithoutAccountRecordId(context));
    }

    @Override
    public Pagination<Payment> getPayments(final Long offset, final Long limit, final String pluginName, final boolean withPluginInfo, final boolean withAttempts, final Iterable<PluginProperty> properties, final TenantContext tenantContext) throws PaymentApiException {
        return paymentControlAwareRefresher.getPayments(offset, limit, pluginName, withPluginInfo, withAttempts, IS_API_PAYMENT, properties, tenantContext, internalCallContextFactory.createInternalTenantContextWithoutAccountRecordId(tenantContext));
    }

    @Override
    public Payment getPayment(final UUID paymentId, final boolean withPluginInfo, final boolean withAttempts, final Iterable<PluginProperty> properties, final TenantContext context) throws PaymentApiException {
        final Payment payment = paymentControlAwareRefresher.getPayment(paymentId, withPluginInfo, withAttempts, IS_API_PAYMENT, properties, context, internalCallContextFactory.createInternalTenantContext(paymentId, ObjectType.PAYMENT, context));
        if (payment == null) {
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_PAYMENT, paymentId);
        }
        return payment;
    }

    @Override
    public Payment getPaymentByExternalKey(final String paymentExternalKey, final boolean withPluginInfo, final boolean withAttempts, final Iterable<PluginProperty> properties, final TenantContext tenantContext)
            throws PaymentApiException {
        final Payment payment = paymentControlAwareRefresher.getPaymentByExternalKey(paymentExternalKey, withPluginInfo, withAttempts, IS_API_PAYMENT, properties, tenantContext, internalCallContextFactory.createInternalTenantContextWithoutAccountRecordId(tenantContext));
        if (payment == null) {
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_PAYMENT, paymentExternalKey);
        }
        return payment;
    }

    @Override
    public Pagination<Payment> searchPayments(final String searchKey, final Long offset, final Long limit, final boolean withPluginInfo, final boolean withAttempts, final Iterable<PluginProperty> properties, final TenantContext context) {
        return paymentControlAwareRefresher.searchPayments(searchKey, offset, limit, withPluginInfo, withAttempts, IS_API_PAYMENT, properties, context, internalCallContextFactory.createInternalTenantContextWithoutAccountRecordId(context));
    }

    @Override
    public Pagination<Payment> searchPayments(final String searchKey, final Long offset, final Long limit, final String pluginName, final boolean withPluginInfo, final boolean withAttempts, final Iterable<PluginProperty> properties, final TenantContext context) throws PaymentApiException {
        return paymentControlAwareRefresher.searchPayments(searchKey, offset, limit, pluginName, withPluginInfo, withAttempts, IS_API_PAYMENT, properties, context, internalCallContextFactory.createInternalTenantContextWithoutAccountRecordId(context));
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
    public UUID addPaymentMethodWithPaymentControl(final Account account, final String paymentMethodExternalKey, final String pluginName, final boolean setDefault, final PaymentMethodPlugin paymentMethodInfo, final Iterable<PluginProperty> properties, final PaymentOptions paymentOptions, final CallContext context) throws PaymentApiException {
        final List<String> paymentControlPluginNames = toPaymentControlPluginNames(paymentOptions, context);
        if (paymentControlPluginNames.isEmpty()) {
            return addPaymentMethod(account, paymentMethodExternalKey, pluginName, setDefault, paymentMethodInfo, properties, context);
        }

        return paymentMethodProcessor.addPaymentMethodWithControl(paymentMethodExternalKey, pluginName, account, setDefault, paymentMethodInfo, properties,
                                                                  paymentControlPluginNames, context, internalCallContextFactory.createInternalCallContext(account.getId(), context));
    }

    @Override
    public List<PaymentMethod> getAccountPaymentMethods(final UUID accountId, final boolean includedInactive, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext context)
            throws PaymentApiException {
        return paymentMethodProcessor.getPaymentMethods(includedInactive, withPluginInfo, properties, context, internalCallContextFactory.createInternalTenantContext(accountId, context));
    }

    @Override
    public PaymentMethod getPaymentMethodById(final UUID paymentMethodId, final boolean includedDeleted, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext context)
            throws PaymentApiException {
        return paymentMethodProcessor.getPaymentMethodById(paymentMethodId, includedDeleted, withPluginInfo, properties, context, internalCallContextFactory.createInternalTenantContext(paymentMethodId, ObjectType.PAYMENT_METHOD, context));
    }

    @Override
    public PaymentMethod getPaymentMethodByExternalKey(final String paymentMethodExternalKey, final boolean includedInactive, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext context)
            throws PaymentApiException {
        return paymentMethodProcessor.getPaymentMethodByExternalKey(paymentMethodExternalKey, includedInactive, withPluginInfo, properties, context, internalCallContextFactory.createInternalTenantContextWithoutAccountRecordId(context));
    }

    @Override
    public Pagination<PaymentMethod> getPaymentMethods(final Long offset, final Long limit, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext context) {
        return paymentMethodProcessor.getPaymentMethods(offset, limit, withPluginInfo, properties, context, internalCallContextFactory.createInternalTenantContextWithoutAccountRecordId(context));
    }

    @Override
    public Pagination<PaymentMethod> getPaymentMethods(final Long offset, final Long limit, final String pluginName, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext context) throws PaymentApiException {
        return paymentMethodProcessor.getPaymentMethods(offset, limit, pluginName, withPluginInfo, properties, context, internalCallContextFactory.createInternalTenantContextWithoutAccountRecordId(context));
    }

    @Override
    public Pagination<PaymentMethod> searchPaymentMethods(final String searchKey, final Long offset, final Long limit, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext context) {
        return paymentMethodProcessor.searchPaymentMethods(searchKey, offset, limit, withPluginInfo, properties, context, internalCallContextFactory.createInternalTenantContextWithoutAccountRecordId(context));
    }

    @Override
    public Pagination<PaymentMethod> searchPaymentMethods(final String searchKey, final Long offset, final Long limit, final String pluginName, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext context) throws PaymentApiException {
        return paymentMethodProcessor.searchPaymentMethods(searchKey, offset, limit, pluginName, withPluginInfo, properties, context, internalCallContextFactory.createInternalTenantContextWithoutAccountRecordId(context));
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

    @Override
    public List<AuditLogWithHistory> getPaymentAuditLogsWithHistoryForId(final UUID paymentId, final AuditLevel auditLevel, final TenantContext tenantContext) {
        return paymentDao.getPaymentAuditLogsWithHistoryForId(paymentId, auditLevel, internalCallContextFactory.createInternalTenantContext(paymentId, ObjectType.PAYMENT, tenantContext));
    }

    @Override
    public List<AuditLogWithHistory> getPaymentMethodAuditLogsWithHistoryForId(final UUID paymentMethodId, final AuditLevel auditLevel, final TenantContext tenantContext) {
        return paymentDao.getPaymentMethodAuditLogsWithHistoryForId(paymentMethodId, auditLevel, internalCallContextFactory.createInternalTenantContext(paymentMethodId, ObjectType.PAYMENT_METHOD, tenantContext));
    }

    @Override
    public List<AuditLogWithHistory> getPaymentAttemptAuditLogsWithHistoryForId(final UUID paymentAttemptId, final AuditLevel auditLevel, final TenantContext tenantContext) {
        return paymentDao.getPaymentAttemptAuditLogsWithHistoryForId(paymentAttemptId, auditLevel, internalCallContextFactory.createInternalTenantContext(paymentAttemptId, ObjectType.PAYMENT_ATTEMPT, tenantContext));
    }

    @Override
    public List<AuditLogWithHistory> getPaymentTransactionAuditLogsWithHistoryForId(final UUID paymentTransactionId, final AuditLevel auditLevel, final TenantContext tenantContext) {
        return paymentDao.getPaymentTransactionAuditLogsWithHistoryForId(paymentTransactionId, auditLevel, internalCallContextFactory.createInternalTenantContext(paymentTransactionId, ObjectType.TRANSACTION, tenantContext));
    }

    @Override
    public Payment getPaymentByTransactionId(final UUID transactionId, final boolean withPluginInfo, final boolean withAttempts, final Iterable<PluginProperty> properties, final TenantContext context) throws PaymentApiException {
        final Payment payment = paymentControlAwareRefresher.getPaymentByTransactionId(transactionId, withPluginInfo, withAttempts, IS_API_PAYMENT, properties, context, internalCallContextFactory.createInternalTenantContext(transactionId, ObjectType.TRANSACTION, context));
        if (payment == null) {
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_PAYMENT, transactionId);
        }
        return payment;
    }

    @Override
    public Payment getPaymentByTransactionExternalKey(final String transactionExternalKey, final boolean withPluginInfo, final boolean withAttempts, final Iterable<PluginProperty> properties, final TenantContext context) throws PaymentApiException {
        final Payment payment = paymentControlAwareRefresher.getPaymentByTransactionExternalKey(transactionExternalKey, withPluginInfo, withAttempts, IS_API_PAYMENT, properties, context, internalCallContextFactory.createInternalTenantContextWithoutAccountRecordId(context));
        if (payment == null) {
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_PAYMENT, transactionExternalKey);
        }
        return payment;
    }

    private PaymentTransaction findPaymentTransaction(final Payment payment, @Nullable final String paymentTransactionExternalKey) {
        // By design, the payment transactions are already correctly sorted (by effective date asc)
        if (paymentTransactionExternalKey == null) {
            return Iterables.getLast(payment.getTransactions());
        } else {
            final List<PaymentTransaction> reversedPaymentTrx = new ArrayList<>(payment.getTransactions());
            Collections.reverse(reversedPaymentTrx);
            return reversedPaymentTrx.stream()
                    .filter(input -> paymentTransactionExternalKey.equals(input.getExternalKey()))
                    .findFirst().get();
        }
    }
}
