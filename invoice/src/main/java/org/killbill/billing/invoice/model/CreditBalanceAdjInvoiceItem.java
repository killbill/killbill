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
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.util.UUIDs;

public class CreditBalanceAdjInvoiceItem extends AdjInvoiceItem {

    public CreditBalanceAdjInvoiceItem(final UUID invoiceId, final UUID accountId,
                                       final LocalDate date, final BigDecimal amount, final Currency currency) {
        this(UUIDs.randomUUID(), null, invoiceId, accountId, date, null, amount, currency);
    }

    public CreditBalanceAdjInvoiceItem(final UUID id, @Nullable final DateTime createdDate, final UUID invoiceId, final UUID accountId,
                                       final LocalDate date, final UUID linkedInvoiceItemId,
                                       final BigDecimal amount, final Currency currency) {
        this(id, createdDate, invoiceId, accountId, date, linkedInvoiceItemId, null, amount, currency);
    }

    public CreditBalanceAdjInvoiceItem(final UUID id, @Nullable final DateTime createdDate, final UUID invoiceId, final UUID accountId,
                                       final LocalDate date, final UUID linkedInvoiceItemId,
                                       @Nullable final String description, final BigDecimal amount, final Currency currency) {
        super(id, createdDate, invoiceId, accountId, date, date, description, amount, currency, linkedInvoiceItemId, InvoiceItemType.CBA_ADJ);
    }

    @Override
    public String getDescription() {
        if (description != null) {
            return description;
        }

        final String secondDescription;
        if (getAmount().compareTo(BigDecimal.ZERO) >= 0) {
            secondDescription = "account credit";
        } else {
            secondDescription = "use of account credit";
        }
        return String.format("Adjustment (%s)", secondDescription);
    }
}
