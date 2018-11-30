/*
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

public class InvoiceItemCatalogBase extends InvoiceItemBase implements InvoiceItem {

    protected final String productName;
    protected final String planName;
    protected final String phaseName;
    protected final String usageName;

    protected final String prettyProductName;
    protected final String prettyPlanName;
    protected final String prettyPhaseName;
    protected final String prettyUsageName;

    public InvoiceItemCatalogBase(final UUID id, @Nullable final DateTime createdDate, final UUID invoiceId, final UUID accountId, @Nullable final UUID bundleId,
                                  @Nullable final UUID subscriptionId, @Nullable final String description, @Nullable final String productName, @Nullable final String planName, @Nullable final String phaseName, @Nullable final String usageName,
                                  final LocalDate startDate, final LocalDate endDate, final BigDecimal amount, final BigDecimal rate, final Currency currency, @Nullable final UUID linkedItemId, final InvoiceItemType invoiceItemType) {
        this(id, createdDate, invoiceId, accountId, bundleId, subscriptionId, description, productName, planName, phaseName, usageName, null, null, null, null, startDate, endDate, amount, rate, currency, linkedItemId, null, null, invoiceItemType);
    }

    public InvoiceItemCatalogBase(final UUID id, @Nullable final DateTime createdDate, final UUID invoiceId, final UUID accountId, @Nullable final UUID bundleId,
                                  @Nullable final UUID subscriptionId, @Nullable final String description, @Nullable final String productName, @Nullable final String planName, @Nullable final String phaseName, @Nullable final String usageName,
                                  final LocalDate startDate, final LocalDate endDate, final BigDecimal amount, final BigDecimal rate, final Currency currency, @Nullable final UUID linkedItemId,
                                  @Nullable final Integer quantity, @Nullable final String itemDetails, final InvoiceItemType invoiceItemType) {
        this(id, createdDate, invoiceId, accountId, bundleId, subscriptionId, description, productName, planName, phaseName, usageName, null, null, null, null, startDate, endDate, amount, rate, currency, linkedItemId, quantity, itemDetails, invoiceItemType);
    }

    public InvoiceItemCatalogBase(final UUID id, @Nullable final DateTime createdDate, final UUID invoiceId, final UUID accountId, @Nullable final UUID bundleId,
                                  @Nullable final UUID subscriptionId, @Nullable final String description, @Nullable final String productName, @Nullable final String planName, @Nullable final String phaseName,
                                  @Nullable final String usageName, @Nullable final String prettyProductName, @Nullable final String prettyPlanName, @Nullable final String prettyPhaseName, @Nullable final String prettyUsageName,
                                  final LocalDate startDate, final LocalDate endDate, final BigDecimal amount, final BigDecimal rate, final Currency currency, @Nullable final UUID linkedItemId, final InvoiceItemType invoiceItemType) {
        this(id, createdDate, invoiceId, accountId, bundleId, subscriptionId, description, productName, planName, phaseName, usageName, prettyProductName, prettyPlanName, prettyPhaseName, prettyUsageName,
             startDate, endDate, amount, rate, currency, linkedItemId, null, null, invoiceItemType);
    }

    public InvoiceItemCatalogBase(final UUID id, @Nullable final DateTime createdDate, final UUID invoiceId, final UUID accountId, @Nullable final UUID bundleId,
                                  @Nullable final UUID subscriptionId, @Nullable final String description, @Nullable final String productName, @Nullable final String planName, @Nullable final String phaseName, @Nullable final String usageName,
                                  @Nullable final String prettyProductName, @Nullable final String prettyPlanName, @Nullable final String prettyPhaseName, @Nullable final String prettyUsageName,
                                  final LocalDate startDate, final LocalDate endDate, final BigDecimal amount, final BigDecimal rate, final Currency currency, @Nullable final UUID linkedItemId,
                                  @Nullable final Integer quantity, @Nullable final String itemDetails, final InvoiceItemType invoiceItemType) {
        super(id, createdDate, invoiceId, accountId, bundleId, subscriptionId, description, startDate, endDate, amount, rate, currency, linkedItemId, quantity, itemDetails, invoiceItemType);
        this.productName = productName;
        this.planName = planName;
        this.phaseName = phaseName;
        this.usageName = usageName;
        this.prettyProductName = prettyProductName;
        this.prettyPlanName = prettyPlanName;
        this.prettyPhaseName = prettyPhaseName;
        this.prettyUsageName = prettyUsageName;
    }

    @Override
    public String getProductName() {
        return productName;
    }

    @Override
    public String getPrettyProductName() {
        return prettyProductName;
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
        return prettyPlanName;
    }

    @Override
    public String getPrettyPhaseName() {
        return prettyPhaseName;
    }

    @Override
    public String getPrettyUsageName() {
        return prettyUsageName;
    }

    @Override
    public boolean matches(final Object o) {

        if (!super.matches(o)) {
            return false;
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
        final StringBuilder sb = new StringBuilder().append(invoiceItemType).append("{");
        sb.append("productName='").append(productName).append('\'');
        sb.append(", planName='").append(planName).append('\'');
        sb.append(", phaseName='").append(phaseName).append('\'');
        sb.append(", usageName='").append(usageName).append('\'');
        sb.append(", startDate=").append(startDate);
        sb.append(", endDate=").append(endDate);
        sb.append(", amount=").append(amount);
        sb.append(", subscriptionId=").append(subscriptionId);
        sb.append(", rate=").append(rate);
        sb.append(", linkedItemId=").append(linkedItemId);
        sb.append('}');
        return sb.toString();
    }
}
