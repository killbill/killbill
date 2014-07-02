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
import org.killbill.billing.payment.core.DirectPaymentProcessor;
import org.killbill.billing.payment.core.PaymentMethodProcessor;
import org.killbill.billing.payment.core.PluginControlledPaymentProcessor;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.entity.Pagination;

public class DefaultDirectPaymentApi implements DirectPaymentApi {

    private static final boolean SHOULD_LOCK_ACCOUNT = true;
    private static final boolean IS_API_PAYMENT = true;

    private final DirectPaymentProcessor directPaymentProcessor;
    private final PaymentMethodProcessor paymentMethodProcessor;
    private final PluginControlledPaymentProcessor pluginControlledPaymentProcessor;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public DefaultDirectPaymentApi(final DirectPaymentProcessor directPaymentProcessor, final PaymentMethodProcessor paymentMethodProcessor, final PluginControlledPaymentProcessor pluginControlledPaymentProcessor, final InternalCallContextFactory internalCallContextFactory) {
        this.directPaymentProcessor = directPaymentProcessor;
        this.paymentMethodProcessor = paymentMethodProcessor;
        this.pluginControlledPaymentProcessor = pluginControlledPaymentProcessor;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Override
    public DirectPayment createAuthorization(final Account account, final UUID paymentMethodId, @Nullable final UUID directPaymentId, final BigDecimal amount, final Currency currency, @Nullable final String directPaymentExternalKey, @Nullable final String directPaymentTransactionExternalKey,
                                             final Iterable<PluginProperty> properties, final CallContext callContext) throws PaymentApiException {

        checkNotNullParameter(account, "account");
        checkNotNullParameter(paymentMethodId, "paymentMethodId");
        checkNotNullParameter(amount, "amount");
        checkNotNullParameter(currency, "currency");
        checkNotNullParameter(properties, "plugin properties");
        checkPositiveAmount(amount);

        final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
        return directPaymentProcessor.createAuthorization(IS_API_PAYMENT, account, paymentMethodId, directPaymentId, amount, currency, directPaymentExternalKey, directPaymentTransactionExternalKey,
                                                          SHOULD_LOCK_ACCOUNT, properties, callContext, internalCallContext);
    }

    @Override
    public DirectPayment createCapture(final Account account, final UUID directPaymentId, final BigDecimal amount, final Currency currency, @Nullable final String directPaymentTransactionExternalKey,
                                       final Iterable<PluginProperty> properties, final CallContext callContext) throws PaymentApiException {

        checkNotNullParameter(account, "account");
        checkNotNullParameter(directPaymentId, "paymentId");
        checkNotNullParameter(currency, "currency");
        checkNotNullParameter(properties, "plugin properties");
        checkPositiveAmount(amount);

        final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
        return directPaymentProcessor.createCapture(IS_API_PAYMENT, account, directPaymentId, amount, currency, directPaymentTransactionExternalKey,
                                                    SHOULD_LOCK_ACCOUNT, properties, callContext, internalCallContext);
    }

    @Override
    public DirectPayment createPurchase(final Account account, final UUID paymentMethodId, @Nullable final UUID directPaymentId, final BigDecimal amount, final Currency currency, @Nullable final String directPaymentExternalKey, final String directPaymentTransactionExternalKey,
                                        final Iterable<PluginProperty> properties, final CallContext callContext) throws PaymentApiException {

        checkNotNullParameter(account, "account");
        checkNotNullParameter(paymentMethodId, "paymentMethodId");
        checkNotNullParameter(amount, "amount");
        checkNotNullParameter(currency, "currency");
        checkNotNullParameter(properties, "plugin properties");
        checkPositiveAmount(amount);

        final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
        return directPaymentProcessor.createPurchase(IS_API_PAYMENT, account, paymentMethodId, directPaymentId, amount, currency, directPaymentExternalKey, directPaymentTransactionExternalKey,
                                                     SHOULD_LOCK_ACCOUNT, properties, callContext, internalCallContext);
    }

    @Override
    public DirectPayment createPurchaseWithPaymentControl(final Account account, @Nullable final UUID paymentMethodId, @Nullable final UUID directPaymentId, final BigDecimal amount, final Currency currency, final String directPaymentExternalKey, final String directPaymentTransactionExternalKey,
                                                          final Iterable<PluginProperty> properties, final PaymentOptions paymentOptions, final CallContext callContext) throws PaymentApiException {

        checkNotNullParameter(account, "account");
        checkNotNullParameter(amount, "amount");
        checkNotNullParameter(currency, "currency");
        checkNotNullParameter(directPaymentExternalKey, "paymentExternalKey");
        checkNotNullParameter(directPaymentTransactionExternalKey, "directPaymentTransactionExternalKey");
        checkNotNullParameter(properties, "plugin properties");
        checkPositiveAmount(amount);

        if (paymentMethodId == null && !paymentOptions.isExternalPayment()) {
            throw new PaymentApiException(ErrorCode.PAYMENT_INVALID_PARAMETER, "paymentMethodId", "should not be null");
        }

        final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);

        // STEPH should throw PaymentApiException -- at least when coming from API; also add description.
        final UUID nonNulPaymentMethodId = (paymentMethodId != null) ?
                                           paymentMethodId :
                                           paymentMethodProcessor.createOrGetExternalPaymentMethod(UUID.randomUUID().toString(), account, properties, callContext, internalCallContext);
        return pluginControlledPaymentProcessor.createPurchase(IS_API_PAYMENT, account, nonNulPaymentMethodId, directPaymentId, amount, currency, directPaymentExternalKey, directPaymentTransactionExternalKey,
                                                               properties, paymentOptions.getPaymentControlPluginName(), callContext, internalCallContext);

    }

