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

import java.util.List;
import java.util.UUID;

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
import com.ning.billing.payment.api.PaymentAttempt.PaymentAttemptStatus;
import com.ning.billing.payment.dao.PaymentDao;

import com.ning.billing.payment.plugin.api.PaymentInfoPlugin;
import com.ning.billing.payment.plugin.api.PaymentPluginApiException;
import com.ning.billing.payment.plugin.api.PaymentProviderPlugin;
import com.ning.billing.payment.provider.PaymentProviderPluginRegistry;

import com.ning.billing.payment.retry.FailedPaymentRetryService;
import com.ning.billing.util.bus.Bus;
import com.ning.billing.util.bus.BusEvent;
import com.ning.billing.util.bus.Bus.EventBusException;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.globallocker.GlobalLock;
import com.ning.billing.util.globallocker.GlobalLocker;
import com.ning.billing.util.globallocker.LockFailedException;
import com.ning.billing.util.globallocker.GlobalLocker.LockerService;

public class DefaultPaymentApi implements PaymentApi {
    
    private final static int NB_LOCK_TRY = 5;
    
    private final PaymentProviderPluginRegistry pluginRegistry;
    private final AccountUserApi accountUserApi;
    private final InvoicePaymentApi invoicePaymentApi;
    private final FailedPaymentRetryService retryService;
    private final PaymentDao paymentDao;
    private final PaymentConfig config;
    private final Bus eventBus;    

    private final GlobalLocker locker;
    
    private static final Logger log = LoggerFactory.getLogger(DefaultPaymentApi.class);

    @Inject
    public DefaultPaymentApi(final PaymentProviderPluginRegistry pluginRegistry,
            final AccountUserApi accountUserApi,
            final InvoicePaymentApi invoicePaymentApi,
            final FailedPaymentRetryService retryService,
            final PaymentDao paymentDao,
            final PaymentConfig config,
            final Bus eventBus,
            final GlobalLocker locker) {
        this.pluginRegistry = pluginRegistry;
        this.accountUserApi = accountUserApi;
        this.invoicePaymentApi = invoicePaymentApi;
        this.retryService = retryService;
        this.paymentDao = paymentDao;
        this.config = config;
        this.eventBus = eventBus;        
        this.locker = locker;
    }

    @Override
    public PaymentMethodInfo getPaymentMethod(final String accountKey, final String paymentMethodId) 
    throws PaymentApiException {
        try {
            final PaymentProviderPlugin plugin = getPaymentProviderPlugin(accountKey);
            return plugin.getPaymentMethodInfo(paymentMethodId);
        } catch (PaymentPluginApiException e) {
            throw new PaymentApiException(e, ErrorCode.PAYMENT_NO_SUCH_PAYMENT_METHOD, accountKey, paymentMethodId);            
        }
    }

    @Override
    public List<PaymentMethodInfo> getPaymentMethods(String accountKey)
    throws PaymentApiException {
        try {
            final PaymentProviderPlugin plugin = getPaymentProviderPlugin(accountKey);
            return plugin.getPaymentMethods(accountKey);
        } catch (PaymentPluginApiException e) {
            throw new PaymentApiException(e, ErrorCode.PAYMENT_NO_PAYMENT_METHODS, accountKey);
        }
    }

    @Override
    public void updatePaymentGateway(final String accountKey, final CallContext context) 
    throws PaymentApiException {

        new WithAccountLock<Void>().processAccountWithLock(locker, accountKey, new WithAccountLockCallback<Void>() {
            @Override
            public Void doOperation() throws PaymentApiException {

                try {
                    final PaymentProviderPlugin plugin = getPaymentProviderPlugin(accountKey);
                    plugin.updatePaymentGateway(accountKey);
                    return null;
                } catch (PaymentPluginApiException e) {
                    throw new PaymentApiException(e, ErrorCode.PAYMENT_UPD_GATEWAY_FAILED, accountKey, e.getMessage());
                }
            }
        });
    }

    @Override
    public PaymentProviderAccount getPaymentProviderAccount(String accountKey)
    throws PaymentApiException {
        try {
            final PaymentProviderPlugin plugin = getPaymentProviderPlugin(accountKey);
            return plugin.getPaymentProviderAccount(accountKey);
        } catch (PaymentPluginApiException e) {
            throw new PaymentApiException(e, ErrorCode.PAYMENT_GET_PAYMENT_PROVIDER, accountKey, e.getMessage());
        }
    }

