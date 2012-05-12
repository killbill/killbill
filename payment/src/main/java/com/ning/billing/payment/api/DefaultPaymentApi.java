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
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.ning.billing.ErrorCode;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.config.PaymentConfig;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoicePaymentApi;
import com.ning.billing.payment.RetryService;
import com.ning.billing.payment.dao.PaymentDao;
import com.ning.billing.payment.provider.PaymentProviderPlugin;
import com.ning.billing.payment.provider.PaymentProviderPluginRegistry;
import com.ning.billing.util.bus.Bus;
import com.ning.billing.util.bus.BusEvent;
import com.ning.billing.util.bus.Bus.EventBusException;
import com.ning.billing.util.callcontext.CallContext;

public class DefaultPaymentApi implements PaymentApi {
    private final PaymentProviderPluginRegistry pluginRegistry;
    private final AccountUserApi accountUserApi;
    private final InvoicePaymentApi invoicePaymentApi;
    private final RetryService retryService;
    private final PaymentDao paymentDao;
    private final PaymentConfig config;
    private final Bus eventBus;


    private static final Logger log = LoggerFactory.getLogger(DefaultPaymentApi.class);

    @Inject
    public DefaultPaymentApi(PaymentProviderPluginRegistry pluginRegistry,
            AccountUserApi accountUserApi,
            InvoicePaymentApi invoicePaymentApi,
            RetryService retryService,
            PaymentDao paymentDao,
            PaymentConfig config,
            Bus eventBus) {
        this.pluginRegistry = pluginRegistry;
        this.accountUserApi = accountUserApi;
        this.invoicePaymentApi = invoicePaymentApi;
        this.retryService = retryService;
        this.paymentDao = paymentDao;
        this.config = config;
        this.eventBus = eventBus;

    }

