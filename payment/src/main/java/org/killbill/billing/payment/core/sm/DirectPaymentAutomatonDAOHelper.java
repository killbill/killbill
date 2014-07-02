/*
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
 *
 * Groupon licenses this file to you under the Apache License, version 2.0
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
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.dao.PaymentMethodModelDao;
import org.killbill.billing.payment.dao.PaymentModelDao;
import org.killbill.billing.payment.dao.PaymentTransactionModelDao;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.payment.plugin.api.PaymentTransactionInfoPlugin;

public class DirectPaymentAutomatonDAOHelper {

    protected final DirectPaymentStateContext directPaymentStateContext;
    protected final DateTime utcNow;
    protected final InternalCallContext internalCallContext;

    protected final PaymentDao paymentDao;

    private final OSGIServiceRegistration<PaymentPluginApi> pluginRegistry;

    // Used to build new payments and transactions
    public DirectPaymentAutomatonDAOHelper(final DirectPaymentStateContext directPaymentStateContext,
                                           final DateTime utcNow, final PaymentDao paymentDao,
                                           final OSGIServiceRegistration<PaymentPluginApi> pluginRegistry,
                                           final InternalCallContext internalCallContext) throws PaymentApiException {
        this.directPaymentStateContext = directPaymentStateContext;
        this.utcNow = utcNow;
        this.paymentDao = paymentDao;
        this.pluginRegistry = pluginRegistry;
        this.internalCallContext = internalCallContext;
    }

    public void createNewDirectPaymentTransaction() throws PaymentApiException {


        final PaymentTransactionModelDao paymentTransactionModelDao;
        if (directPaymentStateContext.getDirectPaymentId() == null) {
            final PaymentModelDao newPaymentModelDao = buildNewDirectPaymentModelDao();
            final PaymentTransactionModelDao newPaymentTransactionModelDao = buildNewDirectPaymentTransactionModelDao(newPaymentModelDao.getId());

            final PaymentModelDao paymentModelDao = paymentDao.insertDirectPaymentWithFirstTransaction(newPaymentModelDao, newPaymentTransactionModelDao, internalCallContext);
            paymentTransactionModelDao = paymentDao.getDirectTransactionsForDirectPayment(paymentModelDao.getId(), internalCallContext).get(0);
        } else {
            final List<PaymentTransactionModelDao> existingTransactions = paymentDao.getDirectTransactionsForDirectPayment(directPaymentStateContext.getDirectPaymentId(), internalCallContext);
            if (existingTransactions.isEmpty()) {
                throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_SUCCESS_PAYMENT, directPaymentStateContext.getDirectPaymentId());
            }
            if (existingTransactions.get(0).getCurrency() != directPaymentStateContext.getCurrency()) {
                throw new PaymentApiException(ErrorCode.PAYMENT_INVALID_PARAMETER, "currency", " should be " + existingTransactions.get(0).getCurrency() + " to match other existing transactions");
            }

            final PaymentTransactionModelDao newPaymentTransactionModelDao = buildNewDirectPaymentTransactionModelDao(directPaymentStateContext.getDirectPaymentId());
            paymentTransactionModelDao = paymentDao.updateDirectPaymentWithNewTransaction(directPaymentStateContext.getDirectPaymentId(), newPaymentTransactionModelDao, internalCallContext);
        }
        // Update the context
        directPaymentStateContext.setDirectPaymentTransactionModelDao(paymentTransactionModelDao);

    }

    public void processPaymentInfoPlugin(final TransactionStatus paymentStatus, @Nullable final PaymentTransactionInfoPlugin paymentInfoPlugin,
                                         final String currentPaymentStateName) {
        final BigDecimal processedAmount = paymentInfoPlugin == null ? null : paymentInfoPlugin.getAmount();
        final Currency processedCurrency = paymentInfoPlugin == null ? null : paymentInfoPlugin.getCurrency();
        final String gatewayErrorCode = paymentInfoPlugin == null ? null : paymentInfoPlugin.getGatewayErrorCode();
        final String gatewayErrorMsg = paymentInfoPlugin == null ? null : paymentInfoPlugin.getGatewayError();

        paymentDao.updateDirectPaymentAndTransactionOnCompletion(directPaymentStateContext.getDirectPaymentId(),
                                                                 currentPaymentStateName,
                                                                 directPaymentStateContext.getDirectPaymentTransactionModelDao().getId(),
                                                                 paymentStatus,
                                                                 processedAmount,
                                                                 processedCurrency,
                                                                 gatewayErrorCode,
                                                                 gatewayErrorMsg,
                                                                 internalCallContext);

        // Update the context
        directPaymentStateContext.setDirectPaymentTransactionModelDao(paymentDao.getDirectPaymentTransaction(directPaymentStateContext.getDirectPaymentTransactionModelDao().getId(), internalCallContext));
    }

    public UUID getDefaultPaymentMethodId() throws PaymentApiException {
        final UUID paymentMethodId = directPaymentStateContext.getAccount().getPaymentMethodId();
        if (paymentMethodId == null) {
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_DEFAULT_PAYMENT_METHOD, directPaymentStateContext.getAccount().getId());
        }
        return paymentMethodId;
    }

    public PaymentPluginApi getPaymentProviderPlugin() throws PaymentApiException {

        final UUID paymentMethodId = directPaymentStateContext.getPaymentMethodId();
        final PaymentMethodModelDao methodDao = paymentDao.getPaymentMethodIncludedDeleted(paymentMethodId, internalCallContext);
        if (methodDao == null) {
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_PAYMENT_METHOD, paymentMethodId);
        }
        return getPaymentPluginApi(methodDao.getPluginName());
    }

    public PaymentModelDao getDirectPayment() throws PaymentApiException {
        final PaymentModelDao paymentModelDao;
        paymentModelDao = paymentDao.getDirectPayment(directPaymentStateContext.getDirectPaymentId(), internalCallContext);
        if (paymentModelDao == null) {
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_PAYMENT, directPaymentStateContext.getDirectPaymentId());
        }
        return paymentModelDao;
    }

    private PaymentModelDao buildNewDirectPaymentModelDao() {
        final DateTime createdDate = utcNow;
        final DateTime updatedDate = utcNow;

        return new PaymentModelDao(createdDate,
                                   updatedDate,
                                   directPaymentStateContext.getAccount().getId(),
                                   directPaymentStateContext.getPaymentMethodId(),
                                   directPaymentStateContext.getDirectPaymentExternalKey());
    }

    private PaymentTransactionModelDao buildNewDirectPaymentTransactionModelDao(final UUID directPaymentId) {
        final DateTime createdDate = utcNow;
        final DateTime updatedDate = utcNow;
        final DateTime effectiveDate = utcNow;
        final String gatewayErrorCode = null;
        final String gatewayErrorMsg = null;

        return new PaymentTransactionModelDao(createdDate,
                                              updatedDate,
                                              directPaymentStateContext.getDirectPaymentTransactionExternalKey(),
                                              directPaymentId,
                                              directPaymentStateContext.getTransactionType(),
                                              effectiveDate,
                                              TransactionStatus.UNKNOWN,
                                              directPaymentStateContext.getAmount(),
                                              directPaymentStateContext.getCurrency(),
                                              gatewayErrorCode,
                                              gatewayErrorMsg);
    }

    private PaymentPluginApi getPaymentPluginApi(final String pluginName) throws PaymentApiException {
        final PaymentPluginApi pluginApi = pluginRegistry.getServiceForName(pluginName);
        if (pluginApi == null) {
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_PAYMENT_PLUGIN, pluginName);
        }
        return pluginApi;
    }
}
