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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ning.billing.payment.api.PaymentStatus;
import com.ning.billing.util.callcontext.CallContext;

public class MockPaymentDao implements PaymentDao {

    private final Map<UUID, PaymentModelDao> payments =  new HashMap<UUID, PaymentModelDao>();
    private final Map<UUID, PaymentAttemptModelDao> attempts =  new HashMap<UUID, PaymentAttemptModelDao>();
    
    @Override
    public PaymentModelDao insertPaymentWithAttempt(PaymentModelDao paymentInfo, PaymentAttemptModelDao attempt,
            CallContext context) {
        synchronized(this) {
            payments.put(paymentInfo.getId(), paymentInfo);
            attempts.put(attempt.getId(), attempt);
        }
        return paymentInfo;
    }

    @Override
    public PaymentAttemptModelDao insertNewAttemptForPayment(UUID paymentId,
            PaymentAttemptModelDao attempt, CallContext context) {
        synchronized(this) {
            attempts.put(attempt.getId(), attempt);
        }
        return attempt;
    }

    @Override
    public void updateStatusForPaymentWithAttempt(UUID paymentId,
            PaymentStatus paymentStatus, String paymentError, UUID attemptId,
            CallContext context) {
        synchronized(this) {
            PaymentModelDao entry = payments.remove(paymentId);
            if (entry != null) {
               payments.put(paymentId, new PaymentModelDao(entry, paymentStatus));
            }
            PaymentAttemptModelDao tmp = attempts.remove(attemptId);
            if (tmp != null) {
                attempts.put(attemptId, new PaymentAttemptModelDao(tmp, paymentStatus, paymentError));
            }
        }
    }
    
    @Override
    public void updateStatusForPayment(UUID paymentId,
            PaymentStatus paymentStatus, CallContext context) {
        synchronized(this) {
            PaymentModelDao entry = payments.remove(paymentId);
            if (entry != null) {
               payments.put(paymentId, new PaymentModelDao(entry, paymentStatus));
            }
        }
    }


    @Override
    public PaymentAttemptModelDao getPaymentAttempt(UUID attemptId) {
        return attempts.get(attemptId);
    }

    @Override
    public List<PaymentModelDao> getPaymentsForInvoice(UUID invoiceId) {
        List<PaymentModelDao> result = new ArrayList<PaymentModelDao>();
        synchronized(this) {
            for (PaymentModelDao cur :payments.values()) {
                if (cur.getInvoiceId().equals(invoiceId)) {
                    result.add(cur);
                }
            }
        }
        return result;
    }

    @Override
    public List<PaymentModelDao> getPaymentsForAccount(UUID accountId) {
        List<PaymentModelDao> result = new ArrayList<PaymentModelDao>();
        synchronized(this) {
            for (PaymentModelDao cur :payments.values()) {
                if (cur.getAccountId().equals(accountId)) {
                    result.add(cur);
                }
            }
        }
        return result;
    }

    @Override
    public PaymentModelDao getPayment(UUID paymentId) {
        return payments.get(paymentId);
    }

    @Override
    public List<PaymentAttemptModelDao> getAttemptsForPayment(UUID paymentId) {
        List<PaymentAttemptModelDao> result = new ArrayList<PaymentAttemptModelDao>();
        synchronized(this) {
            for (PaymentAttemptModelDao cur : attempts.values()) {
                if (cur.getPaymentId().equals(paymentId)) {
                    result.add(cur);
                }
            }
        }
        return result;
    }

    @Override
    public PaymentMethodModelDao insertPaymentMethod(
            PaymentMethodModelDao paymentMethod, CallContext context) {
        return null;
    }

    @Override
    public PaymentMethodModelDao getPaymentMethod(UUID paymentMethodId) {
        return null;
    }

    @Override
    public List<PaymentMethodModelDao> getPaymentMethods(UUID accountId) {
        return null;
    }

    @Override
    public void deletedPaymentMethod(UUID paymentMethodId) {

    }
}
