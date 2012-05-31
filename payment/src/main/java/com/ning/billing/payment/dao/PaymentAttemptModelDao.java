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
    
    public PaymentAttemptModelDao(UUID id, UUID accountId, UUID invoiceId,
            UUID paymentId, PaymentStatus processingStatus, DateTime effectiveDate, String paymentError) {
        super(id);
        this.accountId = accountId;
        this.invoiceId = invoiceId;
        this.paymentId = paymentId;
        this.processingStatus = processingStatus;
        this.effectiveDate = effectiveDate;
        this.paymentError = paymentError;
    }
    
    public PaymentAttemptModelDao(UUID accountId, UUID invoiceId, UUID paymentId) {
        this(UUID.randomUUID(), accountId, invoiceId, paymentId, PaymentStatus.UNKNOWN, null, null);
    }

    public PaymentAttemptModelDao(PaymentAttemptModelDao src, PaymentStatus newProcessingStatus, String paymentError) {
        this(src.getId(), src.getAccountId(), src.getInvoiceId(), src.getPaymentId(), newProcessingStatus, src.getEffectiveDate(), paymentError);
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
}
