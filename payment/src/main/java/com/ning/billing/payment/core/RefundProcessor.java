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

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.inject.name.Named;
import com.ning.billing.ErrorCode;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.invoice.api.InvoicePaymentApi;
import com.ning.billing.payment.api.DefaultRefund;
import com.ning.billing.payment.api.PaymentApiException;
import com.ning.billing.payment.api.PaymentStatus;
import com.ning.billing.payment.api.Refund;
import com.ning.billing.payment.core.ProcessorBase.WithAccountLock;
import com.ning.billing.payment.core.ProcessorBase.WithAccountLockCallback;
import com.ning.billing.payment.dao.PaymentAttemptModelDao;
import com.ning.billing.payment.dao.PaymentDao;
import com.ning.billing.payment.dao.RefundModelDao;
import com.ning.billing.payment.dao.RefundModelDao.RefundStatus;
import com.ning.billing.payment.plugin.api.PaymentPluginApi;
import com.ning.billing.payment.plugin.api.PaymentPluginApiException;
import com.ning.billing.payment.provider.PaymentProviderPluginRegistry;
import com.ning.billing.util.bus.Bus;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallContextFactory;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.UserType;
import com.ning.billing.util.globallocker.GlobalLocker;

import static com.ning.billing.payment.glue.PaymentModule.PLUGIN_EXECUTOR_NAMED;

public class RefundProcessor extends ProcessorBase {

    private final static Logger log = LoggerFactory.getLogger(RefundProcessor.class);

    private final InvoicePaymentApi invoicePaymentApi;
    private final CallContextFactory factory;

    @Inject
    public RefundProcessor(final PaymentProviderPluginRegistry pluginRegistry,
            final AccountUserApi accountUserApi,
            final InvoicePaymentApi invoicePaymentApi,
            final Bus eventBus,
            final CallContextFactory factory,
            final PaymentDao paymentDao,
            final GlobalLocker locker,
            @Named(PLUGIN_EXECUTOR_NAMED) final ExecutorService executor) {
        super(pluginRegistry, accountUserApi, eventBus, paymentDao, locker, executor);
        this.invoicePaymentApi = invoicePaymentApi;
        this.factory = factory;
    }