    @Override
    public String addPaymentMethod(final String accountKey, final PaymentMethodInfo paymentMethod, final CallContext context) 
    throws PaymentApiException {
        
        return new WithAccountLock<String>().processAccountWithLock(locker, accountKey, new WithAccountLockCallback<String>() {

            @Override
            public String doOperation() throws PaymentApiException {
                try {
                final PaymentProviderPlugin plugin = getPaymentProviderPlugin(accountKey);
                return plugin.addPaymentMethod(accountKey, paymentMethod);
                } catch (PaymentPluginApiException e) {
                    throw new PaymentApiException(e, ErrorCode.PAYMENT_ADD_PAYMENT_METHOD, accountKey, e.getMessage());
                }
            }
        });
    }


    @Override
    public void deletePaymentMethod(final String accountKey, final String paymentMethodId, final CallContext context) 
    throws PaymentApiException {
        
        new WithAccountLock<Void>().processAccountWithLock(locker, accountKey, new WithAccountLockCallback<Void>() {

            @Override
            public Void doOperation() throws PaymentApiException {
                try {
                final PaymentProviderPlugin plugin = getPaymentProviderPlugin(accountKey);
                plugin.deletePaymentMethod(accountKey, paymentMethodId);
                return null;
                } catch (PaymentPluginApiException e) {
                    throw new PaymentApiException(e, ErrorCode.PAYMENT_DEL_PAYMENT_METHOD, accountKey, e.getMessage());
                }
            }
        });
    }

    @Override
    public PaymentMethodInfo updatePaymentMethod(final String accountKey, final PaymentMethodInfo paymentMethodInfo, final CallContext context) 
    throws PaymentApiException {

        return new WithAccountLock<PaymentMethodInfo>().processAccountWithLock(locker, accountKey, new WithAccountLockCallback<PaymentMethodInfo>() {

            @Override
            public PaymentMethodInfo doOperation() throws PaymentApiException {
                try {
                    final PaymentProviderPlugin plugin = getPaymentProviderPlugin(accountKey);
                    return plugin.updatePaymentMethod(accountKey, paymentMethodInfo);
                }  catch (PaymentPluginApiException e) {
                    throw new PaymentApiException(e, ErrorCode.PAYMENT_UPD_PAYMENT_METHOD, accountKey, e.getMessage());
                }
            }
        });
    }

    @Override
    public PaymentInfoEvent createPayment(final Account account, final UUID invoiceId, final CallContext context)
    throws PaymentApiException {

        return new WithAccountLock<PaymentInfoEvent>().processAccountWithLock(locker, account.getExternalKey(), new WithAccountLockCallback<PaymentInfoEvent>() {
            @Override
            public PaymentInfoEvent doOperation() throws PaymentApiException {
                    return createPaymentWithAccountLocked(account, invoiceId, context);
            }
        });
    }
    
    @Override
    public PaymentInfoEvent createPayment(final String accountKey, final UUID invoiceId, final CallContext context) 
        throws PaymentApiException {

        return new WithAccountLock<PaymentInfoEvent>().processAccountWithLock(locker, accountKey, new WithAccountLockCallback<PaymentInfoEvent>() {

            @Override
            public PaymentInfoEvent doOperation() throws PaymentApiException {
                try {
                    final Account account = accountUserApi.getAccountByKey(accountKey);
                    return createPaymentWithAccountLocked(account, invoiceId, context);
                } catch (AccountApiException e) {
                    throw new PaymentApiException(e);
                }
            }
        });
    }

