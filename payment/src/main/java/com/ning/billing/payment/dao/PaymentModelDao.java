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

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.payment.api.PaymentStatus;
import com.ning.billing.util.entity.EntityBase;

public class PaymentModelDao extends EntityBase {

    public static final Integer INVALID_PAYMENT_NUMBER = new Integer(-13);

    private final UUID accountId;
    private final UUID invoiceId;
    private final UUID paymentMethodId;
    private final BigDecimal amount;
    private final Currency currency;
    private final DateTime effectiveDate;
    private final Integer paymentNumber;
    private final PaymentStatus paymentStatus;
    private final String extPaymentRefId;


    public PaymentModelDao(final UUID id, final UUID accountId, final UUID invoiceId, final UUID paymentMethodId,
                           final Integer paymentNumber, final BigDecimal amount, final Currency currency,
                           final PaymentStatus paymentStatus, final DateTime effectiveDate, final String extPaymentRefId) {
        super(id);
        this.accountId = accountId;
        this.invoiceId = invoiceId;
        this.paymentMethodId = paymentMethodId;
        this.paymentNumber = paymentNumber;
        this.amount = amount;
        this.currency = currency;
        this.paymentStatus = paymentStatus;
        this.effectiveDate = effectiveDate;
        this.extPaymentRefId = extPaymentRefId;
    }

    public PaymentModelDao(final UUID accountId, final UUID invoiceId, final UUID paymentMethodId,
                           final BigDecimal amount, final Currency currency, final DateTime effectiveDate) {
        this(UUID.randomUUID(), accountId, invoiceId, paymentMethodId, INVALID_PAYMENT_NUMBER, amount, currency, PaymentStatus.UNKNOWN, effectiveDate, null);
    }

    public PaymentModelDao(final PaymentModelDao src, final PaymentStatus newPaymentStatus) {
        this(src.getId(), src.getAccountId(), src.getInvoiceId(), src.getPaymentMethodId(), src.getPaymentNumber(), src.getAmount(), src.getCurrency(), newPaymentStatus, src.getEffectiveDate(), null);
    }

    public UUID getAccountId() {
        return accountId;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public UUID getPaymentMethodId() {
        return paymentMethodId;
    }

    public Integer getPaymentNumber() {
        return paymentNumber;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public Currency getCurrency() {
        return currency;
    }

    public DateTime getEffectiveDate() {
        return effectiveDate;
    }

    public String getExtPaymentRefId() {
        return extPaymentRefId;
    }
}