    public Refund createRefund(final Account account, final UUID paymentId, final BigDecimal refundAmount, final boolean isAdjusted, final CallContext context)
    throws PaymentApiException {

        return new WithAccountLock<Refund>().processAccountWithLock(locker, account.getExternalKey(), new WithAccountLockCallback<Refund>() {

            @Override
            public Refund doOperation() throws PaymentApiException {
                try {


                    final PaymentAttemptModelDao successfulAttempt = getPaymentAttempt(paymentId);
                    if (successfulAttempt == null) {
                        throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_SUCCESS_PAYMENT, paymentId);
                    }
                    if (successfulAttempt.getRequestedAmount().compareTo(refundAmount) < 0) {
                        throw new PaymentApiException(ErrorCode.PAYMENT_REFUND_AMOUNT_TOO_LARGE);
                    }

                    // Look for that refund entry and count any 'similar' refund left in CREATED state (same amount, same paymentId)
                    int foundPluginCompletedRefunds = 0;
                    RefundModelDao refundInfo = null;
                    List<RefundModelDao> existingRefunds = paymentDao.getRefundsForPayment(paymentId);
                    for (RefundModelDao cur : existingRefunds) {
                        if (cur.getAmount().compareTo(refundAmount) == 0) {
                            if (cur.getRefundStatus() == RefundStatus.CREATED) {
                                if (refundInfo == null) {
                                    refundInfo = cur;
                                }
                            } else {
                                foundPluginCompletedRefunds++;
                            }
                        }
                    }
                    if (refundInfo == null) {
                        refundInfo = new RefundModelDao(account.getId(), paymentId, refundAmount, account.getCurrency(), isAdjusted);
                        paymentDao.insertRefund(refundInfo, context);
                    }

                    final PaymentPluginApi plugin = getPaymentProviderPlugin(account);
                    int nbExistingRefunds = plugin.getNbRefundForPaymentAmount(account, paymentId, refundAmount);
                    if (nbExistingRefunds > foundPluginCompletedRefunds) {
                        log.info("Found existing plugin refund for paymentId {}, skip plugin", paymentId);
                    } else {
                        // If there is no such exitng refund we create it
                        plugin.processRefund(account, paymentId, refundAmount);
                    }
                    paymentDao.updateRefundStatus(refundInfo.getId(), RefundStatus.PLUGIN_COMPLETED, context);

                    invoicePaymentApi.createRefund(successfulAttempt.getId(), refundAmount, isAdjusted, refundInfo.getId(), context);

                    paymentDao.updateRefundStatus(refundInfo.getId(), RefundStatus.COMPLETED, context);

                    return new DefaultRefund(refundInfo.getId(), paymentId, refundInfo.getAmount(), account.getCurrency(), isAdjusted);
                } catch (PaymentPluginApiException e) {
                    throw new PaymentApiException(ErrorCode.PAYMENT_CREATE_REFUND, account.getId(), e.getMessage());
                } catch (InvoiceApiException e) {
                    throw new PaymentApiException(e);
                }
            }
        });
    }


    public Refund getRefund(final UUID refundId)
    throws PaymentApiException {
        RefundModelDao result = paymentDao.getRefund(refundId);
        if (result == null) {
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_REFUND, refundId);
        }
        List<RefundModelDao> filteredInput = filterUncompletedPluginRefund(Collections.singletonList(result));
        if (filteredInput.size() == 0) {
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_REFUND, refundId);
        }
        if (completePluginCompletedRefund(filteredInput)) {
            result = paymentDao.getRefund(refundId);
        }
        return new DefaultRefund(result.getId(), result.getPaymentId(), result.getAmount(), result.getCurrency(), result.isAdjsuted());
    }


    public List<Refund> getAccountRefunds(final Account account)
    throws PaymentApiException {
        List<RefundModelDao> result = paymentDao.getRefundsForAccount(account.getId());
        if (completePluginCompletedRefund(result)) {
            result = paymentDao.getRefundsForAccount(account.getId());
        }
        List<RefundModelDao> filteredInput = filterUncompletedPluginRefund(result);
        return toRefunds(filteredInput);
    }

    public List<Refund> getPaymentRefunds(final UUID paymentId)
    throws PaymentApiException {
        List<RefundModelDao> result = paymentDao.getRefundsForPayment(paymentId);
        if (completePluginCompletedRefund(result)) {
            result = paymentDao.getRefundsForPayment(paymentId);
        }
        List<RefundModelDao> filteredInput = filterUncompletedPluginRefund(result);
        return toRefunds(filteredInput);
    }

    public List<Refund> toRefunds(final List<RefundModelDao> in) {
        return new ArrayList<Refund>(Collections2.transform(in, new Function<RefundModelDao, Refund>() {
            @Override
            public Refund apply(RefundModelDao cur) {
                return new DefaultRefund(cur.getId(), cur.getPaymentId(), cur.getAmount(), cur.getCurrency(), cur.isAdjsuted());
            }
        }));
    }

    private List<RefundModelDao> filterUncompletedPluginRefund(final List<RefundModelDao> input) {
        return new ArrayList<RefundModelDao>(Collections2.filter(input, new Predicate<RefundModelDao>() {
            @Override
            public boolean apply(RefundModelDao in) {
                return in.getRefundStatus() != RefundStatus.CREATED;
            }
        }));
    }

    private boolean completePluginCompletedRefund(final List<RefundModelDao> refunds) throws PaymentApiException {

        boolean fixedTheWorld = false;
        try {
            final CallContext context = factory.createCallContext("RefundProcessor", CallOrigin.INTERNAL, UserType.SYSTEM);
            for (RefundModelDao cur : refunds) {
                if (cur.getRefundStatus() == RefundStatus.PLUGIN_COMPLETED) {
                    final PaymentAttemptModelDao successfulAttempt = getPaymentAttempt(cur.getPaymentId());
                    if (successfulAttempt != null) {
                        invoicePaymentApi.createRefund(successfulAttempt.getId(), cur.getAmount(), cur.isAdjsuted(), cur.getId(), context);
                        paymentDao.updateRefundStatus(cur.getId(), RefundStatus.COMPLETED, context);
                        fixedTheWorld = true;
                    }
                }
            }
        } catch (InvoiceApiException e) {
            throw new PaymentApiException(e);
        }
        return fixedTheWorld;
    }

    private PaymentAttemptModelDao getPaymentAttempt(final UUID paymentId) {
        List<PaymentAttemptModelDao> attempts = paymentDao.getAttemptsForPayment(paymentId);
        for (PaymentAttemptModelDao cur : attempts) {
            if (cur.getPaymentStatus() == PaymentStatus.SUCCESS) {
                return cur;
            }
        }
        return null;
    }

}