    @Override
    public DirectPayment createVoid(final Account account, final UUID directPaymentId, @Nullable final String directPaymentTransactionExternalKey, final Iterable<PluginProperty> properties,
                                    final CallContext callContext) throws PaymentApiException {

        checkNotNullParameter(account, "account");
        checkNotNullParameter(directPaymentId, "paymentId");
        checkNotNullParameter(properties, "plugin properties");

        final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
        return directPaymentProcessor.createVoid(IS_API_PAYMENT, account, directPaymentId, directPaymentTransactionExternalKey,
                                                 SHOULD_LOCK_ACCOUNT, properties, callContext, internalCallContext);

    }

    @Override
    public DirectPayment createRefund(final Account account, final UUID directPaymentId, final BigDecimal amount, final Currency currency, @Nullable final String directPaymentTransactionExternalKey, final Iterable<PluginProperty> properties,
                                      final CallContext callContext) throws PaymentApiException {

        checkNotNullParameter(account, "account");
        checkNotNullParameter(amount, "amount");
        checkNotNullParameter(currency, "currency");
        checkNotNullParameter(directPaymentId, "paymentId");
        checkNotNullParameter(properties, "plugin properties");
        checkPositiveAmount(amount);

        final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
        return directPaymentProcessor.createRefund(IS_API_PAYMENT, account, directPaymentId, amount, currency, directPaymentTransactionExternalKey,
                                                   SHOULD_LOCK_ACCOUNT, properties, callContext, internalCallContext);
    }

    @Override
    public DirectPayment createRefundWithPaymentControl(final Account account, final UUID directPaymentId, @Nullable final BigDecimal amount, final Currency currency, final String directPaymentTransactionExternalKey, final Iterable<PluginProperty> properties,
                                                        final PaymentOptions paymentOptions, final CallContext callContext) throws PaymentApiException {

        checkNotNullParameter(account, "account");
        checkNotNullParameter(currency, "currency");
        checkNotNullParameter(directPaymentId, "paymentId");
        checkNotNullParameter(directPaymentTransactionExternalKey, "paymentTransactionExternalKey");
        checkNotNullParameter(properties, "plugin properties");
        if (amount != null) {
            checkPositiveAmount(amount);
        }

        final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
        return pluginControlledPaymentProcessor.createRefund(IS_API_PAYMENT, account, directPaymentId, amount, currency, directPaymentTransactionExternalKey,
                                                             properties, paymentOptions.getPaymentControlPluginName(), callContext, internalCallContext);

    }

    @Override
    public DirectPayment createCredit(final Account account, final UUID paymentMethodId, final UUID directPaymentId, final BigDecimal amount, final Currency currency,
                                      @Nullable final String directPaymentExternalKey, @Nullable  final String directPaymentTransactionExternalKey,
                                      final Iterable<PluginProperty> properties, final CallContext callContext) throws PaymentApiException {

        checkNotNullParameter(account, "account");
        checkNotNullParameter(paymentMethodId, "paymentMethodId");
        checkNotNullParameter(amount, "amount");
        checkNotNullParameter(currency, "currency");
        checkNotNullParameter(directPaymentId, "paymentId");
        checkNotNullParameter(properties, "plugin properties");
        checkPositiveAmount(amount);

        final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
        return directPaymentProcessor.createCredit(IS_API_PAYMENT, account, paymentMethodId, directPaymentId, amount, currency, directPaymentExternalKey, directPaymentTransactionExternalKey,
                                                   SHOULD_LOCK_ACCOUNT, properties, callContext, internalCallContext);

    }

