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
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.ErrorCode;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.util.config.PaymentConfig;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.payment.api.DefaultPayment;
import com.ning.billing.payment.api.DefaultPaymentErrorEvent;
import com.ning.billing.payment.api.DefaultPaymentInfoEvent;
import com.ning.billing.payment.api.Payment;
import com.ning.billing.payment.api.PaymentApiException;
import com.ning.billing.payment.api.PaymentStatus;
import com.ning.billing.payment.dao.PaymentAttemptModelDao;
import com.ning.billing.payment.dao.PaymentDao;
import com.ning.billing.payment.dao.PaymentModelDao;
import com.ning.billing.payment.dao.RefundModelDao;
import com.ning.billing.payment.dispatcher.PluginDispatcher;
import com.ning.billing.payment.plugin.api.PaymentInfoPlugin;
import com.ning.billing.payment.plugin.api.PaymentPluginApi;
import com.ning.billing.payment.plugin.api.PaymentPluginApiException;
import com.ning.billing.payment.provider.PaymentProviderPluginRegistry;
import com.ning.billing.payment.retry.AutoPayRetryService.AutoPayRetryServiceScheduler;
import com.ning.billing.payment.retry.FailedPaymentRetryService.FailedPaymentRetryServiceScheduler;
import com.ning.billing.payment.retry.PluginFailureRetryService.PluginFailureRetryServiceScheduler;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.events.BusInternalEvent;
import com.ning.billing.util.events.PaymentErrorInternalEvent;
import com.ning.billing.util.globallocker.GlobalLocker;
import com.ning.billing.util.svcapi.account.AccountInternalApi;
import com.ning.billing.util.svcapi.invoice.InvoiceInternalApi;
import com.ning.billing.util.svcapi.tag.TagInternalApi;
import com.ning.billing.util.svcsapi.bus.InternalBus;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.inject.name.Named;

public class PaymentProcessor extends ProcessorBase {

    private final PaymentMethodProcessor paymentMethodProcessor;
    private final InvoiceInternalApi invoiceApi;
    private final FailedPaymentRetryServiceScheduler failedPaymentRetryService;
    private final PluginFailureRetryServiceScheduler pluginFailureRetryService;
    private final AutoPayRetryServiceScheduler autoPayoffRetryService;

    private final Clock clock;

    private final PaymentConfig paymentConfig;

