/*
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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

public abstract class InvoiceItemCatalogBase extends InvoiceItemBase implements InvoiceItem {

    protected final String planName;
    protected final String phaseName;
    protected final String usageName;

    public InvoiceItemCatalogBase(final UUID id, @Nullable final DateTime createdDate, final UUID invoiceId, final UUID accountId, @Nullable final UUID bundleId,
                                  @Nullable final UUID subscriptionId, @Nullable final String description, @Nullable final String planName, @Nullable final String phaseName, @Nullable final String usageName,
                                  final LocalDate startDate, final LocalDate endDate, final BigDecimal amount, final BigDecimal rate, final Currency currency, @Nullable final UUID linkedItemId) {
        super(id, createdDate, invoiceId, accountId, bundleId, subscriptionId, description, startDate, endDate, amount, rate, currency);
        this.planName = planName;
        this.phaseName = phaseName;
        this.usageName = usageName;
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
    public String getUsageName() {
        return usageName;
    }


    @Override
    public String getPrettyPlanName() {
        return null;
    }

    @Override
    public String getPrettyPhaseName() {
        return null;
    }

    @Override
    public String getPrettyUsageName() {
        return null;
    }


    @Override
    public boolean matches(final Object o) {

        if (!super.matches(o)) {
            return  false;
        }
        final InvoiceItemCatalogBase that = (InvoiceItemCatalogBase) o;
        if (phaseName != null ? !phaseName.equals(that.phaseName) : that.phaseName != null) {
            return false;
        }
        if (planName != null ? !planName.equals(that.planName) : that.planName != null) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (planName != null ? planName.hashCode() : 0);
        result = 31 * result + (phaseName != null ? phaseName.hashCode() : 0);
        result = 31 * result + (usageName != null ? usageName.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        // Note: we don't use all fields here, as the output would be overwhelming
        // (we output all invoice items as they are generated).
        final StringBuilder sb = new StringBuilder();
        sb.append(getInvoiceItemType());
        sb.append("{");
        if (planName != null) {
            sb.append("planName=").append(planName);
        }
        if (phaseName != null) {
            sb.append("phaseName=").append(phaseName);
        }
        if (usageName != null) {
            sb.append("usageName=").append(usageName);
        }
        if (startDate != null) {
            sb.append("startDate=").append(startDate);
        }
        if (endDate != null) {
            sb.append("endDate=").append(endDate);
        }
        if (amount != null) {
            sb.append("amount=").append(amount);
        }
        if (rate != null) {
            sb.append("rate=").append(rate);
        }
        if (subscriptionId != null) {
            sb.append("subscriptionId=").append(subscriptionId);
        }
        if (linkedItemId != null) {
            sb.append("linkedItemId=").append(linkedItemId);
        }
        sb.append('}');
        return sb.toString();
    }

}
