/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.invoice.model;

import java.math.BigDecimal;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;

import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.InvoicePayment;
import org.killbill.billing.invoice.api.InvoicePaymentType;
import org.killbill.billing.invoice.dao.InvoicePaymentModelDao;
import org.killbill.billing.entity.EntityBase;
import org.killbill.billing.util.UUIDs;

public class DefaultInvoicePayment extends EntityBase implements InvoicePayment {

    private final UUID paymentId;
    private final InvoicePaymentType type;
    private final UUID invoiceId;
    private final DateTime paymentDate;
    private final BigDecimal amount;
    private final Currency currency;
    private final Currency processedCurrency;
    private final String paymentCookieId;
    private final UUID linkedInvoicePaymentId;
    private final Boolean isSuccess;

    public DefaultInvoicePayment(final InvoicePaymentType type, final UUID paymentId, final UUID invoiceId, final DateTime paymentDate,
                                 final BigDecimal amount, final Currency currency, final Currency processedCurrency, final String paymentCookieId, final Boolean isSuccess) {
        this(UUIDs.randomUUID(), null, type, paymentId, invoiceId, paymentDate, amount, currency, processedCurrency, paymentCookieId, null, isSuccess);
    }

    public DefaultInvoicePayment(final UUID id, final InvoicePaymentType type, final UUID paymentId, final UUID invoiceId, final DateTime paymentDate,
                                 @Nullable final BigDecimal amount, @Nullable final Currency currency, @Nullable final Currency processedCurrency, @Nullable final String paymentCookieId,
                                 @Nullable final UUID linkedInvoicePaymentId) {
        this(id, null, type, paymentId, invoiceId, paymentDate, amount, currency, processedCurrency, paymentCookieId, linkedInvoicePaymentId, true);
    }

    public DefaultInvoicePayment(final UUID id, @Nullable final DateTime createdDate, final InvoicePaymentType type, final UUID paymentId, final UUID invoiceId, final DateTime paymentDate,
                                 @Nullable final BigDecimal amount, @Nullable final Currency currency, @Nullable final Currency processedCurrency, @Nullable final String paymentCookieId,
                                 @Nullable final UUID linkedInvoicePaymentId, final Boolean isSuccess) {
        super(id, createdDate, createdDate);
        this.type = type;
        this.paymentId = paymentId;
        this.amount = amount;
        this.invoiceId = invoiceId;
        this.paymentDate = paymentDate;
        this.currency = currency;
        this.processedCurrency =processedCurrency;
        this.paymentCookieId = paymentCookieId;
        this.linkedInvoicePaymentId = linkedInvoicePaymentId;
        this.isSuccess = isSuccess;
    }

    public DefaultInvoicePayment(final InvoicePaymentModelDao invoicePaymentModelDao) {
        this(invoicePaymentModelDao.getId(), invoicePaymentModelDao.getCreatedDate(), invoicePaymentModelDao.getType(),
             invoicePaymentModelDao.getPaymentId(), invoicePaymentModelDao.getInvoiceId(), invoicePaymentModelDao.getPaymentDate(),
             invoicePaymentModelDao.getAmount(), invoicePaymentModelDao.getCurrency(), invoicePaymentModelDao.getProcessedCurrency(),
             invoicePaymentModelDao.getPaymentCookieId(),
             invoicePaymentModelDao.getLinkedInvoicePaymentId(),
             invoicePaymentModelDao.getSuccess());
    }

    @Override
    public InvoicePaymentType getType() {
        return type;
    }

    @Override
    public UUID getPaymentId() {
        return paymentId;
    }

    @Override
    public UUID getInvoiceId() {
        return invoiceId;
    }

    @Override
    public DateTime getPaymentDate() {
        return paymentDate;
    }

    @Override
    public BigDecimal getAmount() {
        return amount;
    }

    @Override
    public Currency getCurrency() {
        return currency;
    }

    @Override
    public UUID getLinkedInvoicePaymentId() {
        return linkedInvoicePaymentId;
    }

    @Override
    public String getPaymentCookieId() {
        return paymentCookieId;
    }

    @Override
    public Currency getProcessedCurrency() {
        return processedCurrency;
    }

    @Override
    public Boolean isSuccess() {
        return isSuccess;
    }

}
