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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.payment.dao.PaymentAttemptModelDao;
import com.ning.billing.payment.dao.PaymentModelDao;
import com.ning.billing.util.entity.EntityBase;

public class DefaultPayment extends EntityBase implements Payment {

    
    private final UUID accountId;
    private final UUID invoiceId;
    private final UUID paymentMethodId;
    private final BigDecimal amount;
    private final Currency currency;
    private final DateTime effectiveDate;
    private final Integer paymentNumber;
    private final PaymentStatus paymentStatus;    
    private final List<PaymentAttempt> attempts;

    
    private DefaultPayment(UUID id, UUID accountId, UUID invoiceId,
            UUID paymentMethodId, BigDecimal amount, Currency currency,
            DateTime effectiveDate, Integer paymentNumber,
            PaymentStatus paymentStatus, String paymentError, List<PaymentAttempt> attempts) {
        super(id);
        this.accountId = accountId;
        this.invoiceId = invoiceId;
        this.paymentMethodId = paymentMethodId;
        this.amount = amount;
        this.currency = currency;
        this.effectiveDate = effectiveDate;
        this.paymentNumber = paymentNumber;
        this.paymentStatus = paymentStatus;
        this.attempts = attempts;
    }
    
    public DefaultPayment(PaymentModelDao src, List<PaymentAttemptModelDao> attempts) {
        this(src.getId(),
             src.getAccountId(),
             src.getInvoiceId(),
             src.getPaymentMethodId(),
             src.getAmount(),
             src.getCurrency(),
             src.getEffectiveDate(),
             src.getPaymentNumber(),
             src.getPaymentStatus(),
             null,
             toPaymentAttempts(attempts));
    }
    

    @Override
    public Integer getPaymentNumber() {
        return paymentNumber;
    }
    
    @Override
    public UUID getAccountId() {
        return accountId;
    }

    @Override
    public UUID getInvoiceId() {
        return invoiceId;
    }

    @Override
    public UUID getPaymentMethodId() {
        return paymentMethodId;
    }

    @Override
    public BigDecimal getAmount() {
        return amount;
    }

    @Override
    public DateTime getEffectiveDate() {
        return effectiveDate;
    }

    @Override
    public Currency getCurrency() {
        return currency;
    }

    @Override
    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }


    @Override
    public List<PaymentAttempt> getAttempts() {
        return attempts;
    }
    
    private static List<PaymentAttempt> toPaymentAttempts(List<PaymentAttemptModelDao> attempts) {
        if (attempts == null || attempts.size() == 0) {
            return Collections.emptyList();
        }
        return new ArrayList<Payment.PaymentAttempt>(Collections2.transform(attempts, new Function<PaymentAttemptModelDao, PaymentAttempt>() {
            @Override
            public PaymentAttempt apply(final PaymentAttemptModelDao input) {
                return new PaymentAttempt() {
                    @Override
                    public PaymentStatus getPaymentStatus() {
                        return input.getPaymentStatus();
                    }
                    @Override
                    public String getErrorMsg() {
                        return input.getPaymentError();
                    }
                    @Override
                    public DateTime getEffectiveDate() {
                        return input.getEffectiveDate();
                    }
                    @Override
                    public UUID getId() {
                        return input.getId();
                    }
                };
            }
        }));
    }
}
