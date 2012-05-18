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

package com.ning.billing.payment.dao;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.ning.billing.payment.api.DefaultPaymentAttempt;
import com.ning.billing.util.callcontext.CallContext;
import org.apache.commons.collections.CollectionUtils;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.payment.api.DefaultPaymentInfoEvent;
import com.ning.billing.payment.api.PaymentAttempt;
import com.ning.billing.payment.api.PaymentInfoEvent;

public class MockPaymentDao implements PaymentDao {
    private final Map<UUID, PaymentInfoEvent> payments = new ConcurrentHashMap<UUID, PaymentInfoEvent>();
    private final Map<UUID, PaymentAttempt> paymentAttempts = new ConcurrentHashMap<UUID, PaymentAttempt>();

    @Override
    public PaymentAttempt getPaymentAttemptForPaymentId(UUID paymentId) {
        for (PaymentAttempt paymentAttempt : paymentAttempts.values()) {
            if (paymentId.equals(paymentAttempt.getPaymentId())) {
                return paymentAttempt;
            }
        }
        return null;
    }

    @Override
    public PaymentAttempt createPaymentAttempt(Invoice invoice, CallContext context) {
        PaymentAttempt updatedPaymentAttempt = new DefaultPaymentAttempt(UUID.randomUUID(), invoice.getId(), invoice.getAccountId(),
                invoice.getBalance(), invoice.getCurrency(), invoice.getInvoiceDate(),
                null, null, null, null, null);

        paymentAttempts.put(updatedPaymentAttempt.getId(), updatedPaymentAttempt);
        return updatedPaymentAttempt;
    }

    @Override
    public PaymentAttempt createPaymentAttempt(PaymentAttempt paymentAttempt, CallContext context) {
        PaymentAttempt updatedPaymentAttempt = new DefaultPaymentAttempt(paymentAttempt.getId(),
                paymentAttempt.getInvoiceId(),
                paymentAttempt.getAccountId(), paymentAttempt.getAmount(), paymentAttempt.getCurrency(),
                paymentAttempt.getInvoiceDate(), paymentAttempt.getPaymentAttemptDate(),
                paymentAttempt.getPaymentId(), paymentAttempt.getRetryCount(),
                paymentAttempt.getCreatedDate(), paymentAttempt.getUpdatedDate());

        paymentAttempts.put(updatedPaymentAttempt.getId(), updatedPaymentAttempt);
        return updatedPaymentAttempt;
    }

    @Override
    public void savePaymentInfo(PaymentInfoEvent paymentInfo, CallContext context) {
        payments.put(paymentInfo.getId(), paymentInfo);
    }

    @Override
    public void updatePaymentAttemptWithPaymentId(UUID paymentAttemptId, UUID paymentId, CallContext context) {
        PaymentAttempt existingPaymentAttempt = paymentAttempts.get(paymentAttemptId);

        if (existingPaymentAttempt != null) {
            paymentAttempts.put(existingPaymentAttempt.getId(),
                                ((DefaultPaymentAttempt) existingPaymentAttempt).cloner().setPaymentId(paymentId).build());
        }
    }

    @Override
    public List<PaymentAttempt> getPaymentAttemptsForInvoiceId(final String invoiceId) {
        Collection<PaymentAttempt> attempts =  Collections2.filter(paymentAttempts.values(), new Predicate<PaymentAttempt>() {
                @Override
                public boolean apply(PaymentAttempt input) {
                    return invoiceId.equals(input.getInvoiceId().toString());
                }
            });
        return new ArrayList<PaymentAttempt>(attempts);
    }

    @Override
    public void updatePaymentInfo(String paymentMethodType, UUID paymentId, String cardType, String cardCountry, CallContext context) {
        DefaultPaymentInfoEvent existingPayment = (DefaultPaymentInfoEvent) payments.get(paymentId);
        if (existingPayment != null) {
            PaymentInfoEvent payment = existingPayment.cloner()
                    .setPaymentMethod(paymentMethodType)
                    .setCardType(cardType)
                    .setCardCountry(cardCountry)
                    .build();
            payments.put(paymentId, payment);
        }
    }

    @Override
    public List<PaymentInfoEvent> getPaymentInfoList(List<String> invoiceIds) {
        List<PaymentAttempt> attempts = getPaymentAttemptsForInvoiceIds(invoiceIds);
        List<PaymentInfoEvent> paymentsToReturn = new ArrayList<PaymentInfoEvent>(invoiceIds.size());

        for (final PaymentAttempt attempt : attempts) {
            paymentsToReturn.addAll(Collections2.filter(payments.values(), new Predicate<PaymentInfoEvent>() {
                @Override
                public boolean apply(PaymentInfoEvent input) {
                    return input.getId().equals(attempt.getPaymentId());
                }
            }));
        }
        return paymentsToReturn;
    }

    @Override
    public PaymentInfoEvent getLastPaymentInfo(List<String> invoiceIds) {
        List<PaymentInfoEvent> payments = getPaymentInfoList(invoiceIds);
        PaymentInfoEvent lastPayment = null;

        for (PaymentInfoEvent payment : payments) {
            if ((lastPayment == null) || (payment.getEffectiveDate().isAfter(lastPayment.getEffectiveDate()))) {
                lastPayment = payment;
            }
        }

        return lastPayment;
    }

    @Override
    public List<PaymentAttempt> getPaymentAttemptsForInvoiceIds(List<String> invoiceIds) {
        List<PaymentAttempt> paymentAttempts = new ArrayList<PaymentAttempt>(invoiceIds.size());
        for (String invoiceId : invoiceIds) {
            List<PaymentAttempt> attempts = getPaymentAttemptsForInvoiceId(invoiceId);
            if (CollectionUtils.isNotEmpty(attempts)) {
                paymentAttempts.addAll(attempts);
            }
        }
        return paymentAttempts;
    }

    @Override
    public PaymentAttempt getPaymentAttemptById(UUID paymentAttemptId) {
        return paymentAttempts.get(paymentAttemptId);
    }

    @Override
    public PaymentInfoEvent getPaymentInfoForPaymentAttemptId(String paymentAttemptId) {
        // TODO Auto-generated method stub
        return null;
    }

}
