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

package com.ning.billing.osgi.bundles.analytics.dao.factory;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoiceItemType;

public class AdjustedCBAInvoiceItem implements InvoiceItem {

    private final UUID invoiceId;
    private final UUID accountId;
    private final BigDecimal amount;
    private final Currency currency;

    public AdjustedCBAInvoiceItem(final UUID invoiceId, final UUID accountId, final BigDecimal amount, final Currency currency) {
        this.invoiceId = invoiceId;
        this.accountId = accountId;
        this.amount = amount;
        this.currency = currency;
    }

    @Override
    public InvoiceItemType getInvoiceItemType() {
        return InvoiceItemType.CBA_ADJ;
    }

    @Override
    public UUID getInvoiceId() {
        return invoiceId;
    }

    @Override
    public UUID getAccountId() {
        return accountId;
    }

    @Override
    public LocalDate getStartDate() {
        return null;
    }

    @Override
    public LocalDate getEndDate() {
        return null;
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
    public String getDescription() {
        return null;
    }

    @Override
    public UUID getBundleId() {
        return null;
    }

    @Override
    public UUID getSubscriptionId() {
        return null;
    }

    @Override
    public String getPlanName() {
        return null;
    }

    @Override
    public String getPhaseName() {
        return null;
    }

    @Override
    public BigDecimal getRate() {
        return null;
    }

    @Override
    public UUID getLinkedItemId() {
        return null;
    }

    @Override
    public boolean matches(final Object other) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UUID getId() {
        return null;
    }

    @Override
    public DateTime getCreatedDate() {
        return null;
    }

    @Override
    public DateTime getUpdatedDate() {
        return null;
    }
}