    @Override
    public PaymentInfoEvent createPaymentForPaymentAttempt(final String accountKey, final UUID paymentAttemptId, final CallContext context) 
    throws PaymentApiException {

        return new WithAccountLock<PaymentInfoEvent>().processAccountWithLock(locker, accountKey, new WithAccountLockCallback<PaymentInfoEvent>() {

            @Override
            public PaymentInfoEvent doOperation() throws PaymentApiException {
                PaymentAttempt paymentAttempt = paymentDao.getPaymentAttemptById(paymentAttemptId);
                try {

                    Invoice invoice = paymentAttempt != null ? invoicePaymentApi.getInvoice(paymentAttempt.getInvoiceId()) : null;
                    Account account = paymentAttempt != null ? accountUserApi.getAccountById(paymentAttempt.getAccountId()) : null;
                    if (invoice == null || account == null) {
                        throw new PaymentApiException(ErrorCode.PAYMENT_CREATE_PAYMENT_FOR_ATTEMPT_BAD, paymentAttemptId);                            
                    }

                    if (invoice.getBalance().compareTo(BigDecimal.ZERO) <= 0 ) {
                        log.info("Received invoice for payment with outstanding amount of 0 {} ", invoice);
                        throw new PaymentApiException(ErrorCode.PAYMENT_CREATE_PAYMENT_FOR_ATTEMPT_WITH_NON_POSITIVE_INV, account.getId());
                    }

                    try {
                        PaymentAttempt newPaymentAttempt = new DefaultPaymentAttempt.Builder(paymentAttempt)
                        .setRetryCount(paymentAttempt.getRetryCount() + 1)
                        .setPaymentAttemptId(UUID.randomUUID())
                        .build();

                        paymentDao.createPaymentAttempt(newPaymentAttempt, PaymentAttemptStatus.IN_PROCESSING, context);
                        PaymentInfoEvent result = processPaymentWithAccountLocked(getPaymentProviderPlugin(account), account, invoice, newPaymentAttempt, context);

                        return result;
                    } catch (PaymentPluginApiException e) {
                        throw new PaymentApiException(ErrorCode.PAYMENT_CREATE_PAYMENT_FOR_ATTEMPT, account.getId(),  paymentAttemptId, e.getMessage());                            
                    }
                } catch (AccountApiException e) {
                    throw new PaymentApiException(e);
                }
            }
        });
    }

    private PaymentInfoEvent createPaymentWithAccountLocked(final Account account, final UUID invoiceId, final CallContext context) 
    throws PaymentApiException {

        try {
            final PaymentProviderPlugin plugin = getPaymentProviderPlugin(account);
            Invoice invoice = invoicePaymentApi.getInvoice(invoiceId);

            if (invoice.isMigrationInvoice()) {
                log.error("Received invoice for payment that is a migration invoice - don't know how to handle those yet: {}", invoice);
                return null;
            }

            PaymentInfoEvent result = null;
            if (invoice.getBalance().compareTo(BigDecimal.ZERO) > 0 ) {
                PaymentAttempt paymentAttempt = paymentDao.createPaymentAttempt(invoice, PaymentAttemptStatus.IN_PROCESSING, context);
                result = processPaymentWithAccountLocked(plugin, account, invoice, paymentAttempt, context);
            }

            return result;
        } catch (PaymentPluginApiException e) {
            throw new PaymentApiException(e, ErrorCode.PAYMENT_CREATE_PAYMENT, account.getId(), e.getMessage());
        }
    }


    private PaymentInfoEvent processPaymentWithAccountLocked(PaymentProviderPlugin plugin, Account account, Invoice invoice,
            PaymentAttempt paymentAttempt, CallContext context) throws PaymentPluginApiException {

        PaymentInfoEvent paymentInfo = null;
        BusEvent event = null;
        try {
            
            PaymentInfoPlugin paymentPluginInfo =  plugin.processInvoice(account, invoice);
            final String paymentMethodId = paymentPluginInfo.getPaymentMethodId();

            log.debug("Fetching payment method info for payment method id " + ((paymentMethodId == null) ? "null" : paymentMethodId));
            PaymentMethodInfo paymentMethodInfo = plugin.getPaymentMethodInfo(paymentMethodId);

            if (paymentMethodInfo instanceof CreditCardPaymentMethodInfo) {
                CreditCardPaymentMethodInfo ccPaymentMethod = (CreditCardPaymentMethodInfo)paymentMethodInfo;
                paymentInfo = new DefaultPaymentInfoEvent(paymentPluginInfo, ccPaymentMethod ,account.getId(), invoice.getId());

            } else if (paymentMethodInfo instanceof PaypalPaymentMethodInfo) {
                PaypalPaymentMethodInfo paypalPaymentMethodInfo = (PaypalPaymentMethodInfo)paymentMethodInfo;
                paymentInfo = new DefaultPaymentInfoEvent(paymentPluginInfo, paypalPaymentMethodInfo ,account.getId(), invoice.getId());
            } else {
                paymentInfo = new DefaultPaymentInfoEvent(paymentPluginInfo, account.getId(), invoice.getId());
            }
            paymentDao.insertPaymentInfoWithPaymentAttemptUpdate(paymentInfo, paymentAttempt.getId(), context);

 
            invoicePaymentApi.notifyOfPaymentAttempt(invoice.getId(),
                        paymentInfo == null || paymentInfo.getStatus().equalsIgnoreCase("Error") ? null : paymentInfo.getAmount(),
                          /*paymentInfo.getRefundAmount(), */
                          paymentInfo == null || paymentInfo.getStatus().equalsIgnoreCase("Error") ? null : invoice.getCurrency(),
                            paymentAttempt.getId(),
                              paymentAttempt.getPaymentAttemptDate(),
                                context);
            event = paymentInfo;
            return paymentInfo;
        } catch (PaymentPluginApiException e) {
            log.info("Could not process a payment for " + paymentAttempt + ", error was " + e.getMessage());
            scheduleRetry(paymentAttempt);
            event = new DefaultPaymentErrorEvent(null, e.getMessage(), account.getId(), invoice.getId(), context.getUserToken());                        
            throw e;
        } finally {
            postPaymentEvent(event, account.getId());
        }

    }
   

