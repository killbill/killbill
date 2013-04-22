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

    private final InvoiceItem cbaInvoiceItem;
    private final BigDecimal amount;
    private final UUID reparationItemId;

    public AdjustedCBAInvoiceItem(final InvoiceItem cbaInvoiceItem,
                                  final BigDecimal amount,
                                  final UUID reparationItemId) {
        this.cbaInvoiceItem = cbaInvoiceItem;
        this.amount = amount;
        this.reparationItemId = reparationItemId;
    }

    @Override
    public InvoiceItemType getInvoiceItemType() {
        return InvoiceItemType.CBA_ADJ;
    }

    @Override
    public UUID getInvoiceId() {
        return cbaInvoiceItem.getInvoiceId();
    }

    @Override
    public UUID getAccountId() {
        return cbaInvoiceItem.getAccountId();
    }

    @Override
    public LocalDate getStartDate() {
        return cbaInvoiceItem.getStartDate();
    }

    @Override
    public LocalDate getEndDate() {
        return cbaInvoiceItem.getStartDate();
    }

    @Override
    public BigDecimal getAmount() {
        return amount;
    }

    @Override
    public Currency getCurrency() {
        return cbaInvoiceItem.getCurrency();
    }

    @Override
    public String getDescription() {
        return cbaInvoiceItem.getDescription();
    }

    @Override
    public UUID getBundleId() {
        return cbaInvoiceItem.getBundleId();
    }

    @Override
    public UUID getSubscriptionId() {
        return cbaInvoiceItem.getSubscriptionId();
    }

    @Override
    public String getPlanName() {
        return cbaInvoiceItem.getPlanName();
    }

    @Override
    public String getPhaseName() {
        return cbaInvoiceItem.getPhaseName();
    }

    @Override
    public BigDecimal getRate() {
        return cbaInvoiceItem.getRate();
    }

    @Override
    public UUID getLinkedItemId() {
        return cbaInvoiceItem.getLinkedItemId();
    }

    @Override
    public boolean matches(final Object other) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UUID getId() {
        return cbaInvoiceItem.getId();
    }

    public UUID getSecondId() {
        return reparationItemId;
    }

    @Override
    public DateTime getCreatedDate() {
        return cbaInvoiceItem.getCreatedDate();
    }

    @Override
    public DateTime getUpdatedDate() {
        return cbaInvoiceItem.getUpdatedDate();
    }
}
