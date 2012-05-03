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

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.InvoiceItem;
import org.joda.time.DateTime;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.util.UUID;

public class CreditInvoiceItem extends InvoiceItemBase {
    public CreditInvoiceItem(UUID invoiceId, UUID accountId, DateTime date, BigDecimal amount, Currency currency) {
        this(UUID.randomUUID(), invoiceId, accountId, date, amount, currency, null, null);
    }

    public CreditInvoiceItem(UUID id, UUID invoiceId, UUID accountId, DateTime date, BigDecimal amount, Currency currency,
                             @Nullable String createdBy, @Nullable DateTime createdDate) {
        super(id, invoiceId, accountId, null, null, null, null, date, date, amount, currency, createdBy, createdDate);
    }

    @Override
    public InvoiceItem asReversingItem() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getDescription() {
        return "Credit";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CreditInvoiceItem that = (CreditInvoiceItem) o;

        if (accountId.compareTo(that.accountId) != 0) return false;
        if (amount.compareTo(that.amount) != 0) return false;
        if (currency != that.currency) return false;
        if (startDate.compareTo(that.startDate) != 0) return false;
        if (endDate.compareTo(that.endDate) != 0) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = accountId.hashCode();
        result = 31 * result + startDate.hashCode();
        result = 31 * result + endDate.hashCode();
        result = 31 * result + amount.hashCode();
        result = 31 * result + currency.hashCode();
        return result;
    }

    @Override
    public int compareTo(InvoiceItem item) {
        if (!(item instanceof CreditInvoiceItem)) {
            return 1;
        }

        CreditInvoiceItem that = (CreditInvoiceItem) item;
        return id.compareTo(that.getId());
    }
}
