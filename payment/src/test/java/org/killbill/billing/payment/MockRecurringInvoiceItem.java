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

package org.killbill.billing.payment;

import java.math.BigDecimal;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.LocalDate;

import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.entity.EntityBase;

public class MockRecurringInvoiceItem extends EntityBase implements InvoiceItem {
    private final BigDecimal rate;
    private final UUID reversedItemId;
    protected final UUID invoiceId;
    protected final UUID accountId;
    protected final UUID subscriptionId;
    protected final UUID bundleId;
    protected final String planName;
    protected final String phaseName;
    protected final LocalDate startDate;
    protected final LocalDate endDate;
    protected final BigDecimal amount;
    protected final Currency currency;
    protected final String usageName;
    protected final Integer quantity;
    protected final String itemDetails;

    public MockRecurringInvoiceItem(final UUID invoiceId, final UUID accountId, final UUID bundleId, final UUID subscriptionId,
                                    final String planName, final String phaseName, final String usageName, final LocalDate startDate, final LocalDate endDate,
                                    final BigDecimal amount, final BigDecimal rate, final Currency currency) {
        this(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, usageName, startDate, endDate, amount, currency, rate, null);
    }

    public MockRecurringInvoiceItem(final UUID invoiceId, final UUID accountId, final UUID bundleId, final UUID subscriptionId, final String planName, final String phaseName, final String usageName,
                                    final LocalDate startDate, final LocalDate endDate, final BigDecimal amount, final Currency currency, final BigDecimal rate, final UUID reversedItemId) {
        this(UUID.randomUUID(), invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, usageName,
             startDate, endDate, amount, currency, rate, reversedItemId, null, null);
    }

    public MockRecurringInvoiceItem(final UUID id, final UUID invoiceId, final UUID accountId, @Nullable final UUID bundleId, @Nullable final UUID subscriptionId, final String planName, final String phaseName,
                                    final String usageName, final LocalDate startDate, final LocalDate endDate, final BigDecimal amount, final Currency currency,
                                    final BigDecimal rate, final UUID reversedItemId, final Integer quantity, final String itemDetails) {
        super(id);
        this.invoiceId = invoiceId;
        this.accountId = accountId;
        this.subscriptionId = subscriptionId;
        this.bundleId = bundleId;
        this.planName = planName;
        this.phaseName = phaseName;
        this.usageName = usageName;
        this.startDate = startDate;
        this.endDate = endDate;
        this.amount = amount;
        this.currency = currency;
        this.rate = rate;
        this.reversedItemId = reversedItemId;
        this.quantity = quantity;
        this.itemDetails = itemDetails;
    }

    @Override
    public UUID getId() {
        return id;
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
    public String getProductName() {
        return null;
    }

    @Override
    public String getPrettyProductName() {
        return null;
    }

    @Override
    public String getPlanName() {
        return planName;
    }

    @Override
    public String getPrettyPlanName() {
        return planName;
    }

    @Override
    public String getPhaseName() {
        return phaseName;
    }

    @Override
    public String getPrettyPhaseName() {
        return phaseName;
    }

    @Override
    public String getUsageName() {
        return usageName;
    }

    @Override
    public String getPrettyUsageName() {
        return usageName;
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
    public InvoiceItemType getInvoiceItemType() {
        return InvoiceItemType.RECURRING;
    }

    @Override
    public String getDescription() {
        return String.format("%s from %s to %s", phaseName, startDate.toString(), endDate.toString());
    }

    @Override
    public UUID getLinkedItemId() {
        return reversedItemId;
    }

    @Override
    public Integer getQuantity() { return quantity; }

    @Override
    public String getItemDetails() { return itemDetails; }

    @Override
    public boolean matches(final Object other) {
        throw new UnsupportedOperationException();
    }

    public boolean reversesItem() {
        return (reversedItemId != null);
    }

    @Override
    public BigDecimal getRate() {
        return rate;
    }

    @Override
    public UUID getChildAccountId() {
        return null;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();

        sb.append(phaseName).append(", ");
        sb.append(startDate.toString()).append(", ");
        sb.append(endDate.toString()).append(", ");
        sb.append(amount.toString()).append(", ");
        sb.append("subscriptionId = ").append(subscriptionId == null ? null : subscriptionId.toString()).append(", ");
        sb.append("bundleId = ").append(bundleId == null ? null : bundleId.toString()).append(", ");

        return sb.toString();
    }
}
