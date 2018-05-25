/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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
import org.joda.time.LocalDate;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.util.UUIDs;

import com.google.common.base.MoreObjects;

public class ItemAdjInvoiceItem extends AdjInvoiceItem {

    public ItemAdjInvoiceItem(final InvoiceItem linkedInvoiceItem, final LocalDate effectiveDate,
                              final BigDecimal amount, final Currency currency) {
        this(UUIDs.randomUUID(), null, linkedInvoiceItem.getInvoiceId(), linkedInvoiceItem.getAccountId(), effectiveDate,
             linkedInvoiceItem.getDescription(), amount, currency, linkedInvoiceItem.getId(), null);
    }

    public ItemAdjInvoiceItem(final UUID id, @Nullable final DateTime createdDate, final UUID invoiceId, final UUID accountId, final LocalDate startDate,
                              @Nullable final String description, final BigDecimal amount, final Currency currency, final UUID linkedItemId, @Nullable final String itemDetails) {
        super(id, createdDate, invoiceId, accountId, startDate, startDate, description, amount, currency, linkedItemId, itemDetails, InvoiceItemType.ITEM_ADJ);
    }

    public ItemAdjInvoiceItem(final UUID id, @Nullable final DateTime createdDate, final UUID invoiceId, final UUID accountId, final LocalDate startDate,
                              @Nullable final String description, final BigDecimal amount, @Nullable final BigDecimal rate, final Currency currency, final UUID linkedItemId, @Nullable final Integer quantity, @Nullable final String itemDetails) {
        super(id, createdDate, invoiceId, accountId, startDate, startDate, description, amount, rate, currency, linkedItemId, quantity, itemDetails, InvoiceItemType.ITEM_ADJ);
    }

    @Override
    public String getDescription() {
        return MoreObjects.firstNonNull(description, "Invoice item adjustment");
    }
}
