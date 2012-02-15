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

import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoicePaymentApi;
import com.ning.billing.invoice.model.DefaultInvoicePayment;
import com.ning.billing.payment.dao.PaymentDao;
import com.ning.billing.payment.provider.PaymentProviderPlugin;
import com.ning.billing.payment.provider.PaymentProviderPluginRegistry;
import com.ning.billing.payment.setup.PaymentConfig;

public class DefaultPaymentApi implements PaymentApi {
    private final PaymentProviderPluginRegistry pluginRegistry;
    private final AccountUserApi accountUserApi;
    private final InvoicePaymentApi invoicePaymentApi;
    private final PaymentDao paymentDao;
    private final PaymentConfig config;

    private static final Logger log = LoggerFactory.getLogger(DefaultPaymentApi.class);

    @Inject
    public DefaultPaymentApi(PaymentProviderPluginRegistry pluginRegistry,
                             AccountUserApi accountUserApi,
                             InvoicePaymentApi invoicePaymentApi,
                             PaymentDao paymentDao,
                             PaymentConfig config) {
        this.pluginRegistry = pluginRegistry;
        this.accountUserApi = accountUserApi;
        this.invoicePaymentApi = invoicePaymentApi;
        this.paymentDao = paymentDao;
        this.config = config;
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
    public Either<PaymentError, String> addPaymentMethod(@Nullable String accountKey, PaymentMethodInfo paymentMethod) {
        final PaymentProviderPlugin plugin = getPaymentProviderPlugin(accountKey);
        return plugin.addPaymentMethod(accountKey, paymentMethod);
    }

    @Override
    public Either<PaymentError, Void> deletePaymentMethod(String accountKey, String paymentMethodId) {
        final PaymentProviderPlugin plugin = getPaymentProviderPlugin(accountKey);
        return plugin.deletePaymentMethod(accountKey, paymentMethodId);
    }

    @Override
    public Either<PaymentError, PaymentMethodInfo> updatePaymentMethod(String accountKey, PaymentMethodInfo paymentMethodInfo) {
        final PaymentProviderPlugin plugin = getPaymentProviderPlugin(accountKey);
        return plugin.updatePaymentMethod(accountKey, paymentMethodInfo);
    }

    @Override
    public List<Either<PaymentError, PaymentInfo>> createPayment(String accountKey, List<String> invoiceIds) {
        final Account account = accountUserApi.getAccountByKey(accountKey);
        return createPayment(account, invoiceIds);
    }

    @Override
    public Either<PaymentError, PaymentInfo> createPayment(UUID paymentAttemptId) {
        PaymentAttempt paymentAttempt = paymentDao.getPaymentAttemptById(paymentAttemptId);

        if (paymentAttempt != null) {
            Invoice invoice = invoicePaymentApi.getInvoice(paymentAttempt.getInvoiceId());
            Account account = accountUserApi.getAccountById(paymentAttempt.getAccountId());

            if (invoice != null && account != null) {
                if (invoice.getBalance().compareTo(BigDecimal.ZERO) == 0 ) {
                    // TODO: send a notification that invoice was ignored?
                    log.info("Received invoice for payment with outstanding amount of 0 {} ", invoice);
                    Either.left(new PaymentError("invoice_balance_0", "Invoice balance was 0"));
                }
                else {
                    return processPayment(getPaymentProviderPlugin(account), account, invoice, paymentAttempt);
                }
            }
        }
        return Either.left(new PaymentError("retry_payment_error", "Could not load payment attempt, invoice or account for id " + paymentAttemptId));
    }

    @Override
    public List<Either<PaymentError, PaymentInfo>> createPayment(Account account, List<String> invoiceIds) {
        final PaymentProviderPlugin plugin = getPaymentProviderPlugin(account);

        List<Either<PaymentError, PaymentInfo>> processedPaymentsOrErrors = new ArrayList<Either<PaymentError, PaymentInfo>>(invoiceIds.size());

        for (String invoiceId : invoiceIds) {
            Invoice invoice = invoicePaymentApi.getInvoice(UUID.fromString(invoiceId));

            if (invoice.getBalance().compareTo(BigDecimal.ZERO) == 0 ) {
                // TODO: send a notification that invoice was ignored?
                log.info("Received invoice for payment with balance of 0 {} ", invoice);
                Either.left(new PaymentError("invoice_balance_0", "Invoice balance was 0"));
            }
            else {
                PaymentAttempt paymentAttempt = paymentDao.createPaymentAttempt(invoice);

                processedPaymentsOrErrors.add(processPayment(plugin, account, invoice, paymentAttempt));
            }
        }

        return processedPaymentsOrErrors;
    }

    private Either<PaymentError, PaymentInfo> processPayment(PaymentProviderPlugin plugin, Account account, Invoice invoice, PaymentAttempt paymentAttempt) {
        Either<PaymentError, PaymentInfo> paymentOrError = plugin.processInvoice(account, invoice);
        PaymentInfo paymentInfo = null;

        if (paymentOrError.isLeft()) {
            String error = StringUtils.substring(paymentOrError.getLeft().getMessage() + paymentOrError.getLeft().getType(), 0, 100);
            log.info("Could not process a payment for " + paymentAttempt + " error was " + error);

            scheduleRetry(paymentAttempt, error);
        }
        else {
            paymentInfo = paymentOrError.getRight();
            paymentDao.savePaymentInfo(paymentInfo);

            Either<PaymentError, PaymentMethodInfo> paymentMethodInfoOrError = plugin.getPaymentMethodInfo(paymentInfo.getPaymentMethodId());

            if (paymentMethodInfoOrError.isRight()) {
                PaymentMethodInfo paymentMethodInfo = paymentMethodInfoOrError.getRight();

                if (paymentMethodInfo instanceof CreditCardPaymentMethodInfo) {
                    CreditCardPaymentMethodInfo ccPaymentMethod = (CreditCardPaymentMethodInfo)paymentMethodInfo;
                    paymentDao.updatePaymentInfo(ccPaymentMethod.getType(), paymentInfo.getPaymentId(), ccPaymentMethod.getCardType(), ccPaymentMethod.getCardCountry());
                }
                else if (paymentMethodInfo instanceof PaypalPaymentMethodInfo) {
                    PaypalPaymentMethodInfo paypalPaymentMethodInfo = (PaypalPaymentMethodInfo)paymentMethodInfo;
                    paymentDao.updatePaymentInfo(paypalPaymentMethodInfo.getType(), paymentInfo.getPaymentId(), null, null);
                }
            }


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

        return paymentOrError;
    }

    private void scheduleRetry(PaymentAttempt paymentAttempt, String error) {
        final List<Integer> retryDays = config.getPaymentRetryDays();

        int retryCount = 0;

        if (paymentAttempt.getRetryCount() != null) {
            retryCount = paymentAttempt.getRetryCount();
        }

        if (retryCount < retryDays.size()) {
            int retryInDays = 0;
            DateTime nextRetryDate = new DateTime(DateTimeZone.UTC);

            try {
                retryInDays = retryDays.get(retryCount);
                nextRetryDate = nextRetryDate.plusDays(retryInDays);
            }
            catch (NumberFormatException ex) {
                log.error("Could not get retry day for retry count {}", retryCount);
            }

            paymentDao.updatePaymentAttemptWithRetryInfo(paymentAttempt.getPaymentAttemptId(), retryCount + 1, nextRetryDate);
        }
        else if (retryCount == retryDays.size()) {
            log.info("Last payment retry failed for {} ", paymentAttempt);
        }
        else {
            log.error("Cannot update payment retry information because retry count is invalid {} ", retryCount);
        }
    }

    @Override
    public Either<PaymentError, String> createPaymentProviderAccount(Account account) {
        final PaymentProviderPlugin plugin = getPaymentProviderPlugin((Account)null);
        return plugin.createPaymentProviderAccount(account);
    }

    @Override
    public Either<PaymentError, Void> updatePaymentProviderAccountContact(Account account) {
        final PaymentProviderPlugin plugin = getPaymentProviderPlugin(account);
        return plugin.updatePaymentProviderAccountExistingContact(account);
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

    @Override
    public List<PaymentInfo> getPaymentInfo(List<String> invoiceIds) {
        return paymentDao.getPaymentInfo(invoiceIds);
    }

    @Override
    public PaymentAttempt getPaymentAttemptForInvoiceId(String invoiceId) {
        return paymentDao.getPaymentAttemptForInvoiceId(invoiceId);
    }

}