    @Override
    public void notifyPendingTransactionOfStateChanged(final Account account, final UUID directPaymentTransactionId, final boolean isSuccess, final CallContext callContext) throws PaymentApiException {

        checkNotNullParameter(account, "account");
        checkNotNullParameter(directPaymentTransactionId, "paymentTransactionId");

        final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
        directPaymentProcessor.notifyPendingPaymentOfStateChanged(account, directPaymentTransactionId, isSuccess, callContext, internalCallContext);
    }

    @Override
    public void notifyPendingTransactionOfStateChangedWithPaymentControl(Account account, UUID directPaymentTransactionId, boolean isSuccess, PaymentOptions paymentOptions, CallContext context) throws PaymentApiException {

    }


    @Override
    public DirectPayment notifyChargeback(final Account account, final UUID directPaymentTransactionId, @Nullable final String chargebackTransactionExternalKey, final BigDecimal amount, final Currency currency,
                                          final CallContext callContext) throws PaymentApiException {

        checkNotNullParameter(account, "account");
        checkNotNullParameter(amount, "amount");
        checkNotNullParameter(currency, "currency");
        checkNotNullParameter(directPaymentTransactionId, "paymentTransactionId");
        checkPositiveAmount(amount);

        final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
        return directPaymentProcessor.notifyChargeback(IS_API_PAYMENT, account, null, directPaymentTransactionId, chargebackTransactionExternalKey, amount, currency, true,
                                                       callContext, internalCallContext);
    }

    @Override
    public DirectPayment notifyChargebackWithPaymentControl(Account account, UUID directPaymentTransactionId,@Nullable String chargebackTransactionExternalKey, BigDecimal amount, Currency currency, final PaymentOptions paymentOptions, CallContext callContext) throws PaymentApiException {
        checkNotNullParameter(account, "account");
        checkNotNullParameter(amount, "amount");
        checkNotNullParameter(currency, "currency");
        checkNotNullParameter(directPaymentTransactionId, "paymentTransactionId");
        checkPositiveAmount(amount);

        final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
        return pluginControlledPaymentProcessor.notifyPaymentPaymentOfChargeback(account, directPaymentTransactionId, chargebackTransactionExternalKey, amount, currency,
                                                                                 paymentOptions.getPaymentControlPluginName(), callContext, internalCallContext);
    }

    @Override
    public List<DirectPayment> getAccountPayments(final UUID accountId, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext tenantContext) throws PaymentApiException {
        return directPaymentProcessor.getAccountPayments(accountId, internalCallContextFactory.createInternalTenantContext(accountId, tenantContext));
    }

    @Override
    public Pagination<DirectPayment> getPayments(final Long offset, final Long limit, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext context) {
        return directPaymentProcessor.getPayments(offset, limit, properties, context, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public Pagination<DirectPayment> getPayments(final Long offset, final Long limit, final String pluginName, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext tenantContext) throws PaymentApiException {
        return directPaymentProcessor.getPayments(offset, limit, pluginName, properties, tenantContext, internalCallContextFactory.createInternalTenantContext(tenantContext));
    }

    @Override
    public DirectPayment getPayment(final UUID paymentId, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext context) throws PaymentApiException {
        final DirectPayment payment = directPaymentProcessor.getPayment(paymentId, withPluginInfo, properties, context, internalCallContextFactory.createInternalTenantContext(context));
        if (payment == null) {
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_PAYMENT, paymentId);
        }
        return payment;
    }

    @Override
    public DirectPayment getPaymentByExternalKey(final String paymentExternalKey, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext tenantContext)
            throws PaymentApiException {
        final DirectPayment payment = directPaymentProcessor.getPaymentByExternalKey(paymentExternalKey, withPluginInfo, properties, tenantContext, internalCallContextFactory.createInternalTenantContext(tenantContext));
        if (payment == null) {
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_PAYMENT, paymentExternalKey);
        }
        return payment;
    }

    // STEPH TODO withPluginInfo needs to be honored...
    @Override
    public Pagination<DirectPayment> searchPayments(final String searchKey, final Long offset, final Long limit, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext context) {
        return directPaymentProcessor.searchPayments(searchKey, offset, limit, properties, context, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public Pagination<DirectPayment> searchPayments(final String searchKey, final Long offset, final Long limit, final String pluginName, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext context) throws PaymentApiException {
        return directPaymentProcessor.searchPayments(searchKey, offset, limit, pluginName, properties, context, internalCallContextFactory.createInternalTenantContext(context));
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
    public PaymentMethod getPaymentMethodByExternalKey(String paymentMethodExternalKey, boolean includedInactive, boolean withPluginInfo, Iterable<PluginProperty> properties, TenantContext context)
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