    private void scheduleRetry(PaymentAttempt paymentAttempt) {
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
        try {
            final PaymentProviderPlugin plugin = getPaymentProviderPlugin((Account)null);
            return plugin.createPaymentProviderAccount(account);
        } catch (PaymentPluginApiException e) {
            throw new PaymentApiException(ErrorCode.PAYMENT_CREATE_PAYMENT_PROVIDER_ACCOUNT, account.getId(), e.getMessage());
        }
    }

    @Override
    public void updatePaymentProviderAccountContact(String externalKey, CallContext context) 
        throws PaymentApiException {
        
        Account account = null;
        try {
            account = accountUserApi.getAccountByKey(externalKey);
            final PaymentProviderPlugin plugin = getPaymentProviderPlugin(account);
            plugin.updatePaymentProviderAccountExistingContact(account);
        } catch (AccountApiException e) {
            throw new PaymentApiException(e);
        } catch (PaymentPluginApiException e) {
            throw new PaymentApiException(ErrorCode.PAYMENT_UPD_PAYMENT_PROVIDER_ACCOUNT, account.getId(), e.getMessage());
        }
    }

    @Override
    public PaymentAttempt getPaymentAttemptForPaymentId(UUID id) {
        return paymentDao.getPaymentAttemptForPaymentId(id);
    }

    @Override
    public PaymentInfoEvent createRefund(Account account, UUID paymentId, CallContext context)
        throws PaymentApiException {

        /*
        try {
            
        final PaymentProviderPlugin plugin = getPaymentProviderPlugin(account);
        List<PaymentInfoPlugin> result = plugin.processRefund(account);
        List<PaymentInfoEvent> info =  new LinkedList<PaymentInfoEvent>();
        int i = 0;
        for (PaymentInfoPlugin cur : result) {
            // STEPH
            //info.add(new DefaultPaymentInfoEvent(cur, account.getId(), invoiceIds.get(i)));
        }
        return info;
        } catch (PaymentPluginApiException e) {
            throw new PaymentApiException(ErrorCode.PAYMENT_CREATE_REFUND, account.getId(), e.getMessage());
        }
        */
        // STEPH
        return null;
    }

    @Override
    public List<PaymentInfoEvent> getPaymentInfo(List<UUID> invoiceIds) {
        return paymentDao.getPaymentInfoList(invoiceIds);
    }

    @Override
    public PaymentInfoEvent getLastPaymentInfo(List<UUID> invoiceIds) {
        return paymentDao.getLastPaymentInfo(invoiceIds);
    }

    @Override
    public List<PaymentAttempt> getPaymentAttemptsForInvoiceId(UUID invoiceId) {
        return paymentDao.getPaymentAttemptsForInvoiceId(invoiceId);
    }

    @Override
    public PaymentInfoEvent getPaymentInfoForPaymentAttemptId(UUID paymentAttemptId) {
        return paymentDao.getPaymentInfoForPaymentAttemptId(paymentAttemptId);
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



    public interface WithAccountLockCallback<T> {
        public T doOperation() throws PaymentApiException;
    }
    
    public static class WithAccountLock<T> {
        public T processAccountWithLock(final GlobalLocker locker, final String accountExternalKey, final WithAccountLockCallback<T> callback)
         throws PaymentApiException {
            GlobalLock lock = null;
            try {
                lock = locker.lockWithNumberOfTries(LockerService.PAYMENT, accountExternalKey, NB_LOCK_TRY);
                return callback.doOperation();
            } catch (LockFailedException e) {
                String format = String.format("Failed to lock account %s", accountExternalKey);
                log.error(String.format(format), e);
                throw new PaymentApiException(ErrorCode.PAYMENT_INTERNAL_ERROR, format);
            } finally {
                if (lock != null) {
                    lock.release();
                }        
            }
        }
    }
    
    
}
