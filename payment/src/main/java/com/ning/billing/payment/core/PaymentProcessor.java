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

import static com.ning.billing.payment.glue.PaymentModule.PLUGIN_EXECUTOR_NAMED;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.inject.name.Named;
import com.ning.billing.ErrorCode;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountUserApi;
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
import com.ning.billing.payment.plugin.api.PaymentPluginApi;
import com.ning.billing.payment.provider.PaymentProviderPluginRegistry;
import com.ning.billing.payment.retry.FailedPaymentRetryService.FailedPaymentRetryServiceScheduler;
import com.ning.billing.payment.retry.PluginFailureRetryService.PluginFailureRetryServiceScheduler;
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
    private final FailedPaymentRetryServiceScheduler failedPaymentRetryService;
    private final PluginFailureRetryServiceScheduler pluginFailureRetryService;
    private final CallContextFactory factory;
    private final Clock clock;
    
    private final PluginDispatcher<Payment> paymentPluginDispatcher;
    private final PluginDispatcher<Void> voidPluginDispatcher;    
    
    private static final Logger log = LoggerFactory.getLogger(PaymentProcessor.class);

    @Inject
    public PaymentProcessor(final PaymentProviderPluginRegistry pluginRegistry,
            final AccountUserApi accountUserApi,
            final InvoicePaymentApi invoicePaymentApi,
            final FailedPaymentRetryServiceScheduler failedPaymentRetryService,
            final PluginFailureRetryServiceScheduler pluginFailureRetryService,
            final PaymentDao paymentDao,
            final Bus eventBus,
            final Clock clock,
            final GlobalLocker locker,
            @Named(PLUGIN_EXECUTOR_NAMED) final ExecutorService executor,            
            final CallContextFactory factory) {
        super(pluginRegistry, accountUserApi, eventBus, paymentDao, locker, executor);
        this.invoicePaymentApi = invoicePaymentApi;
        this.failedPaymentRetryService = failedPaymentRetryService;
        this.pluginFailureRetryService = pluginFailureRetryService;
        this.clock = clock;
        this.factory = factory;
        this.paymentPluginDispatcher = new PluginDispatcher<Payment>(executor);
        this.voidPluginDispatcher = new PluginDispatcher<Void>(executor);
    }
  
    public Payment getPayment(UUID paymentId) {
        return getPayments(Collections.singletonList(paymentDao.getPayment(paymentId))).get(0);        
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

        final PaymentPluginApi plugin = getPaymentProviderPlugin(account);

        try {
            return paymentPluginDispatcher.dispatchWithAccountLock(new CallableWithAccountLock<Payment>(locker,
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
        } catch (TimeoutException e) {
            if (isInstantPayment) {
                throw new PaymentApiException(ErrorCode.PAYMENT_PLUGIN_TIMEOUT, account.getId(), invoiceId);
            } else {
                log.warn(String.format("Payment from Account %s, Invoice %s timedout", account.getId(), invoiceId));
                // If we don't crash, plugin thread will complete (and set the correct status)
                // If we crash before plugin thread completes, we may end up with a UNKNOWN Payment
                // We would like to return an error so the Bus can retry but we are limited by Guava bug
                // swallowing exception
                return null;
            }
        }
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


    public void retryPluginFailure(final UUID paymentId) {
        retryFailedPaymentInternal(paymentId, PaymentStatus.PLUGIN_FAILURE, PaymentStatus.TIMEDOUT);
    }
    
    public void retryFailedPayment(final UUID paymentId) {
        retryFailedPaymentInternal(paymentId, PaymentStatus.PAYMENT_FAILURE);    
    }
    
    private void retryFailedPaymentInternal(final UUID paymentId, final PaymentStatus...expectedPaymentStates) {

        try {
            
            PaymentModelDao payment = paymentDao.getPayment(paymentId);
            if (payment == null) {
                log.error("Invalid retry for non existnt paymentId {}", paymentId);
                return;
            }

            final Account account = accountUserApi.getAccountById(payment.getAccountId());
            final PaymentPluginApi plugin = getPaymentProviderPlugin(account);
            final CallContext context = factory.createCallContext("PaymentRetry", CallOrigin.INTERNAL, UserType.SYSTEM);
            
            voidPluginDispatcher.dispatchWithAccountLock(new CallableWithAccountLock<Void>(locker,
                    account.getExternalKey(),
                    new WithAccountLockCallback<Void>() {

                @Override
                public Void doOperation() throws PaymentApiException {

                    // Fetch gain with account lock this time
                    PaymentModelDao payment = paymentDao.getPayment(paymentId);
                    boolean foundExpectedState = false;
                    for (PaymentStatus cur : expectedPaymentStates) {
                        if (payment.getPaymentStatus() == cur) {
                            foundExpectedState = true;
                            break;
                        }
                    }
                    if (!foundExpectedState) {
                        log.info("Aborted retry for payment {} because it is {} state", paymentId, payment.getPaymentStatus());
                        return null;
                    }

                    final Invoice invoice = invoicePaymentApi.getInvoice(payment.getInvoiceId());
                    if (invoice.isMigrationInvoice()) {
                        return null;
                    }
                    if (invoice.getBalance().compareTo(BigDecimal.ZERO) <= 0 ) {
                        log.info("Aborted retry for payment {} because invoice has been paid", paymentId);
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
        } catch (TimeoutException e) {
            log.warn(String.format("Retry for payment %s timedout", paymentId));
            // STEPH we should throw some exception so NotificationQ does not clear status and retries us
        }
    }

    private Payment processNewPaymentWithAccountLocked(PaymentPluginApi plugin, Account account, Invoice invoice,
            BigDecimal requestedAmount, boolean isInstantPayment, CallContext context) throws PaymentApiException {
        
        final boolean scheduleRetryForPayment = !isInstantPayment;
        PaymentModelDao payment = new PaymentModelDao(account.getId(), invoice.getId(), requestedAmount.setScale(2, RoundingMode.HALF_EVEN), invoice.getCurrency(), invoice.getTargetDate());
        PaymentAttemptModelDao attempt = new PaymentAttemptModelDao(account.getId(), invoice.getId(), payment.getId(), clock.getUTCNow(), requestedAmount);
        
        PaymentModelDao savedPayment = paymentDao.insertPaymentWithAttempt(payment, attempt, scheduleRetryForPayment, context);
        return processPaymentWithAccountLocked(plugin, account, invoice, savedPayment, attempt, isInstantPayment, context);
    }
    
    private Payment processRetryPaymentWithAccountLocked(PaymentPluginApi plugin, Account account, Invoice invoice, PaymentModelDao payment,
            BigDecimal requestedAmount, CallContext context) throws PaymentApiException {
        final boolean scheduleRetryForPayment = true;
        PaymentAttemptModelDao attempt = new PaymentAttemptModelDao(account.getId(), invoice.getId(), payment.getId(), clock.getUTCNow(), requestedAmount);
        paymentDao.insertNewAttemptForPayment(payment.getId(), attempt, scheduleRetryForPayment, context);
        return processPaymentWithAccountLocked(plugin, account, invoice, payment, attempt, false, context);
    }

    
    private Payment processPaymentWithAccountLocked(PaymentPluginApi plugin, Account account, Invoice invoice,
            PaymentModelDao paymentInput, PaymentAttemptModelDao attemptInput, boolean isInstantPayment, CallContext context) throws PaymentApiException {
        
        BusEvent event = null;
        List<PaymentAttemptModelDao> allAttempts = null;
        PaymentAttemptModelDao lastAttempt = null;
        PaymentModelDao payment = null;
        PaymentStatus paymentStatus = PaymentStatus.UNKNOWN;
        try {

            PaymentInfoPlugin paymentPluginInfo = plugin.processPayment(account.getExternalKey(), paymentInput.getId(), attemptInput.getRequestedAmount());
            switch (paymentPluginInfo.getStatus()) {
            case PROCESSED:
                // Update Payment/PaymentAttempt status
                paymentStatus = PaymentStatus.SUCCESS;
                paymentDao.updateStatusForPaymentWithAttempt(paymentInput.getId(), paymentStatus, null, attemptInput.getId(), context);

                // Fetch latest objects
                allAttempts = paymentDao.getAttemptsForPayment(paymentInput.getId());
                lastAttempt = allAttempts.get(allAttempts.size() - 1);
                payment = paymentDao.getPayment(paymentInput.getId());
                
                invoicePaymentApi.notifyOfPaymentAttempt(invoice.getId(),
                        paymentStatus == PaymentStatus.SUCCESS ? payment.getAmount() : null,
                        paymentStatus == PaymentStatus.SUCCESS ? payment.getCurrency() : null,
                        lastAttempt.getId(),
                        lastAttempt.getEffectiveDate(),
                        context);
                
                // Create Bus event
                event = new DefaultPaymentInfoEvent(account.getId(),
                        invoice.getId(), payment.getId(), payment.getAmount(), payment.getPaymentNumber(), paymentStatus, context.getUserToken(), payment.getEffectiveDate());
                break;
                
            case ERROR:
                // Schedule if non instant payment and max attempt for retry not reached yet
                if (!isInstantPayment) {
                    allAttempts = paymentDao.getAttemptsForPayment(paymentInput.getId());
                    final int retryAttempt = getNumberAttemptsInState(paymentInput.getId(), allAttempts,
                            PaymentStatus.UNKNOWN, PaymentStatus.PAYMENT_FAILURE);
                    final boolean isScheduledForRetry = failedPaymentRetryService.scheduleRetry(paymentInput.getId(), retryAttempt);
                    paymentStatus = isScheduledForRetry ? PaymentStatus.PAYMENT_FAILURE : PaymentStatus.PAYMENT_FAILURE_ABORTED; 
                } else {
                    paymentStatus = PaymentStatus.PAYMENT_FAILURE_ABORTED;
                }

                paymentDao.updateStatusForPaymentWithAttempt(paymentInput.getId(), paymentStatus, paymentPluginInfo.getGatewayError(), attemptInput.getId(), context);

                log.info(String.format("Could not process payment for account %s, invoice %s, error = %s",
                        account.getId(), invoice.getId(), paymentPluginInfo.getGatewayError()));
                
                event = new DefaultPaymentErrorEvent(account.getId(), invoice.getId(), paymentInput.getId(), paymentPluginInfo.getGatewayError(), context.getUserToken());                        
                throw new PaymentApiException(ErrorCode.PAYMENT_CREATE_PAYMENT, account.getId(), paymentPluginInfo.getGatewayError());
                
            default:
                String formatError = String.format("Plugin return status %s for payment %s", paymentPluginInfo.getStatus(), paymentInput.getId());
                // This caught right below as a retryable Plugin failure
                throw new PaymentPluginApiException("", formatError);
            }
            
        } catch (PaymentPluginApiException e) {
            //
            // An exception occurred, we are left in an unknown state, we need to schedule a retry
            //
            paymentStatus = isInstantPayment ? PaymentStatus.PAYMENT_FAILURE_ABORTED : scheduleRetryOnPluginFailure(paymentInput.getId());  
            // STEPH message might need truncation to fit??
            
            paymentDao.updateStatusForPaymentWithAttempt(paymentInput.getId(), paymentStatus, e.getMessage(), attemptInput.getId(), context);

            throw new PaymentApiException(ErrorCode.PAYMENT_CREATE_PAYMENT, account.getId(), e.getMessage());
            
        } finally {
            if (event != null) {
                postPaymentEvent(event, account.getId());
            }
        }
        return new DefaultPayment(payment, allAttempts);
    }
    
    private PaymentStatus scheduleRetryOnPluginFailure(UUID paymentId) {
        List<PaymentAttemptModelDao> allAttempts = paymentDao.getAttemptsForPayment(paymentId);
        final int retryAttempt = getNumberAttemptsInState(paymentId, allAttempts, PaymentStatus.UNKNOWN, PaymentStatus.PLUGIN_FAILURE);
        final boolean isScheduledForRetry = pluginFailureRetryService.scheduleRetry(paymentId, retryAttempt);
        return isScheduledForRetry ? PaymentStatus.PLUGIN_FAILURE : PaymentStatus.PLUGIN_FAILURE_ABORTED; 
    }
    
    private int getNumberAttemptsInState(final UUID paymentId, final List<PaymentAttemptModelDao> allAttempts, final PaymentStatus...statuses) {
        if (allAttempts == null || allAttempts.size() == 0) {
            return 0;
        }
        return Collections2.filter(allAttempts, new Predicate<PaymentAttemptModelDao>() {
            @Override
            public boolean apply(PaymentAttemptModelDao input) {
                for (PaymentStatus cur : statuses) {
                    if (input.getPaymentStatus() == cur) {
                        return true;
                    }
                }
                return false;
            }
        }).size();
    }
}
