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
package com.ning.billing.payment.core;

import static com.ning.billing.payment.glue.PaymentModule.PLUGIN_EXECUTOR;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.name.Named;
import com.ning.billing.ErrorCode;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.config.PaymentConfig;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoicePaymentApi;
import com.ning.billing.payment.api.DefaultPayment;
import com.ning.billing.payment.api.DefaultPaymentErrorEvent;
import com.ning.billing.payment.api.DefaultPaymentInfoEvent;
import com.ning.billing.payment.api.Payment;
import com.ning.billing.payment.api.PaymentApiException;
import com.ning.billing.payment.api.PaymentStatus;
import com.ning.billing.payment.dao.PaymentAttemptModelDao;
import com.ning.billing.payment.dao.PaymentDao;
import com.ning.billing.payment.dao.PaymentModelDao;
import com.ning.billing.payment.dispatcher.PluginDispatcher;
import com.ning.billing.payment.plugin.api.PaymentInfoPlugin;
import com.ning.billing.payment.plugin.api.PaymentPluginApiException;
import com.ning.billing.payment.plugin.api.PaymentProviderPlugin;
import com.ning.billing.payment.plugin.api.PaymentInfoPlugin.PaymentPluginStatus;
import com.ning.billing.payment.provider.PaymentProviderPluginRegistry;
import com.ning.billing.payment.retry.FailedPaymentRetryService.FailedPaymentRetryServiceScheduler;
import com.ning.billing.util.bus.Bus;
import com.ning.billing.util.bus.BusEvent;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallContextFactory;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.UserType;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.globallocker.GlobalLocker;

public class PaymentProcessor extends ProcessorBase {
    
    private final InvoicePaymentApi invoicePaymentApi;
    private final FailedPaymentRetryServiceScheduler retryService;
    private final PaymentConfig config;
    private final PaymentDao paymentDao;
    private final CallContextFactory factory;
    private final Clock clock;
    
    private final PluginDispatcher<Payment> paymentPluginDispatcher;
    private final PluginDispatcher<Void> voidPluginDispatcher;    
    
    private static final Logger log = LoggerFactory.getLogger(PaymentProcessor.class);

    @Inject
    public PaymentProcessor(final PaymentProviderPluginRegistry pluginRegistry,
            final AccountUserApi accountUserApi,
            final InvoicePaymentApi invoicePaymentApi,
            final FailedPaymentRetryServiceScheduler retryService,
            final PaymentDao paymentDao,
            final PaymentConfig config,
            final Bus eventBus,
            final Clock clock,
            final GlobalLocker locker,
            @Named(PLUGIN_EXECUTOR) final ExecutorService executor,            
            final CallContextFactory factory) {
        super(pluginRegistry, accountUserApi, eventBus, locker, executor);
        this.invoicePaymentApi = invoicePaymentApi;
        this.retryService = retryService;
        this.paymentDao = paymentDao;
        this.clock = clock;
        this.config = config;
        this.factory = factory;
        this.paymentPluginDispatcher = new PluginDispatcher<Payment>(executor);
        this.voidPluginDispatcher = new PluginDispatcher<Void>(executor);
    }
  

    public List<Payment> getInvoicePayments(UUID invoiceId) {
        return getPayments(paymentDao.getPaymentsForInvoice(invoiceId));        
    }

    
    public List<Payment> getAccountPayments(UUID accountId) {
        return getPayments(paymentDao.getPaymentsForAccount(accountId));
    }
    
    private List<Payment> getPayments(List<PaymentModelDao> payments) {
        List<Payment> result = new LinkedList<Payment>();
        for (PaymentModelDao cur : payments) {
            List<PaymentAttemptModelDao> attempts =  paymentDao.getAttemptsForPayment(cur.getId());
            Payment entry = new DefaultPayment(cur, attempts);
            result.add(entry);
        }
        return result;
    }

    public Payment createPayment(final String accountKey, final UUID invoiceId, final BigDecimal inputAmount, final CallContext context, final boolean isInstantPayment) 
    throws PaymentApiException {
        try {
            final Account account = accountUserApi.getAccountByKey(accountKey);
            return createPayment(account, invoiceId, inputAmount, context, isInstantPayment);
        } catch (AccountApiException e) {
            throw new PaymentApiException(e);
        }
    }

