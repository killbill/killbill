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

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.payment.api.PaymentStatus;
import com.ning.billing.util.entity.EntityBase;

public class PaymentAttemptModelDao extends EntityBase {

    private final UUID accountId;
    private final UUID invoiceId;
    private final UUID paymentId;
    private final PaymentStatus processingStatus;
    private final DateTime effectiveDate;
    private final String paymentError;
    private final BigDecimal requestedAmount;

    public PaymentAttemptModelDao(final UUID id, final UUID accountId, final UUID invoiceId,
                                  final UUID paymentId, final PaymentStatus processingStatus, final DateTime effectiveDate,
                                  final BigDecimal requestedAmount, final String paymentError) {
        super(id);
        this.accountId = accountId;
        this.invoiceId = invoiceId;
        this.paymentId = paymentId;
        this.processingStatus = processingStatus;
        this.effectiveDate = effectiveDate;
        this.requestedAmount = requestedAmount;
        this.paymentError = paymentError;
    }

    public PaymentAttemptModelDao(final UUID accountId, final UUID invoiceId, final UUID paymentId, final PaymentStatus paymentStatus, final DateTime effectiveDate, final BigDecimal requestedAmount) {
        this(UUID.randomUUID(), accountId, invoiceId, paymentId, paymentStatus, effectiveDate, requestedAmount, null);
    }

    public PaymentAttemptModelDao(final UUID accountId, final UUID invoiceId, final UUID paymentId, final DateTime effectiveDate, final BigDecimal requestedAmount) {
        this(UUID.randomUUID(), accountId, invoiceId, paymentId, PaymentStatus.UNKNOWN, effectiveDate, requestedAmount, null);
    }

    public PaymentAttemptModelDao(final PaymentAttemptModelDao src, final PaymentStatus newProcessingStatus, final String paymentError) {
        this(src.getId(), src.getAccountId(), src.getInvoiceId(), src.getPaymentId(), newProcessingStatus,
             src.getEffectiveDate(), src.getRequestedAmount(), paymentError);
    }

    public UUID getAccountId() {
        return accountId;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public PaymentStatus getPaymentStatus() {
        return processingStatus;
    }

    public DateTime getEffectiveDate() {
        return effectiveDate;
    }

    public String getPaymentError() {
        return paymentError;
    }

    public BigDecimal getRequestedAmount() {
        return requestedAmount;
    }
}
