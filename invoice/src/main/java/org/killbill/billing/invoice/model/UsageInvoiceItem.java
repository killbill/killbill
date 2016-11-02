/*
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

import com.google.common.base.MoreObjects;

public class UsageInvoiceItem extends InvoiceItemBase {

    public UsageInvoiceItem(final UUID invoiceId, final UUID accountId, @Nullable final UUID bundleId, @Nullable final UUID subscriptionId,
                            final String planName, final String phaseName, final String usageName,
                            final LocalDate startDate, final LocalDate endDate, final BigDecimal amount, final Currency currency) {
        this(UUIDs.randomUUID(), null, invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, usageName, startDate, endDate, null, amount, currency);
    }

    public UsageInvoiceItem(final UUID id, @Nullable final DateTime createdDate, final UUID invoiceId, final UUID accountId, final UUID bundleId,
                            final UUID subscriptionId, final String planName, final String phaseName, final String usageName,
                            final LocalDate startDate, final LocalDate endDate, @Nullable final String description, final BigDecimal amount, final Currency currency) {
        super(id, createdDate, invoiceId, accountId, bundleId, subscriptionId, description, planName, phaseName, usageName, startDate, endDate, amount, currency);
    }

    @Override
    public InvoiceItemType getInvoiceItemType() {
        return InvoiceItemType.USAGE;
    }

    @Override
    public String getDescription() {
        return MoreObjects.firstNonNull(description, usageName);
    }
}
