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
import com.ning.billing.util.entity.EntityBase;

public abstract class InvoiceItemBase extends EntityBase implements InvoiceItem {
    /* Common to all items */
    protected final UUID invoiceId;
    protected final UUID accountId;
    protected final LocalDate startDate;
    protected final LocalDate endDate;
    protected final BigDecimal amount;
    protected final Currency currency;

    /* Fixed and recurring specific */
    protected final UUID subscriptionId;
    protected final UUID bundleId;
    protected final String planName;
    protected final String phaseName;

    /* Recurring specific */
    protected final BigDecimal rate;

    /* RepairAdjInvoiceItem */
    protected final UUID linkedItemId;

    @Override
    public String toString() {
        return getInvoiceItemType() + ": [startDate=" + startDate + ", endDate="
               + endDate + ", amount=" + amount + ", currency=" + currency
               + ", invoiceId=" + invoiceId
               + ", subscriptionId=" + subscriptionId + ", planName="
               + planName + ", phaseName=" + phaseName + ", rate=" + rate
               + ", linkedItemId=" + linkedItemId + "]";
    }

    /*
     * CTOR without ID; called from generator when creating invoice item
     */
    // No rate and no reversing item
    public InvoiceItemBase(final UUID invoiceId, final UUID accountId, final UUID bundleId, final UUID subscriptionId, final String planName, final String phaseName,
                           final LocalDate startDate, final LocalDate endDate, final BigDecimal amount, final Currency currency) {
        this(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, amount, null, currency);

    }

    // With rate but no reversing item
    public InvoiceItemBase(final UUID invoiceId, final UUID accountId, final UUID bundleId, final UUID subscriptionId, final String planName, final String phaseName,
                           final LocalDate startDate, final LocalDate endDate, final BigDecimal amount, final BigDecimal rate, final Currency currency) {
        this(UUID.randomUUID(), invoiceId, accountId, bundleId, subscriptionId, planName, phaseName,
             startDate, endDate, amount, rate, currency, null);
    }

    // With  reversing item, no rate
    public InvoiceItemBase(final UUID invoiceId, final UUID accountId, final UUID bundleId, final UUID subscriptionId, final String planName, final String phaseName,
                           final LocalDate startDate, final LocalDate endDate, final BigDecimal amount, final Currency currency, final UUID reversedItemId) {
        this(UUID.randomUUID(), invoiceId, accountId, bundleId, subscriptionId, planName, phaseName,
             startDate, endDate, amount, null, currency, reversedItemId);
    }

    /*
    * CTORs with ID; called from DAO when rehydrating
    */
    // No rate and no reversing item
    public InvoiceItemBase(final UUID id, final UUID invoiceId, final UUID accountId, @Nullable final UUID bundleId,
                           @Nullable final UUID subscriptionId, @Nullable final String planName, @Nullable final String phaseName,
                           final LocalDate startDate, final LocalDate endDate, final BigDecimal amount, final Currency currency) {
        this(id, invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, amount, null, currency, null);
    }

    // With rate but no reversing item
    public InvoiceItemBase(final UUID id, final UUID invoiceId, final UUID accountId, @Nullable final UUID bundleId,
                           @Nullable final UUID subscriptionId, @Nullable final String planName, @Nullable final String phaseName,
                           final LocalDate startDate, final LocalDate endDate, final BigDecimal amount, final BigDecimal rate, final Currency currency) {
        this(id, invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, amount, rate, currency, null);
    }

    // With  reversing item, no rate
    public InvoiceItemBase(final UUID id, final UUID invoiceId, final UUID accountId, @Nullable final UUID bundleId,
                           @Nullable final UUID subscriptionId, @Nullable final String planName, @Nullable final String phaseName,
                           final LocalDate startDate, final LocalDate endDate, final BigDecimal amount, final Currency currency, final UUID reversedItemId) {
        this(id, invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, amount, null, currency, reversedItemId);
    }

    private InvoiceItemBase(final UUID id, final UUID invoiceId, final UUID accountId, @Nullable final UUID bundleId,
                            @Nullable final UUID subscriptionId, @Nullable final String planName, @Nullable final String phaseName,
                            final LocalDate startDate, final LocalDate endDate, final BigDecimal amount, final BigDecimal rate, final Currency currency,
                            final UUID reversedItemId) {
        super(id);
        this.invoiceId = invoiceId;
        this.accountId = accountId;
        this.subscriptionId = subscriptionId;
        this.bundleId = bundleId;
        this.planName = planName;
        this.phaseName = phaseName;
        this.startDate = startDate;
        this.endDate = endDate;
        this.amount = amount;
        this.currency = currency;
        this.rate = rate;
        this.linkedItemId = reversedItemId;
    }

    @Override
    public UUID getInvoiceId() {
        return invoiceId;
    }

    @Override
    public UUID getBundleId() {
        return bundleId;
    }

    @Override
    public UUID getAccountId() {
        return accountId;
    }

    @Override
    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    @Override
    public String getPlanName() {
        return planName;
    }

    @Override
    public String getPhaseName() {
        return phaseName;
    }

    @Override
    public BigDecimal getAmount() {
        return amount;
    }

    @Override
    public LocalDate getStartDate() {
        return startDate;
    }

    @Override
    public LocalDate getEndDate() {
        return endDate;
    }

    @Override
    public Currency getCurrency() {
        return currency;
    }

    @Override
    public BigDecimal getRate() {
        return rate;
    }

    @Override
    public UUID getLinkedItemId() {
        return linkedItemId;
    }

    @Override
    public abstract InvoiceItemType getInvoiceItemType();

    @Override
    public abstract String getDescription();

    @Override
    public abstract int compareTo(InvoiceItem invoiceItem);

}
