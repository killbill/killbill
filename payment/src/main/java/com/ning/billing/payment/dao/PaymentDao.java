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

import java.util.List;
import java.util.UUID;

import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.payment.api.PaymentApiException;
import com.ning.billing.payment.api.PaymentAttempt;
import com.ning.billing.payment.api.PaymentInfoEvent;
import com.ning.billing.payment.api.PaymentAttempt.PaymentAttemptStatus;
import com.ning.billing.util.callcontext.CallContext;

public interface PaymentDao {

    PaymentAttempt createPaymentAttempt(Invoice invoice, PaymentAttemptStatus status, CallContext context);
    PaymentAttempt createPaymentAttempt(PaymentAttempt paymentAttempt, PaymentAttemptStatus status, CallContext context);

    void savePaymentInfo(PaymentInfoEvent right, CallContext context);

    PaymentAttempt getPaymentAttemptForPaymentId(UUID paymentId);
    List<PaymentAttempt> getPaymentAttemptsForInvoiceIds(List<UUID> invoiceIds);

    void updatePaymentAttemptWithPaymentId(UUID paymentAttemptId, UUID paymentId, CallContext context);

    List<PaymentAttempt> getPaymentAttemptsForInvoiceId(UUID invoiceId);

    void updatePaymentInfo(String paymentMethodType, UUID paymentId, String cardType, String cardCountry, CallContext context);

    List<PaymentInfoEvent> getPaymentInfoList(List<UUID> invoiceIds);

    PaymentInfoEvent getLastPaymentInfo(List<UUID> invoiceIds);

    PaymentAttempt getPaymentAttemptById(UUID paymentAttemptId);
    PaymentInfoEvent getPaymentInfoForPaymentAttemptId(UUID paymentAttemptId);

    UUID getPaymentAttemptIdFromPaymentId(UUID paymentId) throws PaymentApiException;
}