    public Payment createPayment(final Account account, final UUID invoiceId, final BigDecimal inputAmount , final CallContext context,  final boolean isInstantPayment)
    throws PaymentApiException {

        final PaymentProviderPlugin plugin = getPaymentProviderPlugin(account);

        return paymentPluginDispatcher.dispatchWithAccountLock(new WithAccountLockAndTimeout<Payment>(locker,
                account.getExternalKey(),
                new WithAccountLockCallback<Payment>() {

            @Override
            public Payment doOperation() throws PaymentApiException {
                final Invoice invoice = invoicePaymentApi.getInvoice(invoiceId);

                if (invoice.isMigrationInvoice()) {
                    log.error("Received invoice for payment that is a migration invoice - don't know how to handle those yet: {}", invoice);
                    return null;
                }

                BigDecimal requestedAmount = getAndValidatePaymentAmount(invoice, inputAmount, isInstantPayment);
                return processNewPaymentWithAccountLocked(plugin, account, invoice, requestedAmount, isInstantPayment, context);
            }
        }));
    }

    private BigDecimal getAndValidatePaymentAmount(final Invoice invoice,  final BigDecimal inputAmount, final boolean isInstantPayment)
    throws PaymentApiException {

        if (invoice.getBalance().compareTo(BigDecimal.ZERO) <= 0 ) {
            throw new PaymentApiException(ErrorCode.PAYMENT_NULL_INVOICE, invoice.getId());
        }
        if (isInstantPayment &&
                inputAmount != null &&
                invoice.getBalance().compareTo(inputAmount) < 0) {
                    throw new PaymentApiException(ErrorCode.PAYMENT_AMOUNT_DENIED,
                            invoice.getId(), inputAmount.floatValue(), invoice.getBalance().floatValue());   
        }
        return inputAmount != null ? inputAmount : invoice.getBalance();
    }


    public void retryFailedPayment(final UUID paymentId) {

        try {
            final PaymentModelDao payment = paymentDao.getPayment(paymentId);
            if (payment == null) {
                log.error("Invalid retry for non existnt paymentId {}", paymentId);
                return;
            }
            
            final Account account = accountUserApi.getAccountById(payment.getAccountId());
            final PaymentProviderPlugin plugin = getPaymentProviderPlugin(account);
            final CallContext context = factory.createCallContext("PaymentRetry", CallOrigin.INTERNAL, UserType.SYSTEM);
            
            voidPluginDispatcher.dispatchWithAccountLock(new WithAccountLockAndTimeout<Void>(locker,
                    account.getExternalKey(),
                    new WithAccountLockCallback<Void>() {

                @Override
                public Void doOperation() throws PaymentApiException {
                    final Invoice invoice = invoicePaymentApi.getInvoice(payment.getInvoiceId());
                    if (invoice.isMigrationInvoice()) {
                        return null;
                    }
                    if (invoice.getBalance().compareTo(BigDecimal.ZERO) <= 0 ) {
                        return null;
                    }
                    processRetryPaymentWithAccountLocked(plugin, account, invoice, payment, invoice.getBalance(), context);
                    return null;

                }
            }));

        } catch (AccountApiException e) {
            log.error(String.format("Failed to retry payment for paymentId %s", paymentId), e);
        } catch (PaymentApiException e) {
            log.info(String.format("Failed to retry payment for paymentId %s", paymentId));
        }
    }

   

    private Payment processNewPaymentWithAccountLocked(PaymentProviderPlugin plugin, Account account, Invoice invoice,
            BigDecimal requestedAmount, boolean isInstantPayment, CallContext context) throws PaymentApiException {
        
        PaymentModelDao payment = new PaymentModelDao(account.getId(), invoice.getId(), requestedAmount.setScale(2, RoundingMode.HALF_EVEN), invoice.getCurrency(), invoice.getTargetDate());
        PaymentAttemptModelDao attempt = new PaymentAttemptModelDao(account.getId(), invoice.getId(), payment.getId(), clock.getUTCNow(), requestedAmount);
            
        PaymentModelDao savedPayment = paymentDao.insertPaymentWithAttempt(payment, attempt, context);
        return processPaymentWithAccountLocked(plugin, account, invoice, savedPayment, attempt, isInstantPayment, context);

    }
    
    private Payment processRetryPaymentWithAccountLocked(PaymentProviderPlugin plugin, Account account, Invoice invoice, PaymentModelDao payment,
            BigDecimal requestedAmount, CallContext context) throws PaymentApiException {
        
        PaymentAttemptModelDao attempt = new PaymentAttemptModelDao(account.getId(), invoice.getId(), payment.getId(), clock.getUTCNow(), requestedAmount);
        paymentDao.insertNewAttemptForPayment(payment.getId(), attempt, context);
        return processPaymentWithAccountLocked(plugin, account, invoice, payment, attempt, false, context);
    }

