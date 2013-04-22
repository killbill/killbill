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

public class AdjustmentInvoiceItemForRepair implements InvoiceItem {

    private final InvoiceItem repairInvoiceItem;
    private final InvoiceItem reparationInvoiceItem;

    public AdjustmentInvoiceItemForRepair(final InvoiceItem repairInvoiceItem,
                                          final InvoiceItem reparationInvoiceItem) {
        this.repairInvoiceItem = repairInvoiceItem;
        this.reparationInvoiceItem = reparationInvoiceItem;
    }

    @Override
    public InvoiceItemType getInvoiceItemType() {
        return InvoiceItemType.ITEM_ADJ;
    }

    @Override
    public UUID getInvoiceId() {
        return repairInvoiceItem.getInvoiceId();
    }

    @Override
    public UUID getAccountId() {
        return repairInvoiceItem.getAccountId();
    }

    @Override
    public LocalDate getStartDate() {
        return repairInvoiceItem.getStartDate();
    }

    @Override
    public LocalDate getEndDate() {
        return repairInvoiceItem.getStartDate();
    }

    @Override
    public BigDecimal getAmount() {
        return reparationInvoiceItem.getAmount().add(repairInvoiceItem.getAmount());
    }

    @Override
    public Currency getCurrency() {
        return repairInvoiceItem.getCurrency();
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
        return repairInvoiceItem.getLinkedItemId();
    }

    @Override
    public boolean matches(final Object other) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UUID getId() {
        // We pretend to be the repair, the reparation item record id
        // will be available as secondId
        return repairInvoiceItem.getId();
    }

    public UUID getSecondId() {
        return reparationInvoiceItem.getId();
    }

    @Override
    public DateTime getCreatedDate() {
        return repairInvoiceItem.getCreatedDate();
    }

    @Override
    public DateTime getUpdatedDate() {
        return repairInvoiceItem.getUpdatedDate();
    }
}
