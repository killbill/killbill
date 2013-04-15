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

    AdjInvoiceItem(final UUID id, @Nullable final DateTime createdDate, final UUID invoiceId, final UUID accountId,
                   final LocalDate startDate, final LocalDate endDate, final BigDecimal amount, final Currency currency) {
        this(id, createdDate, invoiceId, accountId, startDate, endDate, amount, currency, null);
    }

    AdjInvoiceItem(final UUID id, @Nullable final DateTime createdDate, final UUID invoiceId, final UUID accountId,
                   final LocalDate startDate, final LocalDate endDate, final BigDecimal amount, final Currency currency, @Nullable final UUID reversingId) {
        super(id, createdDate, invoiceId, accountId, null, null, null, null, startDate, endDate, amount, currency, reversingId);
    }


    @Override
    public abstract InvoiceItemType getInvoiceItemType();
}
