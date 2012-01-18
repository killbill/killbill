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

package com.ning.billing.invoice.model;

import java.math.BigDecimal;
import java.util.UUID;
import org.joda.time.DateTime;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.util.entity.EntityBase;

public class DefaultInvoicePayment extends EntityBase<InvoicePayment> implements InvoicePayment {
    private final UUID invoiceId;
    private final DateTime paymentDate;
    private final BigDecimal amount;
    private final Currency currency;

    public DefaultInvoicePayment(final UUID invoiceId, final DateTime paymentDate,
                                 final BigDecimal amount, final Currency currency) {
        this(UUID.randomUUID(), invoiceId, paymentDate, amount, currency);
    }

    public DefaultInvoicePayment(final UUID id, final UUID invoiceId, final DateTime paymentDate,
                                 final BigDecimal amount, final Currency currency) {
        super(id);
        this.amount = amount;
        this.invoiceId = invoiceId;
        this.paymentDate = paymentDate;
        this.currency = currency;
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
}
