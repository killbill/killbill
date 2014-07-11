/*
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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
import org.killbill.billing.account.api.Account;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.payment.core.PaymentProcessor;
import org.killbill.billing.payment.core.PaymentMethodProcessor;
import org.killbill.billing.payment.core.PluginControlledPaymentProcessor;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.entity.Pagination;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultPaymentApi implements PaymentApi {

    private static final boolean SHOULD_LOCK_ACCOUNT = true;
    private static final boolean IS_API_PAYMENT = true;
    private static final UUID NULL_ATTEMPT_ID = null;

    private static final Logger log = LoggerFactory.getLogger(DefaultPaymentApi.class);

    private final PaymentProcessor paymentProcessor;
    private final PaymentMethodProcessor paymentMethodProcessor;
    private final PluginControlledPaymentProcessor pluginControlledPaymentProcessor;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public DefaultPaymentApi(final PaymentProcessor paymentProcessor, final PaymentMethodProcessor paymentMethodProcessor, final PluginControlledPaymentProcessor pluginControlledPaymentProcessor, final InternalCallContextFactory internalCallContextFactory) {
        this.paymentProcessor = paymentProcessor;
        this.paymentMethodProcessor = paymentMethodProcessor;
        this.pluginControlledPaymentProcessor = pluginControlledPaymentProcessor;
        this.internalCallContextFactory = internalCallContextFactory;
    }


    @Override
    public Payment createAuthorization(final Account account, final UUID paymentMethodId, @Nullable final UUID paymentId, final BigDecimal amount, final Currency currency, @Nullable final String paymentExternalKey, @Nullable final String paymentTransactionExternalKey,
                                             final Iterable<PluginProperty> properties, final CallContext callContext) throws PaymentApiException {

        checkNotNullParameter(account, "account");
        checkNotNullParameter(paymentMethodId, "paymentMethodId");
        checkNotNullParameter(amount, "amount");
        checkNotNullParameter(currency, "currency");
        checkNotNullParameter(properties, "plugin properties");
        checkPositiveAmount(amount);

        logAPICall(TransactionType.AUTHORIZE.name(), account, paymentMethodId, paymentId, amount, currency, paymentExternalKey, paymentTransactionExternalKey);

        final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
        return paymentProcessor.createAuthorization(IS_API_PAYMENT, NULL_ATTEMPT_ID, account, paymentMethodId, paymentId, amount, currency, paymentExternalKey, paymentTransactionExternalKey,
                                                          SHOULD_LOCK_ACCOUNT, properties, callContext, internalCallContext);
    }

    @Override
    public Payment createCapture(final Account account, final UUID paymentId, final BigDecimal amount, final Currency currency, @Nullable final String paymentTransactionExternalKey,
                                       final Iterable<PluginProperty> properties, final CallContext callContext) throws PaymentApiException {

        checkNotNullParameter(account, "account");
        checkNotNullParameter(paymentId, "paymentId");
        checkNotNullParameter(currency, "currency");
        checkNotNullParameter(properties, "plugin properties");
        checkPositiveAmount(amount);

        logAPICall(TransactionType.CAPTURE.name(), account, null, paymentId, amount, currency, null, paymentTransactionExternalKey);

        final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
        return paymentProcessor.createCapture(IS_API_PAYMENT, NULL_ATTEMPT_ID, account, paymentId, amount, currency, paymentTransactionExternalKey,
                                                    SHOULD_LOCK_ACCOUNT, properties, callContext, internalCallContext);
    }

    @Override
    public Payment createPurchase(final Account account, final UUID paymentMethodId, @Nullable final UUID paymentId, final BigDecimal amount, final Currency currency, @Nullable final String paymentExternalKey, @Nullable final String paymentTransactionExternalKey,
                                        final Iterable<PluginProperty> properties, final CallContext callContext) throws PaymentApiException {

        checkNotNullParameter(account, "account");
        checkNotNullParameter(paymentMethodId, "paymentMethodId");
        checkNotNullParameter(amount, "amount");
        checkNotNullParameter(currency, "currency");
        checkNotNullParameter(properties, "plugin properties");
        checkPositiveAmount(amount);

        logAPICall(TransactionType.PURCHASE.name(), account, paymentMethodId, paymentId, amount, currency, paymentExternalKey, paymentTransactionExternalKey);

        final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
        return paymentProcessor.createPurchase(IS_API_PAYMENT, NULL_ATTEMPT_ID, account, paymentMethodId, paymentId, amount, currency, paymentExternalKey, paymentTransactionExternalKey,
                                                     SHOULD_LOCK_ACCOUNT, properties, callContext, internalCallContext);
    }

    @Override
    public Payment createPurchaseWithPaymentControl(final Account account, @Nullable final UUID paymentMethodId, @Nullable final UUID paymentId, final BigDecimal amount, final Currency currency, final String paymentExternalKey, final String paymentTransactionExternalKey,
                                                          final Iterable<PluginProperty> properties, final PaymentOptions paymentOptions, final CallContext callContext) throws PaymentApiException {

        checkNotNullParameter(account, "account");
        checkNotNullParameter(amount, "amount");
        checkNotNullParameter(currency, "currency");
        checkNotNullParameter(paymentExternalKey, "paymentExternalKey");
        checkNotNullParameter(paymentTransactionExternalKey, "paymentTransactionExternalKey");
        checkNotNullParameter(properties, "plugin properties");
        checkPositiveAmount(amount);

        logAPICall(TransactionType.PURCHASE.name(), account, paymentMethodId, paymentId, amount, currency, paymentExternalKey, paymentTransactionExternalKey);

        if (paymentMethodId == null && !paymentOptions.isExternalPayment()) {
            throw new PaymentApiException(ErrorCode.PAYMENT_INVALID_PARAMETER, "paymentMethodId", "should not be null");
        }

        final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);

        // STEPH should throw PaymentApiException -- at least when coming from API; also add description.
        final UUID nonNulPaymentMethodId = (paymentMethodId != null) ?
                                           paymentMethodId :
                                           paymentMethodProcessor.createOrGetExternalPaymentMethod(UUID.randomUUID().toString(), account, properties, callContext, internalCallContext);
        return pluginControlledPaymentProcessor.createPurchase(IS_API_PAYMENT, account, nonNulPaymentMethodId, paymentId, amount, currency, paymentExternalKey, paymentTransactionExternalKey,
                                                               properties, paymentOptions.getPaymentControlPluginName(), callContext, internalCallContext);

    }

    @Override
    public Payment createVoid(final Account account, final UUID paymentId, @Nullable final String paymentTransactionExternalKey, final Iterable<PluginProperty> properties,
                                    final CallContext callContext) throws PaymentApiException {

        checkNotNullParameter(account, "account");
        checkNotNullParameter(paymentId, "paymentId");
        checkNotNullParameter(properties, "plugin properties");

        logAPICall(TransactionType.VOID.name(), account, null, paymentId, null, null, null, paymentTransactionExternalKey);

        final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
        return paymentProcessor.createVoid(IS_API_PAYMENT, NULL_ATTEMPT_ID, account, paymentId, paymentTransactionExternalKey,
                                                 SHOULD_LOCK_ACCOUNT, properties, callContext, internalCallContext);

    }

    @Override
    public Payment createRefund(final Account account, final UUID paymentId, final BigDecimal amount, final Currency currency, @Nullable final String paymentTransactionExternalKey, final Iterable<PluginProperty> properties,
                                      final CallContext callContext) throws PaymentApiException {

        checkNotNullParameter(account, "account");
        checkNotNullParameter(amount, "amount");
        checkNotNullParameter(currency, "currency");
        checkNotNullParameter(paymentId, "paymentId");
        checkNotNullParameter(properties, "plugin properties");
        checkPositiveAmount(amount);

        logAPICall(TransactionType.REFUND.name(), account, null, paymentId, amount, currency, null, paymentTransactionExternalKey);

        final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
        return paymentProcessor.createRefund(IS_API_PAYMENT, NULL_ATTEMPT_ID, account, paymentId, amount, currency, paymentTransactionExternalKey,
                                                   SHOULD_LOCK_ACCOUNT, properties, callContext, internalCallContext);
    }

    @Override
    public Payment createRefundWithPaymentControl(final Account account, final UUID paymentId, @Nullable final BigDecimal amount, final Currency currency, final String paymentTransactionExternalKey, final Iterable<PluginProperty> properties,
                                                        final PaymentOptions paymentOptions, final CallContext callContext) throws PaymentApiException {

        checkNotNullParameter(account, "account");
        checkNotNullParameter(currency, "currency");
        checkNotNullParameter(paymentId, "paymentId");
        checkNotNullParameter(paymentTransactionExternalKey, "paymentTransactionExternalKey");
        checkNotNullParameter(properties, "plugin properties");
        if (amount != null) {
            checkPositiveAmount(amount);
        }

        logAPICall(TransactionType.REFUND.name(), account, null, paymentId, amount, currency, null, paymentTransactionExternalKey);

        final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
        return pluginControlledPaymentProcessor.createRefund(IS_API_PAYMENT, account, paymentId, amount, currency, paymentTransactionExternalKey,
                                                             properties, paymentOptions.getPaymentControlPluginName(), callContext, internalCallContext);

    }

    @Override
    public Payment createCredit(final Account account, final UUID paymentMethodId, @Nullable final UUID paymentId, final BigDecimal amount, final Currency currency,
                                      @Nullable final String paymentExternalKey, @Nullable  final String paymentTransactionExternalKey,
                                      final Iterable<PluginProperty> properties, final CallContext callContext) throws PaymentApiException {

        checkNotNullParameter(account, "account");
        checkNotNullParameter(paymentMethodId, "paymentMethodId");
        checkNotNullParameter(amount, "amount");
        checkNotNullParameter(currency, "currency");
        checkNotNullParameter(properties, "plugin properties");
        checkPositiveAmount(amount);

        logAPICall(TransactionType.CREDIT.name(), account, paymentMethodId, paymentId, amount, currency, paymentExternalKey, paymentTransactionExternalKey);

        final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
        return paymentProcessor.createCredit(IS_API_PAYMENT, NULL_ATTEMPT_ID, account, paymentMethodId, paymentId, amount, currency, paymentExternalKey, paymentTransactionExternalKey,
                                                   SHOULD_LOCK_ACCOUNT, properties, callContext, internalCallContext);

    }

    @Override
    public void notifyPendingTransactionOfStateChanged(final Account account, final UUID paymentTransactionId, final boolean isSuccess, final CallContext callContext) throws PaymentApiException {

        checkNotNullParameter(account, "account");
        checkNotNullParameter(paymentTransactionId, "paymentTransactionId");

        logAPICall("NOTIFY_STATE_CHANGE", account, null, paymentTransactionId /* STEPH TBD if this is paymentId or transactionId */, null, null, null, null);

        final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
        paymentProcessor.notifyPendingPaymentOfStateChanged(account, paymentTransactionId, isSuccess, callContext, internalCallContext);
    }

    @Override
    public void notifyPendingTransactionOfStateChangedWithPaymentControl(final Account account, final UUID paymentTransactionId, final boolean isSuccess, final PaymentOptions paymentOptions, final CallContext context) throws PaymentApiException {

    }


    @Override
    public Payment createChargeback(final Account account, final UUID paymentId, final BigDecimal amount, final Currency currency, final String paymentTransactionExternalKey, final CallContext callContext) throws PaymentApiException {
        checkNotNullParameter(account, "account");
        checkNotNullParameter(amount, "amount");
        checkNotNullParameter(currency, "currency");
        checkNotNullParameter(paymentId, "paymentId");
        checkPositiveAmount(amount);

        logAPICall(TransactionType.CHARGEBACK.name(), account, null, paymentId, amount, currency, null, paymentTransactionExternalKey);

        final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
        return paymentProcessor.createChargeback(IS_API_PAYMENT, NULL_ATTEMPT_ID, account, paymentId, paymentTransactionExternalKey, amount, currency, true,
                                                       callContext, internalCallContext);

    }


    @Override
    public Payment createChargebackWithPaymentControl(final Account account, final UUID paymentId, final BigDecimal amount, final Currency currency, final String paymentTransactionExternalKey, final PaymentOptions paymentOptions, final CallContext callContext) throws PaymentApiException {
        checkNotNullParameter(account, "account");
        checkNotNullParameter(amount, "amount");
        checkNotNullParameter(currency, "currency");
        checkNotNullParameter(paymentId, "paymentId");
        checkPositiveAmount(amount);

        final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
        return pluginControlledPaymentProcessor.createChargeback(account, paymentId, paymentTransactionExternalKey, amount, currency,
                                                                 paymentOptions.getPaymentControlPluginName(), callContext, internalCallContext);
    }

    @Override
    public List<Payment> getAccountPayments(final UUID accountId, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext tenantContext) throws PaymentApiException {
        return paymentProcessor.getAccountPayments(accountId, internalCallContextFactory.createInternalTenantContext(accountId, tenantContext));
    }

    @Override
    public Pagination<Payment> getPayments(final Long offset, final Long limit, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext context) {
        return paymentProcessor.getPayments(offset, limit, properties, context, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public Pagination<Payment> getPayments(final Long offset, final Long limit, final String pluginName, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext tenantContext) throws PaymentApiException {
        return paymentProcessor.getPayments(offset, limit, pluginName, properties, tenantContext, internalCallContextFactory.createInternalTenantContext(tenantContext));
    }

    @Override
    public Payment getPayment(final UUID paymentId, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext context) throws PaymentApiException {
        final Payment payment = paymentProcessor.getPayment(paymentId, withPluginInfo, properties, context, internalCallContextFactory.createInternalTenantContext(context));
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

    // STEPH TODO withPluginInfo needs to be honored...
    @Override
    public Pagination<Payment> searchPayments(final String searchKey, final Long offset, final Long limit, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext context) {
        return paymentProcessor.searchPayments(searchKey, offset, limit, properties, context, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public Pagination<Payment> searchPayments(final String searchKey, final Long offset, final Long limit, final String pluginName, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext context) throws PaymentApiException {
        return paymentProcessor.searchPayments(searchKey, offset, limit, pluginName, properties, context, internalCallContextFactory.createInternalTenantContext(context));
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
        return paymentMethodProcessor.getPaymentMethods(accountId, withPluginInfo, properties, context, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public PaymentMethod getPaymentMethodById(final UUID paymentMethodId, final boolean includedDeleted, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext context)
            throws PaymentApiException {
        return paymentMethodProcessor.getPaymentMethodById(paymentMethodId, includedDeleted, withPluginInfo, properties, context, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public PaymentMethod getPaymentMethodByExternalKey(final String paymentMethodExternalKey, final boolean includedInactive, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext context)
            throws PaymentApiException {
        return paymentMethodProcessor.getPaymentMethodByExternalKey(paymentMethodExternalKey, includedInactive, withPluginInfo, properties, context, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public Pagination<PaymentMethod> getPaymentMethods(final Long offset, final Long limit, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext context) {
        return paymentMethodProcessor.getPaymentMethods(offset, limit, properties, context, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public Pagination<PaymentMethod> getPaymentMethods(final Long offset, final Long limit, final String pluginName, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext context) throws PaymentApiException {
        return paymentMethodProcessor.getPaymentMethods(offset, limit, pluginName, properties, context, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public Pagination<PaymentMethod> searchPaymentMethods(final String searchKey, final Long offset, final Long limit, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext context) {
        return paymentMethodProcessor.searchPaymentMethods(searchKey, offset, limit, properties, context, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public Pagination<PaymentMethod> searchPaymentMethods(final String searchKey, final Long offset, final Long limit, final String pluginName, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext context) throws PaymentApiException {
        return paymentMethodProcessor.searchPaymentMethods(searchKey, offset, limit, pluginName, properties, context, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public void deletePaymentMethod(final Account account, final UUID paymentMethodId, final boolean deleteDefaultPaymentMethodWithAutoPayOff, final Iterable<PluginProperty> properties, final CallContext context)
            throws PaymentApiException {
        paymentMethodProcessor.deletedPaymentMethod(account, paymentMethodId, deleteDefaultPaymentMethodWithAutoPayOff, properties, context, internalCallContextFactory.createInternalCallContext(account.getId(), context));
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

    private void logAPICall(final String transactionType, final Account account, final UUID paymentMethodId, @Nullable final UUID paymentId, @Nullable final BigDecimal amount, @Nullable final Currency currency, @Nullable final String paymentExternalKey, @Nullable final String paymentTransactionExternalKey) {
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
            if (amount != null) {
                logLine.append(", amount = ")
                       .append(amount);
            }
            if (currency != null) {
                logLine.append(", currency = ")
                       .append(currency);
            }
            log.info(logLine.toString());
        }
    }

    private void checkNotNullParameter(final Object parameter, final String parameterName) throws PaymentApiException {
        if (parameter == null) {
            throw new PaymentApiException(ErrorCode.PAYMENT_INVALID_PARAMETER, parameterName, "should not be null");
        }
    }

    private void checkPositiveAmount(final BigDecimal amount) throws PaymentApiException {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentApiException(ErrorCode.PAYMENT_INVALID_PARAMETER, "amount", "should be greater than 0");
        }
    }
}
