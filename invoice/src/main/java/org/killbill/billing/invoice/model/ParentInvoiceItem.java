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
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.InvoiceItemType;

import com.google.common.base.MoreObjects;

public class ParentInvoiceItem extends InvoiceItemBase {

    public ParentInvoiceItem(@Nullable final UUID id, @Nullable final DateTime createdDate, final UUID invoiceId, final UUID accountId, final UUID childAccountId,
                             final BigDecimal amount, final Currency currency, final String description) {
        super(id, createdDate, invoiceId, accountId, childAccountId, amount, currency, description, InvoiceItemType.PARENT_SUMMARY);
    }

    @Override
    public String getDescription() {
        return MoreObjects.firstNonNull(description, "Parent summary item");
    }
}
