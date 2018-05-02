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

public class ExternalChargeInvoiceItem extends InvoiceItemCatalogBase {

    public ExternalChargeInvoiceItem(final UUID invoiceId, final UUID accountId, @Nullable final UUID bundleId, @Nullable final String description,
                                     final LocalDate startDate, final LocalDate endDate, final BigDecimal amount, final Currency currency, @Nullable final String itemDetails) {
        this(UUIDs.randomUUID(), invoiceId, accountId, bundleId, description, startDate, endDate, amount, currency, itemDetails);
    }

    public ExternalChargeInvoiceItem(final UUID id, final UUID invoiceId, final UUID accountId, @Nullable final UUID bundleId,
                                     @Nullable final String description, final LocalDate startDate, final LocalDate endDate, final BigDecimal amount, final Currency currency, @Nullable final String itemDetails) {
        this(id, null, invoiceId, accountId, bundleId, description, startDate, endDate, amount, currency, itemDetails);
    }

    public ExternalChargeInvoiceItem(final UUID id, @Nullable final DateTime createdDate, final UUID invoiceId, final UUID accountId, @Nullable final UUID bundleId,
                                     @Nullable final String description, final LocalDate startDate, final LocalDate endDate, final BigDecimal amount, final Currency currency, @Nullable final String itemDetails) {
        super(id, createdDate, invoiceId, accountId, bundleId, null, description, null, null, null, null, startDate, endDate, amount, null, currency, null, null, itemDetails, InvoiceItemType.EXTERNAL_CHARGE);
    }

    public ExternalChargeInvoiceItem(final UUID id, @Nullable final DateTime createdDate, final UUID invoiceId, final UUID accountId, final UUID bundleId, final UUID subscriptionId,
                                     final String productName, final String planName, final String phaseName, final String prettyProductName, final String prettyPlanName, final String prettyPhaseName, @Nullable final String description, final LocalDate startDate, final LocalDate endDate,
                                     final BigDecimal amount, final BigDecimal rate, final Currency currency, @Nullable final UUID linkedItemId, @Nullable String itemDetails) {
        super(id, createdDate, invoiceId, accountId, bundleId, subscriptionId, description, productName, planName, phaseName, null, prettyProductName, prettyPlanName, prettyPhaseName, null, startDate, endDate, amount, rate, currency, linkedItemId, null, itemDetails, InvoiceItemType.EXTERNAL_CHARGE);
    }

    @Override
    public String getDescription() {
        if (description != null) {
            return description;
        }

        if (getPlanName() == null) {
            return "External charge";
        } else {
            return String.format("%s (external charge)", getPlanName());
        }
    }
}
