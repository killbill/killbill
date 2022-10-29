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

package org.killbill.billing.payment.core.sm;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.core.PaymentPluginServiceRegistration;
import org.killbill.billing.payment.dao.PaymentAndTransactionModelDao;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.dao.PaymentMethodModelDao;
import org.killbill.billing.payment.dao.PaymentModelDao;
import org.killbill.billing.payment.dao.PaymentTransactionModelDao;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.payment.plugin.api.PaymentTransactionInfoPlugin;
import org.killbill.billing.util.optimizer.BusOptimizer;
import org.killbill.bus.api.PersistentBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PaymentAutomatonDAOHelper {

    private static final Logger log = LoggerFactory.getLogger(PaymentAutomatonDAOHelper.class);

    protected final PaymentStateContext paymentStateContext;
    protected final DateTime utcNow;
    protected final InternalCallContext internalCallContext;
    protected final PaymentStateMachineHelper paymentSMHelper;

    protected final PaymentDao paymentDao;

    private final PaymentPluginServiceRegistration paymentPluginServiceRegistration;
    private final BusOptimizer eventBus;

    // Cached
    private String pluginName = null;
    private PaymentPluginApi paymentPluginApi = null;

    // Used by the Janitor
    public PaymentAutomatonDAOHelper(final PaymentDao paymentDao,
                                     final InternalCallContext internalCallContext,
                                     final PaymentStateMachineHelper paymentSMHelper) {
        this(null, null, paymentDao, null, internalCallContext, null, paymentSMHelper);
    }

    // Used to build new payments and transactions
    public PaymentAutomatonDAOHelper(final PaymentStateContext paymentStateContext,
                                     final DateTime utcNow, final PaymentDao paymentDao,
                                     final PaymentPluginServiceRegistration paymentPluginServiceRegistration,
                                     final InternalCallContext internalCallContext,
                                     final BusOptimizer eventBus,
                                     final PaymentStateMachineHelper paymentSMHelper) {
        this.paymentStateContext = paymentStateContext;
        this.utcNow = utcNow;
        this.paymentDao = paymentDao;
        this.paymentPluginServiceRegistration = paymentPluginServiceRegistration;
        this.internalCallContext = internalCallContext;
        this.eventBus = eventBus;
        this.paymentSMHelper = paymentSMHelper;
    }

    public void createNewPaymentTransaction() throws PaymentApiException {

        final PaymentTransactionModelDao paymentTransactionModelDao;
        final List<PaymentTransactionModelDao> existingTransactions;
        if (paymentStateContext.getPaymentId() == null) {
            final PaymentModelDao newPaymentModelDao = buildNewPaymentModelDao();
            final PaymentTransactionModelDao newPaymentTransactionModelDao = buildNewPaymentTransactionModelDao(newPaymentModelDao.getId());

            existingTransactions = Collections.emptyList();
            final PaymentAndTransactionModelDao paymentAndTransactionModelDao = paymentDao.insertPaymentWithFirstTransaction(newPaymentModelDao, newPaymentTransactionModelDao, internalCallContext);
            paymentTransactionModelDao = paymentAndTransactionModelDao.getPaymentTransactionModelDao();

        } else {
            existingTransactions = paymentDao.getTransactionsForPayment(paymentStateContext.getPaymentId(), internalCallContext);
            if (existingTransactions.isEmpty()) {
                throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_SUCCESS_PAYMENT, paymentStateContext.getPaymentId());
            }
            if (paymentStateContext.getCurrency() != null &&
                existingTransactions.get(0).getCurrency() != paymentStateContext.getCurrency() &&
                !TransactionType.CHARGEBACK.equals(paymentStateContext.getTransactionType())) {
                // Note that we allow chargebacks in a different currency
                throw new PaymentApiException(ErrorCode.PAYMENT_INVALID_PARAMETER, "currency", " should be " + existingTransactions.get(0).getCurrency() + " to match other existing transactions");
            }

            final PaymentTransactionModelDao newPaymentTransactionModelDao = buildNewPaymentTransactionModelDao(paymentStateContext.getPaymentId());
            paymentTransactionModelDao = paymentDao.updatePaymentWithNewTransaction(paymentStateContext.getPaymentId(), newPaymentTransactionModelDao, internalCallContext);
        }
        // Update the context
        paymentStateContext.setPaymentTransactionModelDao(paymentTransactionModelDao);
        paymentStateContext.setOnLeavingStateExistingTransactions(existingTransactions);
    }

    public void processPaymentInfoPlugin(final TransactionStatus transactionStatus,
                                         @Nullable final PaymentTransactionInfoPlugin paymentInfoPlugin,
                                         final String currentPaymentStateName) {
        final PaymentAndTransactionModelDao paymentAndTransactionModelDao = processPaymentInfoPlugin(transactionStatus,
                                                                                                     paymentInfoPlugin,
                                                                                                     currentPaymentStateName,
                                                                                                     paymentStateContext.getAmount(),
                                                                                                     paymentStateContext.getCurrency(),
                                                                                                     paymentStateContext.getAccount().getId(),
                                                                                                     paymentStateContext.getAttemptId(),
                                                                                                     paymentStateContext.getPaymentId(),
                                                                                                     paymentStateContext.getPaymentTransactionModelDao().getId(),
                                                                                                     paymentStateContext.getTransactionType(),
                                                                                                     paymentStateContext.isApiPayment());
        // Update the context
        paymentStateContext.setPaymentModelDao(paymentAndTransactionModelDao.getPaymentModelDao());
        paymentStateContext.setPaymentTransactionModelDao(paymentAndTransactionModelDao.getPaymentTransactionModelDao());
    }

    public PaymentAndTransactionModelDao processPaymentInfoPlugin(final TransactionStatus transactionStatus,
                                                                  @Nullable final PaymentTransactionInfoPlugin paymentInfoPlugin,
                                                                  final String currentPaymentStateName,
                                                                  final BigDecimal defaultProcessedAmount,
                                                                  final Currency defaultProcessedCurrency,
                                                                  final UUID accountId,
                                                                  final UUID attemptId,
                                                                  final UUID paymentId,
                                                                  final UUID transactionId,
                                                                  final TransactionType transactionType,
                                                                  final boolean isApiPayment) {
        final String lastSuccessPaymentState = paymentSMHelper.isSuccessState(currentPaymentStateName) ? currentPaymentStateName : null;
        return processPaymentInfoPlugin(transactionStatus,
                                        paymentInfoPlugin,
                                        currentPaymentStateName,
                                        lastSuccessPaymentState,
                                        defaultProcessedAmount,
                                        defaultProcessedCurrency,
                                        accountId,
                                        attemptId,
                                        paymentId,
                                        transactionId,
                                        transactionType,
                                        isApiPayment,
                                        false);
    }

    public PaymentAndTransactionModelDao processPaymentInfoPlugin(final TransactionStatus transactionStatus,
                                                                  @Nullable final PaymentTransactionInfoPlugin paymentInfoPlugin,
                                                                  final String currentPaymentStateName,
                                                                  @Nullable final String lastSuccessPaymentState,
                                                                  final BigDecimal defaultSuccessfulProcessedAmount,
                                                                  final Currency defaultProcessedCurrency,
                                                                  final UUID accountId,
                                                                  final UUID attemptId,
                                                                  final UUID paymentId,
                                                                  final UUID transactionId,
                                                                  final TransactionType transactionType,
                                                                  final boolean isApiPayment,
                                                                  final boolean forceOverrideLastSuccessPaymentState) {
        final BigDecimal processedAmount;
        if (TransactionStatus.SUCCESS.equals(transactionStatus) || TransactionStatus.PENDING.equals(transactionStatus)) {
            if (paymentInfoPlugin == null || paymentInfoPlugin.getAmount() == null) {
                processedAmount = defaultSuccessfulProcessedAmount;
            } else {
                processedAmount = paymentInfoPlugin.getAmount();
            }
        } else {
            processedAmount = BigDecimal.ZERO;
        }
        final Currency processedCurrency;
        if (paymentInfoPlugin == null || paymentInfoPlugin.getCurrency() == null) {
            processedCurrency = defaultProcessedCurrency;
        } else {
            processedCurrency = paymentInfoPlugin.getCurrency();
        }
        final String gatewayErrorCode = paymentInfoPlugin == null ? null : paymentInfoPlugin.getGatewayErrorCode();
        final String gatewayErrorMsg = paymentInfoPlugin == null ? null : paymentInfoPlugin.getGatewayError();

        final PaymentAndTransactionModelDao paymentAndTransactionModelDao;
        if (lastSuccessPaymentState != null || forceOverrideLastSuccessPaymentState) {
            paymentAndTransactionModelDao = paymentDao.updatePaymentAndTransactionOnCompletion(accountId,
                                                                                               attemptId,
                                                                                               paymentId,
                                                                                               transactionType,
                                                                                               currentPaymentStateName,
                                                                                               lastSuccessPaymentState,
                                                                                               transactionId,
                                                                                               transactionStatus,
                                                                                               processedAmount,
                                                                                               processedCurrency,
                                                                                               gatewayErrorCode,
                                                                                               gatewayErrorMsg,
                                                                                               isApiPayment,
                                                                                               internalCallContext);
        } else {
            paymentAndTransactionModelDao = paymentDao.updatePaymentAndTransactionOnCompletion(accountId,
                                                                                               attemptId,
                                                                                               paymentId,
                                                                                               transactionType,
                                                                                               currentPaymentStateName,
                                                                                               transactionId,
                                                                                               transactionStatus,
                                                                                               processedAmount,
                                                                                               processedCurrency,
                                                                                               gatewayErrorCode,
                                                                                               gatewayErrorMsg,
                                                                                               isApiPayment,
                                                                                               internalCallContext);
        }

        return paymentAndTransactionModelDao;
    }

    public String getPaymentProviderPluginName(final boolean includeDeleted) throws PaymentApiException {
        if (pluginName != null) {
            return pluginName;
        }

        final PaymentMethodModelDao methodDao = paymentPluginServiceRegistration.getPaymentMethodById(paymentStateContext.getPaymentMethodId(), includeDeleted, internalCallContext);
        pluginName = methodDao.getPluginName();

        return pluginName;
    }

    public PaymentPluginApi getPaymentPluginApi() throws PaymentApiException {
        final String pluginName = getPaymentProviderPluginName(false);
        return getPaymentPluginApi(pluginName);
    }

    public PaymentModelDao getPayment() throws PaymentApiException {
        final PaymentModelDao paymentModelDao;
        paymentModelDao = paymentDao.getPayment(paymentStateContext.getPaymentId(), internalCallContext);
        if (paymentModelDao == null) {
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_PAYMENT, paymentStateContext.getPaymentId());
        }
        return paymentModelDao;
    }

    public PersistentBus getEventBus() {
        return eventBus;
    }

    public PaymentDao getPaymentDao() {
        return paymentDao;
    }

    private PaymentPluginApi getPaymentPluginApi(final String pluginName) throws PaymentApiException {
        if (paymentPluginApi != null) {
            return paymentPluginApi;
        }

        paymentPluginApi = paymentPluginServiceRegistration.getPaymentPluginApi(pluginName);
        return paymentPluginApi;
    }

    private PaymentModelDao buildNewPaymentModelDao() {
        final DateTime createdDate = utcNow;
        final DateTime updatedDate = utcNow;

        if (paymentStateContext.getPaymentIdForNewPayment() != null) {
            return new PaymentModelDao(paymentStateContext.getPaymentIdForNewPayment(),
                                       createdDate,
                                       updatedDate,
                                       paymentStateContext.getAccount().getId(),
                                       paymentStateContext.getPaymentMethodId(),
                                       paymentStateContext.getPaymentExternalKey());
        } else {
            return new PaymentModelDao(createdDate,
                                       updatedDate,
                                       paymentStateContext.getAccount().getId(),
                                       paymentStateContext.getPaymentMethodId(),
                                       paymentStateContext.getPaymentExternalKey());
        }
    }

    private PaymentTransactionModelDao buildNewPaymentTransactionModelDao(final UUID paymentId) {
        final DateTime createdDate = utcNow;
        final DateTime updatedDate = utcNow;
        final DateTime effectiveDate = paymentStateContext.getEffectiveDate() != null ? paymentStateContext.getEffectiveDate() : utcNow;
        final String gatewayErrorCode = null;
        final String gatewayErrorMsg = null;

        if (paymentStateContext.getPaymentTransactionIdForNewPaymentTransaction() != null) {
            return new PaymentTransactionModelDao(paymentStateContext.getPaymentTransactionIdForNewPaymentTransaction(),
                                                  createdDate,
                                                  updatedDate,
                                                  paymentStateContext.getAttemptId(),
                                                  paymentStateContext.getPaymentTransactionExternalKey(),
                                                  paymentId,
                                                  paymentStateContext.getTransactionType(),
                                                  effectiveDate,
                                                  TransactionStatus.UNKNOWN,
                                                  paymentStateContext.getAmount(),
                                                  paymentStateContext.getCurrency(),
                                                  gatewayErrorCode,
                                                  gatewayErrorMsg);
        } else {
            return new PaymentTransactionModelDao(createdDate,
                                                  updatedDate,
                                                  paymentStateContext.getAttemptId(),
                                                  paymentStateContext.getPaymentTransactionExternalKey(),
                                                  paymentId,
                                                  paymentStateContext.getTransactionType(),
                                                  effectiveDate,
                                                  TransactionStatus.UNKNOWN,
                                                  paymentStateContext.getAmount(),
                                                  paymentStateContext.getCurrency(),
                                                  gatewayErrorCode,
                                                  gatewayErrorMsg);
        }
    }

    public String getPluginName() {
        return pluginName;
    }
}