    @Override
    public PaymentMethodInfo getPaymentMethod(final String accountKey, final String paymentMethodId) 
    throws PaymentApiException {
        final PaymentProviderPlugin plugin = getPaymentProviderPlugin(accountKey);
        Either<PaymentErrorEvent, PaymentMethodInfo> result = plugin.getPaymentMethodInfo(paymentMethodId);
        if (result.isLeft()) {
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_PAYMENT_METHOD, accountKey, paymentMethodId);
        }
        return result.getRight();
    }

    private PaymentProviderPlugin getPaymentProviderPlugin(String accountKey) {
        String paymentProviderName = null;

        if (accountKey != null) {
            Account account;
            try {
                account = accountUserApi.getAccountByKey(accountKey);
                return getPaymentProviderPlugin(account);
            } catch (AccountApiException e) {
                log.error("Error getting payment provider plugin.", e);
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
    public List<PaymentMethodInfo> getPaymentMethods(String accountKey)
    throws PaymentApiException {
        final PaymentProviderPlugin plugin = getPaymentProviderPlugin(accountKey);
        Either<PaymentErrorEvent, List<PaymentMethodInfo>> result =  plugin.getPaymentMethods(accountKey);
        if (result.isLeft()) {
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_PAYMENT_METHODS, accountKey);
        }
        return result.getRight();
    }

    @Override
    public void updatePaymentGateway(String accountKey, CallContext context) 
    throws PaymentApiException {
        final PaymentProviderPlugin plugin = getPaymentProviderPlugin(accountKey);
        Either<PaymentErrorEvent, Void> result =  plugin.updatePaymentGateway(accountKey);
        if (result.isLeft()) {
            throw new PaymentApiException(ErrorCode.PAYMENT_UPD_GATEWAY_FAILED, accountKey, result.getLeft().getMessage());
        }
        return;
    }

    @Override
    public PaymentProviderAccount getPaymentProviderAccount(String accountKey)
    throws PaymentApiException {
        final PaymentProviderPlugin plugin = getPaymentProviderPlugin(accountKey);
        Either<PaymentErrorEvent, PaymentProviderAccount> result = plugin.getPaymentProviderAccount(accountKey);
        if (result.isLeft()) {
            throw new PaymentApiException(ErrorCode.PAYMENT_GET_PAYMENT_PROVIDER, accountKey, result.getLeft().getMessage());
        }
        return result.getRight();
    }

    @Override
    public String addPaymentMethod(String accountKey, PaymentMethodInfo paymentMethod, CallContext context) 
    throws PaymentApiException {
        final PaymentProviderPlugin plugin = getPaymentProviderPlugin(accountKey);
        Either<PaymentErrorEvent, String> result =  plugin.addPaymentMethod(accountKey, paymentMethod);
        if (result.isLeft()) {
            throw new PaymentApiException(ErrorCode.PAYMENT_ADD_PAYMENT_METHOD, accountKey, result.getLeft().getMessage());
        }
        return result.getRight();
    }


    @Override
    public void deletePaymentMethod(String accountKey, String paymentMethodId, CallContext context) 
    throws PaymentApiException {
        final PaymentProviderPlugin plugin = getPaymentProviderPlugin(accountKey);
        Either<PaymentErrorEvent, Void> result =  plugin.deletePaymentMethod(accountKey, paymentMethodId);
        if (result.isLeft()) {
            throw new PaymentApiException(ErrorCode.PAYMENT_DEL_PAYMENT_METHOD, accountKey, result.getLeft().getMessage());
        }
        return;
    }

    @Override
    public PaymentMethodInfo updatePaymentMethod(String accountKey, PaymentMethodInfo paymentMethodInfo, CallContext context) 
    throws PaymentApiException {
        final PaymentProviderPlugin plugin = getPaymentProviderPlugin(accountKey);
        Either<PaymentErrorEvent, PaymentMethodInfo> result = plugin.updatePaymentMethod(accountKey, paymentMethodInfo);
        if (result.isLeft()) {
            throw new PaymentApiException(ErrorCode.PAYMENT_UPD_PAYMENT_METHOD, accountKey, result.getLeft().getMessage());
        }
        return result.getRight();
    }

    @Override
    public List<PaymentInfoEvent> createPayment(String accountKey, List<String> invoiceIds, CallContext context) 
    throws PaymentApiException {
        try {
            final Account account = accountUserApi.getAccountByKey(accountKey);
            return createPayment(account, invoiceIds, context);
        } catch (AccountApiException e) {
            throw new PaymentApiException(e);
        }
    }

    @Override
    public PaymentInfoEvent createPaymentForPaymentAttempt(UUID paymentAttemptId, CallContext context) 
    throws PaymentApiException {

        PaymentAttempt paymentAttempt = paymentDao.getPaymentAttemptById(paymentAttemptId);
        if (paymentAttempt != null) {
            try {
                Invoice invoice = invoicePaymentApi.getInvoice(paymentAttempt.getInvoiceId());
                Account account = accountUserApi.getAccountById(paymentAttempt.getAccountId());

                if (invoice != null && account != null) {
                    if (invoice.getBalance().compareTo(BigDecimal.ZERO) <= 0 ) {
                        // TODO: send a notification that invoice was ignored?
                        log.info("Received invoice for payment with outstanding amount of 0 {} ", invoice);
                        throw new PaymentApiException(ErrorCode.PAYMENT_CREATE_PAYMENT_FOR_ATTEMPT_WITH_NON_POSITIVE_INV, account.getId());

                    } else {
                        
                        PaymentAttempt newPaymentAttempt = new PaymentAttempt.Builder(paymentAttempt)
                        .setRetryCount(paymentAttempt.getRetryCount() + 1)
                        .setPaymentAttemptId(UUID.randomUUID())
                        .build();

                        paymentDao.createPaymentAttempt(newPaymentAttempt, context);
                        Either<PaymentErrorEvent, PaymentInfoEvent> result =  processPayment(getPaymentProviderPlugin(account), account, invoice, newPaymentAttempt, context);
                        if (result.isLeft()) {
                            throw new PaymentApiException(ErrorCode.PAYMENT_CREATE_PAYMENT_FOR_ATTEMPT, account.getId(),  paymentAttemptId, result.getLeft().getMessage());                            
                        }
                        return result.getRight();

                    }
                }
            } catch (AccountApiException e) {
                throw new PaymentApiException(e);
            }
        }
        throw new PaymentApiException(ErrorCode.PAYMENT_CREATE_PAYMENT_FOR_ATTEMPT_BAD, paymentAttemptId);
    }

    @Override
    public List<PaymentInfoEvent> createPayment(Account account, List<String> invoiceIds, CallContext context) 
        throws PaymentApiException {
        
        final PaymentProviderPlugin plugin = getPaymentProviderPlugin(account);

        List<Either<PaymentErrorEvent, PaymentInfoEvent>> processedPaymentsOrErrors = new ArrayList<Either<PaymentErrorEvent, PaymentInfoEvent>>(invoiceIds.size());

        for (String invoiceId : invoiceIds) {
            Invoice invoice = invoicePaymentApi.getInvoice(UUID.fromString(invoiceId));

            if (invoice.getBalance().compareTo(BigDecimal.ZERO) <= 0 ) {
                log.debug("Received invoice for payment with balance of 0 {} ", invoice);
            }
            else if (invoice.isMigrationInvoice()) {
                log.info("Received invoice for payment that is a migration invoice - don't know how to handle those yet: {}", invoice);
                Either<PaymentErrorEvent, PaymentInfoEvent> result = Either.left((PaymentErrorEvent) new DefaultPaymentErrorEvent("migration invoice",
                        "Invoice balance was a migration invoice",
                        account.getId(),
                        UUID.fromString(invoiceId),
                        context.getUserToken()));
                processedPaymentsOrErrors.add(result);
            }
            else {
                PaymentAttempt paymentAttempt = paymentDao.createPaymentAttempt(invoice, context);

                processedPaymentsOrErrors.add(processPayment(plugin, account, invoice, paymentAttempt, context));
            }
        }

        List<Either<PaymentErrorEvent, PaymentInfoEvent>> result =  processedPaymentsOrErrors;
        List<PaymentInfoEvent> info = new LinkedList<PaymentInfoEvent>();
        for (Either<PaymentErrorEvent, PaymentInfoEvent> cur : result) {
            if (cur.isLeft()) {
                throw new PaymentApiException(ErrorCode.PAYMENT_CREATE_PAYMENT, account.getId(), cur.getLeft().getMessage());
            }
            info.add(cur.getRight());
        }
        return info;
    }

    private Either<PaymentErrorEvent, PaymentInfoEvent> processPayment(PaymentProviderPlugin plugin, Account account, Invoice invoice,
            PaymentAttempt paymentAttempt, CallContext context) {
        Either<PaymentErrorEvent, PaymentInfoEvent> paymentOrError = plugin.processInvoice(account, invoice);
        PaymentInfoEvent paymentInfo = null;

        if (paymentOrError.isLeft()) {
            String error = StringUtils.substring(paymentOrError.getLeft().getMessage() + paymentOrError.getLeft().getBusEventType(), 0, 100);
            log.info("Could not process a payment for " + paymentAttempt + " error was " + error);

            scheduleRetry(paymentAttempt, error);
            postPaymentEvent(paymentOrError.getLeft(), account.getId());
        }
        else {
            paymentInfo = paymentOrError.getRight();
            paymentDao.savePaymentInfo(paymentInfo, context);

            final String paymentMethodId = paymentInfo.getPaymentMethodId();
            log.debug("Fetching payment method info for payment method id " + ((paymentMethodId == null) ? "null" : paymentMethodId));
            Either<PaymentErrorEvent, PaymentMethodInfo> paymentMethodInfoOrError = plugin.getPaymentMethodInfo(paymentMethodId);

            if (paymentMethodInfoOrError.isRight()) {
                PaymentMethodInfo paymentMethodInfo = paymentMethodInfoOrError.getRight();

                if (paymentMethodInfo instanceof CreditCardPaymentMethodInfo) {
                    CreditCardPaymentMethodInfo ccPaymentMethod = (CreditCardPaymentMethodInfo)paymentMethodInfo;
                    paymentDao.updatePaymentInfo(ccPaymentMethod.getType(), paymentInfo.getPaymentId(), ccPaymentMethod.getCardType(), ccPaymentMethod.getCardCountry(), context);
                }
                else if (paymentMethodInfo instanceof PaypalPaymentMethodInfo) {
                    PaypalPaymentMethodInfo paypalPaymentMethodInfo = (PaypalPaymentMethodInfo)paymentMethodInfo;
                    paymentDao.updatePaymentInfo(paypalPaymentMethodInfo.getType(), paymentInfo.getPaymentId(), null, null, context);
                }
            } else {
                log.info(paymentMethodInfoOrError.getLeft().getMessage());
            }

            if (paymentInfo.getPaymentId() != null) {
                paymentDao.updatePaymentAttemptWithPaymentId(paymentAttempt.getPaymentAttemptId(), paymentInfo.getPaymentId(), context);
            }
            postPaymentEvent(paymentInfo, account.getId());
       }

        invoicePaymentApi.notifyOfPaymentAttempt(
                invoice.getId(),
                paymentInfo == null || paymentInfo.getStatus().equalsIgnoreCase("Error") ? null : paymentInfo.getAmount(),
                        //                                                                         paymentInfo.getRefundAmount(), TODO
                        paymentInfo == null || paymentInfo.getStatus().equalsIgnoreCase("Error") ? null : invoice.getCurrency(),
                                paymentAttempt.getPaymentAttemptId(),
                                paymentAttempt.getPaymentAttemptDate(),
                                context);

        return paymentOrError;
    }
    
    private void postPaymentEvent(BusEvent ev, UUID accountId) {
        if (ev == null) {
            return;
        }
        try {
            eventBus.post(ev);
        } catch (EventBusException e) {
            log.error("Failed to post Payment event event for account {} ", accountId, e);
        }
    }

    private void scheduleRetry(PaymentAttempt paymentAttempt, String error) {
        final List<Integer> retryDays = config.getPaymentRetryDays();

        int retryCount = 0;

        if (paymentAttempt.getRetryCount() != null) {
            retryCount = paymentAttempt.getRetryCount();
        }

        if (retryCount < retryDays.size()) {
            int retryInDays = 0;
            DateTime nextRetryDate = paymentAttempt.getPaymentAttemptDate();

            try {
                retryInDays = retryDays.get(retryCount);
                nextRetryDate = nextRetryDate.plusDays(retryInDays);
            }
            catch (NumberFormatException ex) {
                log.error("Could not get retry day for retry count {}", retryCount);
            }

            retryService.scheduleRetry(paymentAttempt, nextRetryDate);
        }
        else if (retryCount == retryDays.size()) {
            log.info("Last payment retry failed for {} ", paymentAttempt);
        }
        else {
            log.error("Cannot update payment retry information because retry count is invalid {} ", retryCount);
        }
    }

    @Override
    public String createPaymentProviderAccount(Account account, CallContext context) 
        throws PaymentApiException {
        final PaymentProviderPlugin plugin = getPaymentProviderPlugin((Account)null);
        Either<PaymentErrorEvent, String> result =  plugin.createPaymentProviderAccount(account);
        if (result.isLeft()) {
            throw new PaymentApiException(ErrorCode.PAYMENT_CREATE_PAYMENT_PROVIDER_ACCOUNT, account.getId(), result.getLeft().getMessage());
        }
        return result.getRight();
    }

    @Override
    public void updatePaymentProviderAccountContact(String externalKey, CallContext context) 
        throws PaymentApiException {
        try {
            Account account = accountUserApi.getAccountByKey(externalKey);
            final PaymentProviderPlugin plugin = getPaymentProviderPlugin(account);
            Either<PaymentErrorEvent, Void> result = plugin.updatePaymentProviderAccountExistingContact(account);
            if (result.isLeft()) {
                throw new PaymentApiException(ErrorCode.PAYMENT_UPD_PAYMENT_PROVIDER_ACCOUNT, account.getId(), result.getLeft().getMessage());
            }
            return;
        } catch (AccountApiException e) {
            throw new PaymentApiException(e);
        }
    }

    @Override
    public PaymentAttempt getPaymentAttemptForPaymentId(String id) {
        return paymentDao.getPaymentAttemptForPaymentId(id);
    }

    @Override
    public List<PaymentInfoEvent> createRefund(Account account, List<String> invoiceIds, CallContext context)
        throws PaymentApiException {
            
        final PaymentProviderPlugin plugin = getPaymentProviderPlugin(account);
        List<Either<PaymentErrorEvent, PaymentInfoEvent>> result = plugin.processRefund(account);
        List<PaymentInfoEvent> info =  new LinkedList<PaymentInfoEvent>();
        for (Either<PaymentErrorEvent, PaymentInfoEvent> cur : result) {
            if (cur.isLeft()) {
                throw new PaymentApiException(ErrorCode.PAYMENT_CREATE_REFUND, account.getId(), cur.getLeft().getMessage());
            }
            info.add(cur.getRight());
        }
        return info;
    }

    @Override
    public List<PaymentInfoEvent> getPaymentInfo(List<String> invoiceIds) {
        return paymentDao.getPaymentInfo(invoiceIds);
    }

    @Override
    public List<PaymentAttempt> getPaymentAttemptsForInvoiceId(String invoiceId) {
        return paymentDao.getPaymentAttemptsForInvoiceId(invoiceId);
    }

    @Override
    public PaymentInfoEvent getPaymentInfoForPaymentAttemptId(String paymentAttemptId) {
        return paymentDao.getPaymentInfoForPaymentAttemptId(paymentAttemptId);
    }

}