    private final PluginDispatcher<Payment> paymentPluginDispatcher;
    private final PluginDispatcher<Void> voidPluginDispatcher;

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessor.class);

    @Inject
    public PaymentProcessor(final PaymentProviderPluginRegistry pluginRegistry,
                            final PaymentMethodProcessor paymentMethodProcessor,
                            final AccountInternalApi accountUserApi,
                            final InvoiceInternalApi invoiceApi,
                            final TagInternalApi tagUserApi,
                            final FailedPaymentRetryServiceScheduler failedPaymentRetryService,
                            final PluginFailureRetryServiceScheduler pluginFailureRetryService,
                            final AutoPayRetryServiceScheduler autoPayoffRetryService,
                            final PaymentDao paymentDao,
                            final InternalBus eventBus,
                            final Clock clock,
                            final GlobalLocker locker,
                            final PaymentConfig paymentConfig,
                            @Named(PLUGIN_EXECUTOR_NAMED) final ExecutorService executor) {
        super(pluginRegistry, accountUserApi, eventBus, paymentDao, tagUserApi, locker, executor);
        this.paymentMethodProcessor = paymentMethodProcessor;
        this.invoiceApi = invoiceApi;
        this.failedPaymentRetryService = failedPaymentRetryService;
        this.pluginFailureRetryService = pluginFailureRetryService;
        this.autoPayoffRetryService = autoPayoffRetryService;
        this.clock = clock;
        this.paymentConfig = paymentConfig;
        this.paymentPluginDispatcher = new PluginDispatcher<Payment>(executor);
        this.voidPluginDispatcher = new PluginDispatcher<Void>(executor);
    }

    public Payment getPayment(final UUID paymentId, final InternalTenantContext context) {
        final PaymentModelDao model = paymentDao.getPayment(paymentId, context);
        if (model == null) {
            return null;
        }
        return getPayments(Collections.singletonList(model), context).get(0);
    }


    public List<Payment> getInvoicePayments(final UUID invoiceId, final InternalTenantContext context) {
        return getPayments(paymentDao.getPaymentsForInvoice(invoiceId, context), context);
    }


    public List<Payment> getAccountPayments(final UUID accountId, final InternalTenantContext context) {
        return getPayments(paymentDao.getPaymentsForAccount(accountId, context), context);
    }

    private List<Payment> getPayments(final List<PaymentModelDao> payments, final InternalTenantContext context) {
        if (payments == null) {
            return Collections.emptyList();
        }
        final List<Payment> result = new LinkedList<Payment>();
        for (final PaymentModelDao cur : payments) {
            final List<PaymentAttemptModelDao> attempts = paymentDao.getAttemptsForPayment(cur.getId(), context);
            final List<RefundModelDao> refunds = paymentDao.getRefundsForPayment(cur.getId(), context);
            final Payment entry = new DefaultPayment(cur, attempts, refunds);
            result.add(entry);
        }
        return result;
    }

    public void process_AUTO_PAY_OFF_removal(final Account account, final InternalCallContext context) throws PaymentApiException  {

        try {
            voidPluginDispatcher.dispatchWithAccountLock(new CallableWithAccountLock<Void>(locker,
                    account.getExternalKey(),
                    new WithAccountLockCallback<Void>() {

                @Override
                public Void doOperation() throws PaymentApiException {

                    final List<PaymentModelDao> payments = paymentDao.getPaymentsForAccount(account.getId(), context);
                    final Collection<PaymentModelDao> paymentsToBeCompleted = Collections2.filter(payments, new Predicate<PaymentModelDao>() {
                        @Override
                        public boolean apply(final PaymentModelDao in) {
                            // Payments left in AUTO_PAY_OFF or for which we did not retry enough
                            return (in.getPaymentStatus() == PaymentStatus.AUTO_PAY_OFF ||
                                    in.getPaymentStatus() == PaymentStatus.PAYMENT_FAILURE ||
                                    in.getPaymentStatus() == PaymentStatus.PLUGIN_FAILURE);
                        }
                    });
                    // Insert one retry event for each payment left in AUTO_PAY_OFF
                    for (PaymentModelDao cur : paymentsToBeCompleted) {
                        switch(cur.getPaymentStatus()) {
                        case AUTO_PAY_OFF:
                            autoPayoffRetryService.scheduleRetry(cur.getId(), clock.getUTCNow());
                            break;
                        case PAYMENT_FAILURE:
                            scheduleRetryOnPaymentFailure(cur.getId(), context);
                            break;
                        case PLUGIN_FAILURE:
                            scheduleRetryOnPluginFailure(cur.getId(), context);
                            break;
                        default:
                            // Impossible...
                            throw new RuntimeException("Unexpected case " + cur.getPaymentStatus());
                        }

                    }
                    return null;
                }
            }));
        } catch (TimeoutException e) {
            throw new PaymentApiException(ErrorCode.UNEXPECTED_ERROR, "Unexpected timeout for payment creation (AUTO_PAY_OFF)");
        }
    }

    public Payment createPayment(final Account account, final UUID invoiceId, @Nullable final BigDecimal inputAmount,
                                 final InternalCallContext context, final boolean isInstantPayment, final boolean isExternalPayment)
            throws PaymentApiException {
        // Use the special external payment plugin to handle external payments
        final PaymentPluginApi plugin;
        final UUID paymentMethodId;
        try {
            if (isExternalPayment) {
                plugin = paymentMethodProcessor.getExternalPaymentProviderPlugin(account, context);
                paymentMethodId = paymentMethodProcessor.getExternalPaymentMethod(account, context).getId();
            } else {
                plugin = getPaymentProviderPlugin(account, context);
                paymentMethodId = account.getPaymentMethodId();
            }
        } catch (PaymentApiException e) {
            // This event will be caught by overdue to refresh the overdue state, if needed.
            // Note that at this point, we don't know the exact invoice balance (see getAndValidatePaymentAmount() below).
            // This means that events will be posted for null and zero dollar invoices (e.g. trials).
            final PaymentErrorInternalEvent event = new DefaultPaymentErrorEvent(account.getId(), invoiceId, null,
                                                                         ErrorCode.PAYMENT_NO_DEFAULT_PAYMENT_METHOD.toString(), context.getUserToken(),
                                                                         context.getAccountRecordId(), context.getTenantRecordId());
            postPaymentEvent(event, account.getId(), context);
            throw e;
        }

        try {
            return paymentPluginDispatcher.dispatchWithAccountLock(new CallableWithAccountLock<Payment>(locker,
                    account.getExternalKey(),
                    new WithAccountLockCallback<Payment>() {

                @Override
                public Payment doOperation() throws PaymentApiException {


                    try {
                        final Invoice invoice = invoiceApi.getInvoiceById(invoiceId, context);

                        if (invoice.isMigrationInvoice()) {
                            log.error("Received invoice for payment that is a migration invoice - don't know how to handle those yet: {}", invoice);
                            return null;
                        }

                        final boolean isAccountAutoPayOff = isAccountAutoPayOff(account.getId(), context);
                        setUnsaneAccount_AUTO_PAY_OFFWithAccountLock(account.getId(), paymentMethodId, isAccountAutoPayOff, context, isInstantPayment);

                        final BigDecimal requestedAmount = getAndValidatePaymentAmount(invoice, inputAmount, isInstantPayment);
                        if (!isInstantPayment && isAccountAutoPayOff) {
                            return processNewPaymentForAutoPayOffWithAccountLocked(paymentMethodId, account, invoice, requestedAmount, context);
                        } else {
                            return processNewPaymentWithAccountLocked(paymentMethodId, plugin, account, invoice, requestedAmount, isInstantPayment, context);
                        }
                    } catch (InvoiceApiException e) {
                        throw new PaymentApiException(e);
                    }
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

    private void setUnsaneAccount_AUTO_PAY_OFFWithAccountLock(final UUID accountId, final UUID paymentMethodId, final boolean isAccountAutoPayOff,
                                                              final InternalCallContext context, final boolean isInstantPayment)
    throws PaymentApiException  {

        final PaymentModelDao lastPaymentForPaymentMethod = paymentDao.getLastPaymentForPaymentMethod(accountId, paymentMethodId, context);
        final boolean isLastPaymentForPaymentMethodBad = lastPaymentForPaymentMethod != null &&
        (lastPaymentForPaymentMethod.getPaymentStatus() == PaymentStatus.PLUGIN_FAILURE_ABORTED ||
                lastPaymentForPaymentMethod.getPaymentStatus() == PaymentStatus.UNKNOWN);


        if (isLastPaymentForPaymentMethodBad &&
                !isInstantPayment &&
                !isAccountAutoPayOff) {
            log.warn(String.format("Setting account %s into AUTO_PAY_OFF because of bad payment %s", accountId, lastPaymentForPaymentMethod.getId()));
            setAccountAutoPayOff(accountId, context);
        }
    }



    private BigDecimal getAndValidatePaymentAmount(final Invoice invoice, @Nullable final BigDecimal inputAmount, final boolean isInstantPayment)
    throws PaymentApiException {

        if (invoice.getBalance().compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentApiException(ErrorCode.PAYMENT_NULL_INVOICE, invoice.getId());
        }
        if (isInstantPayment &&
                inputAmount != null &&
                invoice.getBalance().compareTo(inputAmount) < 0) {
            throw new PaymentApiException(ErrorCode.PAYMENT_AMOUNT_DENIED,
                    invoice.getId(), inputAmount.floatValue(), invoice.getBalance().floatValue());
        }
        final BigDecimal result =  inputAmount != null ? inputAmount : invoice.getBalance();
        return result.setScale(2, RoundingMode.HALF_UP);
    }


    public void retryAutoPayOff(final UUID paymentId, final InternalCallContext context) {
        retryFailedPaymentInternal(paymentId, context, PaymentStatus.AUTO_PAY_OFF);
    }

    public void retryPluginFailure(final UUID paymentId, final InternalCallContext context) {
        retryFailedPaymentInternal(paymentId, context, PaymentStatus.PLUGIN_FAILURE);
    }

    public void retryFailedPayment(final UUID paymentId, final InternalCallContext context) {
        log.info("Retrying failed payment " + paymentId + " time = " + clock.getUTCNow());
        retryFailedPaymentInternal(paymentId, context, PaymentStatus.PAYMENT_FAILURE);
    }


    private void retryFailedPaymentInternal(final UUID paymentId, final InternalCallContext context, final PaymentStatus... expectedPaymentStates) {

        try {

            final PaymentModelDao payment = paymentDao.getPayment(paymentId, context);
            if (payment == null) {
                log.error("Invalid retry for non existnt paymentId {}", paymentId);
                return;
            }

            if (isAccountAutoPayOff(payment.getAccountId(), context)) {
                log.info(String.format("Skip retry payment %s in state %s because AUTO_PAY_OFF", payment.getId(), payment.getPaymentStatus()));
                return;
            }

            final Account account = accountInternalApi.getAccountById(payment.getAccountId(), context);
            final PaymentPluginApi plugin = getPaymentProviderPlugin(account, context);

            voidPluginDispatcher.dispatchWithAccountLock(new CallableWithAccountLock<Void>(locker,
                    account.getExternalKey(),
                    new WithAccountLockCallback<Void>() {

                @Override
                public Void doOperation() throws PaymentApiException {
                    try {
                        // Fetch again with account lock this time
                        final PaymentModelDao payment = paymentDao.getPayment(paymentId, context);
                        boolean foundExpectedState = false;
                        for (final PaymentStatus cur : expectedPaymentStates) {
                            if (payment.getPaymentStatus() == cur) {
                                foundExpectedState = true;
                                break;
                            }
                        }
                        if (!foundExpectedState) {
                            log.info("Aborted retry for payment {} because it is {} state", paymentId, payment.getPaymentStatus());
                            return null;
                        }

                        final Invoice invoice = invoiceApi.getInvoiceById(payment.getInvoiceId(), context);
                        if (invoice.isMigrationInvoice()) {
                            return null;
                        }
                        if (invoice.getBalance().compareTo(BigDecimal.ZERO) <= 0) {
                            log.info("Aborted retry for payment {} because invoice has been paid", paymentId);
                            return null;
                        }
                        processRetryPaymentWithAccountLocked(plugin, account, invoice, payment, invoice.getBalance(), context);
                        return null;
                    } catch (InvoiceApiException e) {
                        throw new PaymentApiException(e);
                    }
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

    private Payment processNewPaymentForAutoPayOffWithAccountLocked(final UUID paymentMethodId, final Account account, final Invoice invoice,
                                                                    final BigDecimal requestedAmount, final InternalCallContext context)
            throws PaymentApiException {
        final PaymentStatus paymentStatus = PaymentStatus.AUTO_PAY_OFF;

        final PaymentModelDao paymentInfo = new PaymentModelDao(account.getId(), invoice.getId(), paymentMethodId, requestedAmount, invoice.getCurrency(), clock.getUTCNow(), paymentStatus);
        final PaymentAttemptModelDao attempt = new PaymentAttemptModelDao(account.getId(), invoice.getId(), paymentInfo.getId(), paymentStatus, clock.getUTCNow(), requestedAmount);

        paymentDao.insertPaymentWithAttempt(paymentInfo, attempt, context);
        return new DefaultPayment(paymentInfo, Collections.singletonList(attempt), Collections.<RefundModelDao>emptyList());
    }

    private Payment processNewPaymentWithAccountLocked(final UUID paymentMethodId, final PaymentPluginApi plugin, final Account account, final Invoice invoice,
                                                       final BigDecimal requestedAmount, final boolean isInstantPayment, final InternalCallContext context) throws PaymentApiException {
        final PaymentModelDao payment = new PaymentModelDao(account.getId(), invoice.getId(), paymentMethodId, requestedAmount.setScale(2, RoundingMode.HALF_UP), invoice.getCurrency(), clock.getUTCNow());
        final PaymentAttemptModelDao attempt = new PaymentAttemptModelDao(account.getId(), invoice.getId(), payment.getId(), clock.getUTCNow(), requestedAmount);

        final PaymentModelDao savedPayment = paymentDao.insertPaymentWithAttempt(payment, attempt, context);
        return processPaymentWithAccountLocked(plugin, account, invoice, savedPayment, attempt, isInstantPayment, context);
    }

    private Payment processRetryPaymentWithAccountLocked(final PaymentPluginApi plugin, final Account account, final Invoice invoice, final PaymentModelDao payment,
            final BigDecimal requestedAmount, final InternalCallContext context) throws PaymentApiException {
        final PaymentAttemptModelDao attempt = new PaymentAttemptModelDao(account.getId(), invoice.getId(), payment.getId(), clock.getUTCNow(), requestedAmount);
        paymentDao.insertNewAttemptForPayment(payment.getId(), attempt, context);
        return processPaymentWithAccountLocked(plugin, account, invoice, payment, attempt, false, context);
    }


    private Payment processPaymentWithAccountLocked(final PaymentPluginApi plugin, final Account account, final Invoice invoice,
            final PaymentModelDao paymentInput, final PaymentAttemptModelDao attemptInput, final boolean isInstantPayment, final InternalCallContext context) throws PaymentApiException {


        List<PaymentAttemptModelDao> allAttempts = null;
        if (paymentConfig.isPaymentOff()) {
            paymentDao.updateStatusForPaymentWithAttempt(paymentInput.getId(), PaymentStatus.PAYMENT_SYSTEM_OFF, null, null, null, null, attemptInput.getId(), context);
            allAttempts = paymentDao.getAttemptsForPayment(paymentInput.getId(), context);
            return new DefaultPayment(paymentInput, allAttempts, Collections.<RefundModelDao>emptyList());
        }

        PaymentModelDao payment = null;
        BusInternalEvent event = null;
        PaymentStatus paymentStatus;
        try {

            final PaymentInfoPlugin paymentPluginInfo = plugin.processPayment(account.getExternalKey(), paymentInput.getId(), attemptInput.getRequestedAmount(), context.toCallContext());
            switch (paymentPluginInfo.getStatus()) {
            case PROCESSED:
                // Update Payment/PaymentAttempt status
                paymentStatus = PaymentStatus.SUCCESS;
                paymentDao.updateStatusForPaymentWithAttempt(paymentInput.getId(), paymentStatus, paymentPluginInfo.getGatewayErrorCode(), null, paymentPluginInfo.getExtFirstReferenceId(), paymentPluginInfo.getExtSecondReferenceId(), attemptInput.getId(), context);

                // Fetch latest objects
                allAttempts = paymentDao.getAttemptsForPayment(paymentInput.getId(), context);

                payment = paymentDao.getPayment(paymentInput.getId(), context);
                invoiceApi.notifyOfPayment(invoice.getId(),
                        payment.getAmount(),
                        paymentStatus == PaymentStatus.SUCCESS ? payment.getCurrency() : null,
                                payment.getId(),
                                payment.getEffectiveDate(),
                                context);

                // Create Bus event
                event = new DefaultPaymentInfoEvent(account.getId(),
                        invoice.getId(), payment.getId(), payment.getAmount(), payment.getPaymentNumber(), paymentStatus,
                        paymentPluginInfo.getExtFirstReferenceId(), paymentPluginInfo.getExtSecondReferenceId(), context.getUserToken(), payment.getEffectiveDate(),
                        context.getAccountRecordId(), context.getTenantRecordId());
                break;

            case ERROR:
                allAttempts = paymentDao.getAttemptsForPayment(paymentInput.getId(), context);
                // Schedule if non instant payment and max attempt for retry not reached yet
                if (!isInstantPayment) {
                    paymentStatus = scheduleRetryOnPaymentFailure(paymentInput.getId(), context);
                } else {
                    paymentStatus = PaymentStatus.PAYMENT_FAILURE_ABORTED;
                }

                paymentDao.updateStatusForPaymentWithAttempt(paymentInput.getId(), paymentStatus, paymentPluginInfo.getGatewayErrorCode(),  paymentPluginInfo.getGatewayError(),null, null, attemptInput.getId(), context);

                log.info(String.format("Could not process payment for account %s, invoice %s, error = %s",
                         account.getId(), invoice.getId(), paymentPluginInfo.getGatewayError()));

                event = new DefaultPaymentErrorEvent(account.getId(), invoice.getId(), paymentInput.getId(), paymentPluginInfo.getGatewayError(), context.getUserToken(),
                        context.getAccountRecordId(), context.getTenantRecordId());
                throw new PaymentApiException(ErrorCode.PAYMENT_CREATE_PAYMENT, account.getId(), paymentPluginInfo.getGatewayError());

            default:
                final String formatError = String.format("Plugin return status %s for payment %s", paymentPluginInfo.getStatus(), paymentInput.getId());
                // This caught right below as a retryable Plugin failure
                throw new PaymentPluginApiException("", formatError);
            }

        } catch (PaymentPluginApiException e) {
            //
            // An exception occurred, we are left in an unknown state, we need to schedule a retry
            //
            paymentStatus = isInstantPayment ? PaymentStatus.PAYMENT_FAILURE_ABORTED : scheduleRetryOnPluginFailure(paymentInput.getId(), context);
            // STEPH message might need truncation to fit??

            paymentDao.updateStatusForPaymentWithAttempt(paymentInput.getId(), paymentStatus, null, e.getMessage(), null, null, attemptInput.getId(), context);

            throw new PaymentApiException(ErrorCode.PAYMENT_CREATE_PAYMENT, account.getId(), e.toString());

        } catch (InvoiceApiException e) {
            throw new PaymentApiException(ErrorCode.INVOICE_NOT_FOUND, invoice.getId(), e.toString());
        } finally {
            if (event != null) {
                postPaymentEvent(event, account.getId(), context);
            }
        }
        return new DefaultPayment(payment, allAttempts, Collections.<RefundModelDao>emptyList());
    }

    private PaymentStatus scheduleRetryOnPluginFailure(final UUID paymentId, final InternalTenantContext context) {
        final List<PaymentAttemptModelDao> allAttempts = paymentDao.getAttemptsForPayment(paymentId, context);
        final int retryAttempt = getNumberAttemptsInState(paymentId, allAttempts, PaymentStatus.UNKNOWN, PaymentStatus.PLUGIN_FAILURE);
        final boolean isScheduledForRetry = pluginFailureRetryService.scheduleRetry(paymentId, retryAttempt);
        return isScheduledForRetry ? PaymentStatus.PLUGIN_FAILURE : PaymentStatus.PLUGIN_FAILURE_ABORTED;
    }

    private PaymentStatus scheduleRetryOnPaymentFailure(final UUID paymentId, final InternalTenantContext context) {
        final List<PaymentAttemptModelDao> allAttempts = paymentDao.getAttemptsForPayment(paymentId, context);
        final int retryAttempt = getNumberAttemptsInState(paymentId, allAttempts,
                PaymentStatus.UNKNOWN, PaymentStatus.PAYMENT_FAILURE);


        final boolean isScheduledForRetry = failedPaymentRetryService.scheduleRetry(paymentId, retryAttempt);

        log.debug("scheduleRetryOnPaymentFailure id = " + paymentId + ", retryAttempt = " + retryAttempt + ", retry :" + isScheduledForRetry);

        return isScheduledForRetry ? PaymentStatus.PAYMENT_FAILURE : PaymentStatus.PAYMENT_FAILURE_ABORTED;
    }

    private int getNumberAttemptsInState(final UUID paymentId, final List<PaymentAttemptModelDao> allAttempts, final PaymentStatus... statuses) {
        if (allAttempts == null || allAttempts.size() == 0) {
            return 0;
        }
        return Collections2.filter(allAttempts, new Predicate<PaymentAttemptModelDao>() {
            @Override
            public boolean apply(final PaymentAttemptModelDao input) {
                for (final PaymentStatus cur : statuses) {
                    if (input.getProcessingStatus() == cur) {
                        return true;
                    }
                }
                return false;
            }
        }).size();
    }
}
