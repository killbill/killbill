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

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoiceItemType;

public class FixedPriceInvoiceItem extends InvoiceItemBase {

    public FixedPriceInvoiceItem(final UUID invoiceId, final UUID accountId, @Nullable final UUID bundleId, @Nullable final UUID subscriptionId, final String planName, final String phaseName,
                                 final DateTime startDate, final DateTime endDate, final BigDecimal amount, final Currency currency) {
        super(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, amount, currency);
    }

    public FixedPriceInvoiceItem(final UUID id, final UUID invoiceId, final UUID accountId, final UUID bundleId, final UUID subscriptionId, final String planName, final String phaseName,
                                 final DateTime startDate, final DateTime endDate, final BigDecimal amount, final Currency currency) {
        super(id, invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, amount, currency);
    }

    @Override
    public String getDescription() {
        return String.format("%s (fixed price) on %s", getPhaseName(), getStartDate().toString());
    }

    @Override
    public int hashCode() {
        int result = accountId.hashCode();
        result = 31 * result + (subscriptionId != null ? subscriptionId.hashCode() : 0);
        result = 31 * result + (bundleId != null ? bundleId.hashCode() : 0);
        result = 31 * result + (planName != null ? planName.hashCode() : 0);
        result = 31 * result + (phaseName != null ? phaseName.hashCode() : 0);
        result = 31 * result + (startDate != null ? startDate.hashCode() : 0);
        result = 31 * result + (endDate != null ? endDate.hashCode() : 0);
        result = 31 * result + amount.hashCode();
        result = 31 * result + currency.hashCode();
        return result;
    }

    @Override
    public int compareTo(final InvoiceItem item) {
        if (!(item instanceof FixedPriceInvoiceItem)) {
            return 1;
        }

        final FixedPriceInvoiceItem that = (FixedPriceInvoiceItem) item;
        final int compareAccounts = getAccountId().compareTo(that.getAccountId());
        if (compareAccounts == 0 && bundleId != null) {
            final int compareBundles = getBundleId().compareTo(that.getBundleId());
            if (compareBundles == 0 && subscriptionId != null) {
                final int compareSubscriptions = getSubscriptionId().compareTo(that.getSubscriptionId());
                if (compareSubscriptions == 0) {
                    return getStartDate().compareTo(that.getStartDate());
                } else {
                    return compareSubscriptions;
                }
            } else {
                return compareBundles;
            }
        } else {
            return compareAccounts;
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("InvoiceItem = {").append("id = ").append(id.toString()).append(", ");
        sb.append("invoiceId = ").append(invoiceId.toString()).append(", ");
        sb.append("accountId = ").append(accountId.toString()).append(", ");
        sb.append("subscriptionId = ").append(subscriptionId == null ? null : subscriptionId.toString()).append(", ");
        sb.append("bundleId = ").append(bundleId == null ? null : bundleId.toString()).append(", ");
        sb.append("planName = ").append(planName).append(", ");
        sb.append("phaseName = ").append(phaseName).append(", ");
        sb.append("startDate = ").append(startDate.toString()).append(", ");
        sb.append("endDate = ").append(endDate.toString()).append(", ");

        sb.append("amount = ");
        if (amount == null) {
            sb.append("null");
        } else {
            sb.append(amount.toString());
        }

        sb.append("}");
        return sb.toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final FixedPriceInvoiceItem that = (FixedPriceInvoiceItem) o;
        if (accountId.compareTo(that.accountId) != 0) {
            return false;
        }
        if (subscriptionId != null ? !subscriptionId.equals(that.subscriptionId) : that.subscriptionId != null) {
            return false;
        }
        if (bundleId != null ? !bundleId.equals(that.bundleId) : that.bundleId != null) {
            return false;
        }
        if (amount != null ? amount.compareTo(that.amount) != 0 : that.amount != null) {
            return false;
        }
        if (currency != that.currency) {
            return false;
        }
        if (startDate != null ? startDate.compareTo(that.startDate) != 0 : that.startDate != null) {
            return false;
        }
        if (endDate != null ? endDate.compareTo(that.endDate) != 0 : that.endDate != null) {
            return false;
        }
        if (phaseName != null ? !phaseName.equals(that.phaseName) : that.phaseName != null) {
            return false;
        }
        if (planName != null ? !planName.equals(that.planName) : that.planName != null) {
            return false;
        }

        return true;
    }

    @Override
    public InvoiceItemType getInvoiceItemType() {
        return InvoiceItemType.FIXED;
    }
}
