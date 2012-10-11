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

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoiceItemType;

public abstract class AdjInvoiceItem extends InvoiceItemBase {

    public AdjInvoiceItem(final UUID id, @Nullable final DateTime createdDate, final UUID invoiceId, final UUID accountId,
                          final LocalDate startDate, final LocalDate endDate, final BigDecimal amount, final Currency currency) {
        this(id, createdDate, invoiceId, accountId, startDate, endDate, amount, currency, null);
    }

    public AdjInvoiceItem(final UUID id, @Nullable final DateTime createdDate, final UUID invoiceId, final UUID accountId,
                          final LocalDate startDate, final LocalDate endDate, final BigDecimal amount, final Currency currency, @Nullable final UUID reversingId) {
        super(id, createdDate, invoiceId, accountId, null, null, null, null, startDate, endDate, amount, currency, reversingId);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AdjInvoiceItem that = (AdjInvoiceItem) o;
        return this.compareTo(that) == 0;
    }

    @Override
    public int hashCode() {
        int result = accountId.hashCode();
        result = 31 * result + invoiceId.hashCode();
        result = 31 * result + startDate.hashCode();
        result = 31 * result + amount.hashCode();
        result = 31 * result + currency.hashCode();
        result = 31 * result + getInvoiceItemType().hashCode();
        result = 31 * result + getId().hashCode();
        return result;
    }

    @Override
    public int compareTo(final InvoiceItem item) {

        if (!(item instanceof AdjInvoiceItem)) {
            return 1;
        }

        final AdjInvoiceItem that = (AdjInvoiceItem) item;

        if (accountId.compareTo(that.accountId) != 0) {
            return accountId.compareTo(that.accountId);
        }
        if (invoiceId.compareTo(that.invoiceId) != 0) {
            return invoiceId.compareTo(that.invoiceId);
        }
        if (amount.compareTo(that.amount) != 0) {
            return amount.compareTo(that.amount);
        }
        if (startDate.compareTo(that.startDate) != 0) {
            return startDate.compareTo(that.startDate);
        }
        if (currency != that.currency) {
            return currency.ordinal() > that.currency.ordinal() ? 1 : -1;
        }
        return id.compareTo(that.getId());
    }

    @Override
    public abstract InvoiceItemType getInvoiceItemType();
}
