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

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.InvoiceItem;
import org.joda.time.DateTime;

import java.math.BigDecimal;
import java.util.UUID;

public class FixedPriceInvoiceItem extends InvoiceItemBase {
    public FixedPriceInvoiceItem(UUID invoiceId, UUID subscriptionId, String planName, String phaseName, DateTime startDate, DateTime endDate, BigDecimal amount, Currency currency) {
        super(invoiceId, subscriptionId, planName, phaseName, startDate, endDate, amount, currency);
    }

    public FixedPriceInvoiceItem(UUID id, UUID invoiceId, UUID subscriptionId, String planName, String phaseName, DateTime startDate, DateTime endDate, BigDecimal amount, Currency currency) {
        super(id, invoiceId, subscriptionId, planName, phaseName, startDate, endDate, amount, currency);
    }

    @Override
    public InvoiceItem asCredit() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getDescription() {
        return String.format("%s (fixed price) on %s", getPhaseName(), getStartDate().toString());
    }

    @Override
    public int hashCode() {
        int result = subscriptionId != null ? subscriptionId.hashCode() : 0;
        result = 31 * result + (planName != null ? planName.hashCode() : 0);
        result = 31 * result + (phaseName != null ? phaseName.hashCode() : 0);
        result = 31 * result + (startDate != null ? startDate.hashCode() : 0);
        result = 31 * result + (endDate != null ? endDate.hashCode() : 0);
        result = 31 * result + (amount != null ? amount.hashCode() : 0);
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        return result;
    }

    @Override
    public int compareTo(InvoiceItem item) {
        if (!(item instanceof FixedPriceInvoiceItem)) {
            return 1;
        }

        FixedPriceInvoiceItem that = (FixedPriceInvoiceItem) item;
        int compareSubscriptions = getSubscriptionId().compareTo(that.getSubscriptionId());

        if (compareSubscriptions == 0) {
            return getStartDate().compareTo(that.getStartDate());
        } else {
            return compareSubscriptions;
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append(phaseName).append(", ");
        sb.append(startDate.toString()).append(", ");
        sb.append(endDate.toString()).append(", ");
        sb.append(amount.toString()).append(", ");

        return sb.toString();
//        StringBuilder sb = new StringBuilder();
//        sb.append("InvoiceItem = {").append("id = ").append(id.toString()).append(", ");
//        sb.append("invoiceId = ").append(invoiceId.toString()).append(", ");
//        sb.append("subscriptionId = ").append(subscriptionId.toString()).append(", ");
//        sb.append("planName = ").append(planName).append(", ");
//        sb.append("phaseName = ").append(phaseName).append(", ");
//        sb.append("startDate = ").append(startDate.toString()).append(", ");
//        sb.append("endDate = ").append(endDate.toString()).append(", ");
//
//        sb.append("amount = ");
//        if (amount == null) {
//            sb.append("null");
//        } else {
//            sb.append(amount.toString());
//        }
//
//        sb.append("}");
//        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FixedPriceInvoiceItem that = (FixedPriceInvoiceItem) o;

        if (amount != null ? !amount.equals(that.amount) : that.amount != null) return false;
        if (currency != that.currency) return false;
        if (startDate != null ? !startDate.equals(that.startDate) : that.startDate != null) return false;
        if (endDate != null ? !endDate.equals(that.endDate) : that.endDate != null) return false;
        if (phaseName != null ? !phaseName.equals(that.phaseName) : that.phaseName != null) return false;
        if (planName != null ? !planName.equals(that.planName) : that.planName != null) return false;
        if (subscriptionId != null ? !subscriptionId.equals(that.subscriptionId) : that.subscriptionId != null)
            return false;

        return true;
    }
}