    private Payment processPaymentWithAccountLocked(PaymentProviderPlugin plugin, Account account, Invoice invoice,
            PaymentModelDao paymentInput, PaymentAttemptModelDao attemptInput, boolean isInstantPayment, CallContext context) throws PaymentApiException {
        
        BusEvent event = null;
        List<PaymentAttemptModelDao> allAttempts = null;
        PaymentAttemptModelDao lastAttempt = null;
        PaymentModelDao payment = null;
        try {
            
            PaymentInfoPlugin paymentPluginInfo =  plugin.processPayment(account.getExternalKey(), paymentInput.getId(), attemptInput.getRequestedAmount());
            
            // STEPH check if plugin returns UNKNOWN (exception from plugin)
            // Does plugin throws or returns ERROR?
            PaymentStatus paymentStatus = paymentPluginInfo.getStatus() ==  PaymentPluginStatus.ERROR ? PaymentStatus.ERROR : PaymentStatus.SUCCESS;
            
            paymentDao.updateStatusForPaymentWithAttempt(paymentInput.getId(), paymentStatus, paymentPluginInfo.getError(), attemptInput.getId(), context);

            allAttempts = paymentDao.getAttemptsForPayment(paymentInput.getId());
            lastAttempt = allAttempts.get(allAttempts.size() - 1);
            payment = paymentDao.getPayment(paymentInput.getId());
            
            invoicePaymentApi.notifyOfPaymentAttempt(invoice.getId(),
                    paymentStatus == PaymentStatus.SUCCESS ? payment.getAmount() : null,
                    paymentStatus == PaymentStatus.SUCCESS ? payment.getCurrency() : null,
                    lastAttempt.getId(),
                    lastAttempt.getEffectiveDate(),
                    context);
            
            event = new DefaultPaymentInfoEvent(account.getId(),
                    invoice.getId(), payment.getId(), payment.getAmount(), payment.getPaymentNumber(), paymentStatus, context.getUserToken(), payment.getEffectiveDate());
      
        } catch (PaymentPluginApiException e) {

            final PaymentStatus errorStatus = isInstantPayment ? PaymentStatus.ABORTED : PaymentStatus.ERROR;
            
            paymentDao.updateStatusForPaymentWithAttempt(paymentInput.getId(), errorStatus, e.getMessage(), attemptInput.getId(), context);

            allAttempts = paymentDao.getAttemptsForPayment(paymentInput.getId());
            lastAttempt = allAttempts.get(allAttempts.size() - 1);

            log.info(String.format("Could not process payment for account %s, invoice %s, error = %s",
                    account.getId(), invoice.getId(), e.getMessage()));
            
            if (!isInstantPayment) {
                scheduleRetry(paymentInput.getId(), lastAttempt.getEffectiveDate(), allAttempts.size(), context);
            }
            
            event = new DefaultPaymentErrorEvent(account.getId(), invoice.getId(), paymentInput.getId(), e.getErrorMessage(), context.getUserToken());                        
            throw new PaymentApiException(e, ErrorCode.PAYMENT_CREATE_PAYMENT, account.getId(), e.getMessage());

        } finally {
            postPaymentEvent(event, account.getId());
        }
        return new DefaultPayment(payment, allAttempts);
    }

    private void scheduleRetry(final UUID paymentId, final DateTime lastAttemptDate, final int numberAttempts, final CallContext context) {

        final List<Integer> retryDays = config.getPaymentRetryDays();
        int retryCount = numberAttempts - 1;
        if (retryCount < retryDays.size()) {
            int retryInDays = 0;
            DateTime nextRetryDate = clock.getUTCNow();
            try {
                retryInDays = retryDays.get(retryCount);
                nextRetryDate = nextRetryDate.plusDays(retryInDays);
                retryService.scheduleRetry(paymentId, nextRetryDate);
            } catch (NumberFormatException ex) {
                log.error("Could not get retry day for retry count {}", retryCount);
            }
        } else if (retryCount == retryDays.size()) {
            log.info("Last payment retry failed for {} ", paymentId);
            paymentDao.updateStatusForPayment(paymentId, PaymentStatus.ABORTED, context);
        } else {
            log.error("Cannot update payment retry information because retry count is invalid {} ", retryCount);
        }
    }
}
