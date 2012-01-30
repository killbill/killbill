/*
 * Copyright 2010-2011 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
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

package com.ning.billing.payment.api;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.invoice.model.DefaultInvoicePayment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoicePaymentApi;
import com.ning.billing.payment.dao.PaymentDao;
import com.ning.billing.payment.provider.PaymentProviderPlugin;
import com.ning.billing.payment.provider.PaymentProviderPluginRegistry;

public class DefaultPaymentApi implements PaymentApi {
    private final PaymentProviderPluginRegistry pluginRegistry;
    private final AccountUserApi accountUserApi;
    private final InvoicePaymentApi invoicePaymentApi;
    private final PaymentDao paymentDao;

    private static final Logger log = LoggerFactory.getLogger(DefaultPaymentApi.class);

    @Inject
    public DefaultPaymentApi(PaymentProviderPluginRegistry pluginRegistry,
                             AccountUserApi accountUserApi,
                             InvoicePaymentApi invoicePaymentApi,
                             PaymentDao paymentDao) {
        this.pluginRegistry = pluginRegistry;
        this.accountUserApi = accountUserApi;
        this.invoicePaymentApi = invoicePaymentApi;
        this.paymentDao = paymentDao;
    }

    @Override
    public Either<PaymentError, PaymentMethodInfo> getPaymentMethod(@Nullable String accountKey, String paymentMethodId) {
        final PaymentProviderPlugin plugin = getPaymentProviderPlugin(accountKey);
        return plugin.getPaymentMethodInfo(paymentMethodId);
    }

    private PaymentProviderPlugin getPaymentProviderPlugin(String accountKey) {
        String paymentProviderName = null;

        if (accountKey != null) {
            final Account account = accountUserApi.getAccountByKey(accountKey);
            if (account != null) {
                return getPaymentProviderPlugin(account);
            }
            else {
                throw new IllegalArgumentException("Did not find account with accountKey " + accountKey);
            }
        }

        return pluginRegistry.getPlugin(paymentProviderName);
    }

    private PaymentProviderPlugin getPaymentProviderPlugin(Account account) {
        String paymentProviderName = null;

        if (account != null) {
            paymentProviderName = account.getPaymentProviderName();
        }

        return pluginRegistry.getPlugin(paymentProviderName);
    }

    @Override
    public Either<PaymentError, List<PaymentMethodInfo>> getPaymentMethods(String accountKey) {
        final PaymentProviderPlugin plugin = getPaymentProviderPlugin(accountKey);
        return plugin.getPaymentMethods(accountKey);
    }

    @Override
    public Either<PaymentError, Void> updatePaymentGateway(String accountKey) {
        final PaymentProviderPlugin plugin = getPaymentProviderPlugin(accountKey);
        return plugin.updatePaymentGateway(accountKey);
    }

    @Override
    public Either<PaymentError, PaymentProviderAccount> getPaymentProviderAccount(String accountKey) {
        final PaymentProviderPlugin plugin = getPaymentProviderPlugin(accountKey);
        return plugin.getPaymentProviderAccount(accountKey);
    }

    @Override
    public Either<PaymentError, String> addPaypalPaymentMethod(@Nullable String accountKey, PaypalPaymentMethodInfo paypalPaymentMethod) {
        final PaymentProviderPlugin plugin = getPaymentProviderPlugin(accountKey);
        return plugin.addPaypalPaymentMethod(accountKey, paypalPaymentMethod);
    }

    @Override
    public Either<PaymentError, Void> deletePaymentMethod(String accountKey, String paymentMethodId) {
        final PaymentProviderPlugin plugin = getPaymentProviderPlugin(accountKey);
        return plugin.deletePaypalPaymentMethod(accountKey, paymentMethodId);
    }

    @Override
    public Either<PaymentError, PaymentMethodInfo> updatePaymentMethod(String accountKey, PaymentMethodInfo paymentMethodInfo) {
        final PaymentProviderPlugin plugin = getPaymentProviderPlugin(accountKey);
        return plugin.updatePaypalPaymentMethod(accountKey, paymentMethodInfo);
    }

    @Override
    public List<Either<PaymentError, PaymentInfo>> createPayment(String accountKey, List<String> invoiceIds) {
        final Account account = accountUserApi.getAccountByKey(accountKey);
        return createPayment(account, invoiceIds);
    }

    @Override
    public List<Either<PaymentError, PaymentInfo>> createPayment(Account account, List<String> invoiceIds) {
        final PaymentProviderPlugin plugin = getPaymentProviderPlugin(account);

        List<Either<PaymentError, PaymentInfo>> processedPaymentsOrErrors = new ArrayList<Either<PaymentError, PaymentInfo>>(invoiceIds.size());

        for (String invoiceId : invoiceIds) {
            Invoice invoice = invoicePaymentApi.getInvoice(UUID.fromString(invoiceId));

            if (invoice.getBalance().compareTo(BigDecimal.ZERO) == 0 ) {
            // TODO: send a notification that invoice was ignored?
                log.info("Received invoice for payment with outstanding amount of 0 {} ", invoice);
            }
            else if (invoiceId.equals(paymentDao.getPaymentAttemptForInvoiceId(invoiceId))) {
                //TODO: do equals on invoice instead and only reject when invoice is exactly the same?
                log.info("Duplicate invoice payment event, already received invoice {} ", invoice);
            }
            else {
                PaymentAttempt paymentAttempt = paymentDao.createPaymentAttempt(invoice);
                Either<PaymentError, PaymentInfo> paymentOrError = plugin.processInvoice(account, invoice);
                processedPaymentsOrErrors.add(paymentOrError);

                PaymentInfo paymentInfo = null;

                if (paymentOrError.isRight()) {
                    paymentInfo = paymentOrError.getRight();

                    paymentDao.savePaymentInfo(paymentInfo);

                    if (paymentInfo.getPaymentId() != null) {
                        paymentDao.updatePaymentAttemptWithPaymentId(paymentAttempt.getPaymentAttemptId(), paymentInfo.getPaymentId());
                    }
                }

                invoicePaymentApi.notifyOfPaymentAttempt(new DefaultInvoicePayment(paymentAttempt.getPaymentAttemptId(),
                                                                                   invoice.getId(),
                                                                                   paymentAttempt.getPaymentAttemptDate(),
                                                                                   paymentInfo == null ? null : paymentInfo.getAmount(),
//                                                                                 paymentInfo.getRefundAmount(), TODO
                                                                                   paymentInfo == null ? null : invoice.getCurrency()));

            }
        }

        return processedPaymentsOrErrors;
    }

    @Override
    public Either<PaymentError, String> createPaymentProviderAccount(Account account) {
        final PaymentProviderPlugin plugin = getPaymentProviderPlugin((Account)null);
        return plugin.createPaymentProviderAccount(account);
    }

    @Override
    public Either<PaymentError, PaymentProviderAccount> updatePaymentProviderAccount(Account account) {
        final PaymentProviderPlugin plugin = getPaymentProviderPlugin(account);
        return plugin.updatePaymentProviderAccount(account);
    }

    @Override
    public PaymentAttempt getPaymentAttemptForPaymentId(String id) {
        return paymentDao.getPaymentAttemptForPaymentId(id);
    }

    @Override
    public List<Either<PaymentError, PaymentInfo>> createRefund(Account account, List<String> invoiceIds) {
        //TODO
        throw new UnsupportedOperationException();
    }

}
