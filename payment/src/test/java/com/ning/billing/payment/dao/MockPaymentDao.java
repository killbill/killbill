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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.joda.time.DateTime;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.payment.api.PaymentAttempt;
import com.ning.billing.payment.api.PaymentInfo;
import org.joda.time.DateTimeZone;

public class MockPaymentDao implements PaymentDao {
    private final Map<String, PaymentInfo> payments = new ConcurrentHashMap<String, PaymentInfo>();
    private final Map<UUID, PaymentAttempt> paymentAttempts = new ConcurrentHashMap<UUID, PaymentAttempt>();

    @Override
    public PaymentAttempt getPaymentAttemptForPaymentId(String paymentId) {
        for (PaymentAttempt paymentAttempt : paymentAttempts.values()) {
            if (paymentId.equals(paymentAttempt.getPaymentId())) {
                return paymentAttempt;
            }
        }
        return null;
    }

    @Override
    public PaymentAttempt createPaymentAttempt(Invoice invoice) {
        PaymentAttempt paymentAttempt = new PaymentAttempt(UUID.randomUUID(), invoice);
        paymentAttempts.put(paymentAttempt.getPaymentAttemptId(), paymentAttempt);
        return paymentAttempt;
    }

    @Override
    public PaymentAttempt createPaymentAttempt(PaymentAttempt paymentAttempt) {
        paymentAttempts.put(paymentAttempt.getPaymentAttemptId(), paymentAttempt);
        return paymentAttempt;
    }

    @Override
    public void savePaymentInfo(PaymentInfo paymentInfo) {
        payments.put(paymentInfo.getPaymentId(), paymentInfo);
    }

    @Override
    public void updatePaymentAttemptWithPaymentId(UUID paymentAttemptId, String paymentId) {
        PaymentAttempt existingPaymentAttempt = paymentAttempts.get(paymentAttemptId);

        if (existingPaymentAttempt != null) {
            paymentAttempts.put(existingPaymentAttempt.getPaymentAttemptId(),
                                existingPaymentAttempt.cloner().setPaymentId(paymentId).build());
        }
    }

    @Override
    public PaymentAttempt getPaymentAttemptForInvoiceId(String invoiceId) {
        for (PaymentAttempt paymentAttempt : paymentAttempts.values()) {
            if (invoiceId.equals(paymentAttempt.getInvoiceId().toString())) {
                return paymentAttempt;
            }
        }
        return null;
    }

    @Override
    public void updatePaymentInfo(String paymentMethodType, String paymentId, String cardType, String cardCountry) {
        PaymentInfo existingPayment = payments.get(paymentId);
        if (existingPayment != null) {
            PaymentInfo payment = existingPayment.cloner()
                    .setPaymentMethod(paymentMethodType)
                    .setCardType(cardType)
                    .setCardCountry(cardCountry)
                    // TODO pass the clock?
                    .setUpdatedDate(new DateTime(DateTimeZone.UTC))
                    .build();
            payments.put(paymentId, payment);
        }
    }

    @Override
    public List<PaymentInfo> getPaymentInfo(List<String> invoiceIds) {
        List<PaymentAttempt> attempts = getPaymentAttemptsForInvoiceIds(invoiceIds);
        List<PaymentInfo> paymentsToReturn = new ArrayList<PaymentInfo>(invoiceIds.size());

        for (final PaymentAttempt attempt : attempts) {
            paymentsToReturn.addAll(Collections2.filter(payments.values(), new Predicate<PaymentInfo>() {
                @Override
                public boolean apply(PaymentInfo input) {
                    return input.getPaymentId().equals(attempt.getPaymentId());
                }
            }));
        }
        return paymentsToReturn;
    }

    @Override
    public List<PaymentAttempt> getPaymentAttemptsForInvoiceIds(List<String> invoiceIds) {
        List<PaymentAttempt> paymentAttempts = new ArrayList<PaymentAttempt>(invoiceIds.size());
        for (String invoiceId : invoiceIds) {
            PaymentAttempt attempt = getPaymentAttemptForInvoiceId(invoiceId);
            if (attempt != null) {
                paymentAttempts.add(attempt);
            }
        }
        return paymentAttempts;
    }

    @Override
    public void updatePaymentAttemptWithRetryInfo(UUID paymentAttemptId, int retryCount, DateTime nextRetryDate) {
        PaymentAttempt existingAttempt = paymentAttempts.get(paymentAttemptId);
        if (existingAttempt != null) {
            PaymentAttempt attempt = existingAttempt.cloner().setPaymentAttemptId(paymentAttemptId).setRetryCount(retryCount).setNextRetryDate(nextRetryDate).build();
            paymentAttempts.put(paymentAttemptId, attempt);
        }
    }

    @Override
    public PaymentAttempt getPaymentAttemptById(UUID paymentAttemptId) {
        return paymentAttempts.get(paymentAttemptId);
    }

    @Override
    public PaymentInfo getPaymentInfoForPaymentAttemptId(String paymentAttemptId) {
        // TODO Auto-generated method stub
        return null;
    }

}
